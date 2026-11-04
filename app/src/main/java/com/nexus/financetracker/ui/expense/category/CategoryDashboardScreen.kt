package com.nexus.financetracker.ui.expense.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Savings
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.financetracker.domain.model.Category
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.ui.expense.ExpenseViewModel

private val DarkNavyBg = Color(0xFF072044)
private val DashboardGraphCardBg = Color(0xFFE2E8F0)
private val CardBorderColor = Color(0xFFCBD5E1)
private val TextMutedSlate = Color(0xFF64748B)

enum class CategoryClassificationFilter {
    ALL,
    DISCRETIONARY,
    COMMITTED
}

/**
 * Category Dashboard Screen:
 * - Dynamic theme support (light and dark mode adaptive)
 * - Single-column long cards (1 category per row)
 * - Real-time search bar & "+ New category" button at bottom
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDashboardScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNewCategory: () -> Unit,
    onNavigateToEditCategory: (String) -> Unit = {},
    onCategorySelected: (String) -> Unit
) {
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val categories = uiState.allCategories

    val context = LocalContext.current
    val currentMonthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val currentMonthLabel = remember { currentMonthFormat.format(Date()) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedClassification by remember { mutableStateOf(CategoryClassificationFilter.ALL) }

    val discretionaryCount = remember(categories) {
        categories.count { !it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true) }
    }
    val committedCount = remember(categories) {
        categories.count { it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true) }
    }
    var categoryForReview by remember { mutableStateOf<Category?>(null) }
    val reviewedCategories by viewModel.reviewedCategories.collectAsState()

    var categoryForNewEstimation by remember { mutableStateOf<Category?>(null) }
    var newEstimationInput by remember { mutableStateOf("") }
    val dismissedBannerCategories = remember { mutableStateListOf<String>() }

    val pendingCategory = remember(categories, reviewedCategories, dismissedBannerCategories) {
        categories.firstOrNull { cat ->
            val isComm = cat.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true)
            val reviewKey = "${cat.id}_$currentMonthLabel"
            !isComm && (reviewKey in reviewedCategories) && (cat.estimatedAmount == null || cat.estimatedAmount == 0.0) && (cat.id !in dismissedBannerCategories)
        }
    }
    val activeBannerCat = categoryForNewEstimation ?: pendingCategory
    val isEndOfMonth = remember {
        val date = java.time.LocalDate.now()
        // End of the month review: last 5 days of the month (e.g. 27th onwards)
        date.dayOfMonth >= (date.lengthOfMonth() - 4)
    }

    val currentMonthExpenses = remember(uiState.expenses) {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)
        uiState.expenses.filter { exp ->
            val cal = Calendar.getInstance().apply { time = exp.date }
            cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
        }
    }
    val categorySpentMap = remember(currentMonthExpenses) {
        val map = mutableMapOf<String, Double>()
        currentMonthExpenses.forEach { exp ->
            map[exp.categoryId] = (map[exp.categoryId] ?: 0.0) + exp.amountLKR
            val lower = exp.categoryId.lowercase()
            if (lower != exp.categoryId) {
                map[lower] = (map[lower] ?: 0.0) + exp.amountLKR
            }
        }
        map
    }

    val filteredCategories = remember(categories, searchQuery, selectedClassification) {
        val classified = when (selectedClassification) {
            CategoryClassificationFilter.ALL -> categories
            CategoryClassificationFilter.DISCRETIONARY -> categories.filter {
                !it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true)
            }
            CategoryClassificationFilter.COMMITTED -> categories.filter {
                it.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true)
            }
        }
        if (searchQuery.isBlank()) {
            classified
        } else {
            val query = searchQuery.trim().lowercase()
            classified.filter { it.name.lowercase().contains(query) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Expenses Categories",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = onNavigateToNewCategory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        text = "Search categories...",
                        color = TextMutedSlate,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = DarkNavyBg
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = DarkNavyBg
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF1F5F9),
                    unfocusedContainerColor = Color(0xFFF1F5F9),
                    focusedBorderColor = DarkNavyBg,
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                    focusedTextColor = DarkNavyBg,
                    unfocusedTextColor = DarkNavyBg,
                    cursorColor = DarkNavyBg
                )
            )

            // Classification Filter Chips (All, Discretionary, Committed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryClassificationFilter.values().forEach { filter ->
                    val isSelected = selectedClassification == filter
                    val count = when (filter) {
                        CategoryClassificationFilter.ALL -> categories.size
                        CategoryClassificationFilter.DISCRETIONARY -> discretionaryCount
                        CategoryClassificationFilter.COMMITTED -> committedCount
                    }
                    val label = when (filter) {
                        CategoryClassificationFilter.ALL -> "All ($count)"
                        CategoryClassificationFilter.DISCRETIONARY -> "Discretionary ($count)"
                        CategoryClassificationFilter.COMMITTED -> "Committed ($count)"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedClassification = filter },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            // New Month Estimation Banner (Opens when review is done; disappears after adding estimation)
            AnimatedVisibility(
                visible = activeBannerCat != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                activeBannerCat?.let { bannerCat ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                        border = BorderStroke(1.5.dp, DarkNavyBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                        imageVector = Icons.Default.Savings,
                                        contentDescription = null,
                                        tint = DarkNavyBg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "New Month Estimation",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkNavyBg
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        dismissedBannerCategories.add(bannerCat.id)
                                        categoryForNewEstimation = null
                                        newEstimationInput = ""
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = TextMutedSlate,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Review completed! Current estimate & spent for '${bannerCat.name}' are reset to 0. Enter new estimation budget for the upcoming month:",
                                fontSize = 12.sp,
                                color = TextMutedSlate
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newEstimationInput,
                                    onValueChange = { input ->
                                        newEstimationInput = input.filter { it.isDigit() }
                                    },
                                    placeholder = { Text("e.g. 25000", fontSize = 13.sp, color = TextMutedSlate) },
                                    prefix = { Text("LKR ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkNavyBg) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = DarkNavyBg,
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedTextColor = DarkNavyBg,
                                        unfocusedTextColor = DarkNavyBg,
                                        cursorColor = DarkNavyBg
                                    )
                                )

                                Button(
                                    onClick = {
                                        val amount = newEstimationInput.toDoubleOrNull()
                                        if (amount != null && amount > 0) {
                                            viewModel.updateCategoryEstimation(
                                                categoryId = bannerCat.id,
                                                newEstimation = amount,
                                                onSuccess = {
                                                    Toast.makeText(
                                                        context,
                                                        "New estimation of LKR %,.0f set for ${bannerCat.name}!".format(amount),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    dismissedBannerCategories.add(bannerCat.id)
                                                    categoryForNewEstimation = null
                                                    newEstimationInput = ""
                                                },
                                                onError = { err ->
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        } else {
                                            Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkNavyBg),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }


            if (filteredCategories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "No categories found"
                            } else when (selectedClassification) {
                                CategoryClassificationFilter.DISCRETIONARY -> "No discretionary categories found"
                                CategoryClassificationFilter.COMMITTED -> "No committed categories found"
                                CategoryClassificationFilter.ALL -> "No categories found"
                            },
                            color = DarkNavyBg,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "Try searching with a different term"
                            } else {
                                "Tap '+ New category' to add one"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCategories, key = { it.id }) { category ->
                        val spent = categorySpentMap[category.id]
                            ?: categorySpentMap[category.id.lowercase()]
                            ?: categorySpentMap[category.name]
                            ?: categorySpentMap[category.name.lowercase()]
                            ?: 0.0
                        val est = category.estimatedAmount ?: 0.0
                        val reviewKey = "${category.id}_$currentMonthLabel"
                        val isReviewed = reviewKey in reviewedCategories
                        val hasSurplus = est > 0.0 && spent < est
                        val showReview = isEndOfMonth && hasSurplus && !isReviewed
                        val surplus = (est - spent).coerceAtLeast(0.0)

                        // When review is completed, spent resets to 0.0 for the new month cycle
                        val displaySpent = if (isReviewed) 0.0 else spent
                        val displayEst = if (isReviewed && (category.estimatedAmount == null || category.estimatedAmount == 0.0)) 0.0 else (category.estimatedAmount ?: 0.0)

                        CategoryRowCard(
                            category = category.copy(estimatedAmount = displayEst),
                            spentAmount = displaySpent,
                            showReviewButton = showReview,
                            surplusAmount = surplus,
                            onReviewSurplus = { categoryForReview = category },
                            onEdit = { onNavigateToEditCategory(category.id) },
                            onDelete = { categoryToDelete = category },
                            onClick = { onCategorySelected(category.id) }
                        )
                    }
                }
            }
        }
    }

    // End-of-Month Review Dialog
    categoryForReview?.let { cat ->
        val est = cat.estimatedAmount ?: 0.0
        val spent = categorySpentMap[cat.id] ?: 0.0
        val diff = kotlin.math.abs(est - spent)
        val isSurplus = spent <= est

        AlertDialog(
            onDismissRequest = { categoryForReview = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = null,
                    tint = DarkNavyBg,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "End-of-Month Review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavyBg
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Category: ${cat.name}",
                        fontWeight = FontWeight.Bold,
                        color = DarkNavyBg
                    )
                    Text(
                        text = "Estimated Budget: LKR %,.0f".format(est),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedSlate
                    )
                    Text(
                        text = "Actual Expenses (${currentMonthLabel}): LKR %,.0f".format(spent),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedSlate
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSurplus) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isSurplus) {
                                "Unspent Surplus: LKR %,.0f".format(diff)
                            } else {
                                "Budget Variance: LKR %,.0f".format(diff)
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (isSurplus) Color(0xFF15803D) else Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        text = "Would you like to add this amount to your Goal Contribution list or add it back to general savings?",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkNavyBg
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.completeCategoryReview(
                                categoryId = cat.id,
                                categoryName = cat.name,
                                surplusAmount = diff,
                                monthLabel = currentMonthLabel,
                                addToContribution = true,
                                onSuccess = { resetCat ->
                                    categoryForNewEstimation = resetCat
                                    newEstimationInput = ""
                                }
                            )
                            Toast.makeText(
                                context,
                                "Added LKR %,.0f from ${cat.name} to Contribution List!".format(diff),
                                Toast.LENGTH_LONG
                            ).show()
                            categoryForReview = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkNavyBg),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add to Contribution List", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.completeCategoryReview(
                                categoryId = cat.id,
                                categoryName = cat.name,
                                surplusAmount = diff,
                                monthLabel = currentMonthLabel,
                                addToContribution = false,
                                onSuccess = { resetCat ->
                                    categoryForNewEstimation = resetCat
                                    newEstimationInput = ""
                                }
                            )
                            Toast.makeText(
                                context,
                                "Added LKR %,.0f back to general savings.".format(diff),
                                Toast.LENGTH_SHORT
                            ).show()
                            categoryForReview = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorderColor)
                    ) {
                        Text("Add Back to Savings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkNavyBg)
                    }

                    TextButton(
                        onClick = {
                            categoryForReview = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel (Decide Later)", fontSize = 13.sp, color = TextMutedSlate)
                    }
                }
            },
            dismissButton = null
        )
    }

    // Delete Category Confirmation Dialog
    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
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
                    text = "Delete Category?",
                    fontWeight = FontWeight.Bold,
                    color = DarkNavyBg
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${cat.name}'? Existing recorded expenses under this category will remain, but the category will be removed from future selection.",
                    color = DarkNavyBg,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(
                            categoryId = cat.id,
                            onSuccess = {
                                Toast.makeText(context, "'${cat.name}' deleted", Toast.LENGTH_SHORT).show()
                                categoryToDelete = null
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                categoryToDelete = null
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Compact horizontal category card (one category per line)
 * Background: Dashboard graph card color (Color(0xFFE2E8F0))
 * Text color: Navy Blue (DarkNavyBg)
 * Height: Compact height matching wireframe screenshot
 */
