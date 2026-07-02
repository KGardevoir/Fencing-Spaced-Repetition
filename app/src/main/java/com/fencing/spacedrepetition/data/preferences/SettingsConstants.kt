package com.fencing.spacedrepetition.data.preferences

object SettingsConstants {
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
     * Preset values for FSRS desired retention (integer percent → label).
     * Range 70 %–97 %; the FSRS team recommends 80 %–95 % for most learners.
     */
    val FSRS_RETENTION_PRESETS = listOf(
        70 to "70%",
        75 to "75%",
        80 to "80%",
        85 to "85%",
        90 to "90%",
        92 to "92%",
        95 to "95%",
        97 to "97%"
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
