package com.nexus.financetracker.ui.goal

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.financetracker.domain.model.Goal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

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
        topBar = {
            TopAppBar(
                title = { Text("Savings Goals", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadGoals) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddGoal,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            text = "No Savings Goals Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Define a quantified target like Kavindu's MacBook Pro M4 (LKR 490,000) with a target deadline to take control of your financial trajectory.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = onAddGoal,
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (goal.isActive) {
                        Text(
                            text = "Active Goal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Goal",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Financial Summary: Target & Estimated Current Savings (FR15)
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Target Amount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "LKR ${currencyFormat.format(goal.targetAmountLKR)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Estimated Savings*",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "LKR ${currencyFormat.format(currentSavings)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Real-time Progress Bar (FR15, FR17)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "%.1f%% Complete".format(progressPercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Need LKR ${currencyFormat.format(remaining)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Rates Comparison: Required / Month vs Your Average / Month (FR15, Section 6.5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Required / Month",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = calculation?.let { "LKR ${currencyFormat.format(it.requiredMonthlySavingsLKR)}" }
                                ?: "Calculating...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Your Average / Month",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                        val avgRate = calculation?.actualMonthlyAverageLKR ?: 0.0
                        Text(
                            text = "LKR ${currencyFormat.format(avgRate)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Pace Status & Projected Completion Date (FR16, Section 6.5)
            val projection = calculation?.projection
            if (projection != null) {
                val (containerColor, contentColor) = when (projection.paceStatus) {
                    com.nexus.financetracker.domain.model.PaceStatus.AHEAD_OF_PACE,
                    com.nexus.financetracker.domain.model.PaceStatus.ON_TRACK,
                    com.nexus.financetracker.domain.model.PaceStatus.COMPLETED ->
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) to MaterialTheme.colorScheme.primary
                    com.nexus.financetracker.domain.model.PaceStatus.BEHIND_PACE,
                    com.nexus.financetracker.domain.model.PaceStatus.OVERDUE ->
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) to MaterialTheme.colorScheme.error
                    com.nexus.financetracker.domain.model.PaceStatus.INSUFFICIENT_HISTORY ->
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) to MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = containerColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Text(
                            text = projection.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Deadline: ${dateFormat.format(goal.targetDate)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "*Estimate, not bank balance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
