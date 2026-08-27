// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

// What an export means by the YAML it writes: which keys there are, what goes
// under each of them, and how to read the whole thing back.
//
// The general-purpose half -- how a mapping is written and how a document is
// parsed -- is in Yaml.kt and knows nothing about cards. This file is the
// other half, and knows nothing about streams or storage: it turns entities
// into nodes and nodes back into the ParsedCard records the import pipeline
// has always taken.
//
// The document:
//
//     version: 5
//     groups:                    # only those with settings of their own
//       - name: Footwork
//         cardsPerSession: 20
//     opponents:
//       - name: Alex
//         skillMultiplier: 1.2
//     cards:
//       - question: Parry four
//         answer: |-
//           Blade to the inside line,
//           point high.
//         images: ["<base64>"]
//         groups: [Footwork]
//         state:
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

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.ReviewLog

internal object ArchiveYaml {

    /**
     * The format version this app writes.
     *
     * Five rather than one because it continues the tab-separated format's
     * count: V1 to V4 are the layouts still read by the legacy importer, and
     * a file saying `version: 5` is unambiguous about which of the two shapes
     * it is even without looking at the rest of it.
     */
    const val VERSION = 5

    const val KEY_VERSION = "version"
    const val KEY_GROUPS = "groups"
    const val KEY_OPPONENTS = "opponents"
    const val KEY_CARDS = "cards"
    const val KEY_REVIEW_HISTORY = "reviewHistory"

    const val KEY_NAME = "name"
    const val KEY_SKILL_MULTIPLIER = "skillMultiplier"
    const val KEY_NOTES = "notes"

    const val KEY_QUESTION = "question"
    const val KEY_ANSWER = "answer"
    const val KEY_IMAGES = "images"
    const val KEY_STATE = "state"
    const val KEY_GROUP_STATES = "groupStates"
    const val KEY_GROUP = "group"

    const val KEY_NEXT_REVIEW = "nextReview"
    const val KEY_LAST_REVIEW = "lastReview"
    const val KEY_STABILITY = "stability"
    const val KEY_DIFFICULTY = "difficulty"
    const val KEY_FSRS_STATE = "fsrsState"
    const val KEY_REPS = "reps"
    const val KEY_LAPSES = "lapses"
    const val KEY_SCHEDULED_DAYS = "scheduledDays"
    const val KEY_ELAPSED_DAYS = "elapsedDays"

    const val KEY_CARD = "card"
    const val KEY_REVIEW_TIME = "reviewTime"
    const val KEY_GRADE = "grade"
    const val KEY_ALGORITHM = "algorithm"
    const val KEY_STATE_BEFORE = "stateBefore"
    const val KEY_STATE_AFTER = "stateAfter"
    const val KEY_OPPONENT = "opponent"
    const val KEY_STABILITY_MULTIPLIER = "stabilityMultiplier"

    /**
     * The words a hand-written file is allowed to use instead.
     *
     * A CSV of this app's calls the two columns Concept and Description, and
     * someone retyping one as YAML will use those words. Accepting them costs
     * two lookups and saves an import that would otherwise fail with "Empty
     * question" on every row.
     */
    private const val KEY_CONCEPT = "concept"
    private const val KEY_DESCRIPTION = "description"

    /** What a review log says it was scheduled by when the file does not. */
    private const val DEFAULT_ALGORITHM = "FSRS"

    /** The state context a card's own state is filed under. */
    const val GLOBAL_STATE = "GLOBAL"

    /** For the export path that carries no pictures. */
    private val NoImages = ImageReader { null }

    // ======================================================================
    // Writing
    // ======================================================================

    fun versionNode(): YamlMapping =
        YamlMapping(listOf(KEY_VERSION to yamlNumber(VERSION)))

