package com.nexus.financetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val title: String,
    val route: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        title = "Dashboard",
        route = Screen.Dashboard.route,
        icon = Icons.Default.Dashboard
    ),
    BottomNavItem(
        title = "Income",
        route = Screen.Income.route,
        icon = Icons.AutoMirrored.Filled.TrendingUp
    ),
    BottomNavItem(
        title = "Expenses",
        route = Screen.Expenses.route,
        icon = Icons.AutoMirrored.Filled.TrendingDown
    ),
    BottomNavItem(
        title = "Goals",
        route = Screen.Goals.route,
        icon = Icons.Default.Savings
    ),
    BottomNavItem(
        title = "Settings",
        route = Screen.Settings.route,
        icon = Icons.Default.Settings
    )
)

@Composable
fun AppBottomNavBar(
    currentRoute: String?,
    onNavigateToDestination: (String) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        onNavigateToDestination(item.route)
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
