package com.minitimer.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.minitimer.data.SettingsStore

/**
 * Reanuda el OSD del reloj tras reiniciar el dispositivo, si algún panel está
 * activo. Junto con el servicio en primer plano, es una de las medidas para que
 * el reloj sobreviva a reinicios y no quede apagado hasta abrir la app.
 */
class ClockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> {
                val clock = SettingsStore(context).loadConfig().clock
                if (clock.anyEnabled) {
                    runCatching { ClockOverlayService.sync(context, clock) }
                }
            }
        }
    }
}
