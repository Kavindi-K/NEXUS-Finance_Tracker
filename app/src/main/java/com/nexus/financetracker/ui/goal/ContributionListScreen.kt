package com.nexus.financetracker.ui.goal

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexus.financetracker.domain.model.Contribution
import com.nexus.financetracker.domain.model.Goal
import com.nexus.financetracker.domain.model.GoalPriority
import java.text.NumberFormat
import java.util.Locale

private val DarkNavyBg = Color(0xFF072044)
private val LightCardBg = Color(0xFFF1F5F9)
private val CardBorderColor = Color(0xFFCBD5E1)
private val TextMutedSlate = Color(0xFF64748B)

/**
 * Full-screen Contribution List Screen.
 * - Displays all monthly surplus contributions from category estimation reviews.
 * - Shows source category, contribution amount, and month label.
 * - Prompts confirmation dialog before deleting any contribution.
 * - Tapping a contribution shows all active goals sorted by priority level (High -> Medium -> Low)
 *   to allocate the contribution directly to that goal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionListScreen(
    viewModel: GoalViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contributions by viewModel.contributions.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var contributionToDelete by remember { mutableStateOf<Contribution?>(null) }
    var contributionForAllocation by remember { mutableStateOf<Contribution?>(null) }

    val currencyFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    val availableContributions = contributions.filter { !it.isAllocated }
    val totalAvailableLKR = availableContributions.sumOf { it.amountLKR }
    val totalAllocatedLKR = contributions.filter { it.isAllocated }.sumOf { it.amountLKR }

    // Ongoing goals only (active, not achieved, not overdue) sorted by Priority Level: High -> Medium -> Low
    val ongoingGoals = remember(uiState.goals, uiState.calculations) {
        val priorityRank = mapOf(
            GoalPriority.HIGH to 1,
            GoalPriority.MEDIUM to 2,
            GoalPriority.LOW to 3
        )
        uiState.goals
            .filter { goal ->
                val calc = uiState.calculations[goal.id]
                val currentSavings = calc?.estimatedCurrentSavingsLKR ?: goal.openingSavingsLKR
                val isAchieved = (calc?.isCompleted == true) ||
                    ((calc?.remainingAmountLKR ?: (goal.targetAmountLKR - goal.openingSavingsLKR)) <= 0.0) ||
                    (currentSavings >= goal.targetAmountLKR)
                val isOverdue = (calc?.isOverdue == true && (calc.daysRemaining < 0L)) ||
                    (java.util.Date().after(goal.targetDate) && !isAchieved)
                goal.isActive && !isAchieved && !isOverdue
            }
            .sortedWith(compareBy({ priorityRank[it.priority.lowercase()] ?: 4 }, { it.title.lowercase() }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Goal Contributions",
                        fontWeight = FontWeight.Bold,
                        color = DarkNavyBg
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkNavyBg
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Stats Summary Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = DarkNavyBg
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Monthly Surplus Pool",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${contributions.size} Total",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Available to Allocate",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "LKR ${currencyFormat.format(totalAvailableLKR)}",
                                color = Color(0xFF4ADE80),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Allocated to Goals",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "LKR ${currencyFormat.format(totalAllocatedLKR)}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            // List of Contributions
            if (contributions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = LightCardBg,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = TextMutedSlate,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = "No Contributions Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavyBg
                        )
                        Text(
                            text = "At the end of each month, any unspent surplus from your estimated expense categories can be reviewed and added here to fund your goals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedSlate,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(contributions, key = { it.id }) { item ->
                        ContributionCardItem(
                            contribution = item,
                            currencyFormat = currencyFormat,
                            onItemClick = {
                                if (!item.isAllocated) {
                                    contributionForAllocation = item
                                }
                            },
                            onDeleteClick = {
                                contributionToDelete = item
                            }
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog before deleting contribution
    contributionToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { contributionToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Delete Contribution?",
                    fontWeight = FontWeight.Bold,
                    color = DarkNavyBg
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this contribution of LKR ${currencyFormat.format(item.amountLKR)} from ${item.categoryName}? This action cannot be undone.",
                    color = DarkNavyBg,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteContribution(item.id)
                        Toast.makeText(context, "Contribution deleted", Toast.LENGTH_SHORT).show()
                        contributionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { contributionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Active Goals according to Priority Level for allocation
    contributionForAllocation?.let { item ->
        AlertDialog(
            onDismissRequest = { contributionForAllocation = null },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Allocate to Goal",
                        fontWeight = FontWeight.Bold,
                        color = DarkNavyBg,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Choose an active goal for LKR ${currencyFormat.format(item.amountLKR)} (${item.categoryName})",
                        color = TextMutedSlate,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                if (ongoingGoals.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No ongoing savings goals found.",
                            fontWeight = FontWeight.SemiBold,
                            color = DarkNavyBg
                        )
                        Text(
                            text = "Achieved and expired goals cannot receive contributions. Create a new goal or check your goals on the Goals screen.",
                            fontSize = 12.sp,
                            color = TextMutedSlate,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(ongoingGoals, key = { it.id }) { goal ->
                            GoalPriorityAllocationCard(
                                goal = goal,
                                calculation = uiState.calculations[goal.id],
                                contributionAmount = item.amountLKR,
                                currencyFormat = currencyFormat,
                                onSelect = {
                                    viewModel.allocateContributionToGoal(goal, item) {
                                        Toast.makeText(
                                            context,
                                            "LKR ${currencyFormat.format(item.amountLKR)} allocated to ${goal.title}!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        contributionForAllocation = null
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contributionForAllocation = null }) {
                    Text("Close", color = DarkNavyBg, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ContributionCardItem(
    contribution: Contribution,
    currencyFormat: NumberFormat,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        onClick = onItemClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = LightCardBg,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = DarkNavyBg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = contribution.categoryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavyBg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = contribution.monthLabel.ifBlank { "Monthly Surplus" },
                            fontSize = 11.sp,
                            color = TextMutedSlate
                        )
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Contribution",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Contribution Amount",
                        fontSize = 11.sp,
                        color = TextMutedSlate
                    )
                    Text(
                        text = "LKR ${currencyFormat.format(contribution.amountLKR)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavyBg
                    )
                }

                if (contribution.isAllocated) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Allocated: ${contribution.allocatedGoalTitle ?: "Goal"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF15803D),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDBEAFE),
                        border = BorderStroke(1.dp, Color(0xFF93C5FD))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tap to Allocate ›",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalPriorityAllocationCard(
    goal: Goal,
    calculation: com.nexus.financetracker.domain.model.GoalCalculation? = null,
    contributionAmount: Double,
    currencyFormat: NumberFormat,
    onSelect: () -> Unit
) {
    val (priorityLabel, priorityColor, badgeBg) = when (goal.priority.lowercase()) {
        GoalPriority.HIGH -> Triple("HIGH PRIORITY", Color(0xFFDC2626), Color(0xFFFEE2E2))
        GoalPriority.LOW -> Triple("LOW PRIORITY", Color(0xFF2563EB), Color(0xFFDBEAFE))
        else -> Triple("MEDIUM PRIORITY", Color(0xFFD97706), Color(0xFFFEF3C7))
    }

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = goal.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = DarkNavyBg,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = priorityColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = priorityLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = priorityColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentSaved = calculation?.let { minOf(goal.targetAmountLKR, maxOf(0.0, it.estimatedCurrentSavingsLKR)) } ?: goal.openingSavingsLKR
                val daysText = calculation?.daysRemainingText ?: goal.daysRemainingText
                Text(
                    text = "Saved: LKR ${currencyFormat.format(currentSaved)} / ${currencyFormat.format(goal.targetAmountLKR)}",
                    fontSize = 11.sp,
                    color = TextMutedSlate
                )
                Text(
                    text = daysText,
                    fontSize = 11.sp,
                    color = TextMutedSlate
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFDCFCE7),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "+ LKR ${currencyFormat.format(contributionAmount)} will be added to this goal",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF15803D),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

