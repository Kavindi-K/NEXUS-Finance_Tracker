package com.nexus.financetracker.ui.navigation

/**
 * Defines all navigation routes in the app as a sealed class hierarchy.
 * Use typed constants here — avoids raw string typos across the NavHost.
 */
sealed class Screen(val route: String) {
    // Auth flow
    object Login : Screen("login")
    object Register : Screen("register")

    // Main app flow
    object Dashboard : Screen("dashboard")
    object AddTransaction : Screen("add_transaction?type={type}") {
        fun createRoute(type: String) = "add_transaction?type=$type"
    }
    object TransactionDetail : Screen("transaction/{id}") {
        fun createRoute(id: String) = "transaction/$id"
    }
    object Goals : Screen("goals")
    object AddGoal : Screen("add_goal")
    object GoalDetail : Screen("goal/{id}") {
        fun createRoute(id: String) = "goal/$id"
    }
}
