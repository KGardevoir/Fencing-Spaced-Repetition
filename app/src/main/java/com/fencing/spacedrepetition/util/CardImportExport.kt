package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import java.io.InputStream
import java.io.OutputStream

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
    val question: String,
    val answer: String,
    val lineNumber: Int,
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
    private const val HEADER_MARKER_V1 = "#FSR_EXPORT_V1"
    private const val HEADER_MARKER_V2 = "#FSR_EXPORT_V2"

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

    /**
     * Parses a TSV input stream into a list of ParsedCard objects.
     * Supports both simple (question\tanswer) and full export formats (V1 and V2).
     * Returns pair of (valid cards, error messages)
     */
    fun parseCards(inputStream: InputStream): Pair<List<ParsedCard>, List<String>> {
        val cards = mutableListOf<ParsedCard>()
        val errors = mutableListOf<String>()

        try {
            val lines = inputStream.bufferedReader(Charsets.UTF_8).readLines()
            if (lines.isEmpty()) {
                return Pair(emptyList(), emptyList())
            }

            // Detect format version
            val firstLine = lines.firstOrNull() ?: ""
            val formatVersion = when {
                firstLine.startsWith(HEADER_MARKER_V2) -> 2
                firstLine.startsWith(HEADER_MARKER_V1) -> 1
                else -> 0 // Simple format
            }

            val dataLines = if (formatVersion > 0) lines.drop(1) else lines

            dataLines.forEachIndexed { index, line ->
                val lineNumber = if (formatVersion > 0) index + 2 else index + 1
                val trimmedLine = line.trim()

                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    return@forEachIndexed // Skip empty lines and comments
                }

                try {
                    val parsedCard = when (formatVersion) {
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

        return Pair(cards, errors)
    }

    private fun parseSimpleLine(line: String, lineNumber: Int): ParsedCard? {
        val parts = line.split(DELIMITER, limit = 2)

        return when {
            parts.size < 2 -> throw IllegalArgumentException("Missing answer (no tab delimiter found)")
            parts[0].isBlank() -> throw IllegalArgumentException("Empty question")
            else -> ParsedCard(
                question = unescapeNewlines(parts[0].trim()),
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

        val question = unescapeNewlines(parts.getOrNull(COL_V1_QUESTION)?.trim() ?: "")
        val answer = unescapeNewlines(parts.getOrNull(COL_V1_ANSWER)?.trim() ?: "")

        if (question.isBlank()) throw IllegalArgumentException("Empty question")

        // If only 2 columns, treat as simple format
        if (parts.size == 2) {
            return ParsedCard(question = question, answer = answer, lineNumber = lineNumber)
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
            question = question,
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

        val question = unescapeNewlines(parts.getOrNull(COL_V2_QUESTION)?.trim() ?: "")
        val answer = unescapeNewlines(parts.getOrNull(COL_V2_ANSWER)?.trim() ?: "")

        if (question.isBlank()) throw IllegalArgumentException("Empty question")

        // If only 2 columns, treat as simple format
        if (parts.size == 2) {
            return ParsedCard(question = question, answer = answer, lineNumber = lineNumber)
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
            question = question,
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
     * Exports cards with group-specific learning states to V2 format TSV
     */
    fun exportCardsWithGroupStates(
        cardsWithStates: List<CardWithGroupStates>,
        outputStream: OutputStream
    ): ExportResult {
        return try {
            var rowCount = 0
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                // Write format marker (V2)
                writer.write(HEADER_MARKER_V2)
                writer.newLine()

                // Write column headers
                writer.write(COLUMN_HEADERS_V2)
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
            }
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
     * Converts a ParsedCard to a Card entity
     */
    fun parsedCardToCard(parsed: ParsedCard): Card {
        val now = System.currentTimeMillis()

        return if (parsed.hasFullState) {
            Card(
                question = parsed.question,
                answer = parsed.answer,
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
                question = parsed.question,
                answer = parsed.answer,
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
     * Generates a suggested filename for export
     */
    fun generateExportFilename(groupName: String): String {
        val sanitized = groupName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(50)
        return "${sanitized}_cards.txt"
    }
}
