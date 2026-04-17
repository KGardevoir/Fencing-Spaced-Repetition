package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OpponentViewModel(
    private val repository: OpponentRepository
) : ViewModel() {

    val opponents: StateFlow<List<Opponent>> = repository.getAllOpponents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun addOpponent(name: String, skillMultiplier: Double, notes: String, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insertOpponent(
                Opponent(name = name, skillMultiplier = skillMultiplier, notes = notes)
            )
            if (id < 0) {
                _error.value = "An opponent named \"${name.trim()}\" already exists."
            } else {
                _error.value = null
                onDone(id)
            }
        }
    }

    fun updateOpponent(opponent: Opponent, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateOpponent(opponent)
            onDone()
        }
    }

    fun deleteOpponent(opponent: Opponent, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteOpponent(opponent)
            onDone()
        }
    }

    fun clearError() {
        _error.value = null
    }
}