    fun groupNode(group: Group): YamlMapping {
        val entries = mutableListOf<Pair<String, YamlNode>>(KEY_NAME to yamlText(group.name))
        group.cardsPerSession?.let { entries.add("cardsPerSession" to yamlNumber(it)) }
        group.autoShowAnswer?.let { entries.add("autoShowAnswer" to yamlBoolean(it)) }
        group.randomizeDueCards?.let { entries.add("randomizeDueCards" to yamlBoolean(it)) }
        group.randomizeBucketHours?.let { entries.add("randomizeBucketHours" to yamlNumber(it)) }
        group.practiceDays?.let { entries.add("practiceDays" to yamlText(it)) }
        group.maximumInterval?.let { entries.add("maximumInterval" to yamlNumber(it)) }
        group.fsrsRetention?.let { entries.add("fsrsRetention" to yamlNumber(it)) }
        group.fsrsEnableFuzzing?.let { entries.add("fsrsEnableFuzzing" to yamlBoolean(it)) }
        return YamlMapping(entries)
    }

    fun opponentNode(opponent: Opponent): YamlMapping {
        val entries = mutableListOf<Pair<String, YamlNode>>(
            KEY_NAME to yamlText(opponent.name),
            KEY_SKILL_MULTIPLIER to yamlNumber(opponent.skillMultiplier)
        )
        if (opponent.notes.isNotBlank()) entries.add(KEY_NOTES to yamlText(opponent.notes))
        return YamlMapping(entries)
    }

