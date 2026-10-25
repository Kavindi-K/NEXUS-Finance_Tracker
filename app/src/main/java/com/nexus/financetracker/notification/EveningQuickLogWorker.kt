package com.nexus.financetracker.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.CategoryRepository
import com.nexus.financetracker.data.repository.ExpenseRepository
import com.nexus.financetracker.domain.model.ExpenseClassification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Background worker running daily at 8:30 PM (FR9, FR18–FR19).
 * 1. Fires a gentle reminder to prompt users to log forgotten cash and daily expenses (FR9).
 * 2. Checks weekly discretionary spending leaks and alerts if a category exceeds threshold (FR18–FR19).
 */
@HiltWorker
class EveningQuickLogWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val auth: FirebaseAuth
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        NotificationHelper.createNotificationChannel(context)

        // 1. Daily quick-log expense reminder (FR9)
        NotificationHelper.postDailyQuickLogReminder(context)

        // 2. Weekly discretionary spending leak check (FR18–FR19)
        val userId = auth.currentUser?.uid ?: return Result.success()

        val weekStart = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val expenses = expenseRepository.getExpenses(userId).first()
        val categories = categoryRepository.getCategories(userId).first()
        val categoryMap = categories.associate { it.id to it.name }

        val weekExpenses = expenses.filter { !it.date.before(weekStart) }
        val discretionaryExpenses = weekExpenses.filter {
            it.classification.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true)
        }

        val totalWeeklyExpenses = weekExpenses.sumOf { it.amountLKR }

        val grouped = discretionaryExpenses.groupBy { expense ->
            categoryMap[expense.categoryId]?.takeIf { it.isNotBlank() }
                ?: expense.categoryId.takeIf { it.isNotBlank() }
                ?: "Dining & Coffee"
        }.mapValues { (_, list) -> list.sumOf { it.amountLKR } }

        grouped.forEach { (catName, sum) ->
            val isLeak = sum >= 15000.0 || (totalWeeklyExpenses > 0 && (sum / totalWeeklyExpenses) >= 0.40 && sum >= 5000.0)
            if (isLeak) {
                val notifId = ("leak_" + catName).hashCode()
                NotificationHelper.postDiscretionaryLeakReminder(
                    context = context,
                    notificationId = notifId,
                    categoryName = catName,
                    amountLKR = sum
                )
            }
        }

        return Result.success()
    }
}
