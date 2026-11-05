package com.nexus.financetracker.ui.expense

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.nexus.financetracker.domain.model.DetectedBankExpense
import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.financetracker.data.util.ParsedReceipt
import com.nexus.financetracker.data.util.ReceiptScanner
import com.nexus.financetracker.domain.model.Category
import kotlinx.coroutines.launch
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// App Theme Colors (Matching Deep Sapphire Navy Design System)
private val NavyPrimary = Color(0xFF072044)
private val NavyCardBg = Color(0xFF132D52)
private val NavyCardBorder = Color(0x33FFFFFF)
private val TextHeaderSubtle = Color(0xFFBAC7D5)
private val TextSubtle = Color(0xFF64748B)
private val LightCardBg = Color(0xFFF1F5F9)
private val CardBorderColor = Color(0xFFCBD5E1)

/**
 * Expenses Screen — Conforms to Member 2 Order 3 (feature/expense-list).
 * Features: Expense history, detail sheet, edit, delete, sorting, account filters, and empty view.
 */
private fun resolveCategoryName(categoryId: String, categoriesMap: Map<String, String>): String {
    if (categoryId.isBlank()) return "Uncategorized"
    categoriesMap[categoryId]?.let { return it }

    DEFAULT_EXPENSE_CATEGORIES.find {
        it.id.equals(categoryId, ignoreCase = true) ||
        it.name.equals(categoryId, ignoreCase = true) ||
        it.id.replace("_", "-").equals(categoryId.replace("_", "-"), ignoreCase = true)
    }?.let { return it.name }

    categoriesMap.entries.find {
        it.key.equals(categoryId, ignoreCase = true) ||
        it.value.equals(categoryId, ignoreCase = true) ||
        it.key.replace("_", "-").equals(categoryId.replace("_", "-"), ignoreCase = true)
    }?.let { return it.value }

    if (categoryId.startsWith("cat_") || categoryId.startsWith("cat-")) {
        return categoryId.removePrefix("cat_").removePrefix("cat-")
            .replace("_", " ").replace("-", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
    }

    return categoryId
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    onAddExpense: () -> Unit,
    onManageCategories: () -> Unit = {},
    onEditExpense: (String) -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val fullDateTimeFormatter = remember { SimpleDateFormat("EEEE, MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    var showMonthDialog by remember { mutableStateOf(false) }
    val currencyFormat = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    var selectedExpenseForDetail by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var showBulkAddSheet by remember { mutableStateOf(false) }
    var expenseToCategorize by remember { mutableStateOf<DetectedBankExpense?>(null) }
    var selectedExpenseCategory by remember { mutableStateOf("cat_food") }
    var showPendingAlertsSheet by remember { mutableStateOf(false) }

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val receiveGranted = permissions[Manifest.permission.RECEIVE_SMS] == true
        val readGranted = permissions[Manifest.permission.READ_SMS] == true
        hasSmsPermission = receiveGranted || readGranted
    }

    LaunchedEffect(Unit) {
        if (!hasSmsPermission) {
            smsPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )
            )
        }
    }

    LaunchedEffect(hasSmsPermission) {
        if (hasSmsPermission) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.nexus.financetracker.data.util.BankSmsInboxScanner.scanRecentBankSms(context)
            }
        }
    }

    // Receipt Scanner State (Cash Payment Bill OCR)
    var showScanReceiptOptions by remember { mutableStateOf(false) }
    var isScanningReceipt by remember { mutableStateOf(false) }
    var scannedReceiptResult by remember { mutableStateOf<ParsedReceipt?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            isScanningReceipt = true
            coroutineScope.launch {
                val parsed = ReceiptScanner.processImageUri(context, tempPhotoUri!!)
                isScanningReceipt = false
                if (parsed != null) {
                    scannedReceiptResult = parsed
                } else {
                    Toast.makeText(context, "Could not detect clear bill total. Please enter manually.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isScanningReceipt = true
            coroutineScope.launch {
                val parsed = ReceiptScanner.processImageUri(context, uri)
                isScanningReceipt = false
                if (parsed != null) {
                    scannedReceiptResult = parsed
                } else {
                    Toast.makeText(context, "Could not detect clear bill total. Please enter manually.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Month Selector Dialog (Matching Income Screen)
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
                        tint = NavyPrimary
                    )
                    Text(
                        text = "Select Month",
                        color = NavyPrimary,
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
                    val isAllTimeSelected = uiState.isPeriodAllTime
                    Surface(
                        onClick = {
                            viewModel.onToggleAllTimePeriod(true)
                            showMonthDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAllTimeSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
                                    color = if (isAllTimeSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Expenses: LKR ${currencyFormat.format(uiState.overallExpenseLKR)}",
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
                    uiState.availableMonths.forEach { month ->
                        val isSelected = !uiState.isPeriodAllTime && month == uiState.selectedMonthLabel
                        val monthExpense = uiState.monthlyExpenseTotals[month] ?: 0.0

                        Surface(
                            onClick = {
                                viewModel.selectMonth(month)
                                showMonthDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
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
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (monthExpense > 0) {
                                        Text(
                                            text = "Expenses: LKR ${currencyFormat.format(monthExpense)}",
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
        val catName = resolveCategoryName(expense.categoryId, uiState.categoriesMap)

        ModalBottomSheet(
            onDismissRequest = { selectedExpenseForDetail = null },
            containerColor = MaterialTheme.colorScheme.surface,
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
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(catName),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ExpenseClassification.getDisplayName(expense.classification),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Metadata Rows
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow(label = "Date & Time", value = fullDateTimeFormatter.format(expense.date))
                    val isBackdated = remember(expense) {
                        !expense.isRecurring && run {
                            val calDate = Calendar.getInstance().apply { time = expense.date }
                            val calCreated = Calendar.getInstance().apply { time = expense.createdAt }
                            calDate.before(calCreated) && (
                                calDate.get(Calendar.YEAR) != calCreated.get(Calendar.YEAR) ||
                                calDate.get(Calendar.DAY_OF_YEAR) != calCreated.get(Calendar.DAY_OF_YEAR)
                            )
                        }
                    }
                    if (isBackdated) {
                        DetailRow(
                            label = "Entry Status",
                            value = "Backdated (Logged on ${dateFormatter.format(expense.createdAt)})"
                        )
                    }
                    DetailRow(label = "Payment Account", value = ExpenseAccount.getDisplayName(expense.account))
                    DetailRow(label = "Classification", value = ExpenseClassification.getDisplayName(expense.classification))
                    if (!expense.note.isNullOrBlank()) {
                        DetailRow(label = "Note", value = expense.note)
                    }
                    if (expense.isRecurring) {
                        DetailRow(
                            label = "Recurring Commitment",
                            value = expense.getRecurringProgressText() ?: "Recurring schedule"
                        )
                    } else if (!expense.recurringItemId.isNullOrBlank()) {
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
        val catName = resolveCategoryName(expense.categoryId, uiState.categoriesMap)

        if (expense.isRecurring) {
            AlertDialog(
                onDismissRequest = { expenseToDelete = null },
                title = { Text("Delete Recurring Expense") },
                text = {
                    Text(
                        "This expense is part of an active recurring series (${expense.getRecurringProgressText()}).\n\nWould you like to cancel all remaining future occurrences or only delete this single entry?"
                    )
                },
                confirmButton = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                viewModel.deleteRecurringSeries(expense, deleteFutureOnly = true)
                                expenseToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel Remaining Series", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.deleteExpense(expense.id)
                                expenseToDelete = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Delete This Month Only")
                        }

                        TextButton(
                            onClick = { expenseToDelete = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Keep Expense")
                        }
                    }
                }
            )
        } else {
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
    }

    // Category Management Sheet
    if (showCategoryManagementSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCategoryManagementSheet = false },
            sheetState = sheetState,
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Expense Categories",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Text(
                            text = "Tap any category to filter expenses, or manage custom ones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSubtle
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                showCategoryManagementSheet = false
                                onManageCategories()
                            }
                        ) {
                            Text("Full View ›", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        IconButton(onClick = { showCategoryManagementSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = NavyPrimary)
                        }
                    }
                }

                if (uiState.selectedCategoryFilter != null) {
                    val activeCatName = uiState.categoriesMap[uiState.selectedCategoryFilter]
                        ?: DEFAULT_EXPENSE_CATEGORIES.find { it.id == uiState.selectedCategoryFilter }?.name
                        ?: uiState.selectedCategoryFilter ?: ""
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE0E7FF),
                        border = BorderStroke(1.dp, Color(0xFF818CF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Filter: $activeCatName",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = NavyPrimary
                            )
                            TextButton(
                                onClick = {
                                    viewModel.onCategoryFilterSelected(null)
                                    showCategoryManagementSheet = false
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Clear Filter", color = Color(0xFF1E3A8A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
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

                val standardIds = remember { DEFAULT_EXPENSE_CATEGORIES.map { it.id.lowercase().trim() }.toSet() }
                val customCats = remember(uiState.customCategories) {
                    uiState.customCategories
                        .filter { it.id.isNotBlank() && it.id.lowercase().trim() !in standardIds }
                        .distinctBy { it.id }
                }

                // Scrollable Categories List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (customCats.isNotEmpty()) {
                        item(key = "header_custom_cats") {
                            Text(
                                text = "Your Custom Categories (${customCats.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                        }

                        items(customCats, key = { "custom_${it.id}" }) { cat ->
                            val count = uiState.expenses.count { it.categoryId == cat.id }
                            val isSelectedCategory = uiState.selectedCategoryFilter == cat.id
                            Surface(
                                onClick = {
                                    if (isSelectedCategory) {
                                        viewModel.onCategoryFilterSelected(null)
                                    } else {
                                        viewModel.onCategoryFilterSelected(cat.id)
                                    }
                                    showCategoryManagementSheet = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelectedCategory) Color(0xFFE0E7FF) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isSelectedCategory) NavyPrimary else Color(0xFFE2E8F0)),
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
                                                .background(if (isSelectedCategory) NavyPrimary else Color(0xFFE0E7FF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isSelectedCategory) Icons.Default.Check else Icons.Default.Category,
                                                contentDescription = null,
                                                tint = if (isSelectedCategory) Color.White else Color(0xFF1E3A8A),
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
                                                text = if (isSelectedCategory) "Active filter (Tap to clear)" else "$count past ${if (count == 1) "expense" else "expenses"} • Tap to filter",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelectedCategory) Color(0xFF1E3A8A) else TextSubtle
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
                    item(key = "header_standard_cats") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Standard Categories (${DEFAULT_EXPENSE_CATEGORIES.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }

                    items(DEFAULT_EXPENSE_CATEGORIES, key = { "standard_${it.id}" }) { cat ->
                        val count = uiState.expenses.count { it.categoryId == cat.id }
                        val isSelectedCategory = uiState.selectedCategoryFilter == cat.id
                        Surface(
                            onClick = {
                                if (isSelectedCategory) {
                                    viewModel.onCategoryFilterSelected(null)
                                } else {
                                    viewModel.onCategoryFilterSelected(cat.id)
                                }
                                showCategoryManagementSheet = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelectedCategory) Color(0xFFE0E7FF) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (isSelectedCategory) NavyPrimary else Color(0xFFE2E8F0)),
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
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelectedCategory) NavyPrimary else Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelectedCategory) Icons.Default.Check else getCategoryIcon(cat.name),
                                            contentDescription = null,
                                            tint = if (isSelectedCategory) Color.White else NavyPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelectedCategory) FontWeight.Bold else FontWeight.Medium,
                                            color = NavyPrimary
                                        )
                                        Text(
                                            text = if (isSelectedCategory) "Active filter (Tap to clear)" else "Tap to filter",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelectedCategory) Color(0xFF1E3A8A) else TextSubtle
                                        )
                                    }
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

    // Bulk Add Missed Expenses Bottom Sheet
    if (showBulkAddSheet) {
        BulkAddExpensesSheet(
            categories = uiState.allCategories.ifEmpty { DEFAULT_EXPENSE_CATEGORIES },
            onDismiss = { showBulkAddSheet = false },
            onSubmit = { items ->
                viewModel.submitBulkExpenses(
                    items = items,
                    onSuccess = { count ->
                        Toast.makeText(context, "Added $count missed expenses successfully", Toast.LENGTH_SHORT).show()
                        items.firstOrNull()?.date?.let { firstDate ->
                            viewModel.navigateToExpenseMonth(firstDate)
                        }
                        showBulkAddSheet = false
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // Scan Cash Bill / Receipt Options Sheet
    if (showScanReceiptOptions) {
        ModalBottomSheet(
            onDismissRequest = { showScanReceiptOptions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    // Bottom Sheet for Multiple Pending Bank Debit Alerts
    if (showPendingAlertsSheet) {
        val pendingList = uiState.pendingDetectedExpenses
        ModalBottomSheet(
            onDismissRequest = { showPendingAlertsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Scan Cash Bill / Receipt",
                            style = MaterialTheme.typography.titleMedium,
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Detected Bank Debits",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Automatically extract bill amount for cash expense",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Option 1: Take Photo with Camera
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showScanReceiptOptions = false
                            try {
                                val uri = ReceiptScanner.createTempImageUri(context)
                                tempPhotoUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Take Photo",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Use camera to photograph physical receipt chit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Option 2: Choose from Gallery
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showScanReceiptOptions = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Choose from Gallery",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select an existing bill photo or screenshot",
                            text = if (pendingList.isNotEmpty()) "${pendingList.size} unreviewed alert${if (pendingList.size == 1) "" else "s"}" else "No alerts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (pendingList.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.dismissAllDetectedExpenses()
                                showPendingAlertsSheet = false
                            }
                        ) {
                            Text("Dismiss All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (pendingList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "All caught up!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "No unreviewed bank debit alerts right now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // OCR Scanning In-Progress Dialog
    if (isScanningReceipt) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            text = {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Scanning Bill / Receipt...",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Extracting total amount from bill",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }

    // Review / Confirm Scanned Receipt Bottom Sheet
    scannedReceiptResult?.let { parsed ->
        var editableAmount by remember(parsed) {
            mutableStateOf(
                if (parsed.amountLKR % 1.0 == 0.0) parsed.amountLKR.toLong().toString()
                else String.format(Locale.US, "%.2f", parsed.amountLKR)
            )
        }
        val currentCats = uiState.allCategories.ifEmpty { DEFAULT_EXPENSE_CATEGORIES }
        var selectedCatId by remember(parsed) { mutableStateOf<String?>(null) }
        var selectedReceiptDate by remember(parsed) { mutableStateOf(parsed.date) }
        var categoryError by remember { mutableStateOf(false) }
        var showCatMenu by remember { mutableStateOf(false) }
        val selCat = currentCats.find { it.id == selectedCatId }

        ModalBottomSheet(
            onDismissRequest = { scannedReceiptResult = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Bill Scanned",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Cash payment detected",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Cash Pill Badge (Styled consistently with the app's account tags)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Cash",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Amount Field
                OutlinedTextField(
                    value = editableAmount,
                    onValueChange = { editableAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Total Amount (LKR)") },
                    prefix = { Text("LKR ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Category Selector (User chooses explicitly - no auto-categorization)
                Box {
                    OutlinedTextField(
                        value = selCat?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        isError = categoryError && selectedCatId == null,
                        label = { Text("Choose Category *") },
                        placeholder = { Text("Tap to choose category") },
                        trailingIcon = {
                            IconButton(onClick = { showCatMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Category",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        supportingText = if (categoryError && selectedCatId == null) {
                            { Text("Please choose a category", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCatMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    DropdownMenu(
                        expanded = showCatMenu,
                        onDismissRequest = { showCatMenu = false },
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        currentCats.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name, fontWeight = if (cat.id == selectedCatId) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    selectedCatId = cat.id
                                    categoryError = false
                                    showCatMenu = false
                                }
                            )
                        }
                    }
                }

                // Date Selector (Defaults to extracted receipt date or today, tap to change)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cal = Calendar.getInstance().apply { time = selectedReceiptDate }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                        set(Calendar.HOUR_OF_DAY, 12)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                    }
                                    selectedReceiptDate = newCal.time
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Receipt Date: ${dateFormatter.format(selectedReceiptDate)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val finalAmount = editableAmount.toDoubleOrNull() ?: parsed.amountLKR
                            val updatedParsed = parsed.copy(
                                amountLKR = finalAmount,
                                date = selectedReceiptDate
                            )
                            viewModel.applyScannedReceipt(updatedParsed, userChosenCategoryId = selectedCatId)
                            scannedReceiptResult = null
                            onNavigateToAddExpense()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Edit in Form", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            if (selectedCatId == null) {
                                categoryError = true
                                Toast.makeText(context, "Please choose a category first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val finalAmount = editableAmount.toDoubleOrNull() ?: parsed.amountLKR
                            viewModel.saveScannedReceiptExpense(
                                amount = finalAmount,
                                categoryId = selectedCatId!!,
                                date = selectedReceiptDate,
                                onSuccess = {
                                    Toast.makeText(context, "Cash expense recorded: LKR %,.2f".format(finalAmount), Toast.LENGTH_SHORT).show()
                                    scannedReceiptResult = null
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Save Cash Expense", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pendingList, key = { it.id }) { debit ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.NotificationsActive,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = "${debit.bankName} • ${debit.accountNumber}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${dateFormatter.format(debit.date)}${if (!debit.merchant.isNullOrBlank()) " • ${debit.merchant}" else ""}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Text(
                                            text = "- LKR %,.2f".format(debit.amountLKR),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFDC2626)
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.rejectDetectedExpense(debit) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Reject",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        Button(
                                            onClick = {
                                                selectedExpenseCategory = "cat_food"
                                                expenseToCategorize = debit
                                                showPendingAlertsSheet = false
                                            },
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF15803D),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Accept",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Accept & Categorize", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Categorize Detected Bank Debit Dialog
    if (expenseToCategorize != null) {
        val currentExpense = expenseToCategorize!!
        AlertDialog(
            onDismissRequest = { expenseToCategorize = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Categorize Bank Debit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Summary card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentExpense.bankName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    text = "- LKR %,.2f".format(currentExpense.amountLKR),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFFDC2626)
                                )
                            }
                            if (!currentExpense.merchant.isNullOrBlank()) {
                                Text(
                                    text = "Merchant: ${currentExpense.merchant}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF7F1D1D)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Assign a Category:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val activeCategories = remember(uiState.allCategories) {
                        (uiState.allCategories.ifEmpty { DEFAULT_EXPENSE_CATEGORIES })
                            .distinctBy { it.id }
                            .take(5)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeCategories.take(3).forEach { cat ->
                                val isSelected = selectedExpenseCategory == cat.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedExpenseCategory = cat.id },
                                    label = { Text(cat.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeCategories.drop(3).take(2).forEach { cat ->
                                val isSelected = selectedExpenseCategory == cat.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedExpenseCategory = cat.id },
                                    label = { Text(cat.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // "+ Other" button
                            FilterChip(
                                selected = false,
                                onClick = {
                                    expenseToCategorize = null
                                    viewModel.prefillForCustomExpense(currentExpense)
                                    onAddExpense()
                                },
                                label = { Text("+ Other", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = expenseToCategorize
                        if (current != null) {
                            viewModel.acceptDetectedExpense(
                                expense = current,
                                categoryId = selectedExpenseCategory,
                                onSuccess = {
                                    Toast.makeText(context, "Expense recorded!", Toast.LENGTH_SHORT).show()
                                }
                            )
                            expenseToCategorize = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    )
                ) {
                    Text("Confirm & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToCategorize = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    val pendingCount = uiState.pendingDetectedExpenses.size
                    IconButton(
                        onClick = {
                            val isSmsSettingEnabled = com.nexus.financetracker.data.local.AppSettingsManager.isBankSmsDetectionEnabled(context)
                            if (!isSmsSettingEnabled && pendingCount == 0) {
                                Toast.makeText(context, "Bank SMS detection is turned off in Settings", Toast.LENGTH_SHORT).show()
                            } else if (!hasSmsPermission) {
                                smsPermissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                            } else if (pendingCount > 0) {
                                showPendingAlertsSheet = true
                            } else {
                                Toast.makeText(context, "No pending bank debit alerts", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        BadgedBox(
                            badge = {
                                if (pendingCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text("$pendingCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (!hasSmsPermission) Icons.Default.NotificationsNone
                                    else if (pendingCount > 0) Icons.Default.NotificationsActive
                                    else Icons.Default.Notifications,
                                contentDescription = "Detected Bank Debits",
                                tint = if (pendingCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showBulkAddSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Bulk Add Missed Expenses",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showCategoryManagementSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = "Manage Categories",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // New Feature: Scan Cash Bill / Receipt Icon (Matching Navy Theme)
                FloatingActionButton(
                    onClick = { showScanReceiptOptions = true },
                    containerColor = NavyCardBg,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Scan Bill / Receipt",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Existing + icon (Navigates to Categories / Add Expense)
                FloatingActionButton(
                    onClick = onAddExpense,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp)
            ) {
                // Offline Indicator Banner (Member 2 Order 7)
                if (uiState.isOffline) {
                    item {
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
                                        text = "Changes saved to local Room cache and will sync automatically once reconnected.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. Month Selector Filter Card (Matching Income Screen)
                item {
                    Card(
                        onClick = { showMonthDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
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
                                    text = if (uiState.isPeriodAllTime) "All Time" else uiState.selectedMonthLabel.ifBlank { "All Time" },
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
                }

                // 2. Overview Summary Card (Signature NEXUS Deep Navy Hero Card)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = NavyCardBg
                        ),
                        border = BorderStroke(1.dp, NavyCardBorder),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = Color(0xFFFCA5A5)
                                )
                                Text(
                                    text = "Total Expenses",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextHeaderSubtle
                                )
                            }

                            Text(
                                text = "LKR %,.2f".format(uiState.totalExpenseLKR),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 0.8.dp)

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
                        }
                    }
                }

                // 3. Filter Chips & Sort Controls (Compact grouping matching Income Screen)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = uiState.selectedAccountFilter == null,
                                    onClick = { viewModel.onAccountFilterSelected(null) },
                                    label = { Text("All Accounts (${uiState.expenses.size})", fontSize = 12.sp) },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(28.dp)
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
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.height(28.dp),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (uiState.selectedAccountFilter.equals(account, ignoreCase = true)) MaterialTheme.colorScheme.onSecondaryContainer else NavyPrimary
                                            )
                                        },
                                        label = { Text("${ExpenseAccount.getDisplayName(account)} ($count)", fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        // Modern Sleek Sort Control Bar
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
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Sort by",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isDateActive = uiState.selectedSortOrder == ExpenseSortOrder.DATE_DESC || uiState.selectedSortOrder == ExpenseSortOrder.DATE_ASC
                                val isDateDesc = uiState.selectedSortOrder == ExpenseSortOrder.DATE_DESC

                                Surface(
                                    onClick = {
                                        if (isDateActive) {
                                            viewModel.onSortOrderSelected(if (isDateDesc) ExpenseSortOrder.DATE_ASC else ExpenseSortOrder.DATE_DESC)
                                        } else {
                                            viewModel.onSortOrderSelected(ExpenseSortOrder.DATE_DESC)
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isDateActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (isDateActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = if (isDateActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (isDateActive) {
                                                if (isDateDesc) "Date (Newest)" else "Date (Oldest)"
                                            } else "Date",
                                            fontSize = 12.sp,
                                            fontWeight = if (isDateActive) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isDateActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isDateActive) {
                                            Icon(
                                                imageVector = if (isDateDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }

                                val isAmountActive = uiState.selectedSortOrder == ExpenseSortOrder.AMOUNT_DESC || uiState.selectedSortOrder == ExpenseSortOrder.AMOUNT_ASC
                                val isAmountDesc = uiState.selectedSortOrder == ExpenseSortOrder.AMOUNT_DESC

                                Surface(
                                    onClick = {
                                        if (isAmountActive) {
                                            viewModel.onSortOrderSelected(if (isAmountDesc) ExpenseSortOrder.AMOUNT_ASC else ExpenseSortOrder.AMOUNT_DESC)
                                        } else {
                                            viewModel.onSortOrderSelected(ExpenseSortOrder.AMOUNT_DESC)
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isAmountActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (isAmountActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (isAmountActive) {
                                                if (isAmountDesc) "Amount (High)" else "Amount (Low)"
                                            } else "Amount",
                                            fontSize = 12.sp,
                                            fontWeight = if (isAmountActive) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isAmountActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isAmountActive) {
                                            Icon(
                                                imageVector = if (isAmountDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Active Category Filter Badge
                        if (uiState.selectedCategoryFilter != null) {
                            val activeCatName = resolveCategoryName(uiState.selectedCategoryFilter ?: "", uiState.categoriesMap)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
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
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = getCategoryIcon(activeCatName),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Category: $activeCatName",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.onCategoryFilterSelected(null) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear Category Filter",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
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
                            color = MaterialTheme.colorScheme.surface,
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
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Text(
                                        text = "No expenses recorded",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val activeCatName = uiState.selectedCategoryFilter?.let { catId ->
                                        uiState.categoriesMap[catId] ?: DEFAULT_EXPENSE_CATEGORIES.find { it.id == catId }?.name ?: catId
                                    }
                                    val emptyMessage = when {
                                        activeCatName != null && uiState.selectedAccountFilter != null ->
                                            "No $activeCatName records found for ${ExpenseAccount.getDisplayName(uiState.selectedAccountFilter!!)} in ${uiState.selectedMonthLabel}."
                                        activeCatName != null ->
                                            "No expense records found for category '$activeCatName' in ${uiState.selectedMonthLabel}."
                                        uiState.selectedAccountFilter != null ->
                                            "No records found for ${ExpenseAccount.getDisplayName(uiState.selectedAccountFilter!!)} in ${uiState.selectedMonthLabel}."
                                        else ->
                                            "No expense records found for ${uiState.selectedMonthLabel}."
                                    }
                                    Text(
                                        text = emptyMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (uiState.selectedCategoryFilter != null || uiState.selectedAccountFilter != null) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.onCategoryFilterSelected(null)
                                                viewModel.onAccountFilterSelected(null)
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Clear Filters", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Button(
                                        onClick = onAddExpense,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
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
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                uiState.filteredExpenses.forEachIndexed { index, expense ->
                                    ExpenseItemRow(
                                        expense = expense,
                                        categoryName = resolveCategoryName(expense.categoryId, uiState.categoriesMap),
                                        formattedDate = dateFormatter.format(expense.date),
                                        onClick = { selectedExpenseForDetail = expense },
                                        onDelete = { expenseToDelete = expense }
                                    )
                                    if (index < uiState.filteredExpenses.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = 1.dp
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
        color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)) else null
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = TextHeaderSubtle
                )
                if (percentage > 0.0) {
                    Text(
                        text = "(%.0f%%)".format(percentage),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            Text(
                text = "LKR %,.0f".format(amount),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ExpenseItemRow(
    expense: Expense,
    categoryName: String,
    formattedDate: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Category Icon Badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getCategoryIcon(categoryName),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        // Info Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Category Name
            Text(
                text = categoryName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!expense.note.isNullOrBlank()) {
                Text(
                    text = expense.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Metadata Row: Date • Account • Fixed
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    softWrap = false
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
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (expense.classification == ExpenseClassification.COMMITTED) {
                    val recurringText = expense.getRecurringProgressText()
                    if (recurringText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(0.5.dp, Color(0xFFBFDBFE))
                        ) {
                            Text(
                                text = recurringText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1D4ED8),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "Fixed",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF92400E),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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
                color = Color(0xFFDC2626)
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

/**
 * Bulk-Add Missed Expenses Bottom Sheet — Conforms to Member 2 Order 6 (feature/expense-backdate).
 * Allows batch recording past transactions using actual transaction dates, ensuring correct monthly totals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkAddExpensesSheet(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSubmit: (List<BulkExpenseItem>) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val initialDate = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1) // Defaults to yesterday
        }.time
    }
    var batchDate by remember { mutableStateOf(initialDate) }

    val defaultCatId = categories.firstOrNull()?.id ?: "cat_food"
    var items by remember {
        mutableStateOf(
            listOf(
                BulkExpenseItem(date = initialDate, categoryId = defaultCatId),
                BulkExpenseItem(date = initialDate, categoryId = defaultCatId)
            )
        )
    }

    val datePicker = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val newCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            batchDate = newCal.time
            items = items.map { it.copy(date = newCal.time) }
        },
        Calendar.getInstance().get(Calendar.YEAR),
        Calendar.getInstance().get(Calendar.MONTH),
        Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    )

    val totalAmount = remember(items) {
        items.mapNotNull { it.amountLKR }.sum()
    }
    val validCount = remember(items) {
        items.count { it.isValid }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Bulk-Add Missed Expenses",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                    Text(
                        text = "Catch up on past expenses. Monthly totals update automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSubtle
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = NavyPrimary)
                }
            }

            // Batch Date Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Batch Date: ${dateFormatter.format(batchDate)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        OutlinedButton(
                            onClick = { datePicker.show() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Change Date", fontSize = 12.sp)
                        }
                    }

                    // Quick date presets
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Today" to 0,
                            "Yesterday" to 1,
                            "2 Days Ago" to 2,
                            "Last Sunday" to -1
                        ).forEach { (label, daysAgo) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val newDate = if (daysAgo >= 0) {
                                        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.time
                                    } else {
                                        Calendar.getInstance().apply {
                                            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                                            if (time.after(Date())) add(Calendar.WEEK_OF_YEAR, -1)
                                        }.time
                                    }
                                    batchDate = newDate
                                    items = items.map { it.copy(date = newDate) }
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Items List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.tempId }) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Entry #${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyPrimary
                                )
                                if (items.size > 1) {
                                    IconButton(
                                        onClick = { items = items.filterIndexed { i, _ -> i != index } },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove entry",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Amount & Note
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = item.amountText,
                                    onValueChange = { newAmt ->
                                        if (newAmt.isEmpty() || newAmt.matches(Regex("^\\d*\\.?\\d{0,2}\$"))) {
                                            items = items.toMutableList().also {
                                                it[index] = item.copy(amountText = newAmt)
                                            }
                                        }
                                    },
                                    label = { Text("Amount") },
                                    prefix = { Text("LKR ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )

                                OutlinedTextField(
                                    value = item.note,
                                    onValueChange = { newNote ->
                                        items = items.toMutableList().also {
                                            it[index] = item.copy(note = newNote)
                                        }
                                    },
                                    label = { Text("Note (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            // Category selection chips
                            Text(
                                text = "Category:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSubtle
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categories.take(6).forEach { cat ->
                                    val isSelected = item.categoryId == cat.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            items = items.toMutableList().also {
                                                it[index] = item.copy(categoryId = cat.id)
                                            }
                                        },
                                        label = { Text(cat.name, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NavyPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            // Account selection chips
                            Text(
                                text = "Paid via:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSubtle
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(ExpenseAccount.CARD, ExpenseAccount.CASH, ExpenseAccount.SECONDARY).forEach { acc ->
                                    val isSelected = item.account == acc
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            items = items.toMutableList().also {
                                                it[index] = item.copy(account = acc)
                                            }
                                        },
                                        label = { Text(ExpenseAccount.getDisplayName(acc), fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF1E3A8A),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            items = items + BulkExpenseItem(date = batchDate, categoryId = defaultCatId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Another Missed Expense")
                    }
                }
            }

            // Summary & Submit Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Batch Total",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1E3A8A)
                        )
                        Text(
                            text = "LKR %,.2f".format(totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A)
                        )
                    }
                    Text(
                        text = "$validCount valid ${if (validCount == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1E3A8A)
                    )
                }
            }

            Button(
                onClick = { onSubmit(items) },
                enabled = validCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (validCount > 0) "Save $validCount Missed Expenses" else "Enter Amount to Save",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
