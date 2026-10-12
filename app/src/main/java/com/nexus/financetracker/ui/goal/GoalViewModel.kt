package com.nexus.financetracker.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nexus.financetracker.data.repository.GoalRepository
import com.nexus.financetracker.domain.model.Goal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Goals screens.
 * TODO: Add addGoal(), updateGoal(), deleteGoal() actions.
 */
@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    init {
        loadGoals()
    }

    private fun loadGoals() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            goalRepository.getGoals(userId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
                }
                .collect { goals ->
                    _uiState.value = _uiState.value.copy(goals = goals, isLoading = false)
                }
        }
    }
}

data class GoalUiState(
    val isLoading: Boolean = true,
    val goals: List<Goal> = emptyList(),
    val errorMessage: String? = null
)
