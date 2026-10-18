package com.nexus.financetracker.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexus.financetracker.ui.auth.LoginScreen
import com.nexus.financetracker.ui.auth.RegisterScreen
import com.nexus.financetracker.ui.dashboard.DashboardScreen
import com.nexus.financetracker.ui.expense.ExpenseScreen
import com.nexus.financetracker.ui.goal.GoalListScreen
import com.nexus.financetracker.ui.income.IncomeScreen
import com.nexus.financetracker.ui.settings.SettingsScreen

/**
 * Root Navigation Graph with single-activity Material 3 Scaffold and Bottom Navigation.
 *
 * Bottom bar is automatically shown on top-level destinations (Dashboard, Income, Expenses,
 * Goals, Settings) and hidden during Auth flows (Login, Register).
 */
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Top-level destinations that display the bottom navigation bar
    val topLevelRoutes = listOf(
        Screen.Dashboard.route,
        Screen.Income.route,
        Screen.Expenses.route,
        Screen.Goals.route,
        Screen.Settings.route
    )
    val shouldShowBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                AppBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigateToDestination = { targetRoute ->
                        navController.navigate(targetRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- Authentication Flow ---
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

            // --- Top-Level App Destinations ---
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToGoals = {
                        navController.navigate(Screen.Goals.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAddIncome = {
                        navController.navigate(Screen.Income.route)
                    },
                    onAddExpense = {
                        navController.navigate(Screen.Expenses.route)
                    }
                )
            }

            composable(Screen.Income.route) {
                IncomeScreen(
                    onAddIncome = {
                        navController.navigate(Screen.AddTransaction.createRoute("INCOME"))
                    }
                )
            }

            composable(Screen.Expenses.route) {
                ExpenseScreen(
                    onAddExpense = {
                        navController.navigate(Screen.AddTransaction.createRoute("EXPENSE"))
                    }
                )
            }

            composable(Screen.Goals.route) {
                GoalListScreen(
                    onAddGoal = { navController.navigate(Screen.AddGoal.route) },
                    onBack = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
