// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

// What an export means by the YAML it writes: the document declared as
// classes, and the conversions between those classes and the entities on one
// side and the ParsedCard records the import pipeline takes on the other.
//
// The YAML itself is kaml's -- reading, writing, quoting, block scalars, the
// lot. What is here is only the shape:
//
//     version: 5
//     groups:                    # only those with settings of their own
//       - name: Footwork
//         cardsPerSession: 20
//     opponents:
//       - name: Alex
//         skillMultiplier: 1.25
//     cards:
//       - question: Parry four
//         answer: |-
//           Blade to the inside line,
//           point high.
//         images: ["<base64>"]
//         groups:
//           - Footwork
//         state:                 # absent on a card nobody has practised
//           nextReview: 1774000000000
//           ...
//         groupStates:           # only for groups that learn independently
//           - group: Footwork
//             ...
//     reviewHistory:
//       - card: Parry four
//         reviewTime: 1774000000000
//         grade: 3
//         ...
//
// A card is written once and carries its states, where the tab-separated
// format it replaces wrote a whole row per state -- and with it another copy
// of every photograph on that card, base64 and all. A card in four groups
// that learn independently used to inline its pictures five times.
//
// Every field with a sensible default is left out when it holds that default,
// so a card nobody has practised is three lines and a backup is a good deal
// smaller than the shape above suggests. Nothing is lost by it: what is
// missing reads back as the default it was.

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.MultiLineStringStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.ReviewLog
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable

// ==========================================================================
// The document
// ==========================================================================

@Serializable
internal data class ArchiveDocument(
    /**
     * Read, never written: the writer omits it because it equals the default,
     * and the export puts the line in itself so that a file always says which
     * version it is even when nothing else about it is unusual.
     */
    val version: Int = ArchiveYaml.VERSION,
    val groups: List<ArchiveGroup> = emptyList(),
    val opponents: List<ArchiveOpponent> = emptyList(),
    val cards: List<ArchiveCard> = emptyList(),
    val reviewHistory: List<ArchiveReviewLog> = emptyList()
)

@Serializable
internal data class ArchiveGroup(
    val name: String,
    val cardsPerSession: Int? = null,
    val autoShowAnswer: Boolean? = null,
    val randomizeDueCards: Boolean? = null,
    val randomizeBucketHours: Int? = null,
    val practiceDays: String? = null,
    val maximumInterval: Int? = null,
    val fsrsRetention: Int? = null,
    val fsrsEnableFuzzing: Boolean? = null
)

@Serializable
internal data class ArchiveOpponent(
    val name: String,
    val skillMultiplier: Double = 1.0,
    val notes: String = ""
)

@Serializable
internal data class ArchiveCard(
    val question: String? = null,
    /** What a CSV of this app's calls the same column; accepted, never written. */
    val concept: String? = null,
    val answer: String? = null,
    val description: String? = null,
    val images: List<String> = emptyList(),
    val groups: List<String> = emptyList(),
    val state: ArchiveState? = null,
    val groupStates: List<ArchiveGroupState> = emptyList()
) {
    val questionText: String get() = (question ?: concept).orEmpty().trim()
    val answerText: String get() = (answer ?: description).orEmpty()
}

@Serializable
internal data class ArchiveState(
    val nextReview: Long = 0L,
    val lastReview: Long = 0L,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val fsrsState: String = "NEW",
    val reps: Int = 0,
    val lapses: Int = 0,
    val scheduledDays: Int = 0,
    val elapsedDays: Int = 0
)

@Serializable
internal data class ArchiveGroupState(
    val group: String,
    val nextReview: Long = 0L,
    val lastReview: Long = 0L,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val fsrsState: String = "NEW",
    val reps: Int = 0,
    val lapses: Int = 0,
    val scheduledDays: Int = 0,
    val elapsedDays: Int = 0
)

@Serializable
internal data class ArchiveReviewLog(
    val card: String,
    val reviewTime: Long,
    val grade: Int,
    val algorithm: String = "FSRS",
    val stateBefore: String = "",
    val stateAfter: String = "",
    val scheduledDays: Int = 0,
    val elapsedDays: Int = 0,
    val group: String? = null,
    val notes: String = "",
    val opponent: String? = null,
    val stabilityMultiplier: Double = 1.0,
    val images: List<String> = emptyList()
)

