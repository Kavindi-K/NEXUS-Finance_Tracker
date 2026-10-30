package com.nexus.financetracker.ui.goal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag

import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
private val NavyCardBg = Color(0xFF132D52)           // Hero summary card matching Dashboard/Income
private val NavyCardBorder = Color(0x33FFFFFF)       // Subtle border for navy card

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
    var showPrioritySheet by remember { mutableStateOf(false) }

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
                uiState.isLoading && uiState.goals.isEmpty() -> {
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

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Offline Indicator Banner
                        if (uiState.isOffline) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFEF3C7),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFDE68A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudOff,
                                            contentDescription = "Offline Mode",
                                            tint = Color(0xFFB45309),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Offline Mode Active",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E)
                                        )
                                        Text(
                                            text = "Goals saved to local Room cache and will sync automatically once reconnected.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }
                            }
                        }

                        // 1. Hero Goal Card (Clickable to show High/Med/Low priority breakdown sheet)
                        Card(
                            onClick = { showPrioritySheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = NavyCardBg),
                            border = BorderStroke(1.dp, NavyCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Savings,
                                            contentDescription = null,
                                            tint = GoalNavyTextSubtle
                                        )
                                        Text(
                                            text = "Savings Goals",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GoalNavyTextSubtle
                                        )
                                    }

                                    AssistChip(
                                        onClick = { showPrioritySheet = true },
                                        label = {
                                            Text(
                                                text = if (uiState.selectedPriority != null) {
                                                    "${GoalPriority.getDisplayName(uiState.selectedPriority!!)} Priority"
                                                } else {
                                                    "Priorities ›"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Color.White.copy(alpha = 0.15f)
                                        ),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "${uiState.goals.size} ${if (uiState.goals.size == 1) "Goal" else "Goals"}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // In small display: how many high, medium, and low priority ones (tap to filter)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isHighSelected = uiState.selectedPriority.equals(GoalPriority.HIGH, ignoreCase = true)
                                    PriorityPill(
                                        count = uiState.highPriorityCount,
                                        label = "High",
                                        dotColor = Color(0xFFEF4444),
                                        containerColor = if (isHighSelected) Color(0x66EF4444) else Color(0x26EF4444),
                                        borderColor = if (isHighSelected) Color(0xFFEF4444) else Color(0x4DE57373),
                                        onClick = {
                                            viewModel.onPrioritySelected(if (isHighSelected) null else GoalPriority.HIGH)
                                        }
                                    )

                                    val isMedSelected = uiState.selectedPriority.equals(GoalPriority.MEDIUM, ignoreCase = true)
                                    PriorityPill(
                                        count = uiState.mediumPriorityCount,
                                        label = "Medium",
                                        dotColor = Color(0xFFF59E0B),
                                        containerColor = if (isMedSelected) Color(0x66F59E0B) else Color(0x26F59E0B),
                                        borderColor = if (isMedSelected) Color(0xFFF59E0B) else Color(0x4DFB8C00),
                                        onClick = {
                                            viewModel.onPrioritySelected(if (isMedSelected) null else GoalPriority.MEDIUM)
                                        }
                                    )

                                    val isLowSelected = uiState.selectedPriority.equals(GoalPriority.LOW, ignoreCase = true)
                                    PriorityPill(
                                        count = uiState.lowPriorityCount,
                                        label = "Low",
                                        dotColor = Color(0xFF94A3B8),
                                        containerColor = if (isLowSelected) Color(0x6694A3B8) else Color(0x2694A3B8),
                                        borderColor = if (isLowSelected) Color(0xFF94A3B8) else Color(0x4D94A3B8),
                                        onClick = {
                                            viewModel.onPrioritySelected(if (isLowSelected) null else GoalPriority.LOW)
                                        }
                                    )
                                }
                            }
                        }

                        // 1.5 Active Priority Filter Indicator (if any priority is selected)
                        if (uiState.selectedPriority != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { viewModel.onPrioritySelected(null) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = GoalLightCardBg,
                                    border = BorderStroke(1.dp, GoalNavyPrimary.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Priority: ${GoalPriority.getDisplayName(uiState.selectedPriority!!)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = GoalNavyDark
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear filter",
                                            tint = GoalNavyDark,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { viewModel.onPrioritySelected(null) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Show All Priorities", style = MaterialTheme.typography.labelSmall, color = GoalNavyPrimary)
                                }
                            }
                        }

                        // 2. Filter Chips: All, Ongoing, Achieved, Couldn't Achieved (matching Income Screen)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GoalStatusFilter.values().forEach { filter ->
                                val isSelected = uiState.selectedFilter == filter
                                val count = uiState.getCountForFilter(filter)
                                val label = when (filter) {
                                    GoalStatusFilter.ALL -> "All ($count)"
                                    GoalStatusFilter.ONGOING -> "Ongoing ($count)"
                                    GoalStatusFilter.ACHIEVED -> "Achieved ($count)"
                                    GoalStatusFilter.COULD_NOT_ACHIEVE -> "Couldn't Achieved ($count)"
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onFilterSelected(filter) },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoalNavyDark,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        // 3. Scrollable List of Goal Items (or empty state)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            val filteredGoals = uiState.filteredGoals
                            when {
                                uiState.goals.isEmpty() -> {
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Savings,
                                            contentDescription = null,
                                            tint = GoalNavyPrimary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(64.dp)
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
                                            textAlign = TextAlign.Center
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

                                filteredGoals.isEmpty() -> {
                                    val emptyMessage = when {
                                        uiState.selectedPriority != null -> "No ${GoalPriority.getDisplayName(uiState.selectedPriority!!)} priority goals found."
                                        uiState.selectedFilter == GoalStatusFilter.ONGOING -> "No ongoing goals right now."
                                        uiState.selectedFilter == GoalStatusFilter.ACHIEVED -> "No achieved goals yet. Keep saving!"
                                        uiState.selectedFilter == GoalStatusFilter.COULD_NOT_ACHIEVE -> "No missed deadline goals."
                                        else -> "No goals available."
                                    }
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Savings,
                                            contentDescription = null,
                                            tint = GoalSubtleNavy.copy(alpha = 0.4f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = emptyMessage,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GoalNavyDark
                                        )
                                        Text(
                                            text = "Switch filters above or select another priority category.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GoalSubtleNavy
                                        )
                                    }
                                }

                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 80.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(items = filteredGoals, key = { it.id }) { goal ->
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
            }
        }

        // 4. Modal Bottom Sheet for Priority Breakdown & Quick Filter (opens on tapping Total Goal Card)
        if (showPrioritySheet) {
            GoalPriorityBottomSheet(
                uiState = uiState,
                currencyFormat = currencyFormat,
                onSelectPriority = { priority ->
                    viewModel.onPrioritySelected(priority)
                },
                onDismiss = { showPrioritySheet = false }
            )
        }
    }
}

