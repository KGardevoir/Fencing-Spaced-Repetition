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

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class ThemePreferences(private val context: Context) {
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

    companion object {
        const val DEFAULT_CARDS_PER_SESSION = 3
        const val MIN_CARDS_PER_SESSION = 1
        const val MAX_CARDS_PER_SESSION = 20
        const val DEFAULT_MAXIMUM_INTERVAL = 365 // 1 years in days
        const val MIN_MAXIMUM_INTERVAL = 7 // 1 week
        const val MAX_MAXIMUM_INTERVAL = 3650 // 10 years
        const val DEFAULT_PRACTICES_PER_WEEK = 7 // daily
        const val MIN_PRACTICES_PER_WEEK = 1
        const val MAX_PRACTICES_PER_WEEK = 7
        const val DEFAULT_RANDOMIZE_BUCKET_HOURS = 24 // 1 day
        // All days of week selected by default (1=Monday through 7=Sunday, ISO-8601 convention)
        val DEFAULT_PRACTICE_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)

        // FSRS desired retention as integer percent (70–97), default 90 %
        const val DEFAULT_FSRS_RETENTION = 90
        const val MIN_FSRS_RETENTION = 70
        const val MAX_FSRS_RETENTION = 97

        // SM-2 interval modifier as integer percent (50–200), default 100 %
        const val DEFAULT_SM2_INTERVAL_MODIFIER = 100
        const val MIN_SM2_INTERVAL_MODIFIER = 50
        const val MAX_SM2_INTERVAL_MODIFIER = 200

        // FSRS interval fuzzing (adds ≤5 % random variance to prevent review pile-ups)
        const val DEFAULT_FSRS_ENABLE_FUZZING = false

        // Automatic database backup
        const val DEFAULT_AUTO_BACKUP_ENABLED = false
        const val DEFAULT_AUTO_BACKUP_INTERVAL_DAYS = 1

        // Number of backup files to retain before older ones are pruned
        const val DEFAULT_MAX_BACKUPS_KEPT = 7
        const val MIN_MAX_BACKUPS_KEPT = 1
        const val MAX_MAX_BACKUPS_KEPT = 30
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[THEME_KEY] ?: ThemeMode.SYSTEM.name
            try {
                ThemeMode.valueOf(themeName)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
        }

    val autoShowAnswer: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_SHOW_ANSWER_KEY] ?: false
        }

    val cardsPerSession: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[CARDS_PER_SESSION_KEY] ?: DEFAULT_CARDS_PER_SESSION
        }

    val selectedGroupId: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_GROUP_ID_KEY]
        }

    val randomizeDueCards: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[RANDOMIZE_DUE_CARDS_KEY] ?: false
        }

    val maximumInterval: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[MAXIMUM_INTERVAL_KEY] ?: DEFAULT_MAXIMUM_INTERVAL
        }

    val practicesPerWeek: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PRACTICES_PER_WEEK_KEY] ?: DEFAULT_PRACTICES_PER_WEEK
        }

    val practiceDays: Flow<Set<Int>> = context.dataStore.data
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

    val randomizeBucketHours: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[RANDOMIZE_BUCKET_HOURS_KEY] ?: DEFAULT_RANDOMIZE_BUCKET_HOURS
        }

    val fsrsRetention: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[FSRS_RETENTION_KEY] ?: DEFAULT_FSRS_RETENTION
        }

    val sm2IntervalModifier: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SM2_INTERVAL_MODIFIER_KEY] ?: DEFAULT_SM2_INTERVAL_MODIFIER
        }

    val fsrsEnableFuzzing: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[FSRS_ENABLE_FUZZING_KEY] ?: DEFAULT_FSRS_ENABLE_FUZZING
        }

    val autoBackupEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_BACKUP_ENABLED_KEY] ?: DEFAULT_AUTO_BACKUP_ENABLED
        }

    val autoBackupUri: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_BACKUP_URI_KEY]
        }

    val autoBackupIntervalDays: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_BACKUP_INTERVAL_DAYS_KEY] ?: DEFAULT_AUTO_BACKUP_INTERVAL_DAYS
        }

    val lastBackupTime: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[LAST_BACKUP_TIME_KEY] ?: 0L
        }

    val maxBackupsKept: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[MAX_BACKUPS_KEPT_KEY] ?: DEFAULT_MAX_BACKUPS_KEPT
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = mode.name
        }
    }

    suspend fun setAutoShowAnswer(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_SHOW_ANSWER_KEY] = enabled
        }
    }

    suspend fun setCardsPerSession(count: Int) {
        val validCount = count.coerceIn(MIN_CARDS_PER_SESSION, MAX_CARDS_PER_SESSION)
        context.dataStore.edit { preferences ->
            preferences[CARDS_PER_SESSION_KEY] = validCount
        }
    }

    suspend fun setSelectedGroupId(groupId: Long?) {
        context.dataStore.edit { preferences ->
            if (groupId != null) {
                preferences[SELECTED_GROUP_ID_KEY] = groupId
            } else {
                preferences.remove(SELECTED_GROUP_ID_KEY)
            }
        }
    }

    suspend fun setRandomizeDueCards(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RANDOMIZE_DUE_CARDS_KEY] = enabled
        }
    }

    suspend fun setMaximumInterval(days: Int) {
        val validDays = days.coerceIn(MIN_MAXIMUM_INTERVAL, MAX_MAXIMUM_INTERVAL)
        context.dataStore.edit { preferences ->
            preferences[MAXIMUM_INTERVAL_KEY] = validDays
        }
    }

    suspend fun setPracticesPerWeek(count: Int) {
        val validCount = count.coerceIn(MIN_PRACTICES_PER_WEEK, MAX_PRACTICES_PER_WEEK)
        context.dataStore.edit { preferences ->
            preferences[PRACTICES_PER_WEEK_KEY] = validCount
        }
    }

    suspend fun setPracticeDays(days: Set<Int>) {
        val value = days.sorted().joinToString(",")
        context.dataStore.edit { preferences ->
            preferences[PRACTICE_DAYS_KEY] = value
        }
    }

    suspend fun setRandomizeBucketHours(hours: Int) {
        context.dataStore.edit { preferences ->
            preferences[RANDOMIZE_BUCKET_HOURS_KEY] = hours
        }
    }

    suspend fun setFsrsRetention(percent: Int) {
        val valid = percent.coerceIn(MIN_FSRS_RETENTION, MAX_FSRS_RETENTION)
        context.dataStore.edit { preferences ->
            preferences[FSRS_RETENTION_KEY] = valid
        }
    }

    suspend fun setSm2IntervalModifier(percent: Int) {
        val valid = percent.coerceIn(MIN_SM2_INTERVAL_MODIFIER, MAX_SM2_INTERVAL_MODIFIER)
        context.dataStore.edit { preferences ->
            preferences[SM2_INTERVAL_MODIFIER_KEY] = valid
        }
    }

    suspend fun setFsrsEnableFuzzing(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FSRS_ENABLE_FUZZING_KEY] = enabled
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_BACKUP_ENABLED_KEY] = enabled
        }
    }

    suspend fun setAutoBackupUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[AUTO_BACKUP_URI_KEY] = uri
            } else {
                preferences.remove(AUTO_BACKUP_URI_KEY)
            }
        }
    }

    suspend fun setAutoBackupIntervalDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_BACKUP_INTERVAL_DAYS_KEY] = days
        }
    }

    suspend fun setLastBackupTime(timeMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_BACKUP_TIME_KEY] = timeMillis
        }
    }

    suspend fun setMaxBackupsKept(count: Int) {
        val validCount = count.coerceIn(MIN_MAX_BACKUPS_KEPT, MAX_MAX_BACKUPS_KEPT)
        context.dataStore.edit { preferences ->
            preferences[MAX_BACKUPS_KEPT_KEY] = validCount
        }
    }
}
