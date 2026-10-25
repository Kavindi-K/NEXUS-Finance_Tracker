package com.nexus.financetracker.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.IncomeRepository
import com.nexus.financetracker.domain.model.IncomeSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Background worker running daily at 6:00 PM.
 * Delivers all income-related notifications:
 * 1. Daily quick-log reminder ("Did you receive any income today?")
 * 2. Pending income arrival alerts expected today (FR8)
 */
@HiltWorker
class IncomeReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val incomeRepository: IncomeRepository,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        NotificationHelper.createNotificationChannel(context)

        // 1. Post daily income quick-log prompt
        NotificationHelper.postDailyIncomeQuickLogReminder(context)

        // 2. Check pending incomes expected today (FR8)
        val userId = auth.currentUser?.uid ?: return Result.success()

        val now = Calendar.getInstance()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val todayEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        val pendingIncomes = incomeRepository.getPendingIncomes(userId).first()
        pendingIncomes.forEach { income ->
            val expectedDate = income.expectedDate ?: return@forEach
            if (expectedDate in todayStart..todayEnd) {
                val notificationId = ("income_" + income.id).hashCode()
                val sourceName = IncomeSource.getDisplayName(income.source)
                NotificationHelper.postPendingIncomeReminder(
                    context = context,
                    notificationId = notificationId,
                    sourceName = sourceName,
                    amountLKR = income.amountLKR
                )
            }
        }

        return Result.success()
    }
}
