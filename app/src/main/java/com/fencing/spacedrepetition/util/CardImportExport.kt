// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import android.content.Context
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.ReviewLog
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed class ImportResult {
    data class Success(val importedCount: Int, val skippedCount: Int, val errors: List<String>) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

sealed class ExportResult {
    data class Success(val exportedCount: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

/**
 * Represents a parsed card with optional full state
 */
data class ParsedCard(
    val concept: String,
    val answer: String,
    val lineNumber: Int,
    val imagePaths: List<String> = emptyList(), // For export (file paths)
    val imageData: List<String> = emptyList(),  // For import (base64 encoded)
    // Full state (null if simple import)
    val algorithm: AlgorithmType? = null,
    val stateContext: String? = null,  // "GLOBAL" or group name for group-specific state
    val nextReview: Long? = null,
    val lastReview: Long? = null,
    val fsrsStability: Double? = null,
    val fsrsDifficulty: Double? = null,
    val fsrsState: String? = null,
    val fsrsReps: Int? = null,
    val fsrsLapses: Int? = null,
    val fsrsScheduledDays: Int? = null,
    val fsrsElapsedDays: Int? = null,
    val sm2EaseFactor: Double? = null,
    val sm2Interval: Int? = null,
    val sm2Repetitions: Int? = null,
    val groupNames: List<String> = emptyList()
) {
    val hasFullState: Boolean get() = algorithm != null
    val isGlobalState: Boolean get() = stateContext == null || stateContext == "GLOBAL"
    val isGroupSpecificState: Boolean get() = !isGlobalState
}

/**
 * Data class for exporting a card with its groups
 */
data class CardWithGroupNames(
    val card: Card,
    val groupNames: List<String>
)

/**
 * Data class for exporting a card with group-specific learning states
 */
data class CardWithGroupStates(
    val card: Card,
    val groupNames: List<String>,
    val groupSpecificStates: Map<String, com.fencing.spacedrepetition.data.model.CardGroupLearningState> = emptyMap()
)

object CardImportExport {
    private const val DELIMITER = "\t"
    private const val NEWLINE_PLACEHOLDER = "<br>"
    private const val GROUP_SEPARATOR = "|"
    private const val IMAGE_SEPARATOR = "||"
    private const val HEADER_MARKER_V1 = "#FSR_EXPORT_V1"
    private const val HEADER_MARKER_V2 = "#FSR_EXPORT_V2"
    private const val HEADER_MARKER_V3 = "#FSR_EXPORT_V3"
    private const val GROUP_SETTINGS_PREFIX = "#GROUP_SETTINGS:"
    private const val OPPONENT_PREFIX = "#OPPONENT:"
    private const val REVIEW_HISTORY_START = "#REVIEW_HISTORY_START"
    private const val REVIEW_HISTORY_END = "#REVIEW_HISTORY_END"
    // Review-history columns. v3.1 added OpponentName + StabilityMultiplier at the end.
    // Older parsers tolerate the extra columns (they look up by index and stop early).
    private const val REVIEW_HISTORY_HEADERS = "#CardQuestion\tReviewTime\tGrade\tAlgorithm\tStateBefore\tStateAfter\tScheduledDays\tElapsedDays\tGroupName\tNotes\tImagePaths\tOpponentName\tStabilityMultiplier"

    // Column indices for V1 export format
    private const val COL_V1_QUESTION = 0
    private const val COL_V1_ANSWER = 1
    private const val COL_V1_ALGORITHM = 2
    private const val COL_V1_NEXT_REVIEW = 3
    private const val COL_V1_LAST_REVIEW = 4
    private const val COL_V1_FSRS_STABILITY = 5
    private const val COL_V1_FSRS_DIFFICULTY = 6
    private const val COL_V1_FSRS_STATE = 7
    private const val COL_V1_FSRS_REPS = 8
    private const val COL_V1_FSRS_LAPSES = 9
    private const val COL_V1_FSRS_SCHEDULED_DAYS = 10
    private const val COL_V1_FSRS_ELAPSED_DAYS = 11
    private const val COL_V1_SM2_EASE_FACTOR = 12
    private const val COL_V1_SM2_INTERVAL = 13
    private const val COL_V1_SM2_REPETITIONS = 14
    private const val COL_V1_GROUPS = 15

    // Column indices for V2 export format (includes StateContext)
    private const val COL_V2_QUESTION = 0
    private const val COL_V2_ANSWER = 1
    private const val COL_V2_ALGORITHM = 2
    private const val COL_V2_STATE_CONTEXT = 3  // GLOBAL or group name
    private const val COL_V2_NEXT_REVIEW = 4
    private const val COL_V2_LAST_REVIEW = 5
    private const val COL_V2_FSRS_STABILITY = 6
    private const val COL_V2_FSRS_DIFFICULTY = 7
    private const val COL_V2_FSRS_STATE = 8
    private const val COL_V2_FSRS_REPS = 9
    private const val COL_V2_FSRS_LAPSES = 10
    private const val COL_V2_FSRS_SCHEDULED_DAYS = 11
    private const val COL_V2_FSRS_ELAPSED_DAYS = 12
    private const val COL_V2_SM2_EASE_FACTOR = 13
    private const val COL_V2_SM2_INTERVAL = 14
    private const val COL_V2_SM2_REPETITIONS = 15
    private const val COL_V2_GROUPS = 16

    // Column indices for V3 export format (includes ImagePaths)
    private const val COL_V3_QUESTION = 0
    private const val COL_V3_ANSWER = 1
    private const val COL_V3_IMAGE_PATHS = 2
    private const val COL_V3_ALGORITHM = 3
    private const val COL_V3_STATE_CONTEXT = 4
    private const val COL_V3_NEXT_REVIEW = 5
    private const val COL_V3_LAST_REVIEW = 6
    private const val COL_V3_FSRS_STABILITY = 7
    private const val COL_V3_FSRS_DIFFICULTY = 8
    private const val COL_V3_FSRS_STATE = 9
    private const val COL_V3_FSRS_REPS = 10
    private const val COL_V3_FSRS_LAPSES = 11
    private const val COL_V3_FSRS_SCHEDULED_DAYS = 12
    private const val COL_V3_FSRS_ELAPSED_DAYS = 13
    private const val COL_V3_SM2_EASE_FACTOR = 14
    private const val COL_V3_SM2_INTERVAL = 15
    private const val COL_V3_SM2_REPETITIONS = 16
    private const val COL_V3_GROUPS = 17

    // Column headers for V1 export
    private const val COLUMN_HEADERS_V1 = "#Question\tAnswer\tAlgorithm\tNextReview\tLastReview\t" +
            "FSRS_Stability\tFSRS_Difficulty\tFSRS_State\tFSRS_Reps\tFSRS_Lapses\t" +
            "FSRS_ScheduledDays\tFSRS_ElapsedDays\tSM2_EaseFactor\tSM2_Interval\t" +
            "SM2_Repetitions\tGroups(pipe-separated)"

    // Column headers for V2 export
    private const val COLUMN_HEADERS_V2 = "#Question\tAnswer\tAlgorithm\tStateContext\tNextReview\tLastReview\t" +
            "FSRS_Stability\tFSRS_Difficulty\tFSRS_State\tFSRS_Reps\tFSRS_Lapses\t" +
            "FSRS_ScheduledDays\tFSRS_ElapsedDays\tSM2_EaseFactor\tSM2_Interval\t" +
            "SM2_Repetitions\tGroups(pipe-separated)"

    // Column headers for V3 export (includes ImagePaths)
    private const val COLUMN_HEADERS_V3 = "#Question\tAnswer\tImagePaths(double-pipe-separated)\tAlgorithm\tStateContext\tNextReview\tLastReview\t" +
            "FSRS_Stability\tFSRS_Difficulty\tFSRS_State\tFSRS_Reps\tFSRS_Lapses\t" +
            "FSRS_ScheduledDays\tFSRS_ElapsedDays\tSM2_EaseFactor\tSM2_Interval\t" +
            "SM2_Repetitions\tGroups(pipe-separated)"

    /**
     * Parses a TSV input stream into a list of ParsedCard objects.
     * Supports both simple (question\tanswer) and full export formats (V1, V2, and V3).
     * Returns pair of (valid cards, error messages)
     */
    /** Parsed group settings from an import file */
    var lastParsedGroupSettings: Map<String, Map<String, String>> = emptyMap()
        private set

    /** Parsed review history from an import file (populated after calling parseCards) */
    var lastParsedReviewHistory: List<ParsedReviewLog> = emptyList()
        private set

    /** Parsed opponents from an import file (populated after calling parseCards) */
    var lastParsedOpponents: List<ParsedOpponent> = emptyList()
        private set

    /** Opponent record parsed from an export's `#OPPONENT:` lines. */
    data class ParsedOpponent(
        val name: String,
        val skillMultiplier: Double,
        val notes: String
    )

    fun parseCards(inputStream: InputStream): Pair<List<ParsedCard>, List<String>> {
        val cards = mutableListOf<ParsedCard>()
        val errors = mutableListOf<String>()
        val groupSettings = mutableMapOf<String, Map<String, String>>()
        val opponents = mutableListOf<ParsedOpponent>()

        try {
            val lines = inputStream.bufferedReader(Charsets.UTF_8).readLines()
            lastParsedReviewHistory = parseReviewHistory(lines)
            if (lines.isEmpty()) {
                return Pair(emptyList(), emptyList())
            }

            // Detect format version
            val firstLine = lines.firstOrNull() ?: ""
            val formatVersion = when {
                firstLine.startsWith(HEADER_MARKER_V3) -> 3
                firstLine.startsWith(HEADER_MARKER_V2) -> 2
                firstLine.startsWith(HEADER_MARKER_V1) -> 1
                else -> 0
            }

            if (formatVersion == 0) {
                return Pair(
                    emptyList(),
                    listOf("Invalid file format: file must begin with $HEADER_MARKER_V1, $HEADER_MARKER_V2, or $HEADER_MARKER_V3")
                )
            }

            val dataLines = lines.drop(1)

            var inHistorySection = false
            dataLines.forEachIndexed { index, line ->
                val lineNumber = if (formatVersion > 0) index + 2 else index + 1
                val trimmedLine = line.trim()

                if (trimmedLine.isEmpty()) return@forEachIndexed
                // Skip review history section
                if (trimmedLine == REVIEW_HISTORY_START) { inHistorySection = true; return@forEachIndexed }
                if (trimmedLine == REVIEW_HISTORY_END) { inHistorySection = false; return@forEachIndexed }
                if (inHistorySection) return@forEachIndexed
                // Capture group settings lines
                if (trimmedLine.startsWith(GROUP_SETTINGS_PREFIX)) {
                    parseGroupSettingsLine(trimmedLine)?.let { (name, settings) ->
                        groupSettings[name] = settings
                    }
                    return@forEachIndexed
                }
                // Capture opponent lines
                if (trimmedLine.startsWith(OPPONENT_PREFIX)) {
                    parseOpponentLine(trimmedLine)?.let { opponents.add(it) }
                    return@forEachIndexed
                }
                if (trimmedLine.startsWith("#")) {
                    return@forEachIndexed // Skip other comments
                }

                try {
                    val parsedCard = when (formatVersion) {
                        3 -> parseV3FormatLine(trimmedLine, lineNumber)
                        2 -> parseV2FormatLine(trimmedLine, lineNumber)
                        1 -> parseV1FormatLine(trimmedLine, lineNumber)
                        else -> parseSimpleLine(trimmedLine, lineNumber)
                    }

                    if (parsedCard != null) {
                        cards.add(parsedCard)
                    } else {
                        errors.add("Line $lineNumber: Invalid format")
                    }
                } catch (e: Exception) {
                    errors.add("Line $lineNumber: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to read file: ${e.message}")
        }

        lastParsedGroupSettings = groupSettings
        lastParsedOpponents = opponents
        return Pair(cards, errors)
    }

    private fun parseSimpleLine(line: String, lineNumber: Int): ParsedCard? {
        val parts = line.split(DELIMITER, limit = 2)

        return when {
            parts.size < 2 -> throw IllegalArgumentException("Missing answer (no tab delimiter found)")
            parts[0].isBlank() -> throw IllegalArgumentException("Empty question")
            else -> ParsedCard(
                concept = unescapeNewlines(parts[0].trim()),
                answer = unescapeNewlines(parts[1].trim()),
                lineNumber = lineNumber
            )
        }
    }

    private fun parseV1FormatLine(line: String, lineNumber: Int): ParsedCard? {
        val parts = line.split(DELIMITER)

        if (parts.size < 2) {
            throw IllegalArgumentException("Missing answer")
        }

        val concept = unescapeNewlines(parts.getOrNull(COL_V1_QUESTION)?.trim() ?: "")
        val answer = unescapeNewlines(parts.getOrNull(COL_V1_ANSWER)?.trim() ?: "")

        if (concept.isBlank()) throw IllegalArgumentException("Empty question")

        // If only 2 columns, treat as simple format
        if (parts.size == 2) {
            return ParsedCard(concept = concept, answer = answer, lineNumber = lineNumber)
        }

        // Parse full format
        val algorithmStr = parts.getOrNull(COL_V1_ALGORITHM)?.trim() ?: "FSRS"
        val algorithm = try {
            AlgorithmType.valueOf(algorithmStr)
        } catch (e: Exception) {
            AlgorithmType.FSRS
        }

        val groupsStr = parts.getOrNull(COL_V1_GROUPS)?.trim() ?: ""
        val groupNames = if (groupsStr.isNotEmpty()) {
            groupsStr.split(GROUP_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        return ParsedCard(
            concept = concept,
            answer = answer,
            lineNumber = lineNumber,
            algorithm = algorithm,
            stateContext = "GLOBAL",  // V1 format only has global state
            nextReview = parts.getOrNull(COL_V1_NEXT_REVIEW)?.toLongOrNull() ?: 0L,
            lastReview = parts.getOrNull(COL_V1_LAST_REVIEW)?.toLongOrNull() ?: 0L,
            fsrsStability = parts.getOrNull(COL_V1_FSRS_STABILITY)?.toDoubleOrNull() ?: 0.0,
            fsrsDifficulty = parts.getOrNull(COL_V1_FSRS_DIFFICULTY)?.toDoubleOrNull() ?: 0.0,
            fsrsState = parts.getOrNull(COL_V1_FSRS_STATE)?.trim() ?: "NEW",
            fsrsReps = parts.getOrNull(COL_V1_FSRS_REPS)?.toIntOrNull() ?: 0,
            fsrsLapses = parts.getOrNull(COL_V1_FSRS_LAPSES)?.toIntOrNull() ?: 0,
            fsrsScheduledDays = parts.getOrNull(COL_V1_FSRS_SCHEDULED_DAYS)?.toIntOrNull() ?: 0,
            fsrsElapsedDays = parts.getOrNull(COL_V1_FSRS_ELAPSED_DAYS)?.toIntOrNull() ?: 0,
            sm2EaseFactor = parts.getOrNull(COL_V1_SM2_EASE_FACTOR)?.toDoubleOrNull() ?: 2.5,
            sm2Interval = parts.getOrNull(COL_V1_SM2_INTERVAL)?.toIntOrNull() ?: 0,
            sm2Repetitions = parts.getOrNull(COL_V1_SM2_REPETITIONS)?.toIntOrNull() ?: 0,
            groupNames = groupNames
        )
    }

    private fun parseV2FormatLine(line: String, lineNumber: Int): ParsedCard? {
        val parts = line.split(DELIMITER)

        if (parts.size < 2) {
            throw IllegalArgumentException("Missing answer")
        }

        val concept = unescapeNewlines(parts.getOrNull(COL_V2_QUESTION)?.trim() ?: "")
        val answer = unescapeNewlines(parts.getOrNull(COL_V2_ANSWER)?.trim() ?: "")

        if (concept.isBlank()) throw IllegalArgumentException("Empty question")

        // If only 2 columns, treat as simple format
        if (parts.size == 2) {
            return ParsedCard(concept = concept, answer = answer, lineNumber = lineNumber)
        }

        // Parse V2 full format
        val algorithmStr = parts.getOrNull(COL_V2_ALGORITHM)?.trim() ?: "FSRS"
        val algorithm = try {
            AlgorithmType.valueOf(algorithmStr)
        } catch (e: Exception) {
            AlgorithmType.FSRS
        }

        val stateContext = parts.getOrNull(COL_V2_STATE_CONTEXT)?.trim() ?: "GLOBAL"

        val groupsStr = parts.getOrNull(COL_V2_GROUPS)?.trim() ?: ""
        val groupNames = if (groupsStr.isNotEmpty()) {
            groupsStr.split(GROUP_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        return ParsedCard(
            concept = concept,
            answer = answer,
            lineNumber = lineNumber,
            algorithm = algorithm,
            stateContext = stateContext,
            nextReview = parts.getOrNull(COL_V2_NEXT_REVIEW)?.toLongOrNull() ?: 0L,
            lastReview = parts.getOrNull(COL_V2_LAST_REVIEW)?.toLongOrNull() ?: 0L,
            fsrsStability = parts.getOrNull(COL_V2_FSRS_STABILITY)?.toDoubleOrNull() ?: 0.0,
            fsrsDifficulty = parts.getOrNull(COL_V2_FSRS_DIFFICULTY)?.toDoubleOrNull() ?: 0.0,
            fsrsState = parts.getOrNull(COL_V2_FSRS_STATE)?.trim() ?: "NEW",
            fsrsReps = parts.getOrNull(COL_V2_FSRS_REPS)?.toIntOrNull() ?: 0,
            fsrsLapses = parts.getOrNull(COL_V2_FSRS_LAPSES)?.toIntOrNull() ?: 0,
            fsrsScheduledDays = parts.getOrNull(COL_V2_FSRS_SCHEDULED_DAYS)?.toIntOrNull() ?: 0,
            fsrsElapsedDays = parts.getOrNull(COL_V2_FSRS_ELAPSED_DAYS)?.toIntOrNull() ?: 0,
            sm2EaseFactor = parts.getOrNull(COL_V2_SM2_EASE_FACTOR)?.toDoubleOrNull() ?: 2.5,
            sm2Interval = parts.getOrNull(COL_V2_SM2_INTERVAL)?.toIntOrNull() ?: 0,
            sm2Repetitions = parts.getOrNull(COL_V2_SM2_REPETITIONS)?.toIntOrNull() ?: 0,
            groupNames = groupNames
        )
    }

    private fun parseV3FormatLine(line: String, lineNumber: Int): ParsedCard? {
        val parts = line.split(DELIMITER)

        if (parts.size < 2) {
            throw IllegalArgumentException("Missing answer")
        }

        val concept = unescapeNewlines(parts.getOrNull(COL_V3_QUESTION)?.trim() ?: "")
        val answer = unescapeNewlines(parts.getOrNull(COL_V3_ANSWER)?.trim() ?: "")

        if (concept.isBlank()) throw IllegalArgumentException("Empty question")

        // If only 2 columns, treat as simple format
        if (parts.size == 2) {
            return ParsedCard(concept = concept, answer = answer, lineNumber = lineNumber)
        }

        // Parse image data (base64 encoded)
        val imageDataStr = parts.getOrNull(COL_V3_IMAGE_PATHS)?.trim() ?: ""
        val imageData = if (imageDataStr.isNotEmpty()) {
            imageDataStr.split(IMAGE_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        // Parse V3 full format
        val algorithmStr = parts.getOrNull(COL_V3_ALGORITHM)?.trim() ?: "FSRS"
        val algorithm = try {
            AlgorithmType.valueOf(algorithmStr)
        } catch (e: Exception) {
            AlgorithmType.FSRS
        }

        val stateContext = parts.getOrNull(COL_V3_STATE_CONTEXT)?.trim() ?: "GLOBAL"

        val groupsStr = parts.getOrNull(COL_V3_GROUPS)?.trim() ?: ""
        val groupNames = if (groupsStr.isNotEmpty()) {
            groupsStr.split(GROUP_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        return ParsedCard(
            concept = concept,
            answer = answer,
            lineNumber = lineNumber,
            imageData = imageData,  // Store base64 data for later decoding
            algorithm = algorithm,
            stateContext = stateContext,
            nextReview = parts.getOrNull(COL_V3_NEXT_REVIEW)?.toLongOrNull() ?: 0L,
            lastReview = parts.getOrNull(COL_V3_LAST_REVIEW)?.toLongOrNull() ?: 0L,
            fsrsStability = parts.getOrNull(COL_V3_FSRS_STABILITY)?.toDoubleOrNull() ?: 0.0,
            fsrsDifficulty = parts.getOrNull(COL_V3_FSRS_DIFFICULTY)?.toDoubleOrNull() ?: 0.0,
            fsrsState = parts.getOrNull(COL_V3_FSRS_STATE)?.trim() ?: "NEW",
            fsrsReps = parts.getOrNull(COL_V3_FSRS_REPS)?.toIntOrNull() ?: 0,
            fsrsLapses = parts.getOrNull(COL_V3_FSRS_LAPSES)?.toIntOrNull() ?: 0,
            fsrsScheduledDays = parts.getOrNull(COL_V3_FSRS_SCHEDULED_DAYS)?.toIntOrNull() ?: 0,
            fsrsElapsedDays = parts.getOrNull(COL_V3_FSRS_ELAPSED_DAYS)?.toIntOrNull() ?: 0,
            sm2EaseFactor = parts.getOrNull(COL_V3_SM2_EASE_FACTOR)?.toDoubleOrNull() ?: 2.5,
            sm2Interval = parts.getOrNull(COL_V3_SM2_INTERVAL)?.toIntOrNull() ?: 0,
            sm2Repetitions = parts.getOrNull(COL_V3_SM2_REPETITIONS)?.toIntOrNull() ?: 0,
            groupNames = groupNames
        )
    }

    /**
     * Exports cards with their groups to V1 format TSV (backward compatibility)
     */
    fun exportCardsWithGroups(
        cardsWithGroups: List<CardWithGroupNames>,
        outputStream: OutputStream
    ): ExportResult {
        return try {
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                // Write format marker (V1 for backward compatibility)
                writer.write(HEADER_MARKER_V1)
                writer.newLine()

                // Write column headers
                writer.write(COLUMN_HEADERS_V1)
                writer.newLine()

                cardsWithGroups.forEach { (card, groupNames) ->
                    val line = buildString {
                        append(escapeNewlines(card.question))
                        append(DELIMITER)
                        append(escapeNewlines(card.answer))
                        append(DELIMITER)
                        append(card.algorithm.name)
                        append(DELIMITER)
                        append(card.nextReview)
                        append(DELIMITER)
                        append(card.lastReview)
                        append(DELIMITER)
                        append(card.fsrsStability)
                        append(DELIMITER)
                        append(card.fsrsDifficulty)
                        append(DELIMITER)
                        append(card.fsrsState)
                        append(DELIMITER)
                        append(card.fsrsReps)
                        append(DELIMITER)
                        append(card.fsrsLapses)
                        append(DELIMITER)
                        append(card.fsrsScheduledDays)
                        append(DELIMITER)
                        append(card.fsrsElapsedDays)
                        append(DELIMITER)
                        append(card.sm2EaseFactor)
                        append(DELIMITER)
                        append(card.sm2Interval)
                        append(DELIMITER)
                        append(card.sm2Repetitions)
                        append(DELIMITER)
                        append(groupNames.joinToString(GROUP_SEPARATOR))
                    }
                    writer.write(line)
                    writer.newLine()
                }
            }
            ExportResult.Success(cardsWithGroups.size)
        } catch (e: Exception) {
            ExportResult.Error("Failed to write file: ${e.message}")
        }
    }

    /**
     * Exports cards with group-specific learning states to V3 format TSV.
     * Optionally includes a REVIEW_HISTORY section at the end.
     */
    fun exportCardsWithGroupStates(
        cardsWithStates: List<CardWithGroupStates>,
        outputStream: OutputStream,
        groupSettings: List<Group> = emptyList(),
        reviewLogs: List<ReviewLog> = emptyList(),
        cardQuestions: Map<Long, String> = emptyMap(),
        opponents: List<Opponent> = emptyList(),
        opponentNamesById: Map<Long, String> = emptyMap()
    ): ExportResult {
        return try {
            var rowCount = 0
            val writer = outputStream.bufferedWriter(Charsets.UTF_8)

            // Write format marker (V3)
            writer.write(HEADER_MARKER_V3)
            writer.newLine()

            // Write group settings metadata
            groupSettings.filter { it.hasCustomSettings() }.forEach { group ->
                writer.write(buildGroupSettingsLine(group))
                writer.newLine()
            }

            // Write opponents metadata so review logs can reference them by name on import.
            opponents.forEach { opponent ->
                writer.write(buildOpponentLine(opponent))
                writer.newLine()
            }

            // Write column headers
            writer.write(COLUMN_HEADERS_V3)
            writer.newLine()

            cardsWithStates.forEach { (card, groupNames, groupSpecificStates) ->
                // Write global state row
                writer.write(buildCardStateLine(card, groupNames, "GLOBAL"))
                writer.newLine()
                rowCount++

                // Write group-specific state rows
                groupSpecificStates.forEach { (groupName, learningState) ->
                    writer.write(buildGroupStateLine(card, groupName, learningState))
                    writer.newLine()
                    rowCount++
                }
            }

            // Optionally append review history section
            if (reviewLogs.isNotEmpty() && cardQuestions.isNotEmpty()) {
                writer.write(REVIEW_HISTORY_START)
                writer.newLine()
                writer.write(REVIEW_HISTORY_HEADERS)
                writer.newLine()
                reviewLogs.forEach { log ->
                    val question = cardQuestions[log.cardId] ?: return@forEach
                    val opponentName = log.opponentId?.let { opponentNamesById[it] }
                    writer.write(buildReviewLogLine(log, question, opponentName))
                    writer.newLine()
                }
                writer.write(REVIEW_HISTORY_END)
                writer.newLine()
            }

            writer.flush()
            ExportResult.Success(rowCount)
        } catch (e: Exception) {
            ExportResult.Error("Failed to write file: ${e.message}")
        }
    }

    private fun buildCardStateLine(card: Card, groupNames: List<String>, stateContext: String): String {
        return buildString {
            append(escapeNewlines(card.question))
            append(DELIMITER)
            append(escapeNewlines(card.answer))
            append(DELIMITER)
            // Encode images to base64
            val encodedImages = card.imagePaths.mapNotNull { encodeImageToBase64(it) }
            append(encodedImages.joinToString(IMAGE_SEPARATOR))
            append(DELIMITER)
            append(card.algorithm.name)
            append(DELIMITER)
            append(stateContext)
            append(DELIMITER)
            append(card.nextReview)
            append(DELIMITER)
            append(card.lastReview)
            append(DELIMITER)
            append(card.fsrsStability)
            append(DELIMITER)
            append(card.fsrsDifficulty)
            append(DELIMITER)
            append(card.fsrsState)
            append(DELIMITER)
            append(card.fsrsReps)
            append(DELIMITER)
            append(card.fsrsLapses)
            append(DELIMITER)
            append(card.fsrsScheduledDays)
            append(DELIMITER)
            append(card.fsrsElapsedDays)
            append(DELIMITER)
            append(card.sm2EaseFactor)
            append(DELIMITER)
            append(card.sm2Interval)
            append(DELIMITER)
            append(card.sm2Repetitions)
            append(DELIMITER)
            append(groupNames.joinToString(GROUP_SEPARATOR))
        }
    }

    private fun buildGroupStateLine(
        card: Card,
        groupName: String,
        learningState: com.fencing.spacedrepetition.data.model.CardGroupLearningState
    ): String {
        return buildString {
            append(escapeNewlines(card.question))
            append(DELIMITER)
            append(escapeNewlines(card.answer))
            append(DELIMITER)
            // Encode images to base64
            val encodedImages = card.imagePaths.mapNotNull { encodeImageToBase64(it) }
            append(encodedImages.joinToString(IMAGE_SEPARATOR))
            append(DELIMITER)
            append(card.algorithm.name)
            append(DELIMITER)
            append(groupName)  // StateContext is the group name
            append(DELIMITER)
            append(learningState.nextReview)
            append(DELIMITER)
            append(learningState.lastReview)
            append(DELIMITER)
            append(learningState.fsrsStability)
            append(DELIMITER)
            append(learningState.fsrsDifficulty)
            append(DELIMITER)
            append(learningState.fsrsState)
            append(DELIMITER)
            append(learningState.fsrsReps)
            append(DELIMITER)
            append(learningState.fsrsLapses)
            append(DELIMITER)
            append(learningState.fsrsScheduledDays)
            append(DELIMITER)
            append(learningState.fsrsElapsedDays)
            append(DELIMITER)
            append(learningState.sm2EaseFactor)
            append(DELIMITER)
            append(learningState.sm2Interval)
            append(DELIMITER)
            append(learningState.sm2Repetitions)
            append(DELIMITER)
            append(groupName)  // Groups column - just the group name for group-specific rows
        }
    }

    /**
     * Simple export for backward compatibility (question\tanswer only)
     */
    fun exportCards(cards: List<Card>, outputStream: OutputStream): ExportResult {
        return exportCardsWithGroups(
            cards.map { CardWithGroupNames(it, emptyList()) },
            outputStream
        )
    }

    /**
     * Converts a ParsedCard to a Card entity (without Context - for tests)
     * Note: This version cannot decode base64 images
     */
    fun parsedCardToCard(parsed: ParsedCard): Card {
        val now = System.currentTimeMillis()

        return if (parsed.hasFullState) {
            Card(
                question = parsed.concept,
                answer = parsed.answer,
                imagePaths = parsed.imagePaths,
                algorithm = parsed.algorithm ?: AlgorithmType.FSRS,
                nextReview = parsed.nextReview ?: 0L,
                lastReview = parsed.lastReview ?: 0L,
                fsrsStability = parsed.fsrsStability ?: 0.0,
                fsrsDifficulty = parsed.fsrsDifficulty ?: 0.0,
                fsrsState = parsed.fsrsState ?: "NEW",
                fsrsReps = parsed.fsrsReps ?: 0,
                fsrsLapses = parsed.fsrsLapses ?: 0,
                fsrsScheduledDays = parsed.fsrsScheduledDays ?: 0,
                fsrsElapsedDays = parsed.fsrsElapsedDays ?: 0,
                sm2EaseFactor = parsed.sm2EaseFactor ?: 2.5,
                sm2Interval = parsed.sm2Interval ?: 0,
                sm2Repetitions = parsed.sm2Repetitions ?: 0,
                created = now,
                modified = now
            )
        } else {
            Card(
                question = parsed.concept,
                answer = parsed.answer,
                imagePaths = parsed.imagePaths,
                algorithm = AlgorithmType.FSRS,
                created = now,
                modified = now
            )
        }
    }

    /**
     * Converts a ParsedCard to a Card entity with base64 image decoding
     */
    fun parsedCardToCard(context: Context, parsed: ParsedCard): Card {
        val now = System.currentTimeMillis()

        // Decode base64 images to file paths
        val decodedImagePaths = parsed.imageData.mapNotNull { base64Data ->
            decodeImageFromBase64(context, base64Data)
        }

        return if (parsed.hasFullState) {
            Card(
                question = parsed.concept,
                answer = parsed.answer,
                imagePaths = decodedImagePaths,
                algorithm = parsed.algorithm ?: AlgorithmType.FSRS,
                nextReview = parsed.nextReview ?: 0L,
                lastReview = parsed.lastReview ?: 0L,
                fsrsStability = parsed.fsrsStability ?: 0.0,
                fsrsDifficulty = parsed.fsrsDifficulty ?: 0.0,
                fsrsState = parsed.fsrsState ?: "NEW",
                fsrsReps = parsed.fsrsReps ?: 0,
                fsrsLapses = parsed.fsrsLapses ?: 0,
                fsrsScheduledDays = parsed.fsrsScheduledDays ?: 0,
                fsrsElapsedDays = parsed.fsrsElapsedDays ?: 0,
                sm2EaseFactor = parsed.sm2EaseFactor ?: 2.5,
                sm2Interval = parsed.sm2Interval ?: 0,
                sm2Repetitions = parsed.sm2Repetitions ?: 0,
                created = now,
                modified = now
            )
        } else {
            Card(
                question = parsed.concept,
                answer = parsed.answer,
                imagePaths = decodedImagePaths,
                algorithm = AlgorithmType.FSRS,
                created = now,
                modified = now
            )
        }
    }

    /**
     * Escapes newlines for export (replaces \n with <br>)
     */
    private fun escapeNewlines(text: String): String {
        return text.replace("\r\n", NEWLINE_PLACEHOLDER)
            .replace("\n", NEWLINE_PLACEHOLDER)
            .replace("\r", "")
    }

    /**
     * Unescapes newlines after import (replaces <br> with \n)
     */
    private fun unescapeNewlines(text: String): String {
        return text.replace(NEWLINE_PLACEHOLDER, "\n")
    }

    /**
     * Builds a group settings metadata line for export.
     * Format: #GROUP_SETTINGS:name\tkey=value\tkey=value\t...
     */
    private fun buildGroupSettingsLine(group: Group): String {
        return buildString {
            append(GROUP_SETTINGS_PREFIX)
            append(escapeNewlines(group.name))
            group.cardsPerSession?.let { append("\tcardsPerSession=$it") }
            group.autoShowAnswer?.let { append("\tautoShowAnswer=$it") }
            group.randomizeDueCards?.let { append("\trandomizeDueCards=$it") }
            group.randomizeBucketHours?.let { append("\trandomizeBucketHours=$it") }
            group.practiceDays?.let { append("\tpracticeDays=$it") }
            group.maximumInterval?.let { append("\tmaximumInterval=$it") }
            group.fsrsRetention?.let { append("\tfsrsRetention=$it") }
            group.sm2IntervalModifier?.let { append("\tsm2IntervalModifier=$it") }
        }
    }

    /**
     * Parses a group settings line from import.
     * Returns pair of (group name, settings map) or null if not a settings line.
     */
    fun parseGroupSettingsLine(line: String): Pair<String, Map<String, String>>? {
        if (!line.startsWith(GROUP_SETTINGS_PREFIX)) return null
        val rest = line.removePrefix(GROUP_SETTINGS_PREFIX)
        val parts = rest.split("\t")
        if (parts.isEmpty()) return null
        val groupName = unescapeNewlines(parts[0])
        val settings = mutableMapOf<String, String>()
        parts.drop(1).forEach { part ->
            val eqIndex = part.indexOf('=')
            if (eqIndex > 0) {
                settings[part.substring(0, eqIndex)] = part.substring(eqIndex + 1)
            }
        }
        return groupName to settings
    }

    /**
     * Builds an opponent metadata line for export.
     * Format: #OPPONENT:<escaped name>\tskillMultiplier=X.XX\tnotes=<escaped notes>
     */
    private fun buildOpponentLine(opponent: Opponent): String {
        return buildString {
            append(OPPONENT_PREFIX)
            append(escapeNewlines(opponent.name))
            append("\tskillMultiplier=").append(opponent.skillMultiplier)
            if (opponent.notes.isNotBlank()) {
                append("\tnotes=").append(escapeNewlines(opponent.notes))
            }
        }
    }

    /**
     * Parses an opponent metadata line. Returns null if not a well-formed opponent line.
     */
    fun parseOpponentLine(line: String): ParsedOpponent? {
        if (!line.startsWith(OPPONENT_PREFIX)) return null
        val rest = line.removePrefix(OPPONENT_PREFIX)
        val parts = rest.split("\t")
        if (parts.isEmpty()) return null
        val name = unescapeNewlines(parts[0]).trim()
        if (name.isEmpty()) return null
        var multiplier = 1.0
        var notes = ""
        parts.drop(1).forEach { part ->
            val eqIndex = part.indexOf('=')
            if (eqIndex <= 0) return@forEach
            val key = part.substring(0, eqIndex)
            val value = part.substring(eqIndex + 1)
            when (key) {
                "skillMultiplier" -> value.toDoubleOrNull()?.let { multiplier = it }
                "notes" -> notes = unescapeNewlines(value)
            }
        }
        return ParsedOpponent(name = name, skillMultiplier = multiplier, notes = notes)
    }

    /**
     * Applies parsed settings map to a Group entity.
     */
    fun applyGroupSettings(group: Group, settings: Map<String, String>): Group {
        return group.copy(
            cardsPerSession = settings["cardsPerSession"]?.toIntOrNull(),
            autoShowAnswer = settings["autoShowAnswer"]?.toBooleanStrictOrNull(),
            randomizeDueCards = settings["randomizeDueCards"]?.toBooleanStrictOrNull(),
            randomizeBucketHours = settings["randomizeBucketHours"]?.toIntOrNull(),
            practiceDays = settings["practiceDays"],
            maximumInterval = settings["maximumInterval"]?.toIntOrNull(),
            fsrsRetention = settings["fsrsRetention"]?.toIntOrNull(),
            sm2IntervalModifier = settings["sm2IntervalModifier"]?.toIntOrNull()
        )
    }

    /**
     * Generates a suggested filename for export
     */
    fun generateExportFilename(groupName: String): String {
        val sanitized = groupName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(50)
        return "${sanitized}_cards.tsv.gz"
    }

    /**
     * Encodes image file to base64 string
     */
    fun encodeImageToBase64(imagePath: String): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists() || !file.canRead()) {
                return null
            }
            val bytes = file.readBytes()
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes base64 string and saves to internal storage
     * Returns the saved file path or null if failed
     */
    fun decodeImageFromBase64(context: Context, base64Data: String, subDir: String = "card_images"): String? {
        return try {
            val bytes = java.util.Base64.getDecoder().decode(base64Data)

            // Create images directory if it doesn't exist
            val imagesDir = File(context.filesDir, subDir)
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            // Generate unique filename
            val timestamp = System.currentTimeMillis()
            val fileName = "${subDir}_${timestamp}.jpg"
            val outputFile = File(imagesDir, fileName)

            // Write file
            outputFile.writeBytes(bytes)

            // Return the file path
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Wraps an OutputStream with GZIP compression
     */
    fun createCompressedOutputStream(outputStream: OutputStream): GZIPOutputStream {
        return GZIPOutputStream(outputStream)
    }

    /**
     * Wraps an InputStream with GZIP decompression
     */
    fun createDecompressedInputStream(inputStream: InputStream): GZIPInputStream {
        return GZIPInputStream(inputStream)
    }

    /**
     * Auto-detects whether the input stream is GZIP-compressed by checking magic bytes.
     * Returns a GZIPInputStream if compressed, or the original (buffered) stream if plain text.
     */
    fun smartInputStream(inputStream: InputStream): InputStream {
        val buffered = BufferedInputStream(inputStream)
        buffered.mark(2)
        val byte1 = buffered.read()
        val byte2 = buffered.read()
        buffered.reset()
        // GZIP magic number: 0x1f 0x8b
        return if (byte1 == 0x1f && byte2 == 0x8b) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }
    }

    // ========== Review History Export/Import ==========

    /**
     * Appends a REVIEW_HISTORY section to an already-open writer.
     * Each log entry is keyed by cardQuestion so it can be re-linked on import.
     * @param reviewLogs list of review logs to export
     * @param cardQuestions map of cardId -> question text
     */
    fun appendReviewHistory(
        reviewLogs: List<ReviewLog>,
        cardQuestions: Map<Long, String>,
        outputStream: OutputStream,
        opponentNamesById: Map<Long, String> = emptyMap()
    ) {
        outputStream.bufferedWriter(Charsets.UTF_8).let { writer ->
            writer.write(REVIEW_HISTORY_START)
            writer.newLine()
            writer.write(REVIEW_HISTORY_HEADERS)
            writer.newLine()
            reviewLogs.forEach { log ->
                val question = cardQuestions[log.cardId] ?: return@forEach
                val opponentName = log.opponentId?.let { opponentNamesById[it] }
                writer.write(buildReviewLogLine(log, question, opponentName))
                writer.newLine()
            }
            writer.write(REVIEW_HISTORY_END)
            writer.newLine()
            writer.flush()
        }
    }

    private fun buildReviewLogLine(log: ReviewLog, question: String, opponentName: String?): String {
        return buildString {
            append(escapeNewlines(question))
            append(DELIMITER)
            append(log.reviewTime)
            append(DELIMITER)
            append(log.grade)
            append(DELIMITER)
            append(log.algorithm)
            append(DELIMITER)
            append(escapeNewlines(log.stateBefore))
            append(DELIMITER)
            append(escapeNewlines(log.stateAfter))
            append(DELIMITER)
            append(log.scheduledDays)
            append(DELIMITER)
            append(log.elapsedDays)
            append(DELIMITER)
            append(log.groupName ?: "")
            append(DELIMITER)
            append(escapeNewlines(log.notes))
            append(DELIMITER)
            // Encode review-log images as base64, pipe-separated
            val encodedImages = log.imagePaths.split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { encodeImageToBase64(it) }
            append(encodedImages.joinToString("|"))
            append(DELIMITER)
            append(opponentName?.let { escapeNewlines(it) } ?: "")
            append(DELIMITER)
            append(log.stabilityMultiplier)
        }
    }

    /**
     * Parsed review log from the REVIEW_HISTORY section of an export file.
     * Uses cardQuestion instead of cardId (IDs differ between devices).
     */
    data class ParsedReviewLog(
        val cardQuestion: String,
        val reviewTime: Long,
        val grade: Int,
        val algorithm: String,
        val stateBefore: String,
        val stateAfter: String,
        val scheduledDays: Int,
        val elapsedDays: Int,
        val groupName: String? = null,
        val notes: String = "",
        val imageData: List<String> = emptyList(), // base64-encoded images
        val opponentName: String? = null,
        val stabilityMultiplier: Double = 1.0
    )

    /**
     * Parses the REVIEW_HISTORY section from a list of all lines in the file.
     * Returns the parsed review logs, or empty list if no history section found.
     */
    fun parseReviewHistory(lines: List<String>): List<ParsedReviewLog> {
        val startIndex = lines.indexOfFirst { it.trim() == REVIEW_HISTORY_START }
        if (startIndex < 0) return emptyList()

        val endIndex = lines.indexOfFirst { it.trim() == REVIEW_HISTORY_END }
        val historyLines = if (endIndex > startIndex) {
            lines.subList(startIndex + 1, endIndex)
        } else {
            lines.subList(startIndex + 1, lines.size)
        }

        return historyLines.mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val parts = trimmed.split(DELIMITER)
            if (parts.size < 8) return@mapNotNull null
            try {
                val imageDataRaw = parts.getOrNull(10)?.trim() ?: ""
                val imageData = if (imageDataRaw.isNotEmpty()) {
                    imageDataRaw.split("|").filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                val opponentName = parts.getOrNull(11)
                    ?.let { unescapeNewlines(it) }
                    ?.takeIf { it.isNotBlank() }
                val stabilityMultiplier = parts.getOrNull(12)?.toDoubleOrNull() ?: 1.0
                ParsedReviewLog(
                    cardQuestion = unescapeNewlines(parts[0]),
                    reviewTime = parts[1].toLong(),
                    grade = parts[2].toInt(),
                    algorithm = parts[3],
                    stateBefore = unescapeNewlines(parts[4]),
                    stateAfter = unescapeNewlines(parts[5]),
                    scheduledDays = parts[6].toInt(),
                    elapsedDays = parts[7].toInt(),
                    groupName = parts.getOrNull(8)?.let { it.ifEmpty { null } },
                    notes = parts.getOrNull(9)?.let { unescapeNewlines(it) } ?: "",
                    imageData = imageData,
                    opponentName = opponentName,
                    stabilityMultiplier = stabilityMultiplier
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Converts ParsedReviewLogs to ReviewLog entities using a question->cardId map.
     * Skips logs for cards not found in the map.
     * Decodes base64 images and saves them to internal storage.
     */
    fun parsedReviewLogsToEntities(
        context: Context,
        parsed: List<ParsedReviewLog>,
        questionToCardId: Map<String, Long>,
        opponentNameToId: Map<String, Long> = emptyMap()
    ): List<ReviewLog> {
        return parsed.mapNotNull { p ->
            val cardId = questionToCardId[p.cardQuestion] ?: return@mapNotNull null
            // Decode base64 images for review log notes
            val decodedImagePaths = p.imageData.mapNotNull { base64 ->
                decodeImageFromBase64(context, base64, "review_images")
            }
            ReviewLog(
                cardId = cardId,
                sessionId = null,
                reviewTime = p.reviewTime,
                grade = p.grade,
                algorithm = p.algorithm,
                stateBefore = p.stateBefore,
                stateAfter = p.stateAfter,
                scheduledDays = p.scheduledDays,
                elapsedDays = p.elapsedDays,
                groupName = p.groupName,
                notes = p.notes,
                imagePaths = decodedImagePaths.joinToString(","),
                opponentId = p.opponentName?.let { opponentNameToId[it] },
                stabilityMultiplier = p.stabilityMultiplier
            )
        }
    }

    /**
     * Converts ParsedReviewLogs to ReviewLog entities using a question->cardId map.
     * Skips logs for cards not found in the map.
     * Does not decode images (legacy overload for backward compatibility).
     */
    fun parsedReviewLogsToEntities(
        parsed: List<ParsedReviewLog>,
        questionToCardId: Map<String, Long>,
        opponentNameToId: Map<String, Long> = emptyMap()
    ): List<ReviewLog> {
        return parsed.mapNotNull { p ->
            val cardId = questionToCardId[p.cardQuestion] ?: return@mapNotNull null
            ReviewLog(
                cardId = cardId,
                sessionId = null,
                reviewTime = p.reviewTime,
                grade = p.grade,
                algorithm = p.algorithm,
                stateBefore = p.stateBefore,
                stateAfter = p.stateAfter,
                scheduledDays = p.scheduledDays,
                elapsedDays = p.elapsedDays,
                groupName = p.groupName,
                notes = p.notes,
                opponentId = p.opponentName?.let { opponentNameToId[it] },
                stabilityMultiplier = p.stabilityMultiplier
            )
        }
    }

    // ========== CSV Import/Export ==========

    private const val CSV_DELIMITER = ","
    private const val CSV_HEADER_CONCEPT = "Concept"
    private const val CSV_HEADER_DESCRIPTION = "Description"

    private const val CSV_HEADER_IMAGES = "Images"
    private const val CSV_IMAGE_SEPARATOR = "|"

    /**
     * Parses a CSV input stream into a list of ParsedCard objects.
     * Expected format: Concept,Description,Images
     * The Images column contains a pipe-separated list of base64-encoded files.
     * Fields may be quoted with double quotes per RFC 4180.
     * Returns pair of (valid cards, error messages).
     */
    fun parseCsvCards(inputStream: InputStream): Pair<List<ParsedCard>, List<String>> {
        val cards = mutableListOf<ParsedCard>()
        val errors = mutableListOf<String>()

        try {
            val content = inputStream.bufferedReader(Charsets.UTF_8).readText()
            val lines = parseCsvLines(content)

            if (lines.isEmpty()) {
                return Pair(emptyList(), emptyList())
            }

            // Require that the first row is a header with 'Concept' as the first column
            val firstRow = lines[0]
            val hasHeader = firstRow.isNotEmpty() &&
                firstRow[0].trim().equals(CSV_HEADER_CONCEPT, ignoreCase = true)

            if (!hasHeader) {
                return Pair(
                    emptyList(),
                    listOf("Invalid CSV format: first row must have '$CSV_HEADER_CONCEPT' as the first column header")
                )
            }

            val dataLines = lines.drop(1)

            dataLines.forEachIndexed { index, fields ->
                val lineNumber = if (hasHeader) index + 2 else index + 1

                try {
                    if (fields.isEmpty() || (fields.size == 1 && fields[0].isBlank())) {
                        return@forEachIndexed // Skip empty lines
                    }

                    if (fields.size < 2) {
                        errors.add("Line $lineNumber: Missing description (need at least Concept and Description columns)")
                        return@forEachIndexed
                    }

                    val concept = fields[0].trim()
                    val description = fields[1].trim()

                    if (concept.isBlank()) {
                        errors.add("Line $lineNumber: Empty concept")
                        return@forEachIndexed
                    }

                    // Third column (if present) is pipe-separated base64 images
                    val imageData = if (fields.size >= 3 && fields[2].trim().isNotEmpty()) {
                        fields[2].trim().split(CSV_IMAGE_SEPARATOR)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    } else {
                        emptyList()
                    }

                    cards.add(
                        ParsedCard(
                            concept = concept,
                            answer = description,
                            lineNumber = lineNumber,
                            imageData = imageData
                        )
                    )
                } catch (e: Exception) {
                    errors.add("Line $lineNumber: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to read CSV file: ${e.message}")
        }

        return Pair(cards, errors)
    }

    /**
     * Exports cards to CSV format with columns: Concept, Description, Images.
     * The Images column contains a pipe-separated list of base64-encoded files.
     */
    fun exportCardsToCsv(
        cardsWithGroups: List<CardWithGroupNames>,
        outputStream: OutputStream
    ): ExportResult {
        return try {
            val anyCardHasImages = cardsWithGroups.any { (card, _) ->
                card.imagePaths.isNotEmpty()
            }

            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                // Write header row
                val headerParts = mutableListOf(CSV_HEADER_CONCEPT, CSV_HEADER_DESCRIPTION)
                if (anyCardHasImages) {
                    headerParts.add(CSV_HEADER_IMAGES)
                }
                writer.write(headerParts.joinToString(CSV_DELIMITER) { escapeCsvField(it) })
                writer.newLine()

                // Write data rows
                cardsWithGroups.forEach { (card, _) ->
                    val fields = mutableListOf(
                        escapeCsvField(card.question),
                        escapeCsvField(card.answer)
                    )

                    if (anyCardHasImages) {
                        // Encode all images and join with pipe separator
                        val encodedImages = card.imagePaths.mapNotNull { encodeImageToBase64(it) }
                        val imagesField = encodedImages.joinToString(CSV_IMAGE_SEPARATOR)
                        fields.add(escapeCsvField(imagesField))
                    }

                    writer.write(fields.joinToString(CSV_DELIMITER))
                    writer.newLine()
                }
            }
            ExportResult.Success(cardsWithGroups.size)
        } catch (e: Exception) {
            ExportResult.Error("Failed to write CSV file: ${e.message}")
        }
    }

    /**
     * Generates a suggested filename for CSV export
     */
    fun generateCsvExportFilename(groupName: String): String {
        val sanitized = groupName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(50)
        return "${sanitized}_cards.csv"
    }

    /**
     * Derives a group name from a filename.
     * e.g. "parries_cards.csv" -> "parries cards", "My_Techniques.csv" -> "My Techniques"
     */
    fun deriveGroupNameFromFilename(filename: String): String {
        // Remove file extension(s) - loop until no more known extensions remain
        var name = filename
        val extensions = listOf(".csv", ".tsv", ".gz", ".txt")
        var changed = true
        while (changed) {
            changed = false
            for (ext in extensions) {
                if (name.endsWith(ext, ignoreCase = true)) {
                    name = name.dropLast(ext.length)
                    changed = true
                }
            }
        }
        // Remove trailing "_cards" if present
        if (name.endsWith("_cards", ignoreCase = true)) {
            name = name.dropLast("_cards".length)
        }
        // Replace underscores and hyphens with spaces
        name = name.replace('_', ' ').replace('-', ' ')
        // Trim and collapse multiple spaces
        name = name.trim().replace(Regex("\\s+"), " ")
        // Capitalize first letter of each word
        return name.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    /**
     * Escapes a field for CSV output per RFC 4180.
     * Fields containing commas, double quotes, or newlines are enclosed in double quotes.
     * Double quotes within fields are escaped as "".
     */
    fun escapeCsvField(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

    /**
     * Parses CSV content into a list of rows, where each row is a list of field values.
     * Handles quoted fields with embedded commas, newlines, and escaped quotes per RFC 4180.
     */
    fun parseCsvLines(content: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        while (i < content.length) {
            val c = content[i]

            when {
                inQuotes -> {
                    if (c == '"') {
                        // Check for escaped quote ""
                        if (i + 1 < content.length && content[i + 1] == '"') {
                            currentField.append('"')
                            i += 2
                            continue
                        } else {
                            // End of quoted field
                            inQuotes = false
                            i++
                            continue
                        }
                    } else {
                        currentField.append(c)
                    }
                }
                c == '"' && currentField.isEmpty() -> {
                    // Start of quoted field
                    inQuotes = true
                }
                c == ',' -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                }
                c == '\r' -> {
                    // Handle \r\n or standalone \r
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    if (currentRow.any { it.isNotEmpty() } || currentRow.size > 1) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                    if (i + 1 < content.length && content[i + 1] == '\n') {
                        i++ // Skip the \n in \r\n
                    }
                }
                c == '\n' -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    if (currentRow.any { it.isNotEmpty() } || currentRow.size > 1) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }

        // Handle last field/row
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotEmpty() } || currentRow.size > 1) {
                rows.add(currentRow.toList())
            }
        }

        return rows
    }
}
