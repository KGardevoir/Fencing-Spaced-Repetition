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
        1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S"
    )

    /** Find the closest preset index for a given value. */
    fun findPresetIndex(presets: List<Pair<Int, String>>, value: Int): Int =
        presets.indexOfFirst { it.first >= value }.let { if (it == -1) presets.size - 1 else it }
}
