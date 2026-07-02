package com.minitimer

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlin.math.pow
import androidx.lifecycle.viewModelScope
import com.minitimer.data.SettingsStore
import com.minitimer.model.SPEAKER_AND_HEADSET
import com.minitimer.model.Settings
import com.minitimer.model.TimerItem
import com.minitimer.model.VIBRATION_PATTERNS
import com.minitimer.notify.LiveTimerService
import com.minitimer.util.dedupeSorted
import com.minitimer.util.formatRemaining
import com.minitimer.util.parsePresetInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Phase { IDLE, RUNNING, PAUSED, DONE }

/** Un tono de alarma disponible para seleccionar. */
data class AlarmSound(val name: String, val uri: String)

/** Límite (en dp) del ajuste fino del anillo en cada eje. */
private const val RING_OFFSET_LIMIT = 100

/**
 * Rango (en dB) de la curva perceptual de volumen de la alarma: 100% -> 0 dB,
 * 0% -> -[VOLUME_DB_RANGE] dB. El oído es logarítmico, así que mapear el ajuste
 * lineal a dB hace que los porcentajes bajos suenen realmente bajos.
 */
private const val VOLUME_DB_RANGE = 48f

/**
 * Salidas de audífonos capaces de reproducir MEDIA, por orden de preferencia.
 * IMPORTANTE: se excluye BLUETOOTH_SCO (canal de llamadas, mono) porque NO
 * reproduce media a menos que se active SCO explícitamente; elegirlo deja la
 * alarma en silencio. A2DP / LE Audio sí reproducen media.
 */
