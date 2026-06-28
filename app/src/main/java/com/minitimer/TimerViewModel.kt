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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlin.math.pow
import androidx.lifecycle.viewModelScope
import com.minitimer.data.SettingsStore
import com.minitimer.model.SPEAKER_AND_HEADSET
import com.minitimer.model.Settings
import com.minitimer.model.VIBRATION_PATTERNS
import com.minitimer.notify.LiveTimerService
import com.minitimer.util.dedupeSorted
import com.minitimer.util.formatRemaining
import com.minitimer.util.parsePresetInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Phase { SETUP, RUNNING, PAUSED, DONE }

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

    var phase by mutableStateOf(Phase.SETUP)
        private set
    var digits by mutableStateOf("")
        private set
    var totalMs by mutableStateOf(0L)
        private set
    var remainingMs by mutableStateOf(0L)
        private set
    var showSettings by mutableStateOf(false)

    // Nombre opcional del timer (por ejecución). Se muestra en la barra superior.
    var label by mutableStateOf("")
        private set

    // Offset fino (en dp) del anillo sobre la cámara, ajustable con +/- en ajustes.
    var ringOffsetX by mutableStateOf(store.loadRingOffset().first)
        private set
    var ringOffsetY by mutableStateOf(store.loadRingOffset().second)
        private set

    private var endAt = 0L
    private var tickJob: Job? = null
    private var autoDismissJob: Job? = null
    private val players = mutableListOf<MediaPlayer>()
    private var savedAlarmStreamVolume: Int? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    init {
        TimerBus.accent.value = settings.accent
        publishOverlayPrefs()
        ensureDefaultAlarmSound()
        if (!restoreTimerState()) {
            // Sin timer activo: pre-rellenar con la última duración y el último
            // nombre usados.
            val last = store.loadLastDuration()
            if (last > 0) digits = secondsToDigits(last)
            label = store.loadLastLabel()
            TimerBus.label.value = label
        }
        // Comandos desde los botones del Now Bar (Pausa/Reanudar/Cancelar).
        viewModelScope.launch {
            TimerBus.command.collect { cmd ->
                when (cmd) {
                    TimerCommand.PAUSE -> if (phase == Phase.RUNNING) pause()
                    TimerCommand.RESUME -> if (phase == Phase.PAUSED) resume()
                    TimerCommand.CANCEL -> cancel()
                }
            }
        }
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

    /** Restaura un timer activo tras la muerte del proceso (swipe en Recientes). */
    private fun restoreTimerState(): Boolean {
        val st = store.loadTimerState() ?: return false
        totalMs = st.totalMs
        label = st.label
        when (st.phase) {
            Phase.RUNNING.name -> {
                val left = st.endAt - System.currentTimeMillis()
                if (left > 0L) {
                    endAt = st.endAt
                    remainingMs = left
                    TimerBus.endAt.value = endAt
                    setPhaseAndBus(Phase.RUNNING)
                    startTicking()
                    LiveTimerService.start(getApplication())
                } else {
                    remainingMs = 0
                    onFinished()
                }
            }
            Phase.PAUSED.name -> {
                remainingMs = st.remainingMs
                setPhaseAndBus(Phase.PAUSED)
                LiveTimerService.start(getApplication())
            }
            Phase.DONE.name -> {
                remainingMs = 0
                onFinished()
            }
            else -> return false
        }
        return true
    }

    private fun secondsToDigits(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return "%02d%02d%02d".format(h, m, s).trimStart('0')
    }

    private fun saveTimerState() {
        store.saveTimerState(
            SettingsStore.TimerState(
                phase = phase.name,
                endAt = endAt,
                remainingMs = remainingMs,
                totalMs = totalMs,
                label = label,
            ),
        )
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

    // ---------- Acciones ----------
    fun start() = startWithSeconds(totalSeconds)

    fun startWithSeconds(sec: Int) {
        if (sec <= 0) return
        store.saveLastDuration(sec)
        if (label.isNotBlank()) store.saveLastLabel(label)
        val ms = sec * 1000L
        endAt = System.currentTimeMillis() + ms
        totalMs = ms
        remainingMs = ms
        TimerBus.endAt.value = endAt
        setPhaseAndBus(Phase.RUNNING)
        saveTimerState()
        startTicking()
        LiveTimerService.start(getApplication())
    }

    fun pause() {
        remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0)
        tickJob?.cancel()
        setPhaseAndBus(Phase.PAUSED)
        saveTimerState()
    }

    fun resume() {
        endAt = System.currentTimeMillis() + remainingMs
        TimerBus.endAt.value = endAt
        setPhaseAndBus(Phase.RUNNING)
        saveTimerState()
        startTicking()
    }

    fun cancel() {
        stopAlarm()
        tickJob?.cancel()
        autoDismissJob?.cancel()
        store.clearTimerState()
        prefillLastDuration()
        setPhaseAndBus(Phase.SETUP)
        LiveTimerService.stop(getApplication())
    }

    fun restart() {
        stopAlarm()
        autoDismissJob?.cancel()
        startWithSeconds((totalMs / 1000).toInt())
    }

    fun dismiss() {
        stopAlarm()
        tickJob?.cancel()
        autoDismissJob?.cancel()
        store.clearTimerState()
        prefillLastDuration()
        setPhaseAndBus(Phase.SETUP)
        LiveTimerService.stop(getApplication())
    }

    /** Refleja el texto en edición en vivo (sin persistir), para que acciones
     *  como Start tomen el nombre actual aunque no se haya confirmado aún. */
    fun setDraftLabel(value: String) {
        label = value.take(40)
        TimerBus.label.value = label
    }

    /** Fija el nombre del timer (recortado a 40 caracteres). */
    fun commitLabel(value: String) {
        label = value.take(40)
        TimerBus.label.value = label
        if (label.isNotBlank()) store.saveLastLabel(label)
        if (phase != Phase.SETUP) saveTimerState()
    }

    /** Deja el teclado con la última duración usada y restaura el último nombre. */
    private fun prefillLastDuration() {
        val last = store.loadLastDuration()
        digits = if (last > 0) secondsToDigits(last) else ""
        label = store.loadLastLabel()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val left = endAt - System.currentTimeMillis()
                if (left <= 0) {
                    remainingMs = 0
                    onFinished()
                    break
                } else {
                    remainingMs = left
                    updateBus()
                }
                delay(100)
            }
        }
    }

    private fun onFinished() {
        setPhaseAndBus(Phase.DONE)
        saveTimerState()
        startAlarm()
        val secs = settings.autoDismiss
        if (secs > 0) {
            autoDismissJob?.cancel()
            autoDismissJob = viewModelScope.launch {
                delay(secs * 1000L)
                dismiss()
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

    // ---------- Bus ----------
    private fun setPhaseAndBus(p: Phase) {
        phase = p
        TimerBus.done.value = p == Phase.DONE
        updateBus()
    }

    private fun updateBus() {
        TimerBus.done.value = phase == Phase.DONE
        TimerBus.paused.value = phase == Phase.PAUSED
        TimerBus.remainingMs.value = remainingMs
        TimerBus.totalMs.value = totalMs
        TimerBus.label.value = label
        TimerBus.display.value =
            if (phase == Phase.DONE) I18nLabelTimeUp()
            else formatRemaining(remainingMs)
    }

    private fun I18nLabelTimeUp(): String =
        com.minitimer.i18n.I18n.get(settings.language).timeUp

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
