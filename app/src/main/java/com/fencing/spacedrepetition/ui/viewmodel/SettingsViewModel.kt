package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.preferences.ThemeMode
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themePreferences: ThemePreferences
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val autoShowAnswer: StateFlow<Boolean> = themePreferences.autoShowAnswer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cardsPerSession: StateFlow<Int> = themePreferences.cardsPerSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_CARDS_PER_SESSION)

    val selectedGroupId: StateFlow<Long?> = themePreferences.selectedGroupId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val randomizeDueCards: StateFlow<Boolean> = themePreferences.randomizeDueCards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val maximumInterval: StateFlow<Int> = themePreferences.maximumInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_MAXIMUM_INTERVAL)

    val practicesPerWeek: StateFlow<Int> = themePreferences.practicesPerWeek
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_PRACTICES_PER_WEEK)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun setAutoShowAnswer(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setAutoShowAnswer(enabled)
        }
    }

    fun setCardsPerSession(count: Int) {
        viewModelScope.launch {
            themePreferences.setCardsPerSession(count)
        }
    }

    fun setSelectedGroupId(groupId: Long?) {
        viewModelScope.launch {
            themePreferences.setSelectedGroupId(groupId)
        }
    }

    fun setRandomizeDueCards(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setRandomizeDueCards(enabled)
        }
    }

    fun setMaximumInterval(days: Int) {
        viewModelScope.launch {
            themePreferences.setMaximumInterval(days)
        }
    }

    fun setPracticesPerWeek(count: Int) {
        viewModelScope.launch {
            themePreferences.setPracticesPerWeek(count)
        }
    }
}
