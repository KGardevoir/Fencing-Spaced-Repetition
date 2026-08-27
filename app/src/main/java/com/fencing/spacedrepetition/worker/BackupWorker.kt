// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.worker

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.getDatabase
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.FileImageReader
import com.fencing.spacedrepetition.util.createCompressedOutputStream
import com.fencing.spacedrepetition.util.exportCardsWithGroupStates
import com.fencing.spacedrepetition.util.ExportResult
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.first

/**
 * Periodically writes a full, compressed backup of the database (cards, groups,
 * review history, and opponents) to a user-selected SAF folder.
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        /**
         * What a backup this worker wrote is called now, and what one written
         * under either of the two earlier naming schemes was called.
         *
         * All three are recognised so that pruning does not walk past a
         * folder of older backups and leave them there for ever. Which scheme
         * a file follows says nothing about its age, so the pruning sorts by
         * the time the file was written rather than by its name.
         */
        private val BACKUP_FILE_SUFFIX = "_backup" + CardImportExport.ARCHIVE_EXTENSION
        private const val TSV_BACKUP_FILE_SUFFIX = "_backup.tsv.gz"
        private const val LEGACY_BACKUP_FILE_PREFIX = "fencing_backup_"

        private fun isBackup(name: String?): Boolean =
            name != null &&
                (name.endsWith(BACKUP_FILE_SUFFIX) ||
                    name.endsWith(TSV_BACKUP_FILE_SUFFIX) ||
                    name.startsWith(LEGACY_BACKUP_FILE_PREFIX))
    }

    override suspend fun doWork(): Result {
        val preferences = ThemePreferences(applicationContext)

        if (!preferences.autoBackupEnabled.first()) {
            return Result.success()
        }

        val uriString = preferences.autoBackupUri.first() ?: return Result.success()
        val treeUri = Uri.parse(uriString)
        val backupDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
        if (backupDir == null || !backupDir.exists() || !backupDir.canWrite()) {
            return Result.failure()
        }

        val database = AppDatabase.getDatabase(applicationContext)
        val cardRepository = CardRepository(
            cardDao = database.cardDao(),
            sessionDao = database.practiceSessionDao(),
            reviewLogDao = database.reviewLogDao(),
            groupDao = database.groupDao(),
            opponentDao = database.opponentDao(),
            preferences = preferences
        )
        val groupRepository = GroupRepository(
            groupDao = database.groupDao(),
            cardDao = database.cardDao()
        )
        val opponentRepository = OpponentRepository(
            opponentDao = database.opponentDao()
        )

        val cardsWithStates = cardRepository.getAllCardsWithGroupStates()
        if (cardsWithStates.isEmpty()) {
            return Result.success()
        }

        val allGroups = groupRepository.getAllGroupsSync()
        val reviewLogs = cardRepository.getAllReviewLogsSync()
        val cardQuestions = cardsWithStates.associate { it.card.id to it.card.question }
        val opponents = opponentRepository.getAllOpponentsSync()
        val opponentNamesById = opponents.associate { it.id to it.name }

        val filename = CardImportExport.generateBackupFilename()
        val backupFile = backupDir.createFile("application/gzip", filename)
            ?: return Result.failure()

        val exportResult = try {
            applicationContext.contentResolver.openOutputStream(backupFile.uri)?.use { fileStream ->
                val outputStream = CardImportExport.createCompressedOutputStream(fileStream)
                val result = CardImportExport.exportCardsWithGroupStates(
                    cardsWithStates, outputStream, allGroups, reviewLogs, cardQuestions,
                    opponents, opponentNamesById, FileImageReader(applicationContext)
                )
                outputStream.close()
                result
            } ?: ExportResult.Error("Failed to open backup file for writing")
        } catch (e: Exception) {
            ExportResult.Error("Backup failed: ${e.message}")
        }

        return when (exportResult) {
            is ExportResult.Success -> {
                preferences.setLastBackupTime(Time.now())
                pruneOldBackups(backupDir, preferences.maxBackupsKept.first())
                Result.success()
            }
            is ExportResult.Error -> {
                backupFile.delete()
                Result.retry()
            }
        }
    }

    private fun pruneOldBackups(backupDir: DocumentFile, maxKeptBackups: Int) {
        val backups = backupDir.listFiles()
            .filter { isBackup(it.name) }
            .sortedByDescending { it.lastModified() }

        backups.drop(maxKeptBackups).forEach { it.delete() }
    }
}
