package com.minitimer.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.minitimer.data.SettingsStore
import com.minitimer.model.VIBRATION_PATTERNS
import kotlin.math.pow

/**
 * Reproduce un "cue" corto de alarma para las transiciones del player,
 * replicando el comportamiento del timer (USAGE_ALARM, stream de alarma al
 * máximo, volumen perceptual y vibración) para cumplir "lo que pruebas es lo que
 * suena". No hace bucle: es un aviso breve.
 */
class WorkoutAlarm(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private var savedStreamVolume: Int? = null
    private var focus: AudioFocusRequest? = null
    private var player: MediaPlayer? = null

    /** Mismo rango (dB) que el timer para la curva perceptual de volumen. */
    private fun perceptual(setting: Float): Float {
        val x = setting.coerceIn(0f, 1f)
        if (x <= 0f) return 0f
        val db = (x - 1f) * VOLUME_DB_RANGE
        return 10.0.pow(db / 20.0).toFloat().coerceIn(0f, 1f)
    }

    fun playCue() {
        val s = SettingsStore(context).load()
        val uriStr = s.alarmSoundUri ?: return
        stop()
        boostStream()
        requestFocus()
        if (s.vibrationEnabled) vibrateOnce(s.vibrationPattern)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, Uri.parse(uriStr))
                isLooping = false
                val v = perceptual(s.alarmVolume)
                setVolume(v, v)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        player?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: Exception) {
            }
            mp.release()
        }
        player = null
        cancelVibration()
        abandonFocus()
        restoreStream()
    }

    private fun boostStream() {
        if (savedStreamVolume != null) return
        try {
            savedStreamVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (_: Exception) {
        }
    }

    private fun restoreStream() {
        val saved = savedStreamVolume ?: return
        try {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, saved, 0)
        } catch (_: Exception) {
        }
        savedStreamVolume = null
    }

    private fun requestFocus() {
        if (focus != null) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build()
            audio.requestAudioFocus(req)
            focus = req
        } catch (_: Exception) {
        }
    }

    private fun abandonFocus() {
        val req = focus ?: return
        try {
            audio.abandonAudioFocusRequest(req)
        } catch (_: Exception) {
        }
        focus = null
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun vibrateOnce(patternIndex: Int) {
        val timings = VIBRATION_PATTERNS.getOrElse(patternIndex) { VIBRATION_PATTERNS[0] }.timings
        val v = vibrator()
        v.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1)
        }
    }

    private fun cancelVibration() {
        try {
            vibrator().cancel()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val VOLUME_DB_RANGE = 48f
    }
}
