package com.minitimer

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minitimer.data.SettingsStore
import com.minitimer.model.Settings
import com.minitimer.notify.LiveTimerService
import com.minitimer.util.dedupeSorted
import com.minitimer.util.formatRemaining
import com.minitimer.util.parsePresetInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Phase { SETUP, RUNNING, PAUSED, DONE }

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

    private var endAt = 0L
    private var tickJob: Job? = null
    private var autoDismissJob: Job? = null
    private var ringtone: Ringtone? = null

    init {
        TimerBus.accent.value = settings.accent
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
        val ms = sec * 1000L
        endAt = System.currentTimeMillis() + ms
        totalMs = ms
        remainingMs = ms
        TimerBus.endAt.value = endAt
        setPhaseAndBus(Phase.RUNNING)
        startTicking()
        LiveTimerService.start(getApplication())
    }

    fun pause() {
        remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0)
        tickJob?.cancel()
        setPhaseAndBus(Phase.PAUSED)
    }

    fun resume() {
        endAt = System.currentTimeMillis() + remainingMs
        TimerBus.endAt.value = endAt
        setPhaseAndBus(Phase.RUNNING)
        startTicking()
    }

    fun cancel() {
        stopAlarm()
        tickJob?.cancel()
        autoDismissJob?.cancel()
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
        digits = ""
        setPhaseAndBus(Phase.SETUP)
        LiveTimerService.stop(getApplication())
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
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 600, 400, 600, 400, 600, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(ctx, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        } catch (_: Exception) {
        }
    }

    private fun stopAlarm() {
        val ctx = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
        ringtone?.let { if (it.isPlaying) it.stop() }
        ringtone = null
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
    }

    fun setLanguage(lang: String) = update(settings.copy(language = lang))
    fun setAccent(color: Long) = update(settings.copy(accent = color))
    fun setAutoDismiss(sec: Int) = update(settings.copy(autoDismiss = sec))
    fun resetSettings() = update(Settings())

    fun addPreset(input: String): Boolean {
        val sec = parsePresetInput(input)
        if (sec <= 0) return false
        update(settings.copy(presets = dedupeSorted(settings.presets + sec)))
        return true
    }

    fun removePreset(sec: Int) {
        update(settings.copy(presets = settings.presets.filter { it != sec }))
    }

    override fun onCleared() {
        super.onCleared()
        stopAlarm()
    }
}
