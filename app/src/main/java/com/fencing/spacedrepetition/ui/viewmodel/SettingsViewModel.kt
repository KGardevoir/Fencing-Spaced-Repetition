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
}
