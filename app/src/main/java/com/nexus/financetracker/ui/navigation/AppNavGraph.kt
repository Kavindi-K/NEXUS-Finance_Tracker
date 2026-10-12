package com.nexus.financetracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nexus.financetracker.ui.auth.LoginScreen
import com.nexus.financetracker.ui.auth.RegisterScreen
import com.nexus.financetracker.ui.dashboard.DashboardScreen
import com.nexus.financetracker.ui.goal.GoalListScreen

/**
 * Root navigation graph for the app.
 * Auth screens and main app screens are wired here.
 * Start destination is Login; on successful auth, navigate to Dashboard.
 *
 * TODO: Check FirebaseAuth.currentUser on startup and skip Login if already signed in.
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        // --- Auth ---
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        // --- Main App ---
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToGoals = { navController.navigate(Screen.Goals.route) },
                onAddIncome = { navController.navigate(Screen.AddTransaction.createRoute("INCOME")) },
                onAddExpense = { navController.navigate(Screen.AddTransaction.createRoute("EXPENSE")) }
            )
        }

        composable(Screen.Goals.route) {
            GoalListScreen(
                onAddGoal = { navController.navigate(Screen.AddGoal.route) },
                onBack = { navController.popBackStack() }
            )
        }

        // TODO: Add AddTransaction, TransactionDetail, AddGoal, GoalDetail composables
    }
}
