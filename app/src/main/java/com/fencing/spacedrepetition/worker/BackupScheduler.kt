// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic [BackupWorker] that writes a compressed database backup
 * to the user's chosen folder.
 */
object BackupScheduler {
    private const val PERIODIC_WORK_NAME = "auto_database_backup"
    private const val ONE_TIME_WORK_NAME = "manual_database_backup"

    /** Schedules (or reschedules) the recurring backup at the given interval, in days. */
    fun schedule(context: Context, intervalDays: Int) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            intervalDays.toLong(), TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Cancels the recurring backup. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /** Runs a one-off backup immediately (e.g. "Back up now"). */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