@Composable
private fun PriorityPill(
    count: Int,
    label: String,
    dotColor: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = "$count $label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * Modal Bottom Sheet for High, Medium, and Low Priority Categories Breakdown.
 * Tapping any category filters the relevant savings goals on the main screen (just like Income Stream breakdown).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalPriorityBottomSheet(
    uiState: GoalUiState,
    currencyFormat: NumberFormat,
    onSelectPriority: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Goal Priorities Breakdown",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Total Goals: ${uiState.goals.size} • Tap category to filter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Categories list
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // All Priorities option
                val totalTargetAll = uiState.goals.sumOf { it.targetAmountLKR }
                PriorityCategoryItem(
                    title = "All Priorities",
                    count = uiState.goals.size,
                    totalAmount = totalTargetAll,
                    icon = Icons.Default.Savings,
                    iconBg = GoalNavyPrimary.copy(alpha = 0.12f),
                    iconTint = GoalNavyPrimary,
                    isSelected = uiState.selectedPriority == null,
                    currencyFormat = currencyFormat,
                    onClick = {
                        onSelectPriority(null)
                        onDismiss()
                    }
                )

                // High Priority
                val highGoals = uiState.goals.filter { it.priority.equals(GoalPriority.HIGH, ignoreCase = true) }
                val totalTargetHigh = highGoals.sumOf { it.targetAmountLKR }
                PriorityCategoryItem(
                    title = "High Priority",
                    count = uiState.highPriorityCount,
                    totalAmount = totalTargetHigh,
                    icon = Icons.Default.Flag,
                    iconBg = Color(0xFFFEE2E2),
                    iconTint = Color(0xFFDC2626),
                    isSelected = uiState.selectedPriority.equals(GoalPriority.HIGH, ignoreCase = true),
                    currencyFormat = currencyFormat,
                    onClick = {
                        onSelectPriority(GoalPriority.HIGH)
                        onDismiss()
                    }
                )

                // Medium Priority
                val medGoals = uiState.goals.filter {
                    it.priority.equals(GoalPriority.MEDIUM, ignoreCase = true) ||
                        (!it.priority.equals(GoalPriority.HIGH, ignoreCase = true) && !it.priority.equals(GoalPriority.LOW, ignoreCase = true))
                }
                val totalTargetMed = medGoals.sumOf { it.targetAmountLKR }
                PriorityCategoryItem(
                    title = "Medium Priority",
                    count = uiState.mediumPriorityCount,
                    totalAmount = totalTargetMed,
                    icon = Icons.Default.Flag,
                    iconBg = Color(0xFFFEF3C7),
                    iconTint = Color(0xFFD97706),
                    isSelected = uiState.selectedPriority.equals(GoalPriority.MEDIUM, ignoreCase = true),
                    currencyFormat = currencyFormat,
                    onClick = {
                        onSelectPriority(GoalPriority.MEDIUM)
                        onDismiss()
                    }
                )

                // Low Priority
                val lowGoals = uiState.goals.filter { it.priority.equals(GoalPriority.LOW, ignoreCase = true) }
                val totalTargetLow = lowGoals.sumOf { it.targetAmountLKR }
                PriorityCategoryItem(
                    title = "Low Priority",
                    count = uiState.lowPriorityCount,
                    totalAmount = totalTargetLow,
                    icon = Icons.Default.Flag,
                    iconBg = Color(0xFFF1F5F9),
                    iconTint = Color(0xFF475569),
                    isSelected = uiState.selectedPriority.equals(GoalPriority.LOW, ignoreCase = true),
                    currencyFormat = currencyFormat,
                    onClick = {
                        onSelectPriority(GoalPriority.LOW)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun PriorityCategoryItem(
    title: String,
    count: Int,
    totalAmount: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    isSelected: Boolean,
    currencyFormat: NumberFormat,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) GoalLightCardBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = if (isSelected) BorderStroke(1.5.dp, GoalNavyPrimary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoalNavyDark
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$count ${if (count == 1) "Goal" else "Goals"} • LKR ${currencyFormat.format(totalAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoalSubtleNavy
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = GoalNavyPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = GoalSubtleNavy.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
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
