package com.minitimer

import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minitimer.audio.AlarmPlayer
import com.minitimer.data.SettingsStore
import com.minitimer.model.AlarmConfig
import com.minitimer.model.AppConfig
import com.minitimer.model.TimerItem
import com.minitimer.notify.LiveTimerService
import com.minitimer.util.dedupeSorted
import com.minitimer.util.formatRemaining
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Phase { IDLE, RUNNING, PAUSED, DONE }

/** Un tono de alarma disponible para seleccionar. */
data class AlarmSound(val name: String, val uri: String)

/** Límite (en dp) del ajuste fino del anillo en cada eje. */
private const val RING_OFFSET_LIMIT = 100

/** Categorías (subpantallas) de Ajustes. Los encabezados General/Timer
 *  agrupan estas categorías; la alarma vive dentro de cada pestaña. */
enum class SettingsSection { APPEARANCE, TIMER, OVERLAY, BACKUP, DEVELOPER }

/** Mini-app (pestaña) dueña de un bloque de alarma independiente. */
enum class AlarmScope { TIMER }

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val alarmPlayer = AlarmPlayer(app)

    var config by mutableStateOf(store.loadConfig())
        private set

    var digits by mutableStateOf("")
        private set
    var draftName by mutableStateOf("")
        private set

    /** Epoch (ms) del target seleccionado con chips "Termina a las"; 0 si ninguno. */
    var targetEpochMs by mutableStateOf(0L)
        private set

    var showSettings by mutableStateOf(false)

    /** Sección de Ajustes abierta (null = lista de categorías). */
    var settingsSection by mutableStateOf<SettingsSection?>(null)

    /** Abre Ajustes mostrando la lista de categorías. */
    fun openSettings() {
        settingsSection = null
        showSettings = true
    }

    /** Id del timer cuya pantalla de detalle está abierta; null si ninguna. */
    var detailId by mutableStateOf<Long?>(null)

    /** Lista de temporizadores (multi-timer). */
    val timers = mutableStateListOf<TimerItem>()

    /** Id del timer que ocupa el slot activo (RUNNING/PAUSED/DONE); null si ninguno. */
    var activeId by mutableStateOf<Long?>(null)
        private set

    // Offset fino (en dp) del anillo sobre la cámara, ajustable con +/- en ajustes.
    var ringOffsetX by mutableStateOf(store.loadRingOffset().first)
        private set
    var ringOffsetY by mutableStateOf(store.loadRingOffset().second)
        private set

    private var nextId = 1L
    private var tickJob: Job? = null
    private var autoDismissJob: Job? = null

    init {
        TimerBus.accent.value = config.general.accent
        publishOverlayPrefs()
        ensureDefaultAlarmSound()
        restore()
        val last = store.loadLastDuration()
        if (last > 0) digits = secondsToDigits(last)
        // Comandos desde los botones del Now Bar (Pausa/Reanudar/Cancelar).
        viewModelScope.launch {
            TimerBus.command.collect { cmd ->
                val id = activeId ?: return@collect
                when (cmd) {
                    TimerCommand.PAUSE -> pauseTimer(id)
                    TimerCommand.RESUME -> startTimer(id)
                    TimerCommand.CANCEL ->
                        if (item(id)?.phase == Phase.DONE) dismissTimer(id) else resetTimer(id)
                }
            }
        }
    }

    /** Recarga ajustes y timers desde el store (tras restaurar un respaldo). */
    fun reload() {
        tickJob?.cancel()
        autoDismissJob?.cancel()
        alarmPlayer.stop()
        config = store.loadConfig()
        ringOffsetX = store.loadRingOffset().first
        ringOffsetY = store.loadRingOffset().second
        TimerBus.accent.value = config.general.accent
        publishOverlayPrefs()
        restore()
        val last = store.loadLastDuration()
        digits = if (last > 0) secondsToDigits(last) else ""
    }

    /**
     * En el primer arranque asigna "Beep" como tono por defecto (si existe) a
     * cada pestaña cuya alarma aún no tenga sonido elegido.
     */
    private fun ensureDefaultAlarmSound() {
        val needs = config.timer.alarm.soundUri == null
        if (!needs) return
        val beep = findBeepSound() ?: return
        fun fill(a: AlarmConfig) =
            if (a.soundUri == null) a.copy(soundUri = beep.uri, soundName = beep.name) else a
        update(
            config.copy(
                timer = config.timer.copy(alarm = fill(config.timer.alarm)),
            ),
        )
    }

    /** Busca el tono "Beep" entre las alarmas del sistema; null si no existe. */
    private fun findBeepSound(): AlarmSound? {
        val ctx = getApplication<Application>()
        try {
            val rm = RingtoneManager(ctx).apply { setType(RingtoneManager.TYPE_ALARM) }
            val cursor = rm.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                if (title != null && title.contains("beep", ignoreCase = true)) {
                    return AlarmSound(title, rm.getRingtoneUri(cursor.position).toString())
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    // ---------- Lista de timers ----------
    private fun idx(id: Long) = timers.indexOfFirst { it.id == id }
    fun item(id: Long): TimerItem? = timers.firstOrNull { it.id == id }
    private val activeItem: TimerItem? get() = activeId?.let { item(it) }

    private fun setItem(id: Long, persist: Boolean = true, transform: (TimerItem) -> TimerItem) {
        val i = idx(id)
        if (i < 0) return
        timers[i] = transform(timers[i])
        if (persist) persist()
    }

    private fun persist() = store.saveTimers(timers.toList(), activeId)

    private fun syncService() {
        if (activeId != null) LiveTimerService.start(getApplication())
        else LiveTimerService.stop(getApplication())
    }

    /** Restaura la lista y normaliza el timer activo tras la muerte del proceso. */
    private fun restore() {
        val (loaded, active) = store.loadTimers()
        timers.clear()
        timers.addAll(loaded)
        nextId = (timers.maxOfOrNull { it.id } ?: 0L) + 1
        val a = active?.let { item(it) } ?: return
        when (a.phase) {
            Phase.RUNNING -> {
                val left = a.endAt - System.currentTimeMillis()
                if (left > 0L) {
                    setItem(a.id, persist = false) { it.copy(remainingMs = left) }
                    activeId = a.id
                    startTicking()
                } else {
                    // Terminó mientras el proceso estaba muerto: marcar DONE sin
                    // disparar la alarma de forma sorpresiva.
                    setItem(a.id, persist = false) {
                        it.copy(
                            phase = Phase.DONE,
                            remainingMs = 0,
                            lastFinished = System.currentTimeMillis(),
                        )
                    }
                    activeId = a.id
                }
            }
            Phase.PAUSED, Phase.DONE -> activeId = a.id
            Phase.IDLE -> activeId = null
        }
        syncService()
        publishActive()
        persist()
    }

    private fun secondsToDigits(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "%02d%02d%02d".format(h, m, s).trimStart('0')
    }

    // ---------- Tiempo configurado desde los dígitos ----------
    val setH: Int get() = digitsPadded().substring(0, 2).toInt()
    val setM: Int get() = digitsPadded().substring(2, 4).toInt()
    val setS: Int get() = digitsPadded().substring(4, 6).toInt()
    private val totalSeconds: Int get() = setH * 3600 + setM * 60 + setS

    private fun digitsPadded() = digits.padStart(6, '0')

    // ---------- Teclado ----------
    fun onKey(k: String) {
        digits = when (k) {
            "del" -> digits.dropLast(1)
            "00" -> when {
                digits.isEmpty() -> digits
                digits.length >= 6 -> digits
                digits.length >= 5 -> digits + "0"
                else -> digits + "00"
            }
            else -> when {
                digits.length >= 6 -> digits
                digits.isEmpty() && k == "0" -> digits
                else -> digits + k
            }
        }
    }

    // ---------- Nuevo timer (teclado) ----------
    fun updateDraftName(value: String) { draftName = value.take(40) }

    /** Fija la duración del borrador (rueda H/M/S) en la hoja "Nuevo". */
    fun setDraftTime(h: Int, m: Int, s: Int) {
        val sec = h * 3600 + m * 60 + s
        digits = if (sec > 0) secondsToDigits(sec) else ""
        targetEpochMs = 0L
    }

    /** Fija la duración del borrador desde un epoch objetivo (chip "Termina a las"). */
    fun setTargetTime(epochMs: Long, h: Int, m: Int, s: Int) {
        targetEpochMs = epochMs
    }

    /** Reajusta el total de un timer DETENIDO (IDLE) desde el detalle. */
    fun setTimerTotal(id: Long, sec: Int) {
        val it = item(id) ?: return
        if (it.phase != Phase.IDLE) return
        val ms = (sec * 1000L).coerceAtLeast(0)
        setItem(id) { c -> c.copy(totalMs = ms, remainingMs = ms) }
    }

    /** Descarta y vuelve a iniciar un timer (botón Reiniciar del detalle/done). */
    fun restartTimer(id: Long): Boolean {
        resetTimer(id)
        return startTimer(id)
    }

    /** Prepara la hoja "Nuevo": rellena con la última duración/nombre usados. */
    fun prepareNewTimer() {
        val last = store.loadLastDuration()
        digits = if (last > 0) secondsToDigits(last) else ""
        draftName = store.loadLastLabel()
        targetEpochMs = 0L
    }

    /**
     * Crea un timer con los dígitos actuales e intenta iniciarlo. Devuelve true
     * si se inició; false si quedó creado pero bloqueado por otro timer activo.
     */
    fun confirmNewTimer(): Boolean {
        if (targetEpochMs > 0) {
            val remMs = (targetEpochMs - System.currentTimeMillis()).coerceAtLeast(1000L)
            val id = addTimerMs(remMs, draftName)
            val started = startTimer(id, targetEpochMs)
            digits = ""
            draftName = ""
            targetEpochMs = 0L
            return started
        }
        val sec = totalSeconds
        if (sec <= 0) return true
        val id = addTimer(sec, draftName)
        val started = startTimer(id)
        digits = ""
        draftName = ""
        return started
    }

    fun addTimer(sec: Int, name: String): Long {
        val ms = sec * 1000L
        val id = nextId++
        timers.add(
            TimerItem(id = id, name = name.take(40), totalMs = ms, remainingMs = ms, phase = Phase.IDLE),
        )
        store.saveLastDuration(sec)
        if (name.isNotBlank()) store.saveLastLabel(name)
        persist()
        return id
    }

    /** Crea un timer con duración exacta en ms (para targets "Termina a las"). */
    private fun addTimerMs(ms: Long, name: String): Long {
        val id = nextId++
        timers.add(
            TimerItem(id = id, name = name.take(40), totalMs = ms, remainingMs = ms, phase = Phase.IDLE),
        )
        store.saveLastDuration((ms / 1000).toInt())
        if (name.isNotBlank()) store.saveLastLabel(name)
        persist()
        return id
    }

    // ---------- Acciones por timer ----------
    /** Inicia/reanuda un timer. Devuelve false si otro timer ocupa el slot. */
    fun startTimer(id: Long, targetEndAt: Long = 0L): Boolean {
        if (activeId != null && activeId != id) return false
        val it = item(id) ?: return false
        val rem = it.remainingMs.coerceAtLeast(0)
        if (rem <= 0) return false
        val end = if (targetEndAt > 0) targetEndAt else System.currentTimeMillis() + rem
        setItem(id) { c -> c.copy(phase = Phase.RUNNING, remainingMs = rem, endAt = end) }
        activeId = id
        syncService()
        publishActive()
        startTicking()
        return true
    }

    fun pauseTimer(id: Long) {
        val it = item(id) ?: return
        if (it.phase != Phase.RUNNING) return
        val rem = (it.endAt - System.currentTimeMillis()).coerceAtLeast(0)
        tickJob?.cancel()
        setItem(id) { c -> c.copy(phase = Phase.PAUSED, remainingMs = rem) }
        publishActive()
    }

    /** Play/Pausa de la tarjeta. Devuelve false si el inicio quedó bloqueado. */
    fun togglePlay(id: Long): Boolean {
        val it = item(id) ?: return true
        return when (it.phase) {
            Phase.RUNNING -> { pauseTimer(id); true }
            Phase.PAUSED, Phase.IDLE -> startTimer(id)
            Phase.DONE -> true
        }
    }

    /** Detiene y deja el timer en IDLE (vuelve a su total). Libera el slot. */
    fun resetTimer(id: Long) {
        val wasActive = activeId == id
        if (wasActive) {
            tickJob?.cancel()
            autoDismissJob?.cancel()
            alarmPlayer.stop()
        }
        setItem(id, persist = false) { c -> c.copy(phase = Phase.IDLE, remainingMs = c.totalMs, endAt = 0L) }
        if (wasActive) {
            activeId = null
            clearBus()
            syncService()
        }
        persist()
    }

    /** Descarta un timer terminado (detiene alarma) y lo deja en IDLE. */
    fun dismissTimer(id: Long) = resetTimer(id)

    // Timer eliminado recientemente, para permitir undo.
    private var lastDeletedTimer: TimerItem? = null
    private var lastDeletedIndex: Int = -1

    fun deleteTimer(id: Long) {
        val wasActive = activeId == id
        if (wasActive) {
            tickJob?.cancel()
            autoDismissJob?.cancel()
            alarmPlayer.stop()
        }
        val index = idx(id)
        lastDeletedTimer = timers.firstOrNull { it.id == id }
        lastDeletedIndex = index
        timers.removeAll { it.id == id }
        if (wasActive) {
            activeId = null
            clearBus()
            syncService()
        }
        persist()
    }

    /** Restaura el último timer eliminado. Retorna true si se restauró. */
    fun undoDelete(): Boolean {
        val deleted = lastDeletedTimer ?: return false
        val insertAt = lastDeletedIndex.coerceIn(0, timers.size)
        timers.add(insertAt, deleted)
        lastDeletedTimer = null
        lastDeletedIndex = -1
        persist()
        return true
    }

    fun addTime(id: Long) {
        val incMs = config.timer.addIncrementSec * 1000L
        setItem(id) { c ->
            c.copy(
                totalMs = c.totalMs + incMs,
                remainingMs = c.remainingMs + incMs,
                endAt = if (c.phase == Phase.RUNNING) c.endAt + incMs else c.endAt,
            )
        }
        if (activeId == id) publishActive()
    }

    fun toggleStar(id: Long) = setItem(id) { it.copy(starred = !it.starred) }

    /** Reordena la lista de timers (arrastre manual) y persiste el nuevo orden. */
    fun moveTimer(from: Int, to: Int) {
        if (from == to) return
        if (from !in timers.indices || to !in timers.indices) return
        timers.add(to, timers.removeAt(from))
        persist()
    }

    fun renameTimer(id: Long, name: String) {
        setItem(id) { it.copy(name = name.take(40)) }
        if (activeId == id) publishActive()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val a = activeItem
                if (a == null || a.phase != Phase.RUNNING) break
                val left = a.endAt - System.currentTimeMillis()
                if (left <= 0L) {
                    finishTimer(a.id)
                    break
                }
                setItem(a.id, persist = false) { it.copy(remainingMs = left) }
                publishActive()
                delay(100)
            }
        }
    }

    private fun finishTimer(id: Long) {
        setItem(id) { it.copy(phase = Phase.DONE, remainingMs = 0, lastFinished = System.currentTimeMillis()) }
        publishActive()
        alarmPlayer.start(config.timer.alarm, count = config.timer.alarm.count.coerceAtLeast(1))
        val secs = config.timer.autoDismiss
        if (secs > 0) {
            autoDismissJob?.cancel()
            autoDismissJob = viewModelScope.launch {
                delay(secs * 1000L)
                dismissTimer(id)
            }
        }
    }

    // ---------- Bus (refleja el timer activo) ----------
    private fun publishActive() {
        val a = activeItem
        if (a == null) {
            clearBus()
            return
        }
        TimerBus.done.value = a.phase == Phase.DONE
        TimerBus.paused.value = a.phase == Phase.PAUSED
        TimerBus.remainingMs.value = a.remainingMs
        TimerBus.totalMs.value = a.totalMs
        TimerBus.endAt.value = a.endAt
        TimerBus.label.value = a.name
        TimerBus.display.value =
            if (a.phase == Phase.DONE) com.minitimer.i18n.I18n.get(config.general.language).timeUp
            else formatRemaining(a.remainingMs)
    }

    private fun clearBus() {
        TimerBus.done.value = false
        TimerBus.paused.value = false
        TimerBus.remainingMs.value = 0L
        TimerBus.totalMs.value = 0L
        TimerBus.endAt.value = 0L
        TimerBus.label.value = ""
        TimerBus.display.value = ""
    }

    // ---------- Ajustes ----------
    private fun update(newConfig: AppConfig) {
        config = newConfig
        store.saveConfig(newConfig)
        TimerBus.accent.value = newConfig.general.accent
        publishOverlayPrefs()
    }

    /** Publica al [TimerBus] los interruptores de anillo/overlay/Now Bar (Timer). */
    private fun publishOverlayPrefs() {
        TimerBus.showRing.value = config.timer.showRing
        TimerBus.showOverlay.value = config.timer.showOverlay
        TimerBus.showNowBar.value = config.timer.showNowBar
    }

    // General (toda la app)
    fun setLanguage(lang: String) = update(config.copy(general = config.general.copy(language = lang)))
    fun setAccent(color: Long) = update(config.copy(general = config.general.copy(accent = color)))
    fun setThemeMode(mode: Int) = update(config.copy(general = config.general.copy(themeMode = mode)))
    fun setDeveloperMode(value: Boolean) =
        update(config.copy(general = config.general.copy(developerMode = value)))

    // Timer
    fun setAutoDismiss(sec: Int) = update(config.copy(timer = config.timer.copy(autoDismiss = sec)))
    fun setAddIncrement(sec: Int) = update(config.copy(timer = config.timer.copy(addIncrementSec = sec)))
    fun setShowRing(value: Boolean) = update(config.copy(timer = config.timer.copy(showRing = value)))
    fun setShowOverlay(value: Boolean) = update(config.copy(timer = config.timer.copy(showOverlay = value)))
    fun setShowNowBar(value: Boolean) = update(config.copy(timer = config.timer.copy(showNowBar = value)))

    fun addPresetSeconds(sec: Int): Boolean {
        if (sec <= 0) return false
        update(config.copy(timer = config.timer.copy(presets = dedupeSorted(config.timer.presets + sec))))
        return true
    }

    fun removePreset(sec: Int) =
        update(config.copy(timer = config.timer.copy(presets = config.timer.presets.filter { it != sec })))

    // ---------- Alarma independiente por pestaña ----------
    /** Devuelve el bloque de alarma de la mini-app [scope]. */
    fun alarmFor(scope: AlarmScope): AlarmConfig = when (scope) {
        AlarmScope.TIMER -> config.timer.alarm
    }

    /** Reemplaza el bloque de alarma de la mini-app [scope]. */
    fun setAlarm(scope: AlarmScope, alarm: AlarmConfig) = update(
        when (scope) {
            AlarmScope.TIMER -> config.copy(timer = config.timer.copy(alarm = alarm))
        },
    )

    fun resetSettings() {
        update(AppConfig())
        // Re-aplicar "Beep" por defecto (AppConfig() deja los tonos en null).
        ensureDefaultAlarmSound()
    }

    /** Ajuste fino (en dp) de la posición del anillo sobre la cámara. */
    fun nudgeRingX(delta: Int) {
        ringOffsetX = (ringOffsetX + delta).coerceIn(-RING_OFFSET_LIMIT, RING_OFFSET_LIMIT)
        store.saveRingOffset(ringOffsetX, ringOffsetY)
    }

    fun nudgeRingY(delta: Int) {
        ringOffsetY = (ringOffsetY + delta).coerceIn(-RING_OFFSET_LIMIT, RING_OFFSET_LIMIT)
        store.saveRingOffset(ringOffsetX, ringOffsetY)
    }

    fun resetRingOffset() {
        ringOffsetX = 0
        ringOffsetY = 0
        store.saveRingOffset(0, 0)
    }

    // ---------- Selector de sonido / previsualización ----------

    /** Lista de tonos de alarma y notificación disponibles en el dispositivo. */
    fun loadAlarmSounds(): List<AlarmSound> {
        val ctx = getApplication<Application>()
        val result = mutableListOf<AlarmSound>()
        val seen = mutableSetOf<String>()
        // Listar ambos tipos: alarmas (penetrantes) y notificaciones (cortas).
        for (type in intArrayOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION)) {
            try {
                val rm = RingtoneManager(ctx).apply { setType(type) }
                val cursor = rm.cursor
                while (cursor.moveToNext()) {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val uri = rm.getRingtoneUri(cursor.position)
                    if (title != null && uri != null) {
                        val uriStr = uri.toString()
                        if (seen.add(uriStr)) {
                            result.add(AlarmSound(title, uriStr))
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return result
    }

    /** Previsualiza un tono al volumen dado (igual que sonará la alarma real). */
    fun previewTone(uriStr: String, volume: Float) = alarmPlayer.previewTone(uriStr, volume)

    /** Previsualiza el tono de [alarm] a su volumen (para el stepper de volumen). */
    fun previewVolume(alarm: AlarmConfig) = alarmPlayer.previewVolume(alarm)

    /** Vibra una vez con el patrón indicado, para previsualizarlo. */
    fun previewVibration(index: Int) = alarmPlayer.previewVibration(index)

    fun stopPreview() = alarmPlayer.stopPreview()

    override fun onCleared() {
        super.onCleared()
        alarmPlayer.stop()
        alarmPlayer.stopPreview()
    }
}