// ==========================================================================
// Reading and writing it
// ==========================================================================

internal object ArchiveYaml {

    /**
     * The format version this app writes.
     *
     * Five rather than one because it continues the tab-separated format's
     * count: V1 to V4 are the layouts still read by the legacy importer.
     */
    const val VERSION = 5

    const val KEY_CARDS = "cards"
    const val KEY_VERSION = "version"
    const val KEY_REVIEW_HISTORY = "reviewHistory"

    /** The state context a card's own state is filed under. */
    const val GLOBAL_STATE = "GLOBAL"

    /**
     * How the document is written and read.
     *
     * Every setting here is load-bearing:
     *
     *   encodeDefaults          leaves out what a field already holds, so a
     *                           card nobody has practised is three lines
     *   strictMode              a key this version does not know is ignored
     *                           rather than fatal, so a file from a later
     *                           version still imports what it can
     *   breakScalarsAt          an inlined photograph is one base64 line;
     *                           wrapping it would only make the file longer
     *   singleLineStringStyle   every string quoted. PlainExceptAmbiguous
     *                           reads better and is judged against YAML 1.2,
     *                           where `off`, `12:30` and `2026-08-27` are all
     *                           strings -- but a 1.1 reader, which is what
     *                           PyYAML and Ruby's Psych still are, makes them
     *                           a boolean, a number of seconds and a date. An
     *                           answer of "no" or "12:30" is an ordinary card,
     *                           so the quotes stay
     *   multiLineStringStyle    `|-` for anything with a newline in it, so a
     *                           long answer reads as the prose it is
     *   sequenceBlockIndent     entries indented under their key rather than
     *                           level with it
     *   codePointLimit          snakeyaml refuses a document over 3 MB by
     *                           default, which any export carrying two
     *                           photographs already is
     */
    val format = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            strictMode = false,
            breakScalarsAt = Int.MAX_VALUE,
            singleLineStringStyle = SingleLineStringStyle.DoubleQuoted,
            multiLineStringStyle = MultiLineStringStyle.Literal,
            sequenceBlockIndent = 2,
            codePointLimit = Int.MAX_VALUE
        )
    )

    // ---------- entities to document ----------

    fun groupNode(group: Group) = ArchiveGroup(
        name = group.name,
        cardsPerSession = group.cardsPerSession,
        autoShowAnswer = group.autoShowAnswer,
        randomizeDueCards = group.randomizeDueCards,
        randomizeBucketHours = group.randomizeBucketHours,
        practiceDays = group.practiceDays,
        maximumInterval = group.maximumInterval,
        fsrsRetention = group.fsrsRetention,
        fsrsEnableFuzzing = group.fsrsEnableFuzzing
    )

    fun opponentNode(opponent: Opponent) = ArchiveOpponent(
        name = opponent.name,
        skillMultiplier = opponent.skillMultiplier,
        notes = opponent.notes
    )

    /**
     * One card, its pictures, the groups it is in, and every learning state
     * it holds.
     *
     * The state block is left out when the card holds nothing but defaults --
     * which is what a card nobody has practised holds -- because writing nine
     * zeroes says no more than leaving them out does.
     */
    fun cardNode(
        card: Card,
        groupNames: List<String>,
        groupStates: Map<String, CardGroupLearningState>,
        images: ImageReader
    ): ArchiveCard {
        val state = ArchiveState(
            nextReview = card.nextReview,
            lastReview = card.lastReview,
            stability = card.fsrsStability,
            difficulty = card.fsrsDifficulty,
            fsrsState = card.fsrsState,
            reps = card.fsrsReps,
            lapses = card.fsrsLapses,
            scheduledDays = card.fsrsScheduledDays,
            elapsedDays = card.fsrsElapsedDays
        )
        return ArchiveCard(
            question = card.question,
            answer = card.answer.ifEmpty { null },
            images = card.imagePaths.mapNotNull { CardImportExport.encodeImageToBase64(it, images) },
            groups = groupNames.filter { it.isNotBlank() },
            state = state.takeIf { it != ArchiveState() },
            groupStates = groupStates.map { (name, learning) ->
                ArchiveGroupState(
                    group = name,
                    nextReview = learning.nextReview,
                    lastReview = learning.lastReview,
                    stability = learning.fsrsStability,
                    difficulty = learning.fsrsDifficulty,
                    fsrsState = learning.fsrsState,
                    reps = learning.fsrsReps,
                    lapses = learning.fsrsLapses,
                    scheduledDays = learning.fsrsScheduledDays,
                    elapsedDays = learning.fsrsElapsedDays
                )
            }
        )
    }

    /** A card written without its pictures, for the export that omits them. */
    fun cardNodeWithoutImages(card: Card, groupNames: List<String>): ArchiveCard =
        cardNode(card.copy(imagePaths = emptyList()), groupNames, emptyMap(), NoImages)

    private val NoImages = ImageReader { null }

    /**
     * One review log, keyed by the question of the card it belongs to.
     *
     * By question and not by id, as it always has been: ids are this device's,
     * and an export is read on another one.
     */
    fun reviewLogNode(
        log: ReviewLog,
        question: String,
        opponentName: String?,
        images: ImageReader
    ) = ArchiveReviewLog(
        card = question,
        reviewTime = log.reviewTime,
        grade = log.grade,
        algorithm = log.algorithm,
        stateBefore = log.stateBefore,
        stateAfter = log.stateAfter,
        scheduledDays = log.scheduledDays,
        elapsedDays = log.elapsedDays,
        group = log.groupName,
        notes = log.notes,
        opponent = opponentName,
        stabilityMultiplier = log.stabilityMultiplier,
        images = log.imagePaths.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { CardImportExport.encodeImageToBase64(it, images) }
    )

    // ---------- document to the import pipeline ----------

    /**
     * Whether a parsed document is one of ours.
     *
     * Any of the three top-level keys will do. A file with none of them is
     * valid YAML that says nothing about cards, and saying so is a better
     * error than importing nothing and calling the file empty.
     */
    fun isArchive(document: YamlNode): Boolean = when (document) {
        is YamlList -> true
        is YamlMap ->
            document.get<YamlNode>(KEY_CARDS) != null ||
                document.get<YamlNode>(KEY_VERSION) != null ||
                document.get<YamlNode>(KEY_REVIEW_HISTORY) != null
        else -> false
    }

    /** The card entries, wherever they are: under `cards:`, or the document itself. */
    fun cardEntries(document: YamlNode): List<YamlNode> = when (document) {
        is YamlList -> document.items
        is YamlMap -> document.get<YamlList>(KEY_CARDS)?.items.orEmpty()
        else -> emptyList()
    }

    /**
     * Decodes the entries of one section, keeping a bad entry from taking the
     * rest of the file with it.
     *
     * kaml would refuse the whole document over a single malformed card,
     * which is the wrong answer for a file someone has edited by hand: the
     * other three hundred cards are still perfectly good. Decoding an entry
     * at a time costs a node walk and turns that into one line of the error
     * list, which is what the import screen already knows how to show.
     */
    fun <T> decodeEntries(
        entries: List<YamlNode>,
        serializer: DeserializationStrategy<T>,
        errors: MutableList<String>
    ): List<T> = entries.mapNotNull { entry ->
        try {
            format.decodeFromYamlNode(serializer, entry)
        } catch (e: Exception) {
            errors.add("Line ${entry.location.line}: ${reason(e)}")
            null
        }
    }

    fun <T> decodeSection(
        document: YamlNode,
        key: String,
        serializer: DeserializationStrategy<T>,
        errors: MutableList<String>
    ): List<T> = decodeEntries(
        (document as? YamlMap)?.get<YamlList>(key)?.items.orEmpty(),
        serializer,
        errors
    )

    /** kaml's messages carry the location on a second line; the caller adds its own. */
    private fun reason(e: Exception): String =
        (e.message ?: "could not be read").lines().first().trim()

    fun groupSettings(group: ArchiveGroup): Map<String, String> = buildMap {
        group.cardsPerSession?.let { put("cardsPerSession", it.toString()) }
        group.autoShowAnswer?.let { put("autoShowAnswer", it.toString()) }
        group.randomizeDueCards?.let { put("randomizeDueCards", it.toString()) }
        group.randomizeBucketHours?.let { put("randomizeBucketHours", it.toString()) }
        group.practiceDays?.let { put("practiceDays", it) }
        group.maximumInterval?.let { put("maximumInterval", it.toString()) }
        group.fsrsRetention?.let { put("fsrsRetention", it.toString()) }
        group.fsrsEnableFuzzing?.let { put("fsrsEnableFuzzing", it.toString()) }
    }

    fun parsedOpponent(opponent: ArchiveOpponent) = CardImportExport.ParsedOpponent(
        name = opponent.name.trim(),
        skillMultiplier = opponent.skillMultiplier,
        notes = opponent.notes
    )

    fun parsedReviewLog(log: ArchiveReviewLog) = CardImportExport.ParsedReviewLog(
        cardQuestion = log.card,
        reviewTime = log.reviewTime,
        grade = log.grade,
        algorithm = log.algorithm.ifBlank { "FSRS" },
        stateBefore = log.stateBefore,
        stateAfter = log.stateAfter,
        scheduledDays = log.scheduledDays,
        elapsedDays = log.elapsedDays,
        groupName = log.group?.takeIf { it.isNotBlank() },
        notes = log.notes,
        imageData = log.images,
        opponentName = log.opponent?.takeIf { it.isNotBlank() },
        stabilityMultiplier = log.stabilityMultiplier
    )

    /**
     * One card, flattened into the one-record-per-state shape the import
     * pipeline reads.
     *
     * The document nests -- a card holds its states -- and the repositories
     * take a flat list where the same question appears once for its own state
     * and once more for each group that learns it independently. Flattening
     * here rather than reshaping the repositories keeps this change to the
     * file format, which is what it is.
     *
     * Pictures ride on the card's own record only. The group records are read
     * for their scheduling and nothing else, which is why the format never
     * had to repeat them.
     */
    fun parsedCards(card: ArchiveCard, lineNumber: Int): List<ParsedCard> {
        val question = card.questionText
        val answer = card.answerText
        // A blank group name is not a group. Left in, an import would create
        // one called "" and quietly file cards under it.
        val groups = card.groups.filter { it.isNotBlank() }
        val own = when {
            card.state != null ->
                parsedCard(question, answer, lineNumber, card.images, groups, GLOBAL_STATE, card.state)
            // No state, but groups to be put in: the record still needs a
            // state context, or the importer takes the file for a bare
            // question-and-answer list and drops the groups. Every
            // scheduling field stays null, which is what a card nobody has
            // practised looks like.
            groups.isNotEmpty() -> ParsedCard(
                concept = question,
                answer = answer,
                lineNumber = lineNumber,
                imageData = card.images,
                stateContext = GLOBAL_STATE,
                groupNames = groups
            )
            else -> ParsedCard(
                concept = question,
                answer = answer,
                lineNumber = lineNumber,
                imageData = card.images
            )
        }
        return listOf(own) + card.groupStates.filter { it.group.isNotBlank() }.map { groupState ->
            parsedCard(
                question, answer, lineNumber, emptyList(), listOf(groupState.group),
                groupState.group,
                ArchiveState(
                    nextReview = groupState.nextReview,
                    lastReview = groupState.lastReview,
                    stability = groupState.stability,
                    difficulty = groupState.difficulty,
                    fsrsState = groupState.fsrsState,
                    reps = groupState.reps,
                    lapses = groupState.lapses,
                    scheduledDays = groupState.scheduledDays,
                    elapsedDays = groupState.elapsedDays
                )
            )
        }
    }

    private fun parsedCard(
        question: String,
        answer: String,
        lineNumber: Int,
        imageData: List<String>,
        groupNames: List<String>,
        stateContext: String,
        state: ArchiveState
    ) = ParsedCard(
        concept = question,
        answer = answer,
        lineNumber = lineNumber,
        imageData = imageData,
        stateContext = stateContext,
        nextReview = state.nextReview,
        lastReview = state.lastReview,
        fsrsStability = state.stability,
        fsrsDifficulty = state.difficulty,
        fsrsState = state.fsrsState,
        fsrsReps = state.reps,
        fsrsLapses = state.lapses,
        fsrsScheduledDays = state.scheduledDays,
        fsrsElapsedDays = state.elapsedDays,
        groupNames = groupNames
    )
}
