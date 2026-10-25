package com.nexus.financetracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class.
 * Registers Hilt's [HiltWorkerFactory] so that [BillReminderWorker] can receive
 * injected dependencies through Hilt's assisted injection.
 */
@HiltAndroidApp
class FinanceApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
