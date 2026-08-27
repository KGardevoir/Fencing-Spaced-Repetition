// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.preferences

import com.fencing.spacedrepetition.data.preferences.SettingsConstants as Defaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings in the browser's localStorage.
 *
 * localStorage rather than OPFS, where the database and the images live,
 * because these are a few dozen short strings read on almost every screen and
 * localStorage is the one browser store that answers synchronously. Reading a
 * theme through an asynchronous API would mean the app renders once in the
 * wrong theme and corrects itself, on every load.
 *
 * Its weakness is the flip side of the same property: writes are synchronous
 * and block the main thread. That is fine for values this small and this
 * rarely written, and wrong for anything larger -- which is why the images
 * are not here.
 *
 * Each setting is a StateFlow seeded from storage at construction, so reads
 * after the first cost nothing and every screen watching a setting updates
 * the moment it changes. Storage is written through on each set.
 *
 * Values are stored under the same names DataStore uses on Android. Nothing
 * reads across platforms today, but an export that carried settings would,
 * and matching names cost nothing to choose now.
 */
class LocalStoragePreferences : AppPreferences {

    private val themeModeState = MutableStateFlow(
        readString("theme_mode")?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        } ?: ThemeMode.SYSTEM
    )
    private val autoShowAnswerState = MutableStateFlow(readBoolean("auto_show_answer", false))
    private val cardsPerSessionState =
        MutableStateFlow(readInt("cards_per_session", Defaults.DEFAULT_CARDS_PER_SESSION))
    private val selectedGroupIdState = MutableStateFlow(readLongOrNull("selected_group_id"))
    private val randomizeDueCardsState =
        MutableStateFlow(readBoolean("randomize_due_cards", false))
    private val maximumIntervalState =
        MutableStateFlow(readInt("maximum_interval", Defaults.DEFAULT_MAXIMUM_INTERVAL))
    private val practicesPerWeekState =
        MutableStateFlow(readInt("practices_per_week", Defaults.DEFAULT_PRACTICES_PER_WEEK))
    private val practiceDaysState = MutableStateFlow(
        readString("practice_days")
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: Defaults.DEFAULT_PRACTICE_DAYS
    )
    private val randomizeBucketHoursState =
        MutableStateFlow(readInt("randomize_bucket_hours", Defaults.DEFAULT_RANDOMIZE_BUCKET_HOURS))
    private val fsrsRetentionState =
        MutableStateFlow(readInt("fsrs_retention", Defaults.DEFAULT_FSRS_RETENTION))
    private val fsrsEnableFuzzingState =
        MutableStateFlow(readBoolean("fsrs_enable_fuzzing", Defaults.DEFAULT_FSRS_ENABLE_FUZZING))
    private val autoBackupEnabledState =
        MutableStateFlow(readBoolean("auto_backup_enabled", Defaults.DEFAULT_AUTO_BACKUP_ENABLED))
    private val autoBackupUriState = MutableStateFlow(readString("auto_backup_uri"))
    private val autoBackupIntervalDaysState = MutableStateFlow(
        readInt("auto_backup_interval_days", Defaults.DEFAULT_AUTO_BACKUP_INTERVAL_DAYS)
    )
    private val lastBackupTimeState = MutableStateFlow(readLongOrNull("last_backup_time") ?: 0L)
    private val maxBackupsKeptState =
        MutableStateFlow(readInt("max_backups_kept", Defaults.DEFAULT_MAX_BACKUPS_KEPT))
    private val backupReminderEnabledState = MutableStateFlow(
        readBoolean("backup_reminder_enabled", Defaults.DEFAULT_BACKUP_REMINDER_ENABLED)
    )
    private val backupReminderIntervalDaysState = MutableStateFlow(
        readInt("backup_reminder_interval_days", Defaults.DEFAULT_BACKUP_REMINDER_INTERVAL_DAYS)
    )
    private val backupReminderDismissedTimeState =
        MutableStateFlow(readLongOrNull("backup_reminder_dismissed_time") ?: 0L)

    override val themeMode: Flow<ThemeMode> = themeModeState.asStateFlow()
    override val autoShowAnswer: Flow<Boolean> = autoShowAnswerState.asStateFlow()
    override val cardsPerSession: Flow<Int> = cardsPerSessionState.asStateFlow()
    override val selectedGroupId: Flow<Long?> = selectedGroupIdState.asStateFlow()
    override val randomizeDueCards: Flow<Boolean> = randomizeDueCardsState.asStateFlow()
    override val maximumInterval: Flow<Int> = maximumIntervalState.asStateFlow()
    override val practicesPerWeek: Flow<Int> = practicesPerWeekState.asStateFlow()
    override val practiceDays: Flow<Set<Int>> = practiceDaysState.asStateFlow()
    override val randomizeBucketHours: Flow<Int> = randomizeBucketHoursState.asStateFlow()
    override val fsrsRetention: Flow<Int> = fsrsRetentionState.asStateFlow()
    override val fsrsEnableFuzzing: Flow<Boolean> = fsrsEnableFuzzingState.asStateFlow()
    override val autoBackupEnabled: Flow<Boolean> = autoBackupEnabledState.asStateFlow()
    override val autoBackupUri: Flow<String?> = autoBackupUriState.asStateFlow()
    override val autoBackupIntervalDays: Flow<Int> = autoBackupIntervalDaysState.asStateFlow()
    override val lastBackupTime: Flow<Long> = lastBackupTimeState.asStateFlow()
    override val maxBackupsKept: Flow<Int> = maxBackupsKeptState.asStateFlow()
    override val backupReminderEnabled: Flow<Boolean> = backupReminderEnabledState.asStateFlow()
    override val backupReminderIntervalDays: Flow<Int> =
        backupReminderIntervalDaysState.asStateFlow()
    override val backupReminderDismissedTime: Flow<Long> =
        backupReminderDismissedTimeState.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) =
        write("theme_mode", mode.name, themeModeState, mode)

    override suspend fun setAutoShowAnswer(enabled: Boolean) =
        write("auto_show_answer", enabled.toString(), autoShowAnswerState, enabled)

    override suspend fun setCardsPerSession(count: Int) {
        val valid = count.coerceIn(Defaults.MIN_CARDS_PER_SESSION, Defaults.MAX_CARDS_PER_SESSION)
        write("cards_per_session", valid.toString(), cardsPerSessionState, valid)
    }

    override suspend fun setSelectedGroupId(groupId: Long?) {
        if (groupId == null) removeKey("selected_group_id") else writeString("selected_group_id", groupId.toString())
        selectedGroupIdState.value = groupId
    }

    override suspend fun setRandomizeDueCards(enabled: Boolean) =
        write("randomize_due_cards", enabled.toString(), randomizeDueCardsState, enabled)

    override suspend fun setMaximumInterval(days: Int) {
        val valid = days.coerceIn(Defaults.MIN_MAXIMUM_INTERVAL, Defaults.MAX_MAXIMUM_INTERVAL)
        write("maximum_interval", valid.toString(), maximumIntervalState, valid)
    }

    override suspend fun setPracticesPerWeek(count: Int) {
        val valid = count.coerceIn(Defaults.MIN_PRACTICES_PER_WEEK, Defaults.MAX_PRACTICES_PER_WEEK)
        write("practices_per_week", valid.toString(), practicesPerWeekState, valid)
    }

    override suspend fun setPracticeDays(days: Set<Int>) =
        write("practice_days", days.sorted().joinToString(","), practiceDaysState, days)

    override suspend fun setRandomizeBucketHours(hours: Int) =
        write("randomize_bucket_hours", hours.toString(), randomizeBucketHoursState, hours)

    override suspend fun setFsrsRetention(percent: Int) {
        val valid = percent.coerceIn(Defaults.MIN_FSRS_RETENTION, Defaults.MAX_FSRS_RETENTION)
        write("fsrs_retention", valid.toString(), fsrsRetentionState, valid)
    }

    override suspend fun setFsrsEnableFuzzing(enabled: Boolean) =
        write("fsrs_enable_fuzzing", enabled.toString(), fsrsEnableFuzzingState, enabled)

    override suspend fun setAutoBackupEnabled(enabled: Boolean) =
        write("auto_backup_enabled", enabled.toString(), autoBackupEnabledState, enabled)

    override suspend fun setAutoBackupUri(uri: String?) {
        if (uri == null) removeKey("auto_backup_uri") else writeString("auto_backup_uri", uri)
        autoBackupUriState.value = uri
    }

    override suspend fun setAutoBackupIntervalDays(days: Int) =
        write("auto_backup_interval_days", days.toString(), autoBackupIntervalDaysState, days)

    override suspend fun setLastBackupTime(timeMillis: Long) =
        write("last_backup_time", timeMillis.toString(), lastBackupTimeState, timeMillis)

    override suspend fun setMaxBackupsKept(count: Int) {
        val valid = count.coerceIn(Defaults.MIN_MAX_BACKUPS_KEPT, Defaults.MAX_MAX_BACKUPS_KEPT)
        write("max_backups_kept", valid.toString(), maxBackupsKeptState, valid)
    }

    override suspend fun setBackupReminderEnabled(enabled: Boolean) =
        write("backup_reminder_enabled", enabled.toString(), backupReminderEnabledState, enabled)

    override suspend fun setBackupReminderIntervalDays(days: Int) =
        write(
            "backup_reminder_interval_days",
            days.toString(),
            backupReminderIntervalDaysState,
            days
        )

    override suspend fun setBackupReminderDismissedTime(timeMillis: Long) =
        write(
            "backup_reminder_dismissed_time",
            timeMillis.toString(),
            backupReminderDismissedTimeState,
            timeMillis
        )

    private fun <T> write(key: String, stored: String, state: MutableStateFlow<T>, value: T) {
        writeString(key, stored)
        state.value = value
    }

    private fun readInt(key: String, fallback: Int): Int =
        readString(key)?.toIntOrNull() ?: fallback

    private fun readLongOrNull(key: String): Long? = readString(key)?.toLongOrNull()

    private fun readBoolean(key: String, fallback: Boolean): Boolean =
        readString(key)?.toBooleanStrictOrNull() ?: fallback
}

// localStorage throws rather than returning null when a browser is set to
// block site data, and in a private window in some browsers. A setting that
// cannot be stored should leave the app on its defaults, not stop it loading,
// so every access is guarded and a failed read is indistinguishable from an
// absent value.

private fun readString(key: String): String? = runCatching { rawRead(key) }.getOrNull()

private fun writeString(key: String, value: String) {
    runCatching { rawWrite(key, value) }
}

private fun removeKey(key: String) {
    runCatching { rawRemove(key) }
}

private fun rawRead(key: String): String? = js("window.localStorage.getItem(key)")
private fun rawWrite(key: String, value: String) { js("window.localStorage.setItem(key, value)") }
private fun rawRemove(key: String) { js("window.localStorage.removeItem(key)") }
