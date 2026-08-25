// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

/**
 * Keeping the periodic backup in step with the backup settings.
 *
 * Android runs this on WorkManager, which has no browser counterpart -- a
 * page that is not open cannot back anything up, and nothing in a browser
 * will wake it to try. So the settings are stored on both platforms and
 * acted on by one, which is why this is an interface with a do-nothing
 * default rather than a call the view model makes directly.
 *
 * [reschedule] takes the settings rather than reading them, because the view
 * model has just written them and knows the new values; re-reading would
 * race with its own write.
 */
interface BackupScheduling {

    suspend fun reschedule(enabled: Boolean, uri: String?, intervalDays: Int) {}

    suspend fun runNow() {}

}

/** What the browser uses: the settings are remembered, nothing is scheduled. */
object NoBackups : BackupScheduling
