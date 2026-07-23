package com.minitimer.audio

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
import com.minitimer.model.AlarmConfig
import com.minitimer.model.SPEAKER_AND_HEADSET
import com.minitimer.model.VIBRATION_PATTERNS
import kotlin.math.pow

/**
 * Reproductor ÚNICO de alarma/sonido, compartido por todas las mini-apps
 * (Timer, Athlete, futuras). Cada llamada recibe el [AlarmConfig] propio
 * de la pestaña, de modo que "lo que pruebas es lo que suena": mismo stream,
 * USAGE_ALARM, escalado perceptual en dB, ducking de la música y enrutamiento a
 * audífonos. Antes esta lógica estaba duplicada en TimerViewModel y WorkoutAlarm.
 */
class AlarmPlayer(private val context: Context) {

    private val players = mutableListOf<MediaPlayer>()
    private var previewPlayer: MediaPlayer? = null
    private var savedStreamVolume: Int? = null
    private var focus: AudioFocusRequest? = null

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ---------- Alarma ----------

    /**
     * Suena la alarma con [config]. [loop] = true para el temporizador (bucle
     * hasta detenerlo); false para un "cue" breve (transiciones del player).
     */
    fun start(config: AlarmConfig, loop: Boolean) {
        stop()
        if (config.vibrationEnabled) vibrate(config.vibrationPattern, repeat = loop)
        // Si "Ignorar modo silencio" está desactivado, respetar el modo del equipo.
        val audio = audioManager
        val shouldPlaySound =
            config.ignoreSilent || audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
        if (!shouldPlaySound) return
        val uri = config.soundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        boostStream()
        requestFocus()
        val vol = perceptualVolume(config.volume)

        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val headset = findMediaHeadset(outputs)
        val speaker = outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (headset != null) {
            play(uri, vol, loop, headset)
            if (config.headsetMode == SPEAKER_AND_HEADSET && speaker != null) {
                play(uri, vol, loop, speaker)
            }
        } else {
            play(uri, vol, loop, null)
        }
    }

    private fun play(uri: Uri, vol: Float, loop: Boolean, device: AudioDeviceInfo?) {
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(alarmAttrs())
            mp.setDataSource(context, uri)
            mp.isLooping = loop
            mp.setVolume(vol, vol)
            if (device != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mp.setPreferredDevice(device)
            }
            if (!loop) {
                // Cue breve: liberar al terminar y soltar foco/stream cuando ya no
                // quede ningún reproductor activo.
                mp.setOnCompletionListener { done ->
                    done.release()
                    players.remove(done)
                    if (players.isEmpty()) {
                        abandonFocus()
                        restoreStream()
                        cancelVibration()
                    }
                }
            }
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
            players.add(mp)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        cancelVibration()
        players.forEach { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: Exception) {
            }
            mp.release()
        }
        players.clear()
        abandonFocus()
        restoreStream()
    }

    // ---------- Previsualización (ajustes) ----------

    /**
     * Reproduce un tono como vista previa EXACTAMENTE como sonará la alarma real:
     * stream de alarma al máximo + USAGE_ALARM + ducking + volumen perceptual.
     */
    fun previewTone(uriStr: String, volume: Float) {
        stopPreview()
        try {
            boostStream()
            requestFocus()
            val mp = MediaPlayer()
            mp.setAudioAttributes(alarmAttrs())
            mp.setDataSource(context, Uri.parse(uriStr))
            val vol = perceptualVolume(volume)
            mp.setVolume(vol, vol)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener {
                it.release()
                if (previewPlayer === it) previewPlayer = null
                abandonFocus()
                restoreStream()
            }
            mp.prepareAsync()
            previewPlayer = mp
        } catch (_: Exception) {
        }
    }

    /**
     * Reproduce el tono de [config] a su volumen, para oír el nivel mientras se
     * ajusta el volumen de esa pestaña. Si el volumen es 0, calla.
     */
    fun previewVolume(config: AlarmConfig) {
        if (config.volume <= 0f) {
            stopPreview()
            return
        }
        val uri = config.soundUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.toString()
            ?: return
        previewTone(uri, config.volume)
    }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {
        }
        previewPlayer = null
        abandonFocus()
        restoreStream()
    }

    /** Vibra una vez con el patrón indicado, para previsualizarlo en ajustes. */
    fun previewVibration(patternIndex: Int) = vibrate(patternIndex, repeat = false)

    // ---------- Volumen / stream / foco ----------

    /**
     * Convierte el ajuste lineal (0..1) a una ganancia perceptual con una curva
     * en dB ([VOLUME_DB_RANGE]). 0% -> silencio; 100% -> 0 dB (máximo).
     */
    private fun perceptualVolume(setting: Float): Float {
        val x = setting.coerceIn(0f, 1f)
        if (x <= 0f) return 0f
        val db = (x - 1f) * VOLUME_DB_RANGE
        return 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)
    }

    /** Sube el stream de alarma al máximo (guardando el valor original). */
    private fun boostStream() {
        if (savedStreamVolume != null) return
        try {
            val am = audioManager
            savedStreamVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (_: Exception) {
        }
    }

    private fun restoreStream() {
        val saved = savedStreamVolume ?: return
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, saved, 0)
        } catch (_: Exception) {
        }
        savedStreamVolume = null
    }

    /** Foco transitorio con ducking: baja la música mientras suena la alarma. */
    private fun requestFocus() {
        if (focus != null) return
        try {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(alarmAttrs())
                .build()
            audioManager.requestAudioFocus(req)
            focus = req
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        val req = focus ?: return
        try {
            audioManager.abandonAudioFocusRequest(req)
        } catch (_: Exception) {
        }
        focus = null
    }

    private fun alarmAttrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Primer audífono capaz de reproducir media (excluye Bluetooth SCO). */
    private fun findMediaHeadset(outputs: Array<AudioDeviceInfo>): AudioDeviceInfo? {
        for (type in MEDIA_HEADSET_TYPES) {
            outputs.firstOrNull { it.type == type }?.let { return it }
        }
        return null
    }

    // ---------- Vibración ----------

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun vibrate(patternIndex: Int, repeat: Boolean) {
        val timings = VIBRATION_PATTERNS.getOrElse(patternIndex) { VIBRATION_PATTERNS[0] }.timings
        val v = vibrator()
        v.cancel()
        val rep = if (repeat) 0 else -1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(timings, rep))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, rep)
        }
    }

    private fun cancelVibration() {
        try {
            vibrator().cancel()
        } catch (_: Exception) {
        }
    }

    private companion object {
        /** Rango (dB) de la curva perceptual de volumen: 100% -> 0 dB, 0% -> -48 dB. */
        const val VOLUME_DB_RANGE = 48f

        /**
         * Salidas de audífonos capaces de reproducir MEDIA, por preferencia. Se
         * excluye BLUETOOTH_SCO (canal de llamadas, mono): no reproduce media a
         * menos que se active SCO explícitamente y dejaría la alarma en silencio.
         */
        val MEDIA_HEADSET_TYPES = listOf(
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