@Composable
private fun CategoryRowCard(
    category: Category,
    spentAmount: Double = 0.0,
    showReviewButton: Boolean = false,
    surplusAmount: Double = 0.0,
    onReviewSurplus: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClick: () -> Unit
) {
    val isCommitted = category.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = DashboardGraphCardBg,
        border = BorderStroke(1.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main category details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Line 1: Category Name
                    Text(
                        text = category.name,
                        color = DarkNavyBg,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Line 2: Estimation & Spent details (or recurring frequency for committed)
                    if (isCommitted) {
                        val frequency = category.recurringFrequency?.replaceFirstChar { it.uppercase() } ?: "Monthly"
                        val durText = category.recurringDurationText
                        val displayInfo = if (!durText.isNullOrBlank()) {
                            "$frequency • $durText"
                        } else {
                            "$frequency • Ongoing"
                        }
                        Text(
                            text = displayInfo,
                            color = TextMutedSlate,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        val est = category.estimatedAmount
                        if (est != null && est > 0.0) {
                            Text(
                                text = "Est: LKR %,.0f • Spent: LKR %,.0f".format(est, spentAmount),
                                color = TextMutedSlate,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Est: LKR 0 • Spent: LKR %,.0f".format(spentAmount),
                                color = TextMutedSlate,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Category",
                            tint = DarkNavyBg,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Category",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Review button at the bottom of the category card grid
            if (showReviewButton) {
                Surface(
                    onClick = onReviewSurplus,
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFF15803D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Savings,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "End-of-Month Review: LKR %,.0f Unspent".format(surplusAmount),
                                color = Color(0xFF15803D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Review ›",
                            color = Color(0xFF2563EB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
