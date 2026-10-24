package com.nexus.financetracker.ui.navigation

/**
 * Defines all navigation routes in the app as a sealed class hierarchy.
 * Using typed constants ensures compile-time safety across NavHost and bottom bar.
 */
sealed class Screen(val route: String) {
    // Splash & Auth Flow
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")

    // Bottom Navigation Destinations
    object Dashboard : Screen("dashboard")
    object Income : Screen("income")
    object Expenses : Screen("expenses")
    object Goals : Screen("goals")
    object Settings : Screen("settings")
    object Alerts : Screen("alerts")
    object Reports : Screen("reports")

    // Transaction & Detail Screens
    object AddIncome : Screen("add_income?incomeId={incomeId}") {
        fun createRoute(incomeId: String? = null) = if (incomeId != null) "add_income?incomeId=$incomeId" else "add_income"
    }
    object AddExpense : Screen("add_expense")
    object AddTransaction : Screen("add_transaction?type={type}") {
        fun createRoute(type: String) = "add_transaction?type=$type"
    }
    object TransactionDetail : Screen("transaction/{id}") {
        fun createRoute(id: String) = "transaction/$id"
    }

    // Goal Sub-Screens
    object AddGoal : Screen("add_goal?goalId={goalId}") {
        fun createRoute(goalId: String? = null) = if (goalId != null) "add_goal?goalId=$goalId" else "add_goal"
    }
    object GoalDetail : Screen("goal/{id}") {
        fun createRoute(id: String) = "goal/$id"
    }

    // Profile Screen
    object Profile : Screen("profile")
}
