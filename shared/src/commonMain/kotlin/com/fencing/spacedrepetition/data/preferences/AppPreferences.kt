// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Every setting the app stores, as an interface.
 *
 * [SchedulingPreferences] was this idea applied to the scheduler alone -- the
 * seven values CardRepository reads. This widens it to the rest, for the same
 * reason and one more: the settings screen and the home screen both write
 * settings, so a read-only subset was not enough to get them off Android.
 *
 * DataStore backs it on Android; localStorage in a browser. Neither appears
 * here, which is the point.
 *
 * The backup settings are included even though scheduled backup is Android's
 * (it runs on WorkManager, which has no browser equivalent). They are values
 * the settings screen renders, and a screen that renders them has to be able
 * to read them on both platforms; what a platform *does* with them is a
 * separate question from whether it can store them.
 */
interface AppPreferences : SchedulingPreferences {

    val themeMode: Flow<ThemeMode>
    val autoShowAnswer: Flow<Boolean>
    val cardsPerSession: Flow<Int>
    val selectedGroupId: Flow<Long?>
    val practicesPerWeek: Flow<Int>

    val autoBackupEnabled: Flow<Boolean>
    val autoBackupUri: Flow<String?>
    val autoBackupIntervalDays: Flow<Int>
    val lastBackupTime: Flow<Long>
    val maxBackupsKept: Flow<Int>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAutoShowAnswer(enabled: Boolean)
    suspend fun setCardsPerSession(count: Int)
    suspend fun setSelectedGroupId(groupId: Long?)
    suspend fun setRandomizeDueCards(enabled: Boolean)
    suspend fun setMaximumInterval(days: Int)
    suspend fun setPracticesPerWeek(count: Int)
    suspend fun setPracticeDays(days: Set<Int>)
    suspend fun setRandomizeBucketHours(hours: Int)
    suspend fun setFsrsRetention(percent: Int)
    suspend fun setSm2IntervalModifier(percent: Int)
    suspend fun setFsrsEnableFuzzing(enabled: Boolean)
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    suspend fun setAutoBackupUri(uri: String?)
    suspend fun setAutoBackupIntervalDays(days: Int)
    suspend fun setLastBackupTime(timeMillis: Long)
    suspend fun setMaxBackupsKept(count: Int)
}
