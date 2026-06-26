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
import com.minitimer.overlay.OverlayService
import com.minitimer.ui.TimerApp
import com.minitimer.ui.theme.MiniTimerTheme

class MainActivity : ComponentActivity() {

    private lateinit var vm: TimerViewModel

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureNotificationPermission()
        setContent {
            vm = viewModel()
            MiniTimerTheme {
                TimerApp(vm = vm, requestOverlayPermission = ::ensureOverlayPermission)
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun ensureOverlayPermission() {
        if (!canDrawOverlays()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            try {
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // El usuario sale a Home/Recientes (estado foreground, permite iniciar el
        // foreground service): muestra la ventana flotante si está activada,
        // hay un timer en curso y tenemos permiso de overlay.
        if (::vm.isInitialized &&
            vm.settings.floatingWindow &&
            TimerBus.active.value &&
            canDrawOverlays()
        ) {
            OverlayService.start(this)
        }
    }

    override fun onStart() {
        super.onStart()
        // Al volver al primer plano: oculta la ventana flotante.
        OverlayService.stop(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        OverlayService.stop(this)
    }
}
