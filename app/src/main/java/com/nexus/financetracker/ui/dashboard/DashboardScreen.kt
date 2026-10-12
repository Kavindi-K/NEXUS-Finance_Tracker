package com.nexus.financetracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Dashboard screen — placeholder scaffold.
 * TODO: Implement balance card, recent transactions list, and FAB for adding transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToGoals: () -> Unit,
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Dashboard") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Balance card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Balance", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "LKR %.2f".format(uiState.balance),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Income: LKR %.2f".format(uiState.totalIncome))
                        Text("Expenses: LKR %.2f".format(uiState.totalExpense))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddIncome, modifier = Modifier.weight(1f)) { Text("+ Income") }
                OutlinedButton(onClick = onAddExpense, modifier = Modifier.weight(1f)) { Text("+ Expense") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onNavigateToGoals) { Text("View Goals →") }

            Spacer(Modifier.height(16.dp))
            Text("Recent Transactions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.transactions.isEmpty()) {
                Text("No transactions yet. Add your first one!", style = MaterialTheme.typography.bodyMedium)
            } else {
                // TODO: Replace with LazyColumn
                uiState.transactions.take(5).forEach { tx ->
                    Text("${tx.title}: LKR ${tx.amount} (${tx.type})")
                }
            }
        }
    }
}
