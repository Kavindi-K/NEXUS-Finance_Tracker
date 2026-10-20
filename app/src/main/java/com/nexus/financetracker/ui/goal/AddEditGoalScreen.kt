package com.nexus.financetracker.ui.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

// Color Palette precisely matching user design
private val GoalNavyDark = Color(0xFF0D274D)         // Deep navy for title, labels, button
private val GoalBackground = Color(0xFFF1F5F9)       // Soft background canvas
private val GoalCardSurface = Color(0xFFFFFFFF)      // Pure white card
private val GoalFieldBackground = Color(0xFFE2E7ED)  // Light grey input field fill
private val GoalCalculationBox = Color(0xFFD4DDE8)   // Muted blue-grey calculation container
private val GoalCalculationSubtext = Color(0xFF5A6E85)

/**
 * Screen for creating or editing a savings goal, styled to match the team design mockup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGoalScreen(
    goalId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: GoalViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showTargetDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(goalId) {
        viewModel.initForm(goalId)
    }

    val displayDateFormatter = remember { SimpleDateFormat("dd / MM / yyyy", Locale.getDefault()) }

    // Dynamic Monthly Savings Calculation matching assignment formula:
    // Required monthly savings = max(0, target - alreadySaved) / monthsRemaining
    val monthlyRequiredText = remember(
        formState.targetAmount,
        formState.openingSavings,
        formState.startDate,
        formState.targetDate
    ) {
        val target = formState.targetAmount.trim().toDoubleOrNull() ?: 0.0
        val saved = formState.openingSavings.trim().toDoubleOrNull() ?: 0.0
        val remaining = maxOf(0.0, target - saved)

        val startCal = Calendar.getInstance().apply { timeInMillis = formState.startDate }
        val targetCal = Calendar.getInstance().apply { timeInMillis = formState.targetDate }

        var months = (targetCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)) * 12 +
                (targetCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH))
        if (months <= 0) months = 1

        val monthly = remaining / months
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(monthly.roundToLong())
        "≈ LKR $formatted / month"
    }

    Scaffold(
        containerColor = GoalBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onNavigateBack() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GoalNavyDark,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (formState.isEditing) "Edit Goal" else "New Goal",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = GoalNavyDark,
                            fontSize = 22.sp
                        )
                    )
                }

                if (formState.isEditing && !formState.isDeleting) {
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Goal",
                            tint = MaterialTheme.colorScheme.error
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Elevated White Card Container
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = GoalCardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // General error banner if present
                    formState.generalError?.let { err ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // 1. Goal Name
                    GoalFormField(
                        label = "Goal name",
                        value = formState.title,
                        onValueChange = viewModel::onTitleChanged,
                        placeholder = "MacBook Pro M4",
                        errorMessage = formState.titleError,
                        keyboardType = KeyboardType.Text
                    )

                    // 2. Target Amount (LKR)
                    GoalFormField(
                        label = "Target amount (LKR)",
                        value = formState.targetAmount,
                        onValueChange = viewModel::onTargetAmountChanged,
                        placeholder = "490000",
                        errorMessage = formState.targetAmountError,
                        keyboardType = KeyboardType.Decimal
                    )

                    // 3. Already Saved (LKR)
                    GoalFormField(
                        label = "Already saved (LKR)",
                        value = formState.openingSavings,
                        onValueChange = viewModel::onOpeningSavingsChanged,
                        placeholder = "11200",
                        errorMessage = formState.openingSavingsError,
                        keyboardType = KeyboardType.Decimal
                    )

                    // 4. Start Date
                    GoalClickableDateField(
                        label = "Start date",
                        displayDate = displayDateFormatter.format(Date(formState.startDate)),
                        onClick = { showStartDatePicker = true }
                    )

                    // 5. Target Date
                    GoalClickableDateField(
                        label = "Target date",
                        displayDate = displayDateFormatter.format(Date(formState.targetDate)),
                        errorMessage = formState.dateError,
                        onClick = { showTargetDatePicker = true }
                    )

                    // 6. Savings Calculation Info Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = GoalCalculationBox,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Column {
                            Text(
                                text = "You need to save",
                                fontSize = 12.sp,
                                color = GoalCalculationSubtext,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = monthlyRequiredText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoalNavyDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 7. Save Goal Button
                    Button(
                        onClick = {
                            viewModel.saveGoal(onSuccess = onNavigateBack)
                        },
                        enabled = !formState.isSaving && !formState.isDeleting,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoalNavyDark,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (formState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (formState.isEditing) "Update Goal" else "Save Goal",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Start Date Picker Dialog
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.startDate
        )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            viewModel.onStartDateSelected(selected)
                        }
                        showStartDatePicker = false
                    }
                ) {
                    Text("OK", color = GoalNavyDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel", color = GoalCalculationSubtext)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Target Date Picker Dialog
    if (showTargetDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.targetDate
        )
        DatePickerDialog(
            onDismissRequest = { showTargetDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            viewModel.onTargetDateSelected(selected)
                        }
                        showTargetDatePicker = false
                    }
                ) {
                    Text("OK", color = GoalNavyDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetDatePicker = false }) {
                    Text("Cancel", color = GoalCalculationSubtext)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Goal", fontWeight = FontWeight.Bold, color = GoalNavyDark) },
            text = { Text("Are you sure you want to delete this goal? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        formState.existingGoal?.let {
                            viewModel.deleteGoal(it.id, onSuccess = onNavigateBack)
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = GoalCalculationSubtext)
                }
            }
        )
    }
}

/**
 * Form field with centered bold label and rounded light grey input container matching the mockup.
 */
@Composable
private fun GoalFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = GoalNavyDark,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    color = Color.Gray.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 15.sp
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = GoalFieldBackground,
                unfocusedContainerColor = GoalFieldBackground,
                disabledContainerColor = GoalFieldBackground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent
            ),
            textStyle = TextStyle(
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = GoalNavyDark
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Clickable date field with centered bold label and rounded light grey container matching the mockup.
 */
@Composable
private fun GoalClickableDateField(
    label: String,
    displayDate: String,
    errorMessage: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = GoalNavyDark,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = GoalFieldBackground,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clickable { onClick() }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = displayDate,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GoalNavyDark,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
