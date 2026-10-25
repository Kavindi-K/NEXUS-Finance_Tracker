package com.nexus.financetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.nexus.financetracker.notification.NotificationScheduler
import com.nexus.financetracker.ui.navigation.AppNavGraph
import com.nexus.financetracker.ui.theme.NEXUSFinanceTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Permission launcher for Android 13+ POST_NOTIFICATIONS
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Permission granted or denied — schedule anyway (worker silently skips if denied)
            NotificationScheduler.scheduleDailyBillReminder(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+ then schedule the worker.
        // On older versions (≤ Android 12), notifications are allowed without permission,
        // so we schedule directly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Already granted — schedule immediately
                    NotificationScheduler.scheduleDailyBillReminder(this)
                }
                else -> {
                    // Ask the user for permission (shows system dialog once)
                    requestNotificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            }
        } else {
            NotificationScheduler.scheduleDailyBillReminder(this)
        }

        setContent {
            NEXUSFinanceTrackerTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    AppNavGraph()
                }
            }
        }
    }
}