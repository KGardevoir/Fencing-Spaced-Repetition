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

    /**
     * Whether a backup can happen while the app is not open.
     *
     * The settings screen asks, because the answer decides what it can
     * honestly offer: where this is false the automatic-backup controls are
     * shown greyed out and a reminder takes their place, since a switch that
     * schedules nothing is worse than one that says it cannot.
     *
     * False by default, alongside the do-nothing methods below: an
     * implementation that schedules nothing must not claim otherwise.
     */
    val runsWhenClosed: Boolean get() = false

    suspend fun reschedule(enabled: Boolean, uri: String?, intervalDays: Int) {}

    /**
     * Backs up now, at the user's request.
     *
     * A platform with no scheduler can still have one of these -- in a
     * browser it is an export the user is handed -- which is why it is not
     * tied to [runsWhenClosed].
     */
    suspend fun runNow() {}

}

/** What a platform with neither a scheduler nor a backup of its own uses. */
object NoBackups : BackupScheduling
