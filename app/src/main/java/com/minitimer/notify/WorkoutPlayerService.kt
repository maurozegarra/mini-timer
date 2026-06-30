package com.minitimer.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.minitimer.MainActivity
import com.minitimer.PlayerBus
import com.minitimer.PlayerCommand
import com.minitimer.PlayerSnapshot
import com.minitimer.R
import com.minitimer.data.BackupManager
import com.minitimer.data.SettingsStore
import com.minitimer.data.WorkoutStore
import com.minitimer.i18n.I18n
import com.minitimer.model.ConfirmMode
import com.minitimer.model.DisplayMode
import com.minitimer.model.PlayerStep
import com.minitimer.model.SessionLog
import com.minitimer.model.StepKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Servicio en primer plano que ejecuta una rutina (player) de forma robusta:
 * sobrevive a que la app pase a segundo plano, se apague la pantalla o se cierre
 * la Activity. Mantiene el recorrido (cuenta regresiva + auto-avance), reproduce
 * el cue de alarma en las transiciones, publica el estado en [PlayerBus] y
 * muestra una notificación con el paso actual y la cuenta.
 */
class WorkoutPlayerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private val alarm by lazy { WorkoutAlarm(this) }

    private var steps: List<PlayerStep> = emptyList()
    private var index = 0
    private var running = false
    private var finished = false
    private var endAt = 0L
    private var remainingMs = 0L
    private var name = ""
    private var workoutId = 0L
    private var lastShownSec = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BackupManager.init(this)
        ensureChannel()
        startForegroundCompat(buildNotification())
        if (restore()) {
            publish()
            startTick()
        }
        collectCommands()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_STEPS)?.let { json ->
                steps = decodeSteps(json)
                workoutId = intent.getLongExtra(EXTRA_WORKOUT_ID, 0L)
                name = intent.getStringExtra(EXTRA_NAME) ?: ""
                if (steps.isNotEmpty()) {
                    finished = false
                    beginStep(0)
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_NEXT -> advance()
            ACTION_STOP -> stopPlayer()
        }
        return START_STICKY
    }

    private fun collectCommands() {
        scope.launch {
            PlayerBus.command.collect { cmd ->
                when (cmd) {
                    PlayerCommand.PAUSE -> pause()
                    PlayerCommand.RESUME -> resume()
                    PlayerCommand.NEXT -> advance()
                    PlayerCommand.STOP -> stopPlayer()
                }
            }
        }
    }

    // ---------- Máquina de estados ----------

    private fun beginStep(i: Int) {
        index = i
        val step = steps.getOrNull(i) ?: return finishPlayer()
        if (step.manual) {
            running = false
            remainingMs = 0L
            stopTick()
        } else {
            remainingMs = step.durationSec * 1000L
            running = true
            endAt = System.currentTimeMillis() + remainingMs
            startTick()
        }
        publishAndNotify()
        persist()
    }

    private fun pause() {
        if (!running) return
        remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        running = false
        stopTick()
        publishAndNotify()
        persist()
    }

    private fun resume() {
        val step = steps.getOrNull(index) ?: return
        if (step.manual || running || finished) return
        running = true
        endAt = System.currentTimeMillis() + remainingMs
        startTick()
        publishAndNotify()
        persist()
    }

    private fun advance() {
        if (finished) return
        val next = index + 1
        if (next >= steps.size) {
            finishPlayer()
        } else {
            alarmCue()
            beginStep(next)
        }
    }

    private fun finishPlayer() {
        running = false
        finished = true
        stopTick()
        alarmCue()
        recordSession()
        publish()
        clearPersist()
        // Notificación final no-ongoing y salir del primer plano.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildCompletedNotification())
        stopSelf()
    }

    private fun stopPlayer() {
        running = false
        stopTick()
        alarm.stop()
        clearPersist()
        PlayerBus.state.value = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        stopSelf()
    }

    private fun startTick() {
        stopTick()
        tickJob = scope.launch {
            while (running) {
                val left = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
                remainingMs = left
                if (left <= 0L) {
                    advance()
                    break
                }
                publish()
                val sec = (left + 999) / 1000
                if (sec != lastShownSec) {
                    lastShownSec = sec
                    updateNotification()
                }
                delay(200)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun alarmCue() {
        alarm.playCue()
        scope.launch {
            delay(2000)
            alarm.stop()
        }
    }

    private fun recordSession() {
        try {
            val store = WorkoutStore(this)
            val list = store.loadSessions().toMutableList()
            list.add(0, SessionLog(System.currentTimeMillis(), workoutId, name, System.currentTimeMillis()))
            store.saveSessions(list)
        } catch (_: Exception) {
        }
    }

    // ---------- Publicación / notificación ----------

    private fun currentRemaining(): Long {
        val step = steps.getOrNull(index) ?: return 0L
        if (step.manual) return 0L
        return if (running) (endAt - System.currentTimeMillis()).coerceAtLeast(0L) else remainingMs
    }

    private fun publish() {
        val step = steps.getOrNull(index) ?: return
        PlayerBus.state.value = PlayerSnapshot(
            trainingId = workoutId,
            name = name,
            index = index,
            totalSteps = steps.size,
            stepKind = step.kind,
            stepTitle = step.title,
            note = step.note,
            ownerName = step.ownerName,
            ownerExerciseId = step.ownerExerciseId,
            workoutName = step.workoutName,
            workoutIndex = step.workoutIndex,
            totalWorkouts = step.totalWorkouts,
            setIndex = step.setIndex,
            totalSets = step.totalSets,
            reps = step.reps,
            timeBased = step.timeBased,
            display = step.display,
            finalCount = step.finalCount,
            colorArgb = step.colorArgb,
            weighted = step.weighted,
            weightTotal = step.weightTotal,
            weightLabel = step.weightLabel,
            remainingMs = currentRemaining(),
            running = running,
            finished = finished,
        )
    }

    private fun publishAndNotify() {
        publish()
        lastShownSec = -1L
        updateNotification()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun stepTitleText(step: PlayerStep?, t: com.minitimer.i18n.Strings): String = when (step?.kind) {
        StepKind.PREP -> t.getReady
        StepKind.REST -> t.rest
        StepKind.COOLDOWN -> t.cooldown
        StepKind.WORK -> step.title.ifBlank { t.exercise }
        null -> t.tabAthlete
    }

    private fun buildNotification(): Notification {
        val t = I18n.get(SettingsStore(this).load().language)
        val step = steps.getOrNull(index)
        val title = stepTitleText(step, t)
        val manual = step?.manual == true
        val info = if (manual && step?.kind == StepKind.WORK && !step.timeBased) {
            "${step.reps} ${t.repsUnit}"
        } else {
            fmtClock(currentRemaining())
        }
        val round = if (step != null && step.kind == StepKind.WORK && step.totalSets > 1) {
            " · ${step.setIndex + 1}/${step.totalSets}"
        } else ""

        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("$title$round")
            .setContentText(info)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setShowWhen(false)

        if (manual) {
            builder.addAction(action(R.drawable.ic_notif_play, t.doneLabel, ACTION_NEXT))
        } else {
            if (running) {
                builder.addAction(action(R.drawable.ic_notif_pause, t.pause, ACTION_PAUSE))
            } else {
                builder.addAction(action(R.drawable.ic_notif_play, t.resume, ACTION_RESUME))
            }
            builder.addAction(action(R.drawable.ic_notif_play, t.nextLabel, ACTION_NEXT))
        }
        builder.addAction(action(R.drawable.ic_notif_close, t.close, ACTION_STOP))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun buildCompletedNotification(): Notification {
        val t = I18n.get(SettingsStore(this).load().language)
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(t.workoutComplete)
            .setContentText(name)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
    }

    private fun action(icon: Int, title: String, actionName: String): Notification.Action {
        val intent = Intent(this, WorkoutPlayerService::class.java).setAction(actionName)
        val pi = PendingIntent.getService(
            this,
            actionName.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icon),
            title,
            pi,
        ).build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // ---------- Persistencia (restauración tras muerte del proceso) ----------

    private fun prefs() = getSharedPreferences("athlete_player", Context.MODE_PRIVATE)

    private fun persist() {
        prefs().edit()
            .putBoolean("active", true)
            .putLong("workoutId", workoutId)
            .putString("name", name)
            .putString("steps", encodeSteps(steps))
            .putInt("index", index)
            .putLong("endAt", endAt)
            .putLong("remainingMs", remainingMs)
            .putBoolean("running", running)
            .apply()
    }

    private fun clearPersist() {
        prefs().edit().clear().apply()
    }

    private fun restore(): Boolean {
        val p = prefs()
        if (!p.getBoolean("active", false)) return false
        steps = decodeSteps(p.getString("steps", "[]") ?: "[]")
        if (steps.isEmpty()) {
            clearPersist()
            return false
        }
        workoutId = p.getLong("workoutId", 0L)
        name = p.getString("name", "") ?: ""
        index = p.getInt("index", 0).coerceIn(0, steps.size - 1)
        running = p.getBoolean("running", false)
        finished = false
        val step = steps[index]
        if (step.manual) {
            running = false
            remainingMs = 0L
        } else if (running) {
            endAt = p.getLong("endAt", System.currentTimeMillis())
            remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        } else {
            remainingMs = p.getLong("remainingMs", step.durationSec * 1000L)
        }
        return true
    }

    // Serialización de pasos delegada al companion (encodeSteps/decodeSteps).

    override fun onDestroy() {
        super.onDestroy()
        stopTick()
        scope.cancel()
        alarm.stop()
    }

    companion object {
        private const val CHANNEL_ID = "mini_timer_workout_v1"
        private const val NOTIF_ID = 43
        private const val ACTION_START = "com.minitimer.player.START"
        private const val ACTION_PAUSE = "com.minitimer.player.PAUSE"
        private const val ACTION_RESUME = "com.minitimer.player.RESUME"
        private const val ACTION_NEXT = "com.minitimer.player.NEXT"
        private const val ACTION_STOP = "com.minitimer.player.STOP"
        private const val EXTRA_STEPS = "steps"
        private const val EXTRA_WORKOUT_ID = "workoutId"
        private const val EXTRA_NAME = "name"

        fun start(context: Context, trainingId: Long, name: String, steps: List<PlayerStep>) {
            val intent = Intent(context, WorkoutPlayerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_STEPS, encodeSteps(steps))
                .putExtra(EXTRA_WORKOUT_ID, trainingId)
                .putExtra(EXTRA_NAME, name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun encodeSteps(list: List<PlayerStep>): String {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("kind", s.kind.name)
                        .put("title", s.title)
                        .put("note", s.note)
                        .put("ownerName", s.ownerName)
                        .put("ownerExerciseId", s.ownerExerciseId)
                        .put("workoutName", s.workoutName)
                        .put("workoutIndex", s.workoutIndex)
                        .put("totalWorkouts", s.totalWorkouts)
                        .put("setIndex", s.setIndex)
                        .put("totalSets", s.totalSets)
                        .put("durationSec", s.durationSec)
                        .put("reps", s.reps)
                        .put("timeBased", s.timeBased)
                        .put("display", s.display.name)
                        .put("confirm", s.confirm.name)
                        .put("finalCount", s.finalCount)
                        .put("colorArgb", s.colorArgb)
                        .put("weighted", s.weighted)
                        .put("weightTotal", s.weightTotal)
                        .put("weightLabel", s.weightLabel),
                )
            }
            return arr.toString()
        }

        fun decodeSteps(json: String): List<PlayerStep> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlayerStep(
                    kind = StepKind.valueOf(o.getString("kind")),
                    title = o.optString("title", ""),
                    note = o.optString("note", ""),
                    ownerName = o.optString("ownerName", ""),
                    ownerExerciseId = o.optString("ownerExerciseId", ""),
                    workoutName = o.optString("workoutName", ""),
                    workoutIndex = o.optInt("workoutIndex", 0),
                    totalWorkouts = o.optInt("totalWorkouts", 1),
                    setIndex = o.optInt("setIndex", 0),
                    totalSets = o.optInt("totalSets", 1),
                    durationSec = o.optInt("durationSec", 0),
                    reps = o.optInt("reps", 0),
                    timeBased = o.optBoolean("timeBased", true),
                    display = runCatching { DisplayMode.valueOf(o.optString("display")) }.getOrDefault(DisplayMode.COUNTDOWN),
                    confirm = runCatching { ConfirmMode.valueOf(o.optString("confirm")) }.getOrDefault(ConfirmMode.AUTO),
                    finalCount = o.optInt("finalCount", 0),
                    colorArgb = o.optLong("colorArgb", 0xFF2E9E5BL),
                    weighted = o.optBoolean("weighted", false),
                    weightTotal = o.optDouble("weightTotal", 0.0),
                    weightLabel = o.optString("weightLabel", ""),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WorkoutPlayerService::class.java).setAction(ACTION_STOP),
            )
        }

        private fun fmtClock(ms: Long): String {
            val total = ((ms + 999) / 1000).toInt()
            return "%d:%02d".format(total / 60, total % 60)
        }
    }
}
