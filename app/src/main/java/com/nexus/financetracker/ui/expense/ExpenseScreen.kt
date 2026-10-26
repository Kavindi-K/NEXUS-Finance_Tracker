package com.nexus.financetracker.ui.expense

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import java.text.SimpleDateFormat
import java.util.Locale

// App Theme Colors (Matching Deep Sapphire Navy Design System)
private val NavyPrimary = Color(0xFF072044)
private val TextSubtle = Color(0xFF64748B)

/**
 * Expenses Screen — Conforms to Member 2 Order 3 (feature/expense-list).
 * Features: Expense history, detail sheet, edit, delete, sorting, account filters, and empty view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val fullDateTimeFormatter = remember { SimpleDateFormat("EEEE, MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    var selectedExpenseForDetail by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    // Category Management State
    var showCategoryManagementSheet by remember { mutableStateOf(false) }
    var categoryToRename by remember { mutableStateOf<Category?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }

    var categoryToMerge by remember { mutableStateOf<Category?>(null) }
    var mergeTargetId by remember { mutableStateOf<String?>(null) }
    var mergeError by remember { mutableStateOf<String?>(null) }

    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryError by remember { mutableStateOf<String?>(null) }

    // Detail Bottom Sheet
    selectedExpenseForDetail?.let { expense ->
        val catName = uiState.categoriesMap[expense.categoryId]
            ?: DEFAULT_EXPENSE_CATEGORIES.find { it.id == expense.categoryId }?.name
            ?: expense.categoryId

        ModalBottomSheet(
            onDismissRequest = { selectedExpenseForDetail = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row with Category & Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(catName),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Text(
                                text = ExpenseClassification.getDisplayName(expense.classification),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSubtle
                            )
                        }
                    }

                    Text(
                        text = "- LKR %,.2f".format(expense.amountLKR),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Metadata Rows
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow(label = "Date & Time", value = fullDateTimeFormatter.format(expense.date))
                    DetailRow(label = "Payment Account", value = ExpenseAccount.getDisplayName(expense.account))
                    DetailRow(label = "Classification", value = ExpenseClassification.getDisplayName(expense.classification))
                    if (!expense.note.isNullOrBlank()) {
                        DetailRow(label = "Note", value = expense.note)
                    }
                    if (!expense.recurringItemId.isNullOrBlank()) {
                        DetailRow(label = "Recurring Schedule", value = "Linked to recurring schedule")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions: Edit and Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val id = expense.id
                            selectedExpenseForDetail = null
                            onEditExpense(id)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit")
                    }

                    Button(
                        onClick = {
                            val toDelete = expense
                            selectedExpenseForDetail = null
                            expenseToDelete = toDelete
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    expenseToDelete?.let { expense ->
        val catName = uiState.categoriesMap[expense.categoryId]
            ?: DEFAULT_EXPENSE_CATEGORIES.find { it.id == expense.categoryId }?.name
            ?: expense.categoryId

        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete Expense") },
            text = { Text("Are you sure you want to delete this expense of LKR %,.2f for $catName? This will be removed from your records.".format(expense.amountLKR)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExpense(expense.id)
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Category Management Sheet
    if (showCategoryManagementSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryManagementSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Expense Categories",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Text(
                            text = "Create, rename, or merge categories. Past expenses remain mapped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSubtle
                        )
                    }
                    IconButton(onClick = { showCategoryManagementSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = NavyPrimary)
                    }
                }

                // Add Category Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = {
                            newCategoryName = it
                            newCategoryError = null
                        },
                        label = { Text("New Category Name") },
                        placeholder = { Text("e.g. Gaming, Gym, Pet Care") },
                        singleLine = true,
                        isError = newCategoryError != null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            viewModel.createCategory(
                                name = newCategoryName,
                                onSuccess = {
                                    newCategoryName = ""
                                    newCategoryError = null
                                },
                                onError = { newCategoryError = it }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                if (newCategoryError != null) {
                    Text(
                        text = newCategoryError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Scrollable Categories List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.customCategories.isNotEmpty()) {
                        item {
                            Text(
                                text = "Your Custom Categories (${uiState.customCategories.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                        }

                        items(uiState.customCategories, key = { it.id }) { cat ->
                            val count = uiState.expenses.count { it.categoryId == cat.id }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE0E7FF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Category,
                                                contentDescription = null,
                                                tint = Color(0xFF1E3A8A),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = cat.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = NavyPrimary
                                            )
                                            Text(
                                                text = "$count past ${if (count == 1) "expense" else "expenses"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSubtle
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Rename Action
                                        IconButton(
                                            onClick = {
                                                categoryToRename = cat
                                                renameInput = cat.name
                                                renameError = null
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = NavyPrimary, modifier = Modifier.size(18.dp))
                                        }

                                        // Merge Action
                                        IconButton(
                                            onClick = {
                                                categoryToMerge = cat
                                                mergeTargetId = null
                                                mergeError = null
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = "Merge into...", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                        }

                                        // Delete Action
                                        IconButton(
                                            onClick = { categoryToDelete = cat },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Standard Default Categories
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Standard Categories (${DEFAULT_EXPENSE_CATEGORIES.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }

                    items(DEFAULT_EXPENSE_CATEGORIES, key = { it.id }) { cat ->
                        val count = uiState.expenses.count { it.categoryId == cat.id }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(getCategoryIcon(cat.name), contentDescription = null, tint = NavyPrimary, modifier = Modifier.size(20.dp))
                                    Text(cat.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = NavyPrimary)
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White
                                ) {
                                    Text(
                                        text = "$count ${if (count == 1) "record" else "records"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSubtle,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Category Dialog
    categoryToRename?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToRename = null },
            title = { Text("Rename Category", fontWeight = FontWeight.Bold, color = NavyPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Renaming preserves the stable ID (${cat.id}). All past expenses remain linked to this category automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSubtle
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = {
                            renameInput = it
                            renameError = null
                        },
                        label = { Text("New Name") },
                        singleLine = true,
                        isError = renameError != null,
                        supportingText = renameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameCategory(
                            categoryId = cat.id,
                            newName = renameInput,
                            onSuccess = { categoryToRename = null },
                            onError = { renameError = it }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Merge Category Dialog
    categoryToMerge?.let { sourceCat ->
        val candidateTargets = uiState.allCategories.filter { it.id != sourceCat.id }
        val affectedCount = uiState.expenses.count { it.categoryId == sourceCat.id }

        AlertDialog(
            onDismissRequest = { categoryToMerge = null },
            title = { Text("Merge Category", fontWeight = FontWeight.Bold, color = NavyPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Merge '${sourceCat.name}' into another category. All $affectedCount past expenses will be permanently moved to the selected category, producing no broken entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSubtle
                    )
                    Text(
                        text = "Select Destination Category:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(candidateTargets, key = { it.id }) { target ->
                            val isChosen = mergeTargetId == target.id
                            Surface(
                                onClick = { mergeTargetId = target.id },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChosen) Color(0xFFE0E7FF) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isChosen) Color(0xFF1E3A8A) else Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = target.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) Color(0xFF1E3A8A) else NavyPrimary
                                    )
                                    if (isChosen) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF1E3A8A), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (mergeError != null) {
                        Text(
                            text = mergeError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = mergeTargetId
                        if (targetId.isNullOrBlank()) {
                            mergeError = "Please select a target category"
                        } else {
                            viewModel.mergeCategories(
                                sourceCategoryId = sourceCat.id,
                                targetCategoryId = targetId,
                                onSuccess = { categoryToMerge = null },
                                onError = { mergeError = it }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    enabled = mergeTargetId != null
                ) {
                    Text("Merge Expenses")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToMerge = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Category Dialog
    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category", fontWeight = FontWeight.Bold, color = NavyPrimary) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${cat.name}'? (Tip: If you have past expenses under this category, use 'Merge' instead to retain your records).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSubtle
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(cat.id, onSuccess = { categoryToDelete = null })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NavyPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard",
                            tint = NavyPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCategoryManagementSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = "Manage Categories",
                            tint = NavyPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExpense,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
            ) {
                // 1. Month Navigation / Period Selector Header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.onPreviousMonthClicked() },
                                    enabled = !uiState.isPeriodAllTime
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                                }
                                Text(
                                    text = uiState.selectedMonthLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                                IconButton(
                                    onClick = { viewModel.onNextMonthClicked() },
                                    enabled = !uiState.isPeriodAllTime
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                                }
                            }

                            FilterChip(
                                selected = uiState.isPeriodAllTime,
                                onClick = { viewModel.onToggleAllTimePeriod(!uiState.isPeriodAllTime) },
                                label = { Text(if (uiState.isPeriodAllTime) "Monthly" else "All Time", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // 2. Overview Summary Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
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
                                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Total Expenses",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = "${uiState.filteredExpenses.size} of ${uiState.expenses.size} records",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }

                            Text(
                                text = "LKR %,.2f".format(uiState.totalExpenseLKR),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f))

                            // Accounts Breakdown Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                AccountSummaryPill(
                                    title = "Card",
                                    amount = uiState.cardExpenseLKR,
                                    percentage = uiState.cardExpensePercentage,
                                    isSelected = uiState.selectedAccountFilter.equals(ExpenseAccount.CARD, ignoreCase = true),
                                    onClick = {
                                        if (uiState.selectedAccountFilter.equals(ExpenseAccount.CARD, ignoreCase = true)) {
                                            viewModel.onAccountFilterSelected(null)
                                        } else {
                                            viewModel.onAccountFilterSelected(ExpenseAccount.CARD)
                                        }
                                    }
                                )
                                AccountSummaryPill(
                                    title = "Cash",
                                    amount = uiState.cashExpenseLKR,
                                    percentage = uiState.cashExpensePercentage,
                                    isSelected = uiState.selectedAccountFilter.equals(ExpenseAccount.CASH, ignoreCase = true),
                                    onClick = {
                                        if (uiState.selectedAccountFilter.equals(ExpenseAccount.CASH, ignoreCase = true)) {
                                            viewModel.onAccountFilterSelected(null)
                                        } else {
                                            viewModel.onAccountFilterSelected(ExpenseAccount.CASH)
                                        }
                                    }
                                )
                                AccountSummaryPill(
                                    title = "Secondary",
                                    amount = uiState.secondaryExpenseLKR,
                                    percentage = uiState.secondaryExpensePercentage,
                                    isSelected = uiState.selectedAccountFilter.equals(ExpenseAccount.SECONDARY, ignoreCase = true),
                                    onClick = {
                                        if (uiState.selectedAccountFilter.equals(ExpenseAccount.SECONDARY, ignoreCase = true)) {
                                            viewModel.onAccountFilterSelected(null)
                                        } else {
                                            viewModel.onAccountFilterSelected(ExpenseAccount.SECONDARY)
                                        }
                                    }
                                )
                            }

                            // Visual Account Proportion Bar (Card: Blue, Cash: Green, Secondary: Purple)
                            if (uiState.totalExpenseLKR > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                ) {
                                    if (uiState.cardExpenseLKR > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight((uiState.cardExpenseLKR / uiState.totalExpenseLKR).toFloat())
                                                .fillMaxHeight()
                                                .background(Color(0xFF3B82F6))
                                        )
                                    }
                                    if (uiState.cashExpenseLKR > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight((uiState.cashExpenseLKR / uiState.totalExpenseLKR).toFloat())
                                                .fillMaxHeight()
                                                .background(Color(0xFF10B981))
                                        )
                                    }
                                    if (uiState.secondaryExpenseLKR > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight((uiState.secondaryExpenseLKR / uiState.totalExpenseLKR).toFloat())
                                                .fillMaxHeight()
                                                .background(Color(0xFFA855F7))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Filter and Sort Controls
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Account Filter Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.selectedAccountFilter == null,
                                onClick = { viewModel.onAccountFilterSelected(null) },
                                label = { Text("All Accounts (${uiState.expenses.size})") }
                            )
                            ExpenseAccount.ALL.forEach { account ->
                                val count = uiState.expenses.count { it.account.equals(account, ignoreCase = true) }
                                val icon = when (account) {
                                    ExpenseAccount.CARD -> Icons.Default.CreditCard
                                    ExpenseAccount.CASH -> Icons.Default.Payments
                                    else -> Icons.Default.AccountBalanceWallet
                                }
                                FilterChip(
                                    selected = uiState.selectedAccountFilter.equals(account, ignoreCase = true),
                                    onClick = { viewModel.onAccountFilterSelected(account) },
                                    leadingIcon = {
                                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = { Text("${ExpenseAccount.getDisplayName(account)} ($count)") }
                                )
                            }
                        }

                        // Sort Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sort:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextSubtle
                            )
                            SortChip(
                                label = "Date ↓",
                                selected = uiState.selectedSortOrder == ExpenseSortOrder.DATE_DESC,
                                onClick = { viewModel.onSortOrderSelected(ExpenseSortOrder.DATE_DESC) }
                            )
                            SortChip(
                                label = "Date ↑",
                                selected = uiState.selectedSortOrder == ExpenseSortOrder.DATE_ASC,
                                onClick = { viewModel.onSortOrderSelected(ExpenseSortOrder.DATE_ASC) }
                            )
                            SortChip(
                                label = "Amount ↓",
                                selected = uiState.selectedSortOrder == ExpenseSortOrder.AMOUNT_DESC,
                                onClick = { viewModel.onSortOrderSelected(ExpenseSortOrder.AMOUNT_DESC) }
                            )
                            SortChip(
                                label = "Amount ↑",
                                selected = uiState.selectedSortOrder == ExpenseSortOrder.AMOUNT_ASC,
                                onClick = { viewModel.onSortOrderSelected(ExpenseSortOrder.AMOUNT_ASC) }
                            )
                        }
                    }
                }

                // 4. Empty State or Expenses List
                if (uiState.filteredExpenses.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            tonalElevation = 1.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Text(
                                        text = "No expenses recorded",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyPrimary
                                    )
                                    Text(
                                        text = if (uiState.selectedAccountFilter != null) {
                                            "No records found for ${ExpenseAccount.getDisplayName(uiState.selectedAccountFilter!!)} in ${uiState.selectedMonthLabel}."
                                        } else {
                                            "No expense records found for ${uiState.selectedMonthLabel}."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSubtle
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Button(
                                        onClick = onAddExpense,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Record an Expense")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.filteredExpenses, key = { it.id }) { expense ->
                        ExpenseItemCard(
                            expense = expense,
                            categoryName = uiState.categoriesMap[expense.categoryId]
                                ?: DEFAULT_EXPENSE_CATEGORIES.find { it.id == expense.categoryId }?.name
                                ?: expense.categoryId,
                            formattedDate = dateFormatter.format(expense.date),
                            onClick = { selectedExpenseForDetail = expense },
                            onDelete = { expenseToDelete = expense }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp) }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSubtle)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = NavyPrimary)
    }
}

@Composable
private fun AccountSummaryPill(
    title: String,
    amount: Double,
    percentage: Double = 0.0,
    isSelected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.error.copy(alpha = 0.25f) else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
                if (percentage > 0.0) {
                    Text(
                        text = "(%.0f%%)".format(percentage),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                    )
                }
            }
            Text(
                text = "LKR %,.0f".format(amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ExpenseItemCard(
    expense: Expense,
    categoryName: String,
    formattedDate: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(categoryName),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )

                if (!expense.note.isNullOrBlank()) {
                    Text(
                        text = expense.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSubtle,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8)
                    )
                    val (accBg, accText) = when (expense.account.lowercase()) {
                        ExpenseAccount.CARD -> Color(0xFFE0E7FF) to Color(0xFF1E3A8A)
                        ExpenseAccount.CASH -> Color(0xFFDCFCE7) to Color(0xFF166534)
                        ExpenseAccount.SECONDARY -> Color(0xFFF3E8FF) to Color(0xFF6B21A8)
                        else -> Color(0xFFF1F5F9) to NavyPrimary
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accBg
                    ) {
                        Text(
                            text = ExpenseAccount.getDisplayName(expense.account),
                            style = MaterialTheme.typography.labelSmall,
                            color = accText,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (expense.classification == ExpenseClassification.COMMITTED) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "Fixed",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Amount Column + Delete action
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "- LKR %,.2f".format(expense.amountLKR),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getCategoryIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("food") || lower.contains("dining") || lower.contains("restaurant") -> Icons.Default.Restaurant
        lower.contains("grocer") -> Icons.Default.ShoppingCart
        lower.contains("transport") || lower.contains("fuel") || lower.contains("cab") -> Icons.Default.DirectionsCar
        lower.contains("rent") || lower.contains("house") || lower.contains("housing") -> Icons.Default.Home
        lower.contains("bill") || lower.contains("utilit") || lower.contains("electric") || lower.contains("water") -> Icons.Default.Receipt
        lower.contains("shop") -> Icons.Default.ShoppingBag
        lower.contains("entertain") || lower.contains("movie") || lower.contains("game") -> Icons.Default.Movie
        lower.contains("health") || lower.contains("medic") || lower.contains("doctor") -> Icons.Default.LocalHospital
        else -> Icons.Default.Category
    }
}
