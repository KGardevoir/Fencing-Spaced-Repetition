// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.preferences.AppPreferences
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
import com.fencing.spacedrepetition.data.preferences.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themePreferences: AppPreferences,
    private val backups: BackupScheduling = NoBackups
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val autoShowAnswer: StateFlow<Boolean> = themePreferences.autoShowAnswer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cardsPerSession: StateFlow<Int> = themePreferences.cardsPerSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_CARDS_PER_SESSION)

    val selectedGroupId: StateFlow<Long?> = themePreferences.selectedGroupId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val randomizeDueCards: StateFlow<Boolean> = themePreferences.randomizeDueCards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val maximumInterval: StateFlow<Int> = themePreferences.maximumInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_MAXIMUM_INTERVAL)

    val practicesPerWeek: StateFlow<Int> = themePreferences.practicesPerWeek
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_PRACTICES_PER_WEEK)

    val practiceDays: StateFlow<Set<Int>> = themePreferences.practiceDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_PRACTICE_DAYS)

    val randomizeBucketHours: StateFlow<Int> = themePreferences.randomizeBucketHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_RANDOMIZE_BUCKET_HOURS)

    val fsrsRetention: StateFlow<Int> = themePreferences.fsrsRetention
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_FSRS_RETENTION)

    val sm2IntervalModifier: StateFlow<Int> = themePreferences.sm2IntervalModifier
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_SM2_INTERVAL_MODIFIER)

    val fsrsEnableFuzzing: StateFlow<Boolean> = themePreferences.fsrsEnableFuzzing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_FSRS_ENABLE_FUZZING)

    val autoBackupEnabled: StateFlow<Boolean> = themePreferences.autoBackupEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_AUTO_BACKUP_ENABLED)

    val autoBackupUri: StateFlow<String?> = themePreferences.autoBackupUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val autoBackupIntervalDays: StateFlow<Int> = themePreferences.autoBackupIntervalDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_AUTO_BACKUP_INTERVAL_DAYS)

    val lastBackupTime: StateFlow<Long> = themePreferences.lastBackupTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val maxBackupsKept: StateFlow<Int> = themePreferences.maxBackupsKept
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsConstants.DEFAULT_MAX_BACKUPS_KEPT)

    val backupReminderEnabled: StateFlow<Boolean> = themePreferences.backupReminderEnabled
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SettingsConstants.DEFAULT_BACKUP_REMINDER_ENABLED
        )

    val backupReminderIntervalDays: StateFlow<Int> = themePreferences.backupReminderIntervalDays
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SettingsConstants.DEFAULT_BACKUP_REMINDER_INTERVAL_DAYS
        )

    /**
     * Whether this platform backs up on its own, which decides what the
     * settings screen offers: the automatic backup, or the reminder that
     * stands in for it. Not a flow -- it is a fact about the build, and it
     * cannot change while the app is running.
     */
    val automaticBackups: Boolean get() = backups.runsWhenClosed

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

    fun setPracticeDays(days: Set<Int>) {
        viewModelScope.launch {
            themePreferences.setPracticeDays(days)
        }
    }

    fun togglePracticeDay(day: Int) {
        viewModelScope.launch {
            val current = themePreferences.practiceDays.first()
            val updated = if (current.contains(day)) {
                // Don't allow deselecting the last day
                if (current.size > 1) current - day else current
            } else {
                current + day
            }
            themePreferences.setPracticeDays(updated)
        }
    }

    fun setRandomizeBucketHours(hours: Int) {
        viewModelScope.launch {
            themePreferences.setRandomizeBucketHours(hours)
        }
    }

    fun setFsrsRetention(percent: Int) {
        viewModelScope.launch {
            themePreferences.setFsrsRetention(percent)
        }
    }

    fun setSm2IntervalModifier(percent: Int) {
        viewModelScope.launch {
            themePreferences.setSm2IntervalModifier(percent)
        }
    }

    fun setFsrsEnableFuzzing(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setFsrsEnableFuzzing(enabled)
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setAutoBackupEnabled(enabled)
            backups.reschedule(
                enabled = enabled,
                uri = themePreferences.autoBackupUri.first(),
                intervalDays = themePreferences.autoBackupIntervalDays.first()
            )
        }
    }

    fun setAutoBackupUri(uri: String?) {
        viewModelScope.launch {
            themePreferences.setAutoBackupUri(uri)
            backups.reschedule(
                enabled = themePreferences.autoBackupEnabled.first(),
                uri = uri,
                intervalDays = themePreferences.autoBackupIntervalDays.first()
            )
        }
    }

    fun setAutoBackupIntervalDays(days: Int) {
        viewModelScope.launch {
            themePreferences.setAutoBackupIntervalDays(days)
            backups.reschedule(
                enabled = themePreferences.autoBackupEnabled.first(),
                uri = themePreferences.autoBackupUri.first(),
                intervalDays = days
            )
        }
    }

    fun runBackupNow() {
        viewModelScope.launch { backups.runNow() }
    }

    fun setBackupReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setBackupReminderEnabled(enabled)
        }
    }

    fun setBackupReminderIntervalDays(days: Int) {
        viewModelScope.launch {
            themePreferences.setBackupReminderIntervalDays(days)
        }
    }

    fun setMaxBackupsKept(count: Int) {
        viewModelScope.launch {
            themePreferences.setMaxBackupsKept(count)
        }
    }
}
