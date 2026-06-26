package com.minitimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.minitimer.data.SettingsStore
import com.minitimer.ui.TimerApp
import com.minitimer.ui.theme.MiniTimerTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
        ensureOverlayPermission()
        setContent {
            MiniTimerTheme {
                TimerApp(vm = viewModel())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        TimerBus.appForeground.value = true
    }

    override fun onStop() {
        super.onStop()
        TimerBus.appForeground.value = false
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Pide UNA sola vez el permiso "Mostrar sobre otras apps" para el overlay
     * flotante. Si el usuario lo rechaza, la app degrada con elegancia: no hay
     * overlay, pero la notificación / Now Bar siguen funcionando.
     */
    private fun ensureOverlayPermission() {
        val store = SettingsStore(this)
        if (!Settings.canDrawOverlays(this) && !store.overlayAsked()) {
            store.setOverlayAsked()
            try {
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            } catch (_: Exception) {
            }
        }
    }
}
