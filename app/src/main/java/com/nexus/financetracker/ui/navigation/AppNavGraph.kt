package com.nexus.financetracker.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.nexus.financetracker.ui.auth.AuthViewModel
import com.nexus.financetracker.ui.auth.LoginScreen
import com.nexus.financetracker.ui.auth.RegisterScreen
import com.nexus.financetracker.ui.dashboard.DashboardScreen
import com.nexus.financetracker.ui.expense.ExpenseScreen
import com.nexus.financetracker.ui.goal.AddEditGoalScreen
import com.nexus.financetracker.ui.goal.GoalListScreen
import com.nexus.financetracker.ui.income.IncomeEntryScreen
import com.nexus.financetracker.ui.income.IncomeScreen
import com.nexus.financetracker.ui.income.IncomeViewModel
import com.nexus.financetracker.ui.settings.SettingsScreen

/**
 * Root Navigation Graph with single-activity Material 3 Scaffold and Bottom Navigation.
 *
 * Implements persistent session restoration (FR2):
 * - If user is already authenticated with Firebase, starts at Dashboard.
 * - Otherwise starts at Login.
 *
 * Implements protected back stack (FR2):
 * - On successful login/registration, auth screens are popped off the stack.
 * - On sign-out, Firebase session is cleared and back stack is completely purged (popUpTo(0)).
 */
@Composable
fun AppNavGraph(
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Computed ONCE at first composition — never re-evaluated on recomposition.
    // Prevents NavHost from rebuilding the graph when auth.currentUser changes mid-session.
    val startDestination = remember {
        if (authViewModel.isUserLoggedIn) Screen.Dashboard.route else Screen.Login.route
    }

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    },
                    onQuickAdd = {
                        navController.navigate(Screen.Income.route)
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
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
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Income.route) {
                val incomeViewModel: IncomeViewModel = hiltViewModel()
                IncomeScreen(
                    viewModel = incomeViewModel,
                    onAddIncome = {
                        navController.navigate(Screen.AddIncome.createRoute())
                    },
                    onEditIncome = { incomeId ->
                        navController.navigate(Screen.AddIncome.createRoute(incomeId))
                    }
                )
            }

            composable(
                route = Screen.AddIncome.route,
                arguments = listOf(
                    navArgument("incomeId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val incomeId = backStackEntry.arguments?.getString("incomeId")
                val incomeViewModel: IncomeViewModel = hiltViewModel()
                IncomeEntryScreen(
                    viewModel = incomeViewModel,
                    incomeId = incomeId,
                    onNavigateBack = {
                        incomeViewModel.resetForm()
                        navController.popBackStack()
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
                    onAddGoal = { navController.navigate(Screen.AddGoal.createRoute()) },
                    onEditGoal = { goalId -> navController.navigate(Screen.AddGoal.createRoute(goalId)) },
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

            composable(
                route = Screen.AddGoal.route,
                arguments = listOf(
                    navArgument("goalId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.getString("goalId")
                AddEditGoalScreen(
                    goalId = goalId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    userEmail = authViewModel.currentUser?.email,
                    displayName = authViewModel.currentUser?.displayName,
                    onLogout = {
                        // Sign out of Firebase Auth and clear all session state
                        authViewModel.signOut()
                        // Completely purge back stack so user cannot return to protected screens
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
