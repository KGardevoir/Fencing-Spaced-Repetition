// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.preferences

object SettingsConstants {
    /**
     * Desired-retention range for FSRS, as integer percent.
     *
     * Here rather than on the Android preferences class that used to own them,
     * because the retention planner and the retention slider both need these
     * bounds and neither is Android-specific.
     */
    const val MIN_FSRS_RETENTION = 70
    const val MAX_FSRS_RETENTION = 97

    /**
     * Defaults and bounds for every stored setting.
     *
     * These lived on the Android preferences class, which put the definition
     * of "the default number of cards per session" behind DataStore and a
     * Context. Two implementations of AppPreferences now need them and have to
     * agree, so they belong here -- the same move the retention bounds above
     * already made, for the same reason. ThemePreferences keeps them as
     * aliases so its existing call sites are untouched.
     */
    const val DEFAULT_CARDS_PER_SESSION = 3
    const val MIN_CARDS_PER_SESSION = 1
    const val MAX_CARDS_PER_SESSION = 20

    const val DEFAULT_MAXIMUM_INTERVAL = 365
    const val MIN_MAXIMUM_INTERVAL = 7
    const val MAX_MAXIMUM_INTERVAL = 3650

    const val DEFAULT_PRACTICES_PER_WEEK = 7
    const val MIN_PRACTICES_PER_WEEK = 1
    const val MAX_PRACTICES_PER_WEEK = 7

    const val DEFAULT_RANDOMIZE_BUCKET_HOURS = 24

    /** ISO-8601 weekdays, 1 = Monday. Every day, by default. */
    val DEFAULT_PRACTICE_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)

    const val DEFAULT_FSRS_RETENTION = 90

    const val DEFAULT_SM2_INTERVAL_MODIFIER = 100
    const val MIN_SM2_INTERVAL_MODIFIER = 50
    const val MAX_SM2_INTERVAL_MODIFIER = 200

    const val DEFAULT_FSRS_ENABLE_FUZZING = false

    const val DEFAULT_AUTO_BACKUP_ENABLED = false
    const val DEFAULT_AUTO_BACKUP_INTERVAL_DAYS = 1

    const val DEFAULT_MAX_BACKUPS_KEPT = 7
    const val MIN_MAX_BACKUPS_KEPT = 1
    const val MAX_MAX_BACKUPS_KEPT = 30

    /** Cards per session slider range and steps. */
    const val CARDS_PER_SESSION_MIN = 1f
    const val CARDS_PER_SESSION_MAX = 6f
    const val CARDS_PER_SESSION_STEPS = 4 // 6 values, steps = N - 2

    /** Preset values for sampling bucket size (hours to label). */
    val BUCKET_PRESETS = listOf(
        24 to "1 day",
        72 to "3 days",
        168 to "1 week",
        336 to "2 weeks",
        672 to "4 weeks"
    )

    /** Preset values for maximum interval (days to label). */
    val INTERVAL_PRESETS = listOf(
        7 to "1 week",
        14 to "2 weeks",
        30 to "1 month",
        60 to "2 months",
        90 to "3 months",
        180 to "6 months",
        365 to "1 year",
        730 to "2 years",
        1825 to "5 years",
        3650 to "10 years"
    )

    /** Day labels for practice day chips (ISO-8601: 1=Mon..7=Sun). */
    val DAY_LABELS = listOf(
        7 to "S", 1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S"
    )

    /**
     * Preset values for SM-2 interval modifier (integer percent → label).
     * 100 % = default SM-2 behaviour; lower = more frequent reviews; higher = longer intervals.
     */
    val SM2_MODIFIER_PRESETS = listOf(
        50  to "50%",
        75  to "75%",
        100 to "100%",
        125 to "125%",
        150 to "150%",
        200 to "200%"
    )

    /** Preset values for automatic backup frequency (days to label). */
    val BACKUP_INTERVAL_PRESETS = listOf(
        1 to "Daily",
        3 to "Every 3 days",
        7 to "Weekly"
    )

    /** Preset values for the number of backup files to keep before pruning older ones. */
    val MAX_BACKUPS_KEPT_PRESETS = listOf(
        3 to "3",
        5 to "5",
        7 to "7",
        10 to "10",
        15 to "15",
        20 to "20",
        30 to "30"
    )

    /** Find the closest preset index for a given value. */
    fun findPresetIndex(presets: List<Pair<Int, String>>, value: Int): Int =
        presets.indexOfFirst { it.first >= value }.let { if (it == -1) presets.size - 1 else it }
}
