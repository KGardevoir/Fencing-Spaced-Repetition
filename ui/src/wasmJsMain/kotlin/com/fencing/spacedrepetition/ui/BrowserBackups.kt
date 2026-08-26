// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui

import com.fencing.spacedrepetition.data.preferences.AppPreferences
import com.fencing.spacedrepetition.ui.viewmodel.BackupScheduling
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.util.Time

/**
 * Backing up in a browser: a file the user is handed, when they ask.
 *
 * [runsWhenClosed] stays false, and [reschedule] stays the do-nothing default,
 * because both are true of a page: nothing wakes it, and a backup it is not
 * open for cannot happen. That is what the settings screen greys the
 * automatic-backup controls on, and why it offers a reminder instead.
 *
 * What a browser *can* do is the backup itself, which is the export this PR
 * gave it -- everything, review history included, gzipped, downloaded. The
 * time is recorded only when that succeeded, so the reminder is silenced by a
 * backup rather than by an attempt at one.
 *
 * "Succeeded" means the file was built and the download started; where it
 * went afterwards is the browser's business and there is no event back. Which
 * is the same promise Android's worker makes about a document provider.
 */
class DownloadBackups(
    private val cards: CardViewModel,
    private val preferences: AppPreferences
) : BackupScheduling {

    override suspend fun runNow() {
        val filename = "$BACKUP_FILE_PREFIX${backupTimestamp()}.tsv.gz"
        if (cards.backUp(archiveDownload(filename))) {
            preferences.setLastBackupTime(Time.now())
        }
    }

    private companion object {
        /** The same prefix the Android backup worker writes, so the two sort together. */
        const val BACKUP_FILE_PREFIX = "fencing_backup_"
    }
}

/**
 * yyyyMMdd_HHmmss, in UTC.
 *
 * UTC rather than the reader's own time, unlike the Android worker's: this
 * name is chosen in a browser that will happily hand the same collection to
 * two machines in two time zones, and a name that sorts by when it was made
 * is worth more here than one that matches the clock on the wall.
 */
private fun backupTimestamp(): String = js(
    "new Date().toISOString().slice(0, 19).replace(/[-:]/g, '').replace('T', '_')"
)
