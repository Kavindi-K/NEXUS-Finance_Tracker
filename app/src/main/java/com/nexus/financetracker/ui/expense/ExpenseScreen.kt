package com.nexus.financetracker.ui.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.nexus.financetracker.domain.model.Expense
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.ExpenseClassification
import java.text.SimpleDateFormat
import java.util.Locale

// App Theme Colors (Matching Deep Sapphire Navy Design System)
private val NavyPrimary = Color(0xFF072044)
private val NavyCardBg = Color(0xFF132D52)
private val NavyCardBorder = Color(0x33FFFFFF)
private val LightCardBg = Color(0xFFF1F5F9)
private val TextHeaderSubtle = Color(0xFFBAC7D5)
private val TextSubtle = Color(0xFF64748B)
private val ExpenseRed = Color(0xFFDC2626)
private val CardBorderColor = Color(0xFFE2E8F0)

/**
 * Expenses Screen — Shows real-time recorded expenses and provides quick entry action (FR9-FR14).
 * Styled to perfectly match the NEXUS Deep Sapphire Navy theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    onAddExpense: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    // Delete Confirmation Dialog
    expenseToDelete?.let { expense ->
        val catName = uiState.categoriesMap[expense.categoryId] ?: expense.categoryId
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete Expense", fontWeight = FontWeight.Bold, color = NavyPrimary) },
            text = { Text("Are you sure you want to delete this expense of LKR %,.2f for $catName?".format(expense.amountLKR), color = Color(0xFF334155)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteExpense(expense.id)
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ExpenseRed)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { expenseToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = NavyPrimary)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExpense,
                containerColor = NavyPrimary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        },
        containerColor = Color.White
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NavyPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
            ) {
                // 1. Overview Summary Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = NavyCardBg
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, NavyCardBorder)
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
                                        tint = TextHeaderSubtle
                                    )
                                    Text(
                                        text = "Total Expenses",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextHeaderSubtle
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${uiState.expenses.size} record${if (uiState.expenses.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "LKR %,.2f".format(uiState.totalExpenseLKR),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            HorizontalDivider(color = Color(0x33FFFFFF))

                            // Accounts Breakdown Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                AccountSummaryPill(title = "Card", amount = uiState.cardExpenseLKR)
                                AccountSummaryPill(title = "Cash", amount = uiState.cashExpenseLKR)
                                AccountSummaryPill(title = "Secondary", amount = uiState.secondaryExpenseLKR)
                            }
                        }
                    }
                }

                // 2. Section Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recorded Transactions",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                    }
                }

                // 3. Empty State or Expenses List
                if (uiState.expenses.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = LightCardBg),
                            border = BorderStroke(1.dp, CardBorderColor)
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
                                        tint = TextSubtle.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "No expenses recorded yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyPrimary
                                    )
                                    Text(
                                        text = "Tap the button below or '+' to add your first expense.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSubtle
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Button(
                                        onClick = onAddExpense,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NavyPrimary,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Record an Expense", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.expenses, key = { it.id }) { expense ->
                        ExpenseItemCard(
                            expense = expense,
                            categoryName = uiState.categoriesMap[expense.categoryId]
                                ?: DEFAULT_EXPENSE_CATEGORIES.find { it.id == expense.categoryId }?.name
                                ?: expense.categoryId,
                            formattedDate = dateFormatter.format(expense.date),
                            onDelete = { expenseToDelete = expense }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSummaryPill(title: String, amount: Double) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextHeaderSubtle
        )
        Text(
            text = "LKR %,.0f".format(amount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun ExpenseItemCard(
    expense: Expense,
    categoryName: String,
    formattedDate: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, CardBorderColor),
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
                    .background(NavyPrimary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(categoryName),
                    contentDescription = null,
                    tint = NavyPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryName,
                    fontSize = 15.sp,
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
                        color = TextSubtle
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSubtle
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = LightCardBg
                    ) {
                        Text(
                            text = ExpenseAccount.getDisplayName(expense.account),
                            style = MaterialTheme.typography.labelSmall,
                            color = NavyPrimary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (expense.classification == ExpenseClassification.COMMITTED) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NavyPrimary.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = "Fixed",
                                style = MaterialTheme.typography.labelSmall,
                                color = NavyPrimary,
                                fontWeight = FontWeight.SemiBold,
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseRed
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = TextSubtle,
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