private val MEDIA_HEADSET_TYPES = listOf(
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_HEARING_AID,
)

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    var settings by mutableStateOf(store.load())
        private set

    var digits by mutableStateOf("")
        private set
    var draftName by mutableStateOf("")
        private set
    var showSettings by mutableStateOf(false)

    /** Id del timer cuya pantalla de detalle está abierta; null si ninguna. */
    var detailId by mutableStateOf<Long?>(null)

    /** Lista de temporizadores (multi-timer). */
    val timers = mutableStateListOf<TimerItem>()

    /** Id del timer que ocupa el slot activo (RUNNING/PAUSED/DONE); null si ninguno. */
    var activeId by mutableStateOf<Long?>(null)
        private set

    /** Pestaña inferior seleccionada (0=Timer, 1=Athlete, 2=Water); persistida. */
    var selectedTab by mutableStateOf(store.loadSelectedTab())
        private set

    fun selectTab(index: Int) {
        selectedTab = index
        store.saveSelectedTab(index)
    }

    // Offset fino (en dp) del anillo sobre la cámara, ajustable con +/- en ajustes.
    var ringOffsetX by mutableStateOf(store.loadRingOffset().first)
        private set
    var ringOffsetY by mutableStateOf(store.loadRingOffset().second)
        private set

    private var nextId = 1L
    private var tickJob: Job? = null
    private var autoDismissJob: Job? = null
    private val players = mutableListOf<MediaPlayer>()
    private var savedAlarmStreamVolume: Int? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    init {
        TimerBus.accent.value = settings.accent
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
        stopAlarm()
        settings = store.load()
        ringOffsetX = store.loadRingOffset().first
        ringOffsetY = store.loadRingOffset().second
        TimerBus.accent.value = settings.accent
        publishOverlayPrefs()
        restore()
        val last = store.loadLastDuration()
        digits = if (last > 0) secondsToDigits(last) else ""
    }

    /** En el primer arranque selecciona "Beep" como tono por defecto si existe. */
    private fun ensureDefaultAlarmSound() {
        if (settings.alarmSoundUri != null) return
        val ctx = getApplication<Application>()
        try {
            val rm = RingtoneManager(ctx).apply { setType(RingtoneManager.TYPE_ALARM) }
            val cursor = rm.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                if (title != null && title.contains("beep", ignoreCase = true)) {
                    val uri = rm.getRingtoneUri(cursor.position)
                    update(settings.copy(alarmSoundUri = uri.toString(), alarmSoundName = title))
                    return
                }
            }
        } catch (_: Exception) {
        }
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
    }

    /**
     * Crea un timer con los dígitos actuales e intenta iniciarlo. Devuelve true
     * si se inició; false si quedó creado pero bloqueado por otro timer activo.
     */
    fun confirmNewTimer(): Boolean {
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

    // ---------- Acciones por timer ----------
    /** Inicia/reanuda un timer. Devuelve false si otro timer ocupa el slot. */
    fun startTimer(id: Long): Boolean {
        if (activeId != null && activeId != id) return false
        val it = item(id) ?: return false
        val rem = it.remainingMs.coerceAtLeast(0)
        if (rem <= 0) return false
        val end = System.currentTimeMillis() + rem
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
            stopAlarm()
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

    fun deleteTimer(id: Long) {
        val wasActive = activeId == id
        if (wasActive) {
            tickJob?.cancel()
            autoDismissJob?.cancel()
            stopAlarm()
        }
        timers.removeAll { it.id == id }
        if (wasActive) {
            activeId = null
            clearBus()
            syncService()
        }
        persist()
    }

    fun addTime(id: Long) {
        val incMs = settings.addIncrementSec * 1000L
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
        startAlarm()
        val secs = settings.autoDismiss
        if (secs > 0) {
            autoDismissJob?.cancel()
            autoDismissJob = viewModelScope.launch {
                delay(secs * 1000L)
                dismissTimer(id)
            }
        }
    }

    // ---------- Alarma ----------
    private fun startAlarm() {
        stopAlarm()
        val ctx = getApplication<Application>()
        if (settings.vibrationEnabled) {
            val timings = VIBRATION_PATTERNS
                .getOrElse(settings.vibrationPattern) { VIBRATION_PATTERNS[0] }
                .timings
            val vibrator = getVibrator(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, 0)
            }
        }
        // Si "Ignorar modo silencio" está desactivado, respetar el modo del
        // teléfono: en silencio o vibración no se reproduce el sonido.
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val shouldPlaySound =
            settings.ignoreSilent || audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
        if (!shouldPlaySound) return
        val uri = settings.alarmSoundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        // Volumen independiente del equipo + ducking de la música mientras suena.
        boostAlarmStream()
        requestAlarmFocus()

        // Enrutamiento cuando hay audífonos conectados.
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val headset = findMediaHeadset(outputs)
        val speaker = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        if (headset != null) {
            // Con audífonos: enrutar la alarma a ellos; en SPEAKER_AND_HEADSET,
            // también por el altavoz.
            play(ctx, uri, device = headset)
            if (settings.headsetMode == SPEAKER_AND_HEADSET && speaker != null) {
                play(ctx, uri, device = speaker)
            }
        } else {
            play(ctx, uri, device = null)
        }
    }

    /**
     * Convierte el ajuste de volumen lineal (0..1) a una ganancia perceptual
     * usando una curva en dB ([VOLUME_DB_RANGE]). 0% -> silencio; 100% -> 0 dB.
     */
    private fun perceptualVolume(setting: Float): Float {
        val x = setting.coerceIn(0f, 1f)
        if (x <= 0f) return 0f
        val db = (x - 1f) * VOLUME_DB_RANGE
        return 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)
    }

    /** Primer audífono capaz de reproducir media (excluye Bluetooth SCO). */
    private fun findMediaHeadset(outputs: Array<AudioDeviceInfo>): AudioDeviceInfo? {
        for (type in MEDIA_HEADSET_TYPES) {
            outputs.firstOrNull { it.type == type }?.let { return it }
        }
        return null
    }

    /**
     * Reproduce el tono de alarma en bucle con USAGE_ALARM (suena en silencio y
     * es excepción de No molestar). [device] fuerza opcionalmente la salida.
     */
    private fun play(ctx: Context, uri: Uri, device: AudioDeviceInfo?) {
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(ctx, uri)
            mp.isLooping = true
            val vol = perceptualVolume(settings.alarmVolume)
            mp.setVolume(vol, vol)
            if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mp.setPreferredDevice(device)
            }
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
            players.add(mp)
        } catch (_: Exception) {
        }
    }

    private fun stopAlarm() {
        getVibrator(getApplication()).cancel()
        players.forEach { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: Exception) {
            }
            mp.release()
        }
        players.clear()
        abandonAlarmFocus()
        restoreAlarmStream()
    }

    private val audioManager: AudioManager
        get() = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Sube el stream de alarma al máximo (guardando el valor original) para que
     * el volumen sea independiente del nivel configurado en el equipo.
     */
    private fun boostAlarmStream() {
        if (savedAlarmStreamVolume != null) return
        try {
            val am = audioManager
            savedAlarmStreamVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (_: Exception) {
        }
    }

    /** Restaura el volumen del stream de alarma previo a [boostAlarmStream]. */
    private fun restoreAlarmStream() {
        val saved = savedAlarmStreamVolume ?: return
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, saved, 0)
        } catch (_: Exception) {
        }
        savedAlarmStreamVolume = null
    }

    /**
     * Pide foco de audio transitorio con ducking: el sistema baja la música de
     * fondo mientras suena la alarma y la restaura al abandonarlo.
     */
    private fun requestAlarmFocus() {
        if (audioFocusRequest != null) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
            audioManager.requestAudioFocus(req)
            audioFocusRequest = req
        } catch (_: Exception) {
        }
    }

    /** Abandona el foco de audio para que la música recupere su volumen. */
    private fun abandonAlarmFocus() {
        val req = audioFocusRequest ?: return
        try {
            audioManager.abandonAudioFocusRequest(req)
        } catch (_: Exception) {
        }
        audioFocusRequest = null
    }

    private fun getVibrator(ctx: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    /** Vibra una vez con el patrón indicado, para previsualizarlo en ajustes. */
    fun previewVibration(index: Int) {
        val timings = VIBRATION_PATTERNS.getOrNull(index)?.timings ?: return
        val vibrator = getVibrator(getApplication())
        vibrator.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
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
            if (a.phase == Phase.DONE) com.minitimer.i18n.I18n.get(settings.language).timeUp
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
    private fun update(newSettings: Settings) {
        settings = newSettings
        store.save(newSettings)
        TimerBus.accent.value = newSettings.accent
        publishOverlayPrefs()
    }

    /** Publica al [TimerBus] los interruptores de anillo/overlay/Now Bar. */
    private fun publishOverlayPrefs() {
        TimerBus.showRing.value = settings.showRing
        TimerBus.showOverlay.value = settings.showOverlay
        TimerBus.showNowBar.value = settings.showNowBar
    }

    fun setLanguage(lang: String) = update(settings.copy(language = lang))
    fun setAccent(color: Long) = update(settings.copy(accent = color))
    fun setAutoDismiss(sec: Int) = update(settings.copy(autoDismiss = sec))
    fun setIgnoreSilent(value: Boolean) = update(settings.copy(ignoreSilent = value))
    fun setAlarmSound(uri: String?, name: String?) =
        update(settings.copy(alarmSoundUri = uri, alarmSoundName = name))
    fun setHeadsetMode(mode: Int) = update(settings.copy(headsetMode = mode))
    fun setVibrationEnabled(value: Boolean) = update(settings.copy(vibrationEnabled = value))
    fun setVibrationPattern(index: Int) = update(settings.copy(vibrationPattern = index))
    fun setAlarmVolume(value: Float) = update(settings.copy(alarmVolume = value.coerceIn(0f, 1f)))
    fun setShowRing(value: Boolean) = update(settings.copy(showRing = value))
    fun setShowOverlay(value: Boolean) = update(settings.copy(showOverlay = value))
    fun setShowNowBar(value: Boolean) = update(settings.copy(showNowBar = value))
    fun setAddIncrement(sec: Int) = update(settings.copy(addIncrementSec = sec))
    fun setDeveloperMode(value: Boolean) = update(settings.copy(developerMode = value))
    fun setPadPlayerClock(value: Boolean) = update(settings.copy(padPlayerClock = value))
    fun resetSettings() {
        update(Settings())
        // Re-aplicar "Beep" como tono por defecto (Settings() deja el tono en null).
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

    fun addPreset(input: String): Boolean {
        val sec = parsePresetInput(input)
        if (sec <= 0) return false
        update(settings.copy(presets = dedupeSorted(settings.presets + sec)))
        return true
    }

    fun removePreset(sec: Int) {
        update(settings.copy(presets = settings.presets.filter { it != sec }))
    }

    // ---------- Selector de sonido con previsualización ----------
    private var previewPlayer: MediaPlayer? = null

    /** Lista de tonos de alarma disponibles en el dispositivo. */
    fun loadAlarmSounds(): List<AlarmSound> {
        val ctx = getApplication<Application>()
        val result = mutableListOf<AlarmSound>()
        try {
            val rm = RingtoneManager(ctx).apply { setType(RingtoneManager.TYPE_ALARM) }
            val cursor = rm.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = rm.getRingtoneUri(cursor.position)
                if (title != null && uri != null) {
                    result.add(AlarmSound(title, uri.toString()))
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    /**
     * Reproduce un tono como vista previa EXACTAMENTE como sonará la alarma real:
     * stream de alarma al máximo + USAGE_ALARM + ducking de la música.
     */
    fun previewSound(uriStr: String) {
        stopPreview()
        val ctx = getApplication<Application>()
        try {
            boostAlarmStream()
            requestAlarmFocus()
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            mp.setDataSource(ctx, Uri.parse(uriStr))
            val vol = perceptualVolume(settings.alarmVolume)
            mp.setVolume(vol, vol)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                it.release()
                if (previewPlayer === it) previewPlayer = null
                abandonAlarmFocus()
                restoreAlarmStream()
            }
            mp.prepareAsync()
            previewPlayer = mp
        } catch (_: Exception) {
        }
    }

    /**
     * Reproduce el tono de alarma actual al volumen configurado, para oír el
     * nivel mientras se ajusta "Timer alarm volume". Si el volumen es 0, calla.
     */
    fun previewCurrentAlarmVolume() {
        if (settings.alarmVolume <= 0f) {
            stopPreview()
            return
        }
        val uri = settings.alarmSoundUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString()
            ?: return
        previewSound(uri)
    }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {
        }
        previewPlayer = null
        abandonAlarmFocus()
        restoreAlarmStream()
    }

    override fun onCleared() {
        super.onCleared()
        stopAlarm()
        stopPreview()
    }
}
