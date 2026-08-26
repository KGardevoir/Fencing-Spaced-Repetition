// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemePreferences(private val context: Context) : AppPreferences {
    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val AUTO_SHOW_ANSWER_KEY = booleanPreferencesKey("auto_show_answer")
    private val CARDS_PER_SESSION_KEY = intPreferencesKey("cards_per_session")
    private val SELECTED_GROUP_ID_KEY = longPreferencesKey("selected_group_id")
    private val RANDOMIZE_DUE_CARDS_KEY = booleanPreferencesKey("randomize_due_cards")
    private val MAXIMUM_INTERVAL_KEY = intPreferencesKey("maximum_interval")
    private val PRACTICES_PER_WEEK_KEY = intPreferencesKey("practices_per_week")
    private val PRACTICE_DAYS_KEY = stringPreferencesKey("practice_days")
    private val RANDOMIZE_BUCKET_HOURS_KEY = intPreferencesKey("randomize_bucket_hours")
    private val FSRS_RETENTION_KEY = intPreferencesKey("fsrs_retention")
    private val SM2_INTERVAL_MODIFIER_KEY = intPreferencesKey("sm2_interval_modifier")
    private val FSRS_ENABLE_FUZZING_KEY = booleanPreferencesKey("fsrs_enable_fuzzing")
    private val AUTO_BACKUP_ENABLED_KEY = booleanPreferencesKey("auto_backup_enabled")
    private val AUTO_BACKUP_URI_KEY = stringPreferencesKey("auto_backup_uri")
    private val AUTO_BACKUP_INTERVAL_DAYS_KEY = intPreferencesKey("auto_backup_interval_days")
    private val LAST_BACKUP_TIME_KEY = longPreferencesKey("last_backup_time")
    private val MAX_BACKUPS_KEPT_KEY = intPreferencesKey("max_backups_kept")
    private val BACKUP_REMINDER_ENABLED_KEY = booleanPreferencesKey("backup_reminder_enabled")
    private val BACKUP_REMINDER_INTERVAL_DAYS_KEY =
        intPreferencesKey("backup_reminder_interval_days")

    companion object {
        // Every value below now lives in SettingsConstants, which the
        // browser build shares. Kept here as aliases so the Android call
        // sites that reach for them through this class still compile.
        const val DEFAULT_CARDS_PER_SESSION = SettingsConstants.DEFAULT_CARDS_PER_SESSION
        const val MIN_CARDS_PER_SESSION = SettingsConstants.MIN_CARDS_PER_SESSION
        const val MAX_CARDS_PER_SESSION = SettingsConstants.MAX_CARDS_PER_SESSION
        const val DEFAULT_MAXIMUM_INTERVAL = SettingsConstants.DEFAULT_MAXIMUM_INTERVAL
        const val MIN_MAXIMUM_INTERVAL = SettingsConstants.MIN_MAXIMUM_INTERVAL
        const val MAX_MAXIMUM_INTERVAL = SettingsConstants.MAX_MAXIMUM_INTERVAL
        const val DEFAULT_PRACTICES_PER_WEEK = SettingsConstants.DEFAULT_PRACTICES_PER_WEEK
        const val MIN_PRACTICES_PER_WEEK = SettingsConstants.MIN_PRACTICES_PER_WEEK
        const val MAX_PRACTICES_PER_WEEK = SettingsConstants.MAX_PRACTICES_PER_WEEK
        const val DEFAULT_RANDOMIZE_BUCKET_HOURS = SettingsConstants.DEFAULT_RANDOMIZE_BUCKET_HOURS
        val DEFAULT_PRACTICE_DAYS: Set<Int> = SettingsConstants.DEFAULT_PRACTICE_DAYS
        const val DEFAULT_FSRS_RETENTION = SettingsConstants.DEFAULT_FSRS_RETENTION
        const val MIN_FSRS_RETENTION = SettingsConstants.MIN_FSRS_RETENTION
        const val MAX_FSRS_RETENTION = SettingsConstants.MAX_FSRS_RETENTION
        const val DEFAULT_SM2_INTERVAL_MODIFIER = SettingsConstants.DEFAULT_SM2_INTERVAL_MODIFIER
        const val MIN_SM2_INTERVAL_MODIFIER = SettingsConstants.MIN_SM2_INTERVAL_MODIFIER
        const val MAX_SM2_INTERVAL_MODIFIER = SettingsConstants.MAX_SM2_INTERVAL_MODIFIER
        const val DEFAULT_FSRS_ENABLE_FUZZING = SettingsConstants.DEFAULT_FSRS_ENABLE_FUZZING
        const val DEFAULT_AUTO_BACKUP_ENABLED = SettingsConstants.DEFAULT_AUTO_BACKUP_ENABLED
        const val DEFAULT_AUTO_BACKUP_INTERVAL_DAYS = SettingsConstants.DEFAULT_AUTO_BACKUP_INTERVAL_DAYS
        const val DEFAULT_MAX_BACKUPS_KEPT = SettingsConstants.DEFAULT_MAX_BACKUPS_KEPT
        const val MIN_MAX_BACKUPS_KEPT = SettingsConstants.MIN_MAX_BACKUPS_KEPT
        const val MAX_MAX_BACKUPS_KEPT = SettingsConstants.MAX_MAX_BACKUPS_KEPT
    }

    override val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[THEME_KEY] ?: ThemeMode.SYSTEM.name
            try {
                ThemeMode.valueOf(themeName)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
        }

    override val autoShowAnswer: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_SHOW_ANSWER_KEY] ?: false
        }

    override val cardsPerSession: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[CARDS_PER_SESSION_KEY] ?: DEFAULT_CARDS_PER_SESSION
        }

    override val selectedGroupId: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_GROUP_ID_KEY]
        }

    override val randomizeDueCards: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[RANDOMIZE_DUE_CARDS_KEY] ?: false
        }

    override val maximumInterval: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[MAXIMUM_INTERVAL_KEY] ?: DEFAULT_MAXIMUM_INTERVAL
        }

    override val practicesPerWeek: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PRACTICES_PER_WEEK_KEY] ?: DEFAULT_PRACTICES_PER_WEEK
        }

    override val practiceDays: Flow<Set<Int>> = context.dataStore.data
        .map { preferences ->
            val stored = preferences[PRACTICE_DAYS_KEY]
            if (stored != null) {
                stored.split(",")
                    .filter { it.isNotBlank() }
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .toSet()
            } else {
                DEFAULT_PRACTICE_DAYS
            }
        }

    override val randomizeBucketHours: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[RANDOMIZE_BUCKET_HOURS_KEY] ?: DEFAULT_RANDOMIZE_BUCKET_HOURS
        }

    override val fsrsRetention: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[FSRS_RETENTION_KEY] ?: DEFAULT_FSRS_RETENTION
        }

    override val sm2IntervalModifier: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SM2_INTERVAL_MODIFIER_KEY] ?: DEFAULT_SM2_INTERVAL_MODIFIER
        }

    override val fsrsEnableFuzzing: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[FSRS_ENABLE_FUZZING_KEY] ?: DEFAULT_FSRS_ENABLE_FUZZING
        }

    override val autoBackupEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_BACKUP_ENABLED_KEY] ?: DEFAULT_AUTO_BACKUP_ENABLED
        }

    override val autoBackupUri: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_BACKUP_URI_KEY]
        }

    override val autoBackupIntervalDays: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_BACKUP_INTERVAL_DAYS_KEY] ?: DEFAULT_AUTO_BACKUP_INTERVAL_DAYS
        }

    override val lastBackupTime: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_BACKUP_TIME_KEY] ?: 0L
        }

    override val maxBackupsKept: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[MAX_BACKUPS_KEPT_KEY] ?: DEFAULT_MAX_BACKUPS_KEPT
        }

    // Stored here as well as in the browser, though the Android settings
    // screen does not offer them: the reminder is what a platform without
    // WorkManager shows instead of the automatic backup, and AppPreferences
    // is one interface for both.
    override val backupReminderEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[BACKUP_REMINDER_ENABLED_KEY]
                ?: SettingsConstants.DEFAULT_BACKUP_REMINDER_ENABLED
        }

    override val backupReminderIntervalDays: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[BACKUP_REMINDER_INTERVAL_DAYS_KEY]
                ?: SettingsConstants.DEFAULT_BACKUP_REMINDER_INTERVAL_DAYS
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = mode.name
        }
    }

    override suspend fun setAutoShowAnswer(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_SHOW_ANSWER_KEY] = enabled
        }
    }

    override suspend fun setCardsPerSession(count: Int) {
        val validCount = count.coerceIn(MIN_CARDS_PER_SESSION, MAX_CARDS_PER_SESSION)
        context.dataStore.edit { preferences ->
            preferences[CARDS_PER_SESSION_KEY] = validCount
        }
    }

    override suspend fun setSelectedGroupId(groupId: Long?) {
        context.dataStore.edit { preferences ->
            if (groupId != null) {
                preferences[SELECTED_GROUP_ID_KEY] = groupId
            } else {
                preferences.remove(SELECTED_GROUP_ID_KEY)
            }
        }
    }

    override suspend fun setRandomizeDueCards(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RANDOMIZE_DUE_CARDS_KEY] = enabled
        }
    }

    override suspend fun setMaximumInterval(days: Int) {
        val validDays = days.coerceIn(MIN_MAXIMUM_INTERVAL, MAX_MAXIMUM_INTERVAL)
        context.dataStore.edit { preferences ->
            preferences[MAXIMUM_INTERVAL_KEY] = validDays
        }
    }

    override suspend fun setPracticesPerWeek(count: Int) {
        val validCount = count.coerceIn(MIN_PRACTICES_PER_WEEK, MAX_PRACTICES_PER_WEEK)
        context.dataStore.edit { preferences ->
            preferences[PRACTICES_PER_WEEK_KEY] = validCount
        }
    }

    override suspend fun setPracticeDays(days: Set<Int>) {
        val value = days.sorted().joinToString(",")
        context.dataStore.edit { preferences ->
            preferences[PRACTICE_DAYS_KEY] = value
        }
    }

    override suspend fun setRandomizeBucketHours(hours: Int) {
        context.dataStore.edit { preferences ->
            preferences[RANDOMIZE_BUCKET_HOURS_KEY] = hours
        }
    }

    override suspend fun setFsrsRetention(percent: Int) {
        val valid = percent.coerceIn(MIN_FSRS_RETENTION, MAX_FSRS_RETENTION)
        context.dataStore.edit { preferences ->
            preferences[FSRS_RETENTION_KEY] = valid
        }
    }

    override suspend fun setSm2IntervalModifier(percent: Int) {
        val valid = percent.coerceIn(MIN_SM2_INTERVAL_MODIFIER, MAX_SM2_INTERVAL_MODIFIER)
        context.dataStore.edit { preferences ->
            preferences[SM2_INTERVAL_MODIFIER_KEY] = valid
        }
    }

    override suspend fun setFsrsEnableFuzzing(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FSRS_ENABLE_FUZZING_KEY] = enabled
        }
    }

    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_BACKUP_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setAutoBackupUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[AUTO_BACKUP_URI_KEY] = uri
            } else {
                preferences.remove(AUTO_BACKUP_URI_KEY)
            }
        }
    }

    override suspend fun setAutoBackupIntervalDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_BACKUP_INTERVAL_DAYS_KEY] = days
        }
    }

    override suspend fun setLastBackupTime(timeMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_BACKUP_TIME_KEY] = timeMillis
        }
    }

    override suspend fun setMaxBackupsKept(count: Int) {
        val validCount = count.coerceIn(MIN_MAX_BACKUPS_KEPT, MAX_MAX_BACKUPS_KEPT)
        context.dataStore.edit { preferences ->
            preferences[MAX_BACKUPS_KEPT_KEY] = validCount
        }
    }

    override suspend fun setBackupReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BACKUP_REMINDER_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setBackupReminderIntervalDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[BACKUP_REMINDER_INTERVAL_DAYS_KEY] = days
        }
    }
}