    /**
     * One card, its pictures, the groups it is in, and every learning state
     * it holds.
     *
     * [groupStates] is empty for the export that carries no per-group states,
     * and [images] is [NoImages] for the one that carries no pictures -- see
     * [cardNodeWithoutImages].
     */
    fun cardNode(
        card: Card,
        groupNames: List<String>,
        groupStates: Map<String, CardGroupLearningState>,
        images: ImageReader
    ): YamlMapping {
        val entries = mutableListOf<Pair<String, YamlNode>>(
            KEY_QUESTION to yamlText(card.question),
            KEY_ANSWER to yamlText(card.answer)
        )
        val encoded = card.imagePaths.mapNotNull { CardImportExport.encodeImageToBase64(it, images) }
        if (encoded.isNotEmpty()) {
            entries.add(KEY_IMAGES to YamlSequence(encoded.map { yamlText(it) }))
        }
        if (groupNames.isNotEmpty()) {
            entries.add(KEY_GROUPS to YamlSequence(groupNames.map { yamlText(it) }, flow = true))
        }
        entries.add(
            KEY_STATE to stateNode(
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
        )
        if (groupStates.isNotEmpty()) {
            entries.add(
                KEY_GROUP_STATES to YamlSequence(
                    groupStates.map { (name, state) -> groupStateNode(name, state) }
                )
            )
        }
        return YamlMapping(entries)
    }

    /** A card written without its pictures, for the export that omits them. */
    fun cardNodeWithoutImages(card: Card, groupNames: List<String>): YamlMapping =
        cardNode(card.copy(imagePaths = emptyList()), groupNames, emptyMap(), NoImages)

    private fun groupStateNode(
        groupName: String,
        state: CardGroupLearningState
    ): YamlMapping = YamlMapping(
        listOf<Pair<String, YamlNode>>(KEY_GROUP to yamlText(groupName)) +
            stateNode(
                nextReview = state.nextReview,
                lastReview = state.lastReview,
                stability = state.fsrsStability,
                difficulty = state.fsrsDifficulty,
                fsrsState = state.fsrsState,
                reps = state.fsrsReps,
                lapses = state.fsrsLapses,
                scheduledDays = state.fsrsScheduledDays,
                elapsedDays = state.fsrsElapsedDays
            ).entries
    )

    private fun stateNode(
        nextReview: Long,
        lastReview: Long,
        stability: Double,
        difficulty: Double,
        fsrsState: String,
        reps: Int,
        lapses: Int,
        scheduledDays: Int,
        elapsedDays: Int
    ): YamlMapping = YamlMapping(
        listOf(
            KEY_NEXT_REVIEW to yamlNumber(nextReview),
            KEY_LAST_REVIEW to yamlNumber(lastReview),
            KEY_STABILITY to yamlNumber(stability),
            KEY_DIFFICULTY to yamlNumber(difficulty),
            KEY_FSRS_STATE to yamlText(fsrsState),
            KEY_REPS to yamlNumber(reps),
            KEY_LAPSES to yamlNumber(lapses),
            KEY_SCHEDULED_DAYS to yamlNumber(scheduledDays),
            KEY_ELAPSED_DAYS to yamlNumber(elapsedDays)
        )
    )

    /**
     * One review log, keyed by the question of the card it belongs to.
     *
     * By question and not by id, as it always has been: ids are this
     * device's, and an export is read on another one.
     */
    fun reviewLogNode(
        log: ReviewLog,
        question: String,
        opponentName: String?,
        images: ImageReader
    ): YamlMapping {
        val entries = mutableListOf<Pair<String, YamlNode>>(
            KEY_CARD to yamlText(question),
            KEY_REVIEW_TIME to yamlNumber(log.reviewTime),
            KEY_GRADE to yamlNumber(log.grade),
            KEY_ALGORITHM to yamlText(log.algorithm),
            KEY_STATE_BEFORE to yamlText(log.stateBefore),
            KEY_STATE_AFTER to yamlText(log.stateAfter),
            KEY_SCHEDULED_DAYS to yamlNumber(log.scheduledDays),
            KEY_ELAPSED_DAYS to yamlNumber(log.elapsedDays)
        )
        log.groupName?.let { entries.add(KEY_GROUP to yamlText(it)) }
        if (log.notes.isNotBlank()) entries.add(KEY_NOTES to yamlText(log.notes))
        opponentName?.let { entries.add(KEY_OPPONENT to yamlText(it)) }
        entries.add(KEY_STABILITY_MULTIPLIER to yamlNumber(log.stabilityMultiplier))

        val encoded = log.imagePaths.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { CardImportExport.encodeImageToBase64(it, images) }
        if (encoded.isNotEmpty()) {
            entries.add(KEY_IMAGES to YamlSequence(encoded.map { yamlText(it) }))
        }
        return YamlMapping(entries)
    }

    // ======================================================================
    // Reading
    // ======================================================================

    /**
     * Whether a parsed document is one of ours.
     *
     * Any of the three top-level keys will do. A file with none of them is
     * valid YAML that says nothing about cards, and saying so is a better
     * error than importing nothing and calling the file empty.
     */
    fun isArchive(document: YamlNode): Boolean = when (document) {
        is YamlSequence -> true
        is YamlMapping ->
            document[KEY_CARDS] != null ||
                document[KEY_VERSION] != null ||
                document[KEY_REVIEW_HISTORY] != null
        else -> false
    }

    /** The card list, wherever it is: under `cards:`, or the document itself. */
    private fun cardNodes(document: YamlNode): List<YamlNode> = when (document) {
        is YamlSequence -> document.items
        is YamlMapping -> document[KEY_CARDS].itemsOrEmpty()
        else -> emptyList()
    }

    private fun section(document: YamlNode, key: String): List<YamlMapping> =
        (document as? YamlMapping)?.get(key).mappingsOrEmpty()

    fun readGroupSettings(document: YamlNode): Map<String, Map<String, String>> {
        val settings = LinkedHashMap<String, Map<String, String>>()
        section(document, KEY_GROUPS).forEach { node ->
            val name = node.text(KEY_NAME)?.trim().orEmpty()
            if (name.isEmpty()) return@forEach
            val values = LinkedHashMap<String, String>()
            node.entries.forEach { (key, value) ->
                if (key != KEY_NAME) value.textOrNull()?.let { values[key] = it }
            }
            settings[name] = values
        }
        return settings
    }

    fun readOpponents(document: YamlNode): List<CardImportExport.ParsedOpponent> =
        section(document, KEY_OPPONENTS).mapNotNull { node ->
            val name = node.text(KEY_NAME)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            CardImportExport.ParsedOpponent(
                name = name,
                skillMultiplier = node.double(KEY_SKILL_MULTIPLIER) ?: 1.0,
                notes = node.text(KEY_NOTES).orEmpty()
            )
        }

    fun readReviewHistory(document: YamlNode): List<CardImportExport.ParsedReviewLog> =
        section(document, KEY_REVIEW_HISTORY).mapNotNull { node ->
            val question = node.text(KEY_CARD)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val reviewTime = node.long(KEY_REVIEW_TIME) ?: return@mapNotNull null
            val grade = node.int(KEY_GRADE) ?: return@mapNotNull null
            CardImportExport.ParsedReviewLog(
                cardQuestion = question,
                reviewTime = reviewTime,
                grade = grade,
                algorithm = node.text(KEY_ALGORITHM)?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_ALGORITHM,
                stateBefore = node.text(KEY_STATE_BEFORE).orEmpty(),
                stateAfter = node.text(KEY_STATE_AFTER).orEmpty(),
                scheduledDays = node.int(KEY_SCHEDULED_DAYS) ?: 0,
                elapsedDays = node.int(KEY_ELAPSED_DAYS) ?: 0,
                groupName = node.text(KEY_GROUP)?.takeIf { it.isNotBlank() },
                notes = node.text(KEY_NOTES).orEmpty(),
                imageData = node.textList(KEY_IMAGES),
                opponentName = node.text(KEY_OPPONENT)?.takeIf { it.isNotBlank() },
                stabilityMultiplier = node.double(KEY_STABILITY_MULTIPLIER) ?: 1.0
            )
        }

    /**
     * The cards, flattened into the one-record-per-state shape the import
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
    fun readCards(document: YamlNode): Pair<List<ParsedCard>, List<String>> {
        val cards = mutableListOf<ParsedCard>()
        val errors = mutableListOf<String>()

        cardNodes(document).forEach { node ->
            if (node !is YamlMapping) {
                errors.add("Line ${node.line}: a card must be a list of \"key: value\" lines")
                return@forEach
            }
            val question = (node.text(KEY_QUESTION) ?: node.text(KEY_CONCEPT))?.trim().orEmpty()
            if (question.isEmpty()) {
                errors.add("Line ${node.line}: Empty question")
                return@forEach
            }
            val answer = (node.text(KEY_ANSWER) ?: node.text(KEY_DESCRIPTION)).orEmpty()
            val imageData = node.textList(KEY_IMAGES)
            val groupNames = node.textList(KEY_GROUPS)
            val state = node[KEY_STATE] as? YamlMapping

            cards.add(
                when {
                    state != null -> parsedCard(
                        question, answer, node.line, imageData, groupNames, GLOBAL_STATE, state
                    )
                    // No state, but groups to be put in: the record still
                    // needs a state context, or the importer takes the file
                    // for a bare question-and-answer list and drops the
                    // groups. Every scheduling field stays null, which is
                    // what a card nobody has practised yet looks like.
                    groupNames.isNotEmpty() -> ParsedCard(
                        concept = question,
                        answer = answer,
                        lineNumber = node.line,
                        imageData = imageData,
                        stateContext = GLOBAL_STATE,
                        groupNames = groupNames
                    )
                    else -> ParsedCard(
                        concept = question,
                        answer = answer,
                        lineNumber = node.line,
                        imageData = imageData
                    )
                }
            )

            node[KEY_GROUP_STATES].mappingsOrEmpty().forEach { groupState ->
                val groupName = groupState.text(KEY_GROUP)?.trim().orEmpty()
                if (groupName.isEmpty()) {
                    errors.add("Line ${groupState.line}: a group state needs a \"group:\" name")
                    return@forEach
                }
                cards.add(
                    parsedCard(
                        question, answer, groupState.line, emptyList(),
                        listOf(groupName), groupName, groupState
                    )
                )
            }
        }
        return Pair(cards, errors)
    }

    private fun parsedCard(
        question: String,
        answer: String,
        lineNumber: Int,
        imageData: List<String>,
        groupNames: List<String>,
        stateContext: String,
        state: YamlMapping
    ): ParsedCard = ParsedCard(
        concept = question,
        answer = answer,
        lineNumber = lineNumber,
        imageData = imageData,
        stateContext = stateContext,
        nextReview = state.long(KEY_NEXT_REVIEW) ?: 0L,
        lastReview = state.long(KEY_LAST_REVIEW) ?: 0L,
        fsrsStability = state.double(KEY_STABILITY) ?: 0.0,
        fsrsDifficulty = state.double(KEY_DIFFICULTY) ?: 0.0,
        fsrsState = state.text(KEY_FSRS_STATE)?.takeIf { it.isNotBlank() } ?: "NEW",
        fsrsReps = state.int(KEY_REPS) ?: 0,
        fsrsLapses = state.int(KEY_LAPSES) ?: 0,
        fsrsScheduledDays = state.int(KEY_SCHEDULED_DAYS) ?: 0,
        fsrsElapsedDays = state.int(KEY_ELAPSED_DAYS) ?: 0,
        groupNames = groupNames
    )
}
