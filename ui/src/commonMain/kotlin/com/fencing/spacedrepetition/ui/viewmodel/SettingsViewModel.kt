// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.preferences.AppPreferences
import com.fencing.spacedrepetition.data.preferences.SettingsConstants
import com.fencing.spacedrepetition.data.preferences.ThemeMode
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Global's learning settings: how a session is drawn and progress scheduled
 * in Global, and what every learning group uses where it sets nothing itself.
 */
data class LearningDefaults(
    val cardsPerSession: Int = SettingsConstants.DEFAULT_CARDS_PER_SESSION,
    val randomizeDueCards: Boolean = false,
    val randomizeBucketHours: Int = SettingsConstants.DEFAULT_RANDOMIZE_BUCKET_HOURS,
    val practiceDays: Set<Int> = SettingsConstants.DEFAULT_PRACTICE_DAYS,
    val maximumInterval: Int = SettingsConstants.DEFAULT_MAXIMUM_INTERVAL,
    val fsrsRetention: Int = SettingsConstants.DEFAULT_FSRS_RETENTION,
    val fsrsEnableFuzzing: Boolean = SettingsConstants.DEFAULT_FSRS_ENABLE_FUZZING,
    /** Whether Global schedules by concept. */
    val conceptScheduling: Boolean = SettingsConstants.DEFAULT_GLOBAL_CONCEPT_SCHEDULING
)

/** The app's own settings: the theme, auto-show, and backups. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoShowAnswer: Boolean = false,
    val autoBackupEnabled: Boolean = SettingsConstants.DEFAULT_AUTO_BACKUP_ENABLED,
    val autoBackupIntervalDays: Int = SettingsConstants.DEFAULT_AUTO_BACKUP_INTERVAL_DAYS,
    val lastBackupTime: Long = 0L,
    val maxBackupsKept: Int = SettingsConstants.DEFAULT_MAX_BACKUPS_KEPT,
    val backupReminderEnabled: Boolean = SettingsConstants.DEFAULT_BACKUP_REMINDER_ENABLED,
    val backupReminderIntervalDays: Int = SettingsConstants.DEFAULT_BACKUP_REMINDER_INTERVAL_DAYS,
    val backupReminderDismissedTime: Long = 0L
)

class SettingsViewModel(
    private val themePreferences: AppPreferences,
    private val backups: BackupScheduling = NoBackups
) : ViewModel() {

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val learningDefaults: StateFlow<LearningDefaults> = with(themePreferences) {
        combine(
            combine(cardsPerSession, randomizeDueCards, randomizeBucketHours, practiceDays) { cards, shuffle, bucket, days ->
                LearningDefaults(cards, shuffle, bucket, days)
            },
            combine(maximumInterval, fsrsRetention, fsrsEnableFuzzing, globalConceptScheduling) { interval, retention, fuzz, concepts ->
                LearningDefaults(maximumInterval = interval, fsrsRetention = retention, fsrsEnableFuzzing = fuzz, conceptScheduling = concepts)
            }
        ) { session, scheduling ->
            scheduling.copy(
                cardsPerSession = session.cardsPerSession,
                randomizeDueCards = session.randomizeDueCards,
                randomizeBucketHours = session.randomizeBucketHours,
                practiceDays = session.practiceDays
            )
        }
    }.state(LearningDefaults())

    val settings: StateFlow<AppSettings> = with(themePreferences) {
        combine(
            combine(themeMode, autoShowAnswer, autoBackupEnabled, autoBackupIntervalDays, lastBackupTime) { theme, show, backup, interval, last ->
                AppSettings(theme, show, backup, interval, last)
            },
            combine(maxBackupsKept, backupReminderEnabled, backupReminderIntervalDays, backupReminderDismissedTime) { kept, remind, every, dismissed ->
                AppSettings(maxBackupsKept = kept, backupReminderEnabled = remind, backupReminderIntervalDays = every, backupReminderDismissedTime = dismissed)
            }
        ) { app, reminder ->
            app.copy(
                maxBackupsKept = reminder.maxBackupsKept,
                backupReminderEnabled = reminder.backupReminderEnabled,
                backupReminderIntervalDays = reminder.backupReminderIntervalDays,
                backupReminderDismissedTime = reminder.backupReminderDismissedTime
            )
        }
    }.state(AppSettings())

    /** Separately, for the theme around everything. */
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode.state(ThemeMode.SYSTEM)

    val selectedGroupId: StateFlow<Long?> = themePreferences.selectedGroupId.state(null)

    val autoBackupUri: StateFlow<String?> = themePreferences.autoBackupUri.state(null)

    /**
     * Whether this platform backs up on its own, which decides what the
     * settings screen offers: the automatic backup, or the reminder that
     * stands in for it. Not a flow -- it is a fact about the build, and it
     * cannot change while the app is running.
     */
    val automaticBackups: Boolean get() = backups.runsWhenClosed

    private fun save(write: suspend AppPreferences.() -> Unit) {
        viewModelScope.launch { themePreferences.write() }
    }

    fun setThemeMode(mode: ThemeMode) = save { setThemeMode(mode) }
    fun setAutoShowAnswer(enabled: Boolean) = save { setAutoShowAnswer(enabled) }
    fun setCardsPerSession(count: Int) = save { setCardsPerSession(count) }
    fun setSelectedGroupId(groupId: Long?) = save { setSelectedGroupId(groupId) }
    fun setRandomizeDueCards(enabled: Boolean) = save { setRandomizeDueCards(enabled) }
    fun setMaximumInterval(days: Int) = save { setMaximumInterval(days) }
    fun setPracticeDays(days: Set<Int>) = save { setPracticeDays(days) }
    fun setRandomizeBucketHours(hours: Int) = save { setRandomizeBucketHours(hours) }
    fun setFsrsRetention(percent: Int) = save { setFsrsRetention(percent) }
    fun setGlobalConceptScheduling(enabled: Boolean) = save { setGlobalConceptScheduling(enabled) }
    fun setFsrsEnableFuzzing(enabled: Boolean) = save { setFsrsEnableFuzzing(enabled) }
    fun setBackupReminderEnabled(enabled: Boolean) = save { setBackupReminderEnabled(enabled) }
    fun setBackupReminderIntervalDays(days: Int) = save { setBackupReminderIntervalDays(days) }
    fun setMaxBackupsKept(count: Int) = save { setMaxBackupsKept(count) }

    /**
     * Puts the reminder away until the interval comes round again.
     *
     * Stored rather than held in the composition: a dismissal that a reload
     * undoes is not a dismissal, and a browser tab is reloaded often.
     */
    fun dismissBackupReminder() = save { setBackupReminderDismissedTime(Time.now()) }

    /** Changing when backups run reschedules them. */
    private fun saveAndReschedule(write: suspend AppPreferences.() -> Unit) = save {
        write()
        backups.reschedule(
            enabled = autoBackupEnabled.first(),
            uri = autoBackupUri.first(),
            intervalDays = autoBackupIntervalDays.first()
        )
    }

    fun setAutoBackupEnabled(enabled: Boolean) = saveAndReschedule { setAutoBackupEnabled(enabled) }
    fun setAutoBackupUri(uri: String?) = saveAndReschedule { setAutoBackupUri(uri) }
    fun setAutoBackupIntervalDays(days: Int) = saveAndReschedule { setAutoBackupIntervalDays(days) }

    fun runBackupNow() {
        viewModelScope.launch { backups.runNow() }
    }
}
