package com.nexus.financetracker.ui.expense.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.financetracker.domain.model.ExpenseClassification
import com.nexus.financetracker.ui.expense.ExpenseViewModel

private val NavyPrimary = Color(0xFF072044)
private val LightCardBg = Color(0xFFF1F5F9)
private val TextSubtle = Color(0xFF64748B)
private val CardBorderColor = Color(0xFFE2E8F0)

/**
 * Add / New Category Screen matching Screen 2 in the wireframe:
 * - Category Name input
 * - Default type toggle (Discretionary vs Committed)
 * - Dynamic field: Estimation value (if Discretionary) OR Recurring frequency (if Committed)
 * - Save button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryAddScreen(
    viewModel: ExpenseViewModel,
    categoryId: String? = null,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val existingCategory = remember(categoryId, uiState.allCategories) {
        categoryId?.let { id -> uiState.allCategories.find { it.id == id } }
    }

    var categoryName by remember { mutableStateOf(existingCategory?.name ?: "") }
    var selectedType by remember {
        mutableStateOf(
            if (existingCategory?.classification?.equals(ExpenseClassification.COMMITTED, ignoreCase = true) == true)
                ExpenseClassification.COMMITTED
            else
                ExpenseClassification.DISCRETIONARY
        )
    }
    var estimationValueText by remember {
        val est = existingCategory?.estimatedAmount
        val text = if (est != null && est > 0.0) {
            if (est % 1.0 == 0.0) est.toLong().toString() else est.toString()
        } else ""
        mutableStateOf(text)
    }
    var selectedFrequency by remember {
        mutableStateOf(existingCategory?.recurringFrequency?.lowercase() ?: "monthly")
    }

    LaunchedEffect(existingCategory) {
        existingCategory?.let { cat ->
            categoryName = cat.name
            selectedType = if (cat.classification.equals(ExpenseClassification.COMMITTED, ignoreCase = true)) {
                ExpenseClassification.COMMITTED
            } else {
                ExpenseClassification.DISCRETIONARY
            }
            val est = cat.estimatedAmount
            estimationValueText = if (est != null && est > 0.0) {
                if (est % 1.0 == 0.0) est.toLong().toString() else est.toString()
            } else ""
            selectedFrequency = cat.recurringFrequency?.lowercase() ?: "monthly"
        }
    }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (categoryId != null) "Edit category" else "New category",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NavyPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NavyPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Name Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Name",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        OutlinedTextField(
                            value = categoryName,
                            onValueChange = {
                                categoryName = it
                                errorMessage = null
                            },
                            placeholder = { Text("e.g. Coffee", color = TextSubtle) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = LightCardBg,
                                unfocusedContainerColor = LightCardBg,
                                focusedBorderColor = NavyPrimary,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = NavyPrimary
                            )
                        )
                    }

                    // 2. Default Type Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Default type",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isDiscretionary = selectedType.equals(ExpenseClassification.DISCRETIONARY, ignoreCase = true)
                            val isCommitted = selectedType.equals(ExpenseClassification.COMMITTED, ignoreCase = true)

                            Surface(
                                onClick = {
                                    selectedType = ExpenseClassification.DISCRETIONARY
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDiscretionary) NavyPrimary else LightCardBg,
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Discretionary",
                                        color = if (isDiscretionary) Color.White else NavyPrimary,
                                        fontWeight = if (isDiscretionary) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    selectedType = ExpenseClassification.COMMITTED
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCommitted) NavyPrimary else LightCardBg,
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Committed",
                                        color = if (isCommitted) Color.White else NavyPrimary,
                                        fontWeight = if (isCommitted) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // 3. Conditional Input Fields
                    val isCommittedMode = selectedType.equals(ExpenseClassification.COMMITTED, ignoreCase = true)
                    if (!isCommittedMode) {
                        // Estimation Value Field
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Estimation value (LKR)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            OutlinedTextField(
                                value = estimationValueText,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() || it == '.' }
                                    estimationValueText = filtered
                                    errorMessage = null
                                },
                                placeholder = { Text("0.00", color = TextSubtle) },
                                prefix = { Text("LKR ", fontWeight = FontWeight.Bold, color = NavyPrimary) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = LightCardBg,
                                    unfocusedContainerColor = LightCardBg,
                                    focusedBorderColor = NavyPrimary,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = NavyPrimary
                                ),
                                supportingText = {
                                    Text(
                                        text = "Monthly estimated spending for this category",
                                        color = TextSubtle,
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }
                    } else {
                        // Committed: Recurring Frequency Selector (Weekly, Monthly, Yearly)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Recurring frequency",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("weekly" to "Weekly", "monthly" to "Monthly", "yearly" to "Yearly").forEach { (freqKey, freqLabel) ->
                                    val isSelected = selectedFrequency.equals(freqKey, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedFrequency = freqKey },
                                        label = {
                                            Text(
                                                text = freqLabel,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NavyPrimary,
                                            selectedLabelColor = Color.White,
                                            containerColor = LightCardBg,
                                            labelColor = NavyPrimary
                                        ),
                                        border = if (isSelected) null else BorderStroke(1.dp, CardBorderColor)
                                    )
                                }
                            }
                            Text(
                                text = "Fixed recurring commitment interval",
                                color = TextSubtle,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 4. Save Button
                    Button(
                        onClick = {
                            if (categoryName.trim().isBlank()) {
                                errorMessage = "Please enter a category name"
                                return@Button
                            }
                            isSubmitting = true
                            errorMessage = null
                            val est = estimationValueText.toDoubleOrNull()
                            val isComm = selectedType.equals(ExpenseClassification.COMMITTED, ignoreCase = true)
                            val finalClassification = if (isComm) ExpenseClassification.COMMITTED else ExpenseClassification.DISCRETIONARY
                            val finalFreq = if (isComm) selectedFrequency.lowercase() else null
                            val finalEst = if (!isComm) est else null

                            if (categoryId != null) {
                                viewModel.updateCategory(
                                    category = com.nexus.financetracker.domain.model.Category(
                                        id = categoryId,
                                        userId = existingCategory?.userId ?: "",
                                        name = categoryName.trim(),
                                        classification = finalClassification,
                                        estimatedAmount = finalEst,
                                        recurringFrequency = finalFreq,
                                        createdAt = existingCategory?.createdAt ?: java.util.Date()
                                    ),
                                    onSuccess = {
                                        isSubmitting = false
                                        onNavigateBack()
                                    },
                                    onError = { error ->
                                        isSubmitting = false
                                        errorMessage = error
                                    }
                                )
                            } else {
                                viewModel.createCategoryWithDetails(
                                    name = categoryName.trim(),
                                    classification = finalClassification,
                                    estimatedAmount = finalEst,
                                    recurringFrequency = finalFreq,
                                    onSuccess = {
                                        isSubmitting = false
                                        onNavigateBack()
                                    },
                                    onError = { error ->
                                        isSubmitting = false
                                        errorMessage = error
                                    }
                                )
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavyPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (categoryId != null) "Save changes" else "Save",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
