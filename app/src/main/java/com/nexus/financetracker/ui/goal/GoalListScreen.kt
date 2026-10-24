package com.nexus.financetracker.ui.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalPriority
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

// Design System Colors matching exact Figma Wireframe Theme
private val GoalNavyDark = Color(0xFF072044)         // Deep navy for title & primary emphasis
private val GoalNavyPrimary = Color(0xFF0B3B6F)      // Sapphire navy
private val GoalLightCardBg = Color(0xFFE2E8F0)      // Light blue-grey container matching Dashboard cards
private val GoalSubtleNavy = Color(0xFF64748B)       // Muted slate label text
private val GoalWhite = Color(0xFFFFFFFF)            // Pure white for progress track
private val GoalNavyTextSubtle = Color(0xFFBAC7D5)   // Muted silver/slate on navy containers

/**
 * Screen displaying the user's list of savings goals.
 * Supports adding new goals and tapping to edit existing goals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListScreen(
    onAddGoal: () -> Unit,
    onEditGoal: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: GoalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currencyFormat = remember { NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 } }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        containerColor = GoalWhite,
        topBar = {
            TopAppBar(
                title = { Text("Savings Goals", fontWeight = FontWeight.Bold, color = GoalNavyDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GoalNavyDark)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadGoals) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = GoalNavyDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GoalWhite
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddGoal,
                containerColor = GoalNavyDark,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Goal")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = GoalNavyPrimary)
                }

                uiState.errorMessage != null && uiState.goals.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Failed to load goals",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = viewModel::loadGoals) {
                            Text("Retry")
                        }
                    }
                }

                uiState.goals.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = GoalNavyPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            text = "No Savings Goals Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = GoalNavyDark
                        )
                        Text(
                            text = "Define a quantified target like Kavindu's MacBook Pro M4 (LKR 490,000) with a target deadline to take control of your financial trajectory.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoalSubtleNavy,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onAddGoal,
                            colors = ButtonDefaults.buttonColors(containerColor = GoalNavyDark),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create First Goal")
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(items = uiState.goals, key = { it.id }) { goal ->
                            GoalCard(
                                goal = goal,
                                calculation = uiState.calculations[goal.id],
                                currencyFormat = currencyFormat,
                                dateFormat = dateFormat,
                                onEdit = { onEditGoal(goal.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    calculation: com.nexus.financetracker.domain.model.GoalCalculation?,
    currencyFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GoalLightCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Row: Title, Active, Priority & Edit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoalNavyDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (goal.isActive) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = GoalWhite.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = "Active",
                                    fontSize = 11.sp,
                                    color = GoalNavyPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val (badgeBg, badgeFg) = when (goal.priority.lowercase()) {
                            GoalPriority.HIGH -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
                            GoalPriority.LOW -> GoalWhite.copy(alpha = 0.9f) to Color(0xFF475569)
                            else -> Color(0xFFFEF3C7) to Color(0xFFD97706)
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeBg
                        ) {
                            Text(
                                text = "${GoalPriority.getDisplayName(goal.priority)} Priority",
                                fontSize = 11.sp,
                                color = badgeFg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Goal",
                        tint = GoalNavyDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Financial Summary: Target Amount & Estimated Current Savings (FR15)
            val currentSavings = calculation?.estimatedCurrentSavingsLKR ?: goal.openingSavingsLKR
            val remaining = calculation?.remainingAmountLKR ?: maxOf(0.0, goal.targetAmountLKR - goal.openingSavingsLKR)
            val progressFraction = calculation?.progressFraction ?: if (goal.targetAmountLKR > 0) {
                (goal.openingSavingsLKR / goal.targetAmountLKR).toFloat().coerceIn(0f, 1f)
            } else 0f
            val progressPercent = calculation?.progressPercentage ?: if (goal.targetAmountLKR > 0) {
                ((goal.openingSavingsLKR / goal.targetAmountLKR) * 100.0).coerceAtLeast(0.0)
            } else 0.0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Target Amount",
                        fontSize = 12.sp,
                        color = GoalSubtleNavy,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "LKR ${currencyFormat.format(goal.targetAmountLKR)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoalNavyDark
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Estimated Savings",
                        fontSize = 12.sp,
                        color = GoalSubtleNavy,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "LKR ${currencyFormat.format(currentSavings)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoalNavyDark
                    )
                }
            }

            // 3. Real-time Progress Bar (FR15, FR17)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(GoalWhite)
                ) {
                    if (progressFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = progressFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(GoalNavyDark)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%.1f%% Complete".format(progressPercent),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoalNavyDark
                    )
                    Text(
                        text = "Need LKR ${currencyFormat.format(remaining)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GoalSubtleNavy
                    )
                }
            }

            // 4. Rates Comparison: Required / Month vs Your Average / Month (FR15, Section 6.5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoalNavyDark,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = "Required / Month",
                            fontSize = 11.sp,
                            color = GoalNavyTextSubtle,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = calculation?.let { "LKR ${currencyFormat.format(it.requiredMonthlySavingsLKR)}" }
                                ?: "Calculating...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoalNavyDark,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = "Your Average / Month",
                            fontSize = 11.sp,
                            color = GoalNavyTextSubtle,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val avgRate = calculation?.actualMonthlyAverageLKR ?: 0.0
                        Text(
                            text = "LKR ${currencyFormat.format(avgRate)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // 5. Pace Status & Projected Completion Date (FR16, Section 6.5)
            val projection = calculation?.projection
            if (projection != null) {
                val (containerColor, contentColor) = when (projection.paceStatus) {
                    com.nexus.financetracker.domain.model.PaceStatus.COMPLETED,
                    com.nexus.financetracker.domain.model.PaceStatus.AHEAD_OF_PACE ->
                        Color(0xFFDCFCE7) to Color(0xFF15803D) // Green for Goal Achieved
                    com.nexus.financetracker.domain.model.PaceStatus.ON_TRACK ->
                        Color(0xFFFFEDD5) to Color(0xFFC2410C) // Orange for On Track
                    com.nexus.financetracker.domain.model.PaceStatus.BEHIND_PACE,
                    com.nexus.financetracker.domain.model.PaceStatus.OVERDUE ->
                        Color(0xFFFEE2E2) to Color(0xFFDC2626) // Red for Slightly Behind & Overdue
                    com.nexus.financetracker.domain.model.PaceStatus.INSUFFICIENT_HISTORY ->
                        GoalWhite to Color(0xFF475569)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = containerColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = when (projection.paceStatus) {
                                com.nexus.financetracker.domain.model.PaceStatus.AHEAD_OF_PACE -> "Ahead of pace"
                                com.nexus.financetracker.domain.model.PaceStatus.ON_TRACK -> "On track"
                                com.nexus.financetracker.domain.model.PaceStatus.BEHIND_PACE -> "Slightly behind pace"
                                com.nexus.financetracker.domain.model.PaceStatus.COMPLETED -> "Goal Achieved"
                                com.nexus.financetracker.domain.model.PaceStatus.OVERDUE -> "Deadline Passed"
                                com.nexus.financetracker.domain.model.PaceStatus.INSUFFICIENT_HISTORY -> "Pace Projection"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Text(
                            text = projection.statusMessage,
                            fontSize = 11.sp,
                            color = Color(0xFF1E293B),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // 6. Deadline Row & Days Left
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = GoalSubtleNavy,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Deadline: ${dateFormat.format(goal.targetDate)}",
                        fontSize = 12.sp,
                        color = GoalSubtleNavy,
                        fontWeight = FontWeight.Medium
                    )
                }

                val daysText = calculation?.daysRemainingText ?: goal.daysRemainingText
                val isOverdue = (calculation?.daysRemaining ?: goal.daysRemaining) < 0
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isOverdue) Color(0xFFFEE2E2) else GoalNavyDark
                ) {
                    Text(
                        text = daysText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverdue) Color(0xFFDC2626) else Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
