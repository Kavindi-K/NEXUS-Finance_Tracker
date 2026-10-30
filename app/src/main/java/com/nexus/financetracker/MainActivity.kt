package com.nexus.financetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.nexus.financetracker.data.local.AppSettingsManager
import com.nexus.financetracker.data.local.SecurityManager
import com.nexus.financetracker.notification.NotificationScheduler
import com.nexus.financetracker.ui.navigation.AppNavGraph
import com.nexus.financetracker.ui.theme.NEXUSFinanceTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appSettingsManager: AppSettingsManager

    @Inject
    lateinit var securityManager: SecurityManager

    // Permission launcher for Android 13+ POST_NOTIFICATIONS
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            NotificationScheduler.scheduleDailyBillReminder(this)
            NotificationScheduler.scheduleDailyIncomeReminder(this)
            NotificationScheduler.scheduleDailyEveningReminder(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    NotificationScheduler.scheduleDailyBillReminder(this)
                    NotificationScheduler.scheduleDailyIncomeReminder(this)
                    NotificationScheduler.scheduleDailyEveningReminder(this)
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            }
        } else {
            NotificationScheduler.scheduleDailyBillReminder(this)
            NotificationScheduler.scheduleDailyIncomeReminder(this)
            NotificationScheduler.scheduleDailyEveningReminder(this)
        }

        setContent {
            val settings by appSettingsManager.settings.collectAsState()
            NEXUSFinanceTrackerTheme(themeMode = settings.themeMode) {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    AppNavGraph(securityManager = securityManager)
                }
            }
        }
    }
}