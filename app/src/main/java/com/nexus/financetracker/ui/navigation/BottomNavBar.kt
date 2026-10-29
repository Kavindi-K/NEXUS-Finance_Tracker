package com.nexus.financetracker.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/**
 * Custom Bottom Navigation Bar matching the exact Figma wireframe design:
 * - Pure white container with thin top border
 * - 4 symmetric items: Home, Income, Alerts, Profile
 * - Central elevated Navy circular Floating Action Button with white plus icon
 */
@Composable
fun AppBottomNavBar(
    currentRoute: String?,
    hasUnreadAlerts: Boolean = false,
    onNavigateToDestination: (String) -> Unit,
    onQuickAdd: () -> Unit = { onNavigateToDestination(Screen.CategoryDashboard.route) }
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
    ) {
        // 1. Base Navigation Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Subtle top border divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFFE2E8F0))
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Home
                    WireframeBottomNavItem(
                        title = "Home",
                        isSelected = currentRoute == Screen.Dashboard.route,
                        onClick = { onNavigateToDestination(Screen.Dashboard.route) }
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Outlined.Home,
                            contentDescription = "Home",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Income
                    WireframeBottomNavItem(
                        title = "Income",
                        isSelected = currentRoute == Screen.Income.route,
                        onClick = { onNavigateToDestination(Screen.Income.route) }
                    ) { tint ->
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = "Income",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Gap in the center reserved for the elevated Floating Action Button
                    Spacer(modifier = Modifier.weight(1f))

                    // Alerts
                    WireframeBottomNavItem(
                        title = "Alerts",
                        isSelected = currentRoute == Screen.Alerts.route,
                        onClick = { onNavigateToDestination(Screen.Alerts.route) }
                    ) { tint ->
                        BadgedBox(
                            badge = {
                                if (hasUnreadAlerts) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = "Alerts",
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Profile
                    WireframeBottomNavItem(
                        title = "Profile",
                        isSelected = currentRoute == Screen.Profile.route || currentRoute == Screen.Settings.route,
                        onClick = { onNavigateToDestination(Screen.Profile.route) }
                    ) { tint ->
                        Icon(
                            imageVector = Icons.Outlined.PersonOutline,
                            contentDescription = "Profile",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 2. Central Elevated Navy Action Button (matches wireframe center plus circle)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
                .size(56.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF072044))
                .border(3.5.dp, Color.White, CircleShape)
                .clickable { onQuickAdd() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Quick Add",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun RowScope.WireframeBottomNavItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val activeColor = Color(0xFF072044)
    val inactiveColor = Color(0xFF94A3B8)
    val color = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon(color)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = title,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * 3 Rounded horizontal lines icon matching the Expenses tab in the wireframe.
 */
@Composable
private fun ExpensesNavIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val startX = 2.dp.toPx()
        val endX = size.width - 2.dp.toPx()
        val y1 = size.height * 0.28f
        val y2 = size.height * 0.50f
        val y3 = size.height * 0.72f
        drawLine(tint, Offset(startX, y1), Offset(endX, y1), stroke, StrokeCap.Round)
        drawLine(tint, Offset(startX, y2), Offset(endX, y2), stroke, StrokeCap.Round)
        drawLine(tint, Offset(startX, y3), Offset(endX, y3), stroke, StrokeCap.Round)
    }
}
