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
}
