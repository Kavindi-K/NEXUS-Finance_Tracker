package com.nexus.financetracker.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.nexus.financetracker.ui.auth.ForgotPasswordScreen
import com.nexus.financetracker.ui.auth.LoginScreen
import com.nexus.financetracker.ui.auth.RegisterScreen
import com.nexus.financetracker.ui.splash.SplashScreen
import com.nexus.financetracker.ui.dashboard.DashboardScreen
import com.nexus.financetracker.ui.expense.ExpenseAddScreen
import com.nexus.financetracker.ui.expense.ExpenseScreen
import com.nexus.financetracker.ui.expense.ExpenseViewModel
import com.nexus.financetracker.ui.goal.AddEditGoalScreen
import com.nexus.financetracker.ui.goal.GoalListScreen
import com.nexus.financetracker.ui.income.IncomeEntryScreen
import com.nexus.financetracker.ui.income.IncomeScreen
import com.nexus.financetracker.ui.income.IncomeViewModel
import com.nexus.financetracker.ui.alerts.AlertsScreen
import com.nexus.financetracker.ui.reports.ReportsScreen
import com.nexus.financetracker.ui.settings.SettingsScreen
import com.nexus.financetracker.ui.profile.ProfileScreen
import com.nexus.financetracker.ui.profile.ProfileViewModel

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
        if (authViewModel.isUserLoggedIn) Screen.Dashboard.route else Screen.Splash.route
    }

    // Top-level destinations that display the bottom navigation bar
    val topLevelRoutes = listOf(
        Screen.Dashboard.route,
        Screen.Income.route,
        Screen.Expenses.route,
        Screen.Goals.route,
        Screen.Settings.route,
        Screen.Profile.route,
        Screen.Alerts.route,
        Screen.Reports.route
    )
    val shouldShowBottomBar = currentRoute in topLevelRoutes

    val onBackToDashboard: () -> Unit = {
        val popped = navController.popBackStack(Screen.Dashboard.route, inclusive = false)
        if (!popped) {
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Dashboard.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (shouldShowBottomBar) {
                AppBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigateToDestination = { targetRoute ->
                        if (targetRoute == Screen.Dashboard.route) {
                            val popped = navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                            if (!popped) {
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo(Screen.Dashboard.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            navController.navigate(targetRoute) {
                                popUpTo(Screen.Dashboard.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onQuickAdd = {
                        navController.navigate(Screen.Expenses.route) {
                            popUpTo(Screen.Dashboard.route) {
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
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- Splash / Onboarding Flow ---
            composable(Screen.Splash.route) {
                SplashScreen(
                    onGetStarted = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // --- Authentication Flow ---
            composable(
                route = Screen.Login.route,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec = tween(durationMillis = 500, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(400))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(300))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec = tween(durationMillis = 400)
                    )
                }
            ) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToRegister = {
                        navController.navigate(Screen.Register.route)
                    },
                    onNavigateToForgotPassword = {
                        navController.navigate(Screen.ForgotPassword.route)
                    }
                )
            }

            composable(
                route = Screen.Register.route,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(durationMillis = 400)
                    ) + fadeOut(animationSpec = tween(300))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(durationMillis = 400, easing = EaseOutCubic)
                    ) + fadeIn(animationSpec = tween(300))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(durationMillis = 400)
                    ) + fadeOut(animationSpec = tween(300))
                }
            ) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }

            composable(Screen.ForgotPassword.route) {
                ForgotPasswordScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // --- Top-Level App Destinations ---
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToGoals = {
                        navController.navigate(Screen.Goals.route) {
                            popUpTo(Screen.Dashboard.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAddIncome = {
                        navController.navigate(Screen.Income.route) {
                            popUpTo(Screen.Dashboard.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAddExpense = {
                        navController.navigate(Screen.AddExpense.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(Screen.Dashboard.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToReports = {
                        navController.navigate(Screen.Reports.route) {
                            popUpTo(Screen.Dashboard.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToProfile = {
                        navController.navigate(Screen.Profile.route) {
                            popUpTo(Screen.Dashboard.route) {
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
                    },
                    onBack = onBackToDashboard
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
                val expenseViewModel: ExpenseViewModel = hiltViewModel()
                ExpenseScreen(
                    viewModel = expenseViewModel,
                    onAddExpense = {
                        navController.navigate(Screen.AddExpense.route)
                    },
                    onBack = onBackToDashboard
                )
            }

            composable(Screen.AddExpense.route) {
                val expenseViewModel: ExpenseViewModel = hiltViewModel()
                ExpenseAddScreen(
                    viewModel = expenseViewModel,
                    onNavigateBack = {
                        expenseViewModel.resetForm()
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Goals.route) {
                GoalListScreen(
                    onAddGoal = { navController.navigate(Screen.AddGoal.createRoute()) },
                    onEditGoal = { goalId -> navController.navigate(Screen.AddGoal.createRoute(goalId)) },
                    onBack = onBackToDashboard
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

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigate(Screen.Splash.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = onBackToDashboard,
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                val profileUiState by profileViewModel.uiState.collectAsState()
                SettingsScreen(
                    userEmail = profileUiState.profile?.email,
                    displayName = profileUiState.profile?.capitalizedDisplayName,
                    onLogout = {
                        authViewModel.signOut()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = {
                        val popped = navController.popBackStack()
                        if (!popped) {
                            navController.navigate(Screen.Profile.route) {
                                popUpTo(Screen.Dashboard.route) { saveState = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(Screen.Alerts.route) {
                AlertsScreen(onBack = onBackToDashboard)
            }

            composable(Screen.Reports.route) {
                ReportsScreen(onBack = onBackToDashboard)
            }
        }
    }
}
