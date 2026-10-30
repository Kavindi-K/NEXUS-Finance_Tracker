package com.nexus.financetracker.ui.income

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    incomeId: String? = null,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // Load income for edit if ID is provided, otherwise reset to blank/default draft
    LaunchedEffect(incomeId) {
        if (!incomeId.isNullOrBlank()) {
            viewModel.loadIncomeForEdit(incomeId)
        } else {
            viewModel.resetForm()
        }
    }

    // Auto-fetch live market exchange rate whenever AdSense (USD) is chosen
    val isAdSense = formState.source == IncomeSource.ADSENSE
    LaunchedEffect(isAdSense) {
        if (isAdSense && !formState.isEditMode) {
            viewModel.fetchLiveExchangeRate("USD", formState.dateReceived)
        }
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

    val isSalary = formState.source == IncomeSource.SALARY
    val isFreelance = formState.source == IncomeSource.FREELANCE
    val isCrypto = formState.source == IncomeSource.CRYPTO
    val isOther = formState.source == IncomeSource.OTHER

    val screenTitle = if (formState.isEditMode) "Edit Income" else "Record Income"
    val submitButtonText = if (formState.isEditMode) "Update Income" else "Save Income"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, style = MaterialTheme.typography.titleLarge) },
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
                        enabled = !formState.isSubmitting && !formState.isLoading
                    ) {
                        if (formState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(submitButtonText, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (formState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

            // Offline Mode Indicator Banner
            if (uiState.isOffline) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline Mode",
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Offline Mode — Changes saved locally and will sync once reconnected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

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
                    val chipLabel = if (source == IncomeSource.OTHER) "+ Other" else IncomeSource.getDisplayName(source)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onSourceChanged(source) },
                        label = { Text(chipLabel) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
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
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Confirmed (Received)",
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = formState.status == IncomeStatus.PENDING,
                    onClick = { viewModel.onStatusChanged(IncomeStatus.PENDING) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.HourglassEmpty,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Pending (Expected)",
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Custom Source Title / Category (Shown when '+ Other' is selected)
            if (isOther) {
                OutlinedTextField(
                    value = formState.customSourceTitle,
                    onValueChange = viewModel::onCustomSourceTitleChanged,
                    label = { Text("Income Title / Source") },
                    placeholder = { Text("e.g. Gift, Dividends, Consulting, Rental") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Specify what this income is for") }
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
                            trailingIcon = {
                                if (formState.isFetchingRate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    IconButton(onClick = { viewModel.fetchLiveExchangeRate(formState.currency, formState.dateReceived) }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh rate for selected date",
                                            tint = if (formState.isRateLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                if (formState.isFetchingRate) {
                                    Text("Fetching rate for ${dateFormatter.format(formState.dateReceived)}...", color = MaterialTheme.colorScheme.primary)
                                } else if (formState.isRateLive) {
                                    Text("Rate (${dateFormatter.format(formState.dateReceived)}): 1 USD = ${formState.exchangeRate} LKR (tap refresh to update, or edit manually)", color = Color(0xFF15803D))
                                } else {
                                    Text("1 USD = ${formState.exchangeRate} LKR (editable)")
                                }
                            }
                        )
                    }
                }
            }

            // Crypto Specific Guidance & Profit/Loss Toggle
            if (isCrypto) {
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
                                Icons.Default.CurrencyBitcoin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Realized Crypto P&L Only",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Record net realized profit or loss from closed trades only. Do not record gross trading turnover (buy/sell order volume) as earnings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !formState.isCryptoLoss,
                                onClick = { viewModel.onCryptoLossToggled(false) },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = { Text("Profit (+)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE8F5E9),
                                    selectedLabelColor = Color(0xFF2E7D32),
                                    selectedLeadingIconColor = Color(0xFF2E7D32)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            FilterChip(
                                selected = formState.isCryptoLoss,
                                onClick = { viewModel.onCryptoLossToggled(true) },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = { Text("Realized Loss (-)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Amount Input Field
            val amountLabel = if (isAdSense) "Original Amount (USD)" else "Amount (LKR)"
            val amountSupportingText: String = when {
                isSalary -> "Prefilled from your last salary deposit (tap to edit)"
                isFreelance -> "Milestone amount in LKR"
                isCrypto -> if (formState.isCryptoLoss) "Recording a realized loss (-LKR)" else "Recording a realized gain (+LKR)"
                isAdSense -> "Original USD payout amount"
                isOther -> "Amount in LKR"
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
                placeholder = { Text(if (isCrypto && formState.isCryptoLoss) "-0.00" else "0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    formState.computedLkrAmount?.let { lkr ->
                        Text("Formatted: LKR %,.2f".format(lkr))
                    } ?: Text(amountSupportingText)
                }
            )

            // Computed LKR Total Banner (Shown with xxx.00 formatting for all streams)
            formState.computedLkrAmount?.let { lkr ->
                val isNegative = lkr < 0
                val containerColor = if (isNegative) {
                    MaterialTheme.colorScheme.errorContainer
                } else if (isCrypto) {
                    Color(0xFFE8F5E9)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }

                val contentColor = if (isNegative) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else if (isCrypto) {
                    Color(0xFF2E7D32)
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }

                val titleText = when {
                    isCrypto && isNegative -> "Realized Crypto Loss (Deducted from Total)"
                    isCrypto -> "Realized Crypto Profit"
                    isAdSense -> "Actual LKR Received"
                    else -> "Recorded Amount"
                }

                val formattedAmountText = if (isNegative) {
                    "- LKR %,.2f".format(kotlin.math.abs(lkr))
                } else if (isCrypto) {
                    "+ LKR %,.2f".format(lkr)
                } else {
                    "LKR %,.2f".format(lkr)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = containerColor)
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
                                text = titleText,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor
                            )
                            Text(
                                text = formattedAmountText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                        Icon(
                            imageVector = if (isNegative) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = contentColor,
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
            val notePlaceholder = when {
                isFreelance -> "e.g. Acme Corp - Phase 2 milestone"
                isSalary -> "e.g. Monthly salary deposit"
                isOther -> "e.g. Additional details"
                else -> "e.g. Transaction details"
            }

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
}
