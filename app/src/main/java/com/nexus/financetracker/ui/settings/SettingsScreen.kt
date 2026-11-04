package com.nexus.financetracker.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nexus.financetracker.data.local.ThemeMode
import com.nexus.financetracker.domain.model.ExpenseAccount
import com.nexus.financetracker.domain.model.UserProfile

private val NavyPrimary = Color(0xFF072044)

/**
 * Settings Screen displaying app preferences, theme customization, security controls,
 * offline cache management, and coordinated account logout.
 * Conforms to Member 2 Order 9 (feature/app-settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userEmail: String? = null,
    displayName: String? = null,
    onLogout: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showGeminiDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    // Sign Out Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    "Sign Out",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text("Are you sure you want to sign out? Your offline cache will be securely cleared on this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut { onLogout() }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Clear Cache Confirmation Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = {
                Text(
                    "Clear Local Read Cache",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "This will remove ${uiState.cachedExpensesCount} locally cached offline records across all modules (expenses, income, goals, categories) from Room. " +
                            "Your remote records remain safe on Firestore and will reload upon network sync."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearLocalCache { }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Base Currency Selector Dialog
    if (showCurrencyDialog) {
        val currencies = listOf("LKR", "USD", "EUR", "GBP")
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Base Currency", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencies.forEach { currency ->
                        val isSelected = uiState.baseCurrency == currency
                        Surface(
                            onClick = {
                                viewModel.setBaseCurrency(currency)
                                showCurrencyDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (currency) {
                                        "LKR" -> "LKR — Sri Lankan Rupee (Rs)"
                                        "USD" -> "USD — US Dollar ($)"
                                        "EUR" -> "EUR — Euro (€)"
                                        "GBP" -> "GBP — British Pound (£)"
                                        else -> currency
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Default Expense Account Selector Dialog
    if (showAccountDialog) {
        val accounts = listOf(ExpenseAccount.CARD, ExpenseAccount.CASH, ExpenseAccount.SECONDARY)
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text("Default Expense Account", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        val isSelected = uiState.defaultExpenseAccount == account
                        Surface(
                            onClick = {
                                viewModel.setDefaultExpenseAccount(account)
                                showAccountDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ExpenseAccount.getDisplayName(account),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 4-Digit Security PIN Configuration Dialog
    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Text(
                    text = if (uiState.isPinSet) "Manage 4-Digit PIN" else "Set 4-Digit PIN",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uiState.isPinSet) {
                        Text(
                            text = "A 4-digit PIN is currently active. You can enter a new 4-digit PIN to replace it, or remove it.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Create a 4-digit security PIN to unlock NEXUS when opening the app.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("4-Digit PIN") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { Text("Confirm 4-Digit PIN") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pinError != null) {
                        Text(
                            text = pinError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length != 4) {
                            pinError = "PIN must be exactly 4 digits"
                        } else if (pinInput != confirmPinInput) {
                            pinError = "PINs do not match"
                        } else {
                            viewModel.setPin(pinInput)
                            showPinDialog = false
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                Row {
                    if (uiState.isPinSet) {
                        TextButton(
                            onClick = {
                                viewModel.removePin()
                                showPinDialog = false
                            }
                        ) {
                            Text("Remove PIN", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    var apiKeyInput by remember(uiState.geminiApiKey) { mutableStateOf(uiState.geminiApiKey) }
    if (showGeminiDialog) {
        AlertDialog(
            onDismissRequest = { showGeminiDialog = false },
            title = {
                Text(
                    "NEXUS AI (Gemini Cloud)",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "NEXUS AI is powered by Google Gemini. A pre-configured cloud key is active by default. You can also paste your own Google AI Studio API key below.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key (Optional)") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Leave empty to use the built-in default cloud intelligence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setGeminiApiKey(apiKeyInput.trim())
                        showGeminiDialog = false
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                Row {
                    if (uiState.geminiApiKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                apiKeyInput = ""
                                viewModel.setGeminiApiKey("")
                                showGeminiDialog = false
                            }
                        ) {
                            Text("Reset Default", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showGeminiDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Profile"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Summary Header
            val finalDisplayName = uiState.displayName ?: displayName
            val finalEmail = uiState.userEmail ?: userEmail

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = finalDisplayName?.let { UserProfile.formatCapitalizedName(it) }?.ifBlank { null }
                                ?: "NEXUS User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = finalEmail ?: "Connected via Firebase Auth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1. Appearance / Theme Customization (Coordinated with Member 3)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Appearance & Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose your preferred color theme across all screens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionChip(
                            label = "System",
                            isSelected = uiState.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "Light",
                            isSelected = uiState.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionChip(
                            label = "Dark",
                            isSelected = uiState.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. App Preferences (Notifications, Security, Finance)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Push Notifications
                    SettingToggleRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Push Notifications",
                        subtitle = "Daily bill reminders, savings goal alerts, and quick-log notifications",
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Biometric Lock
                    SettingToggleRow(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Biometric Lock",
                        subtitle = "Require biometric authentication to open app",
                        checked = uiState.biometricLockEnabled,
                        onCheckedChange = { viewModel.setBiometricLockEnabled(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 4-Digit Security PIN
                    SettingClickableRow(
                        icon = Icons.Outlined.Lock,
                        title = "4-Digit Security PIN",
                        subtitle = if (uiState.isPinSet) "Configured (used as fallback or primary lock)" else "Not set (optional)",
                        badgeValue = if (uiState.isPinSet) "Active" else "Set PIN",
                        onClick = { showPinDialog = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Base Currency
                    SettingClickableRow(
                        icon = Icons.Outlined.Payments,
                        title = "Base Currency",
                        subtitle = "Primary currency used in charts and totals",
                        badgeValue = uiState.baseCurrency,
                        onClick = { showCurrencyDialog = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Default Account
                    SettingClickableRow(
                        icon = Icons.Outlined.CreditCard,
                        title = "Default Expense Account",
                        subtitle = "Pre-selected payment account for rapid logging",
                        badgeValue = ExpenseAccount.getDisplayName(uiState.defaultExpenseAccount),
                        onClick = { showAccountDialog = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // NEXUS AI (Gemini Cloud)
                    SettingClickableRow(
                        icon = Icons.Outlined.Psychology,
                        title = "NEXUS AI Copilot",
                        subtitle = if (uiState.geminiApiKey.isNotBlank()) "Google Gemini (Custom Key Active)" else "Google Gemini Cloud Active",
                        badgeValue = if (uiState.geminiApiKey.isNotBlank()) "Custom Key" else "Active",
                        onClick = { showGeminiDialog = true }
                    )
                }
            }

            // 3. Offline Data & Cache Management (Member 2 Specialty)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Data & Offline Storage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Room database serves as your offline read cache for instantaneous display.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Cached Records",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${uiState.cachedExpensesCount} records cached across all modules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = { showClearCacheDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !uiState.isClearingCache && uiState.cachedExpensesCount > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Clear Cache", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 4. App Info Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "NEXUS Finance Tracker",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version 1.0.0 (Production Build)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Designed for seamless offline-first personal financial management.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Logout Action (Coordinated with Member 1)
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ThemeOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCBD5E1)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeValue: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Text(
                text = badgeValue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
