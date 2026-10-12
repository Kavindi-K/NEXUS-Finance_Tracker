package com.nexus.financetracker.ui.goal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Goal list screen — placeholder scaffold.
 * TODO: Replace with LazyColumn, progress bars per goal, swipe-to-delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListScreen(
    onAddGoal: () -> Unit,
    onBack: () -> Unit,
    viewModel: GoalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddGoal) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.goals.isEmpty()) {
                Text("No goals yet. Tap + to add one!", style = MaterialTheme.typography.bodyMedium)
            } else {
                // TODO: Replace with LazyColumn
                uiState.goals.forEach { goal ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(goal.title, style = MaterialTheme.typography.titleMedium)
                            Text("Target: LKR ${goal.targetAmount} | Saved: LKR ${goal.savedAmount}")
                        }
                    }
                }
            }
        }
    }
}
