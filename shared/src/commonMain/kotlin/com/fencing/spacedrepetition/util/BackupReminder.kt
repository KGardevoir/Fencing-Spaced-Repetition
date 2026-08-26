// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

/**
 * A standing nudge to back up, for platforms that cannot do it themselves.
 *
 * Android schedules a real backup and this never appears there. A browser
 * has nothing to schedule with -- no page is running when the page is
 * closed -- so the honest substitute is to ask the user, and to ask no more
 * often than they said.
 *
 * @param daysSinceBackup how long it has been, or null if there has never
 *   been a backup at all. The screen says those two things differently.
 */
data class BackupReminder(val daysSinceBackup: Int?)

/** Milliseconds in a day, as the reminder counts them. */
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * The reminder to show, or null for silence.
 *
 * Silent when the reminder is switched off, when there are no cards to lose,
 * and until [intervalDays] have passed since the last backup. A collection
 * that has never been backed up is overdue from the moment it has anything
 * in it: that is the case the reminder exists for, since it is also the case
 * where the browser evicting its storage costs the user everything.
 *
 * [dismissedTime] silences it for one more interval. Dismissing is not
 * turning it off -- that is a switch in the settings, and it is the one the
 * user should reach for if they mean never. Waiting the same interval again
 * is what "not now" is worth: any shorter and dismissing achieves nothing,
 * any longer and it is a different setting that nobody chose.
 *
 * A function of its arguments and the clock, so the caller can test it and
 * the screens can stay presentation.
 */
fun backupReminder(
    enabled: Boolean,
    cardCount: Int,
    lastBackupTime: Long,
    dismissedTime: Long,
    intervalDays: Int,
    now: Long = Time.now()
): BackupReminder? {
    if (!enabled || cardCount <= 0) return null

    // A time stamped in the future is a clock that moved, not something that
    // has not happened yet: it counts as recent rather than nagging until the
    // date catches up. Which is what comparing the elapsed time does, since a
    // future stamp makes it negative.
    val interval = intervalDays.coerceAtLeast(1) * DAY_MILLIS
    if (dismissedTime > 0L && now - dismissedTime < interval) return null

    if (lastBackupTime <= 0L) return BackupReminder(daysSinceBackup = null)

    val elapsed = now - lastBackupTime
    if (elapsed < interval) return null

    return BackupReminder(daysSinceBackup = (elapsed / DAY_MILLIS).toInt())
}
