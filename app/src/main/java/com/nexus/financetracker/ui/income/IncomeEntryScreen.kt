package com.nexus.financetracker.ui.income

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nexus.financetracker.domain.model.IncomeSource
import com.nexus.financetracker.domain.model.IncomeStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncomeEntryScreen(
    viewModel: IncomeViewModel,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // Reset form each time the entry screen is opened so previous unsaved drafts are not retained
    LaunchedEffect(Unit) {
        viewModel.resetForm()
    }

    val calendar = remember { Calendar.getInstance() }

    fun showDatePicker(initialDate: Date, onDateSelected: (Date) -> Unit) {
        calendar.time = initialDate
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(year, month, dayOfMonth, 12, 0, 0)
                onDateSelected(selectedCal.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val isAdSense = formState.source == IncomeSource.ADSENSE
    val isSalary = formState.source == IncomeSource.SALARY
    val isFreelance = formState.source == IncomeSource.FREELANCE
    val isCrypto = formState.source == IncomeSource.CRYPTO

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Income", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = {
                            viewModel.submitIncome(onSuccess = onNavigateBack)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !formState.isSubmitting
                    ) {
                        if (formState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save Income", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Source Selection (FlowRow)
            Text(
                text = "Income Source",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IncomeSource.ALL.forEach { source ->
                    val isSelected = formState.source == source
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onSourceChanged(source) },
                        label = { Text(IncomeSource.getDisplayName(source)) }
                    )
                }
            }

            // Status Selection: Confirmed vs Pending
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = formState.status == IncomeStatus.CONFIRMED,
                    onClick = { viewModel.onStatusChanged(IncomeStatus.CONFIRMED) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("Confirmed (Received)") },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = formState.status == IncomeStatus.PENDING,
                    onClick = { viewModel.onStatusChanged(IncomeStatus.PENDING) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("Pending (Expected)") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Currency & Exchange Rate (Shown ONLY for AdSense as specified in Stream requirements)
            if (isAdSense) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CurrencyExchange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "AdSense USD Transfer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "AdSense is paid in USD and converted to LKR at transfer. The original USD amount and rate are stored so history never shifts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = formState.exchangeRate,
                            onValueChange = viewModel::onExchangeRateChanged,
                            label = { Text("Exchange Rate to LKR") },
                            leadingIcon = { Icon(Icons.Default.CurrencyExchange, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("1 USD = ${formState.exchangeRate} LKR") }
                        )
                    }
                }
            }

            // Amount Input Field
            val amountLabel = if (isAdSense) "Original Amount (USD)" else "Amount (LKR)"
            val amountSupportingText: String = when {
                isSalary -> "Prefilled from your last salary deposit (tap to edit)"
                isFreelance -> "Milestone amount in LKR"
                isCrypto -> "Enter positive for profit, negative for realized loss"
                isAdSense -> "Original USD payout amount"
                else -> "Enter amount"
            }

            var isAmountFocused by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = formState.amountOriginal,
                onValueChange = viewModel::onAmountOriginalChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (isAmountFocused && !focusState.isFocused) {
                            formState.amountOriginal.toDoubleOrNull()?.let { num ->
                                viewModel.onAmountOriginalChanged(String.format(Locale.US, "%.2f", num))
                            }
                        }
                        isAmountFocused = focusState.isFocused
                    },
                label = { Text(amountLabel) },
                prefix = { Text(if (isAdSense) "$ " else "LKR ") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    formState.computedLkrAmount?.let { lkr ->
                        Text("Formatted: LKR %,.2f".format(lkr))
                    } ?: Text(amountSupportingText)
                }
            )

            // Computed LKR Total Banner (Shown with xxx.00 formatting for all streams)
            formState.computedLkrAmount?.let { lkr ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isAdSense) "Actual LKR Received" else "Recorded Amount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "LKR %,.2f".format(lkr),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Date Selection
            val dateLabel = if (formState.status == IncomeStatus.CONFIRMED) "Date Received" else "Expected Date"
            val currentDate = if (formState.status == IncomeStatus.CONFIRMED) formState.dateReceived else formState.expectedDate

            OutlinedTextField(
                value = dateFormatter.format(currentDate),
                onValueChange = {},
                readOnly = true,
                label = { Text(dateLabel) },
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showDatePicker(currentDate) { selected ->
                            if (formState.status == IncomeStatus.CONFIRMED) {
                                viewModel.onDateReceivedChanged(selected)
                            } else {
                                viewModel.onExpectedDateChanged(selected)
                            }
                        }
                    },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                    disabledBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Note Field (Customized label for Freelance client/project reference)
            val noteLabel = if (isFreelance) "Client or Project Reference (Optional)" else "Note (Optional)"
            val notePlaceholder = if (isFreelance) "e.g. Acme Corp - Phase 2 milestone" else "e.g. Monthly salary deposit"

            OutlinedTextField(
                value = formState.note,
                onValueChange = viewModel::onNoteChanged,
                label = { Text(noteLabel) },
                placeholder = { Text(notePlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                supportingText = {
                    if (isFreelance) {
                        Text("Carries client or invoice reference so you never lose track")
                    }
                }
            )

            // Validation Error Banner
            formState.error?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
