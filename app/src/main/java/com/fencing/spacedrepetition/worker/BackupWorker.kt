package com.fencing.spacedrepetition.worker

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.ExportResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        private const val BACKUP_FILE_PREFIX = "fencing_backup_"
        private const val MAX_KEPT_BACKUPS = 7
        private val FILENAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
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

        val filename = "$BACKUP_FILE_PREFIX${FILENAME_FORMAT.format(Date())}.tsv.gz"
        val backupFile = backupDir.createFile("application/gzip", filename)
            ?: return Result.failure()

        val exportResult = try {
            applicationContext.contentResolver.openOutputStream(backupFile.uri)?.use { fileStream ->
                val outputStream = CardImportExport.createCompressedOutputStream(fileStream)
                val result = CardImportExport.exportCardsWithGroupStates(
                    cardsWithStates, outputStream, allGroups, reviewLogs, cardQuestions,
                    opponents, opponentNamesById
                )
                outputStream.close()
                result
            } ?: ExportResult.Error("Failed to open backup file for writing")
        } catch (e: Exception) {
            ExportResult.Error("Backup failed: ${e.message}")
        }

        return when (exportResult) {
            is ExportResult.Success -> {
                preferences.setLastBackupTime(System.currentTimeMillis())
                pruneOldBackups(backupDir)
                Result.success()
            }
            is ExportResult.Error -> {
                backupFile.delete()
                Result.retry()
            }
        }
    }

    private fun pruneOldBackups(backupDir: DocumentFile) {
        val backups = backupDir.listFiles()
            .filter { it.name?.startsWith(BACKUP_FILE_PREFIX) == true }
            .sortedByDescending { it.name }

        backups.drop(MAX_KEPT_BACKUPS).forEach { it.delete() }
    }
}
