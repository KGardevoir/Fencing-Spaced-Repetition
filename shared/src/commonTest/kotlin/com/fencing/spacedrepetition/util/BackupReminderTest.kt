// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * When the browser build asks the user to back up.
 *
 * Worth its own tests because both mistakes are quiet: a reminder that never
 * fires leaves someone's collection one cleared-site-data away from gone,
 * and one that fires every launch is turned off within a week and then never
 * fires again either.
 */
class BackupReminderTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun reminder(
        enabled: Boolean = true,
        cardCount: Int = 20,
        lastBackupTime: Long = now - 8 * day,
        dismissedTime: Long = 0L,
        intervalDays: Int = 7
    ) = backupReminder(enabled, cardCount, lastBackupTime, dismissedTime, intervalDays, now)

    @Test
    fun remindsOnceTheIntervalHasPassed() {
        assertEquals(BackupReminder(daysSinceBackup = 8), reminder())
    }

    @Test
    fun saysNothingBeforeTheIntervalHasPassed() {
        assertNull(reminder(lastBackupTime = now - 6 * day))
    }

    /** Exactly due counts as due: a weekly reminder appears on the seventh day. */
    @Test
    fun remindsOnTheIntervalItself() {
        assertEquals(BackupReminder(daysSinceBackup = 7), reminder(lastBackupTime = now - 7 * day))
    }

    @Test
    fun saysNothingWhenTurnedOff() {
        assertNull(reminder(enabled = false))
        assertNull(reminder(enabled = false, lastBackupTime = 0L))
    }

    /** Nothing to lose, nothing to say -- a fresh install is not overdue. */
    @Test
    fun saysNothingWithoutCards() {
        assertNull(reminder(cardCount = 0, lastBackupTime = 0L))
    }

    /** The case it exists for: cards, and no backup of them anywhere. */
    @Test
    fun remindsWhenThereHasNeverBeenABackup() {
        assertEquals(BackupReminder(daysSinceBackup = null), reminder(lastBackupTime = 0L))
    }

    /** A clock that moved backwards is not a reason to nag. */
    @Test
    fun saysNothingForABackupStampedInTheFuture() {
        assertNull(reminder(lastBackupTime = now + 3 * day))
    }

    // Dismissing. "Not now" is worth one more interval of quiet -- less and
    // the button does nothing, more and it has quietly become the switch in
    // the settings, which is the thing to reach for if the answer is never.
    @Test
    fun saysNothingForAnIntervalAfterBeingDismissed() {
        assertNull(reminder(dismissedTime = now - 2 * day))
    }

    @Test
    fun comesBackOnceTheIntervalSinceTheDismissalHasPassed() {
        assertEquals(
            BackupReminder(daysSinceBackup = 30),
            reminder(lastBackupTime = now - 30 * day, dismissedTime = now - 8 * day)
        )
    }

    /** Dismissing quiets the never-backed-up case too, and only for as long. */
    @Test
    fun aDismissalAlsoQuietsACollectionThatHasNeverBeenBackedUp() {
        assertNull(reminder(lastBackupTime = 0L, dismissedTime = now - 3 * day))
        assertEquals(
            BackupReminder(daysSinceBackup = null),
            reminder(lastBackupTime = 0L, dismissedTime = now - 9 * day)
        )
    }

    @Test
    fun aLongerIntervalWaitsLonger() {
        assertNull(reminder(lastBackupTime = now - 20 * day, intervalDays = 30))
        assertEquals(
            BackupReminder(daysSinceBackup = 40),
            reminder(lastBackupTime = now - 40 * day, intervalDays = 30)
        )
    }
}
