package com.nexus.financetracker.ui.goal

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    val monthLabelFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val currentMonthLabel = remember { monthLabelFormat.format(Date()) }
    var selectedMonthLabel by remember { mutableStateOf(currentMonthLabel) }
    var isPeriodAllTime by remember { mutableStateOf(true) }
    var showMonthDialog by remember { mutableStateOf(false) }

    val rollingMonths = remember {
        (0 downTo -23).map { offset ->
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
            monthLabelFormat.format(c.time)
        }
    }

    val contributionMonths = remember(contributions) {
        contributions.mapNotNull { item ->
            val label = item.monthLabel.trim().ifBlank { monthLabelFormat.format(item.createdAt) }
            label.takeIf { it.isNotBlank() }
        }
    }

    val availableMonths = remember(rollingMonths, contributionMonths) {
        (rollingMonths + contributionMonths).distinct().sortedByDescending { label ->
            try {
                monthLabelFormat.parse(label)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }

    val currencyFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    // Robust month matching: checks exact label, createdAt date, or parsed month & year
    fun matchesContributionMonth(contribution: Contribution, targetMonth: String): Boolean {
        val raw = contribution.monthLabel.trim()
        if (raw.equals(targetMonth, ignoreCase = true)) return true

        val createdLabel = monthLabelFormat.format(contribution.createdAt)
        if (createdLabel.equals(targetMonth, ignoreCase = true)) return true

        return try {
            val targetDate = monthLabelFormat.parse(targetMonth) ?: return false
            val calTarget = Calendar.getInstance().apply { time = targetDate }

            val parsedDate = listOf(
                monthLabelFormat,
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()),
                SimpleDateFormat("MMM yyyy", Locale.US),
                SimpleDateFormat("MMM yyyy", Locale.getDefault())
            ).firstNotNullOfOrNull { fmt ->
                try { fmt.parse(raw) } catch (_: Exception) { null }
            } ?: contribution.createdAt

            val calItem = Calendar.getInstance().apply { time = parsedDate }
            calItem.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR) &&
                calItem.get(Calendar.MONTH) == calTarget.get(Calendar.MONTH)
        } catch (_: Exception) {
            false
        }
    }

    val monthlyContributionTotals = remember(contributions, availableMonths) {
        availableMonths.associateWith { month ->
            val matched = contributions.filter { item ->
                matchesContributionMonth(item, month)
            }
            Pair(matched.sumOf { it.amountLKR }, matched.size)
        }
    }

    val filteredContributions = remember(contributions, isPeriodAllTime, selectedMonthLabel) {
        if (isPeriodAllTime) {
            contributions
        } else {
            contributions.filter { item ->
                matchesContributionMonth(item, selectedMonthLabel)
            }
        }
    }

    val availableContributions = filteredContributions.filter { !it.isAllocated }
    val totalAvailableLKR = availableContributions.sumOf { it.amountLKR }
    val totalAllocatedLKR = filteredContributions.filter { it.isAllocated }.sumOf { it.amountLKR }

    // Month Selector Dialog (Matching Income and Expense Screens)
    if (showMonthDialog) {
        AlertDialog(
            onDismissRequest = { showMonthDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Select Month",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Option 1: "All Time"
                    val isAllTimeSelected = isPeriodAllTime
                    Surface(
                        onClick = {
                            isPeriodAllTime = true
                            showMonthDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAllTimeSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "All Time",
                                    fontSize = 15.sp,
                                    fontWeight = if (isAllTimeSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllTimeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                val allTimeSum = contributions.sumOf { it.amountLKR }
                                Text(
                                    text = "Total: LKR ${currencyFormat.format(allTimeSum)} • ${contributions.size} item${if (contributions.size == 1) "" else "s"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            if (isAllTimeSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Option 2: Available Months
                    availableMonths.forEach { month ->
                        val isSelected = !isPeriodAllTime && month == selectedMonthLabel
                        val (monthTotal, monthCount) = monthlyContributionTotals[month] ?: Pair(0.0, 0)

                        Surface(
                            onClick = {
                                selectedMonthLabel = month
                                isPeriodAllTime = false
                                showMonthDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = month,
                                        fontSize = 15.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (monthCount > 0) {
                                        Text(
                                            text = "Total: LKR ${currencyFormat.format(monthTotal)} • $monthCount item${if (monthCount == 1) "" else "s"}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month Selector Filter Card (Matching Income and Expense screens)
            Card(
                onClick = { showMonthDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = "Month Filter",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = if (isPeriodAllTime) "All Time" else selectedMonthLabel.ifBlank { "All Time" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Choose",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Stats Summary Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
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
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Monthly Surplus Pool",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${filteredContributions.size} Total",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
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
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "LKR ${currencyFormat.format(totalAllocatedLKR)}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            // List of Contributions
            if (filteredContributions.isEmpty()) {
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
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isPeriodAllTime || contributions.isEmpty()) "No Contributions Yet" else "No Contributions in $selectedMonthLabel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isPeriodAllTime || contributions.isEmpty()) {
                                "At the end of each month, any unspent surplus from your estimated expense categories can be reviewed and added here to fund your goals."
                            } else {
                                "No goal contributions were saved for $selectedMonthLabel. Choose another month or select 'All Time' from the month filter above."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    items(filteredContributions, key = { it.id }) { item ->
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                val catDisplayName = if (item.categoryName.startsWith("Monthly Savings Surplus", ignoreCase = true)) "Monthly Savings Surplus" else item.categoryName
                Text(
                    text = "Are you sure you want to delete this contribution of LKR ${currencyFormat.format(item.amountLKR)} from $catDisplayName? This action cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                    val allocCatName = if (item.categoryName.startsWith("Monthly Savings Surplus", ignoreCase = true)) "Monthly Savings Surplus" else item.categoryName
                    Text(
                        text = "Choose an active goal for LKR ${currencyFormat.format(item.amountLKR)} ($allocCatName)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Achieved and expired goals cannot receive contributions. Create a new goal or check your goals on the Goals screen.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        val displayCategoryName = if (contribution.categoryName.startsWith("Monthly Savings Surplus", ignoreCase = true)) {
                            "Monthly Savings Surplus"
                        } else {
                            contribution.categoryName
                        }
                        Text(
                            text = displayCategoryName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val itemMonth = contribution.monthLabel.trim().ifBlank {
                            SimpleDateFormat("MMMM yyyy", Locale.US).format(contribution.createdAt)
                        }
                        Text(
                            text = itemMonth,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Contribution Amount",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "LKR ${currencyFormat.format(contribution.amountLKR)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tap to Allocate ›",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
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
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = daysText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
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
