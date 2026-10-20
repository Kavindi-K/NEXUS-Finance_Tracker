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
            // Source Selection (FlowRow so chip titles never break awkwardly)
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

            // Currency & Exchange Rate
            Text(
                text = "Currency & Exchange Rate",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            val currencies = listOf("LKR", "USD", "EUR", "GBP", "AUD")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                currencies.forEach { cur ->
                    FilterChip(
                        selected = formState.currency == cur,
                        onClick = { viewModel.onCurrencyChanged(cur) },
                        label = { Text(cur) }
                    )
                }
            }

            if (formState.currency != "LKR") {
                OutlinedTextField(
                    value = formState.exchangeRate,
                    onValueChange = viewModel::onExchangeRateChanged,
                    label = { Text("Exchange Rate to LKR") },
                    leadingIcon = { Icon(Icons.Default.CurrencyExchange, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("1 ${formState.currency} = ${formState.exchangeRate} LKR") }
                )
            }

            // Original Amount
            OutlinedTextField(
                value = formState.amountOriginal,
                onValueChange = viewModel::onAmountOriginalChanged,
                label = { Text("Original Amount (${formState.currency})") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    if (formState.source == IncomeSource.CRYPTO) {
                        Text("For crypto, enter positive for profit or negative for realized loss")
                    } else {
                        Text("Enter the exact amount received or expected")
                    }
                }
            )

            // Computed LKR Total Banner
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
                                text = "LKR Amount Equivalent",
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

            // Optional Note
            OutlinedTextField(
                value = formState.note,
                onValueChange = viewModel::onNoteChanged,
                label = { Text("Note (Optional)") },
                placeholder = { Text("e.g. Monthly salary, Web design project") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
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

            // Spacer for scroll clearance above pinned bottom bar
            Spacer(Modifier.height(16.dp))
        }
    }
}
