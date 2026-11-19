// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

// The whole of the import/export format: what an export writes, what an
// import will read, and the CSV interchange alongside them. It reads
// List<String> and writes Appendable, reaches images through ImageReader, and
// touches no stream, file or Context -- the Android half of that lives beside
// it in :app, as CardImportExportIo.kt.
//
// Exports are YAML. The document's shape is declared in ArchiveYaml.kt and
// the YAML itself is kaml's; what is left here is the pipeline around them,
// the CSV format and the filenames. The tab-separated V1-V4 layouts that
// came before YAML are no longer read; such a file is recognised and refused
// with LEGACY_FORMAT_ERROR.

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog

sealed class ImportResult {
    data class Success(val importedCount: Int, val skippedCount: Int, val errors: List<String>) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

sealed class ExportResult {
    data class Success(val exportedCount: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

object CardImportExport {

    /**
     * The line an export opens with.
     *
     * A YAML comment, so it costs the parser nothing, and the first thing
     * anyone who opens the file in a text editor reads.
     */
    private const val YAML_HEADER_COMMENT = "# Fencing Spaced Repetition export"

    /**
     * What every old tab-separated export began with. It is a YAML comment
     * too, so such a file handed to the YAML parser would come back as an
     * empty document; the check comes first so it is refused with a reason.
     */
    private const val LEGACY_HEADER_PREFIX = "#FSR_EXPORT_V"

    /** What an old tab-separated export is told: they are no longer read. */
    const val LEGACY_FORMAT_ERROR =
        "This is a tab-separated export from an older version of the app, which can no longer be " +
            "imported. Import it with an older version and export again, as YAML."

    private const val KEY_GROUPS = "groups"
    private const val KEY_LEARNING_GROUPS = "learningGroups"
    private const val KEY_OPPONENTS = "opponents"



    /** Parsed group settings from an import file */
    var lastParsedGroupSettings: Map<String, Map<String, String>> = emptyMap()
        private set

    /** The key a group's learning group travels under in [lastParsedGroupSettings]. */
    /** The key a group's description travels under in [lastParsedGroupSettings]. */
    const val DESCRIPTION_SETTING = "description"

    const val LEARNING_GROUP_SETTING = "learningGroup"

    /** Learning groups an import file declared, as name to description. */
    var lastParsedLearningGroups: Map<String, String> = emptyMap()
        private set

    /**
     * The learning settings an import file gives each learning group it
     * declares, by name -- the map [applyLearningGroupSettings] takes. A
     * learning group the file gives no settings is left out.
     */
    var lastParsedLearningGroupSettings: Map<String, Map<String, String>> = emptyMap()
        private set

    /**
     * Of [lastParsedLearningGroups], the ones that schedule by learning concept.
     * The others were declared with it off.
     */
    var lastParsedConceptLearningGroups: Set<String> = emptySet()
        private set

    /**
     * The settings that belong to a learning group, which older files wrote
     * on the groups.
     */
    val LEARNING_SETTING_KEYS = setOf(
        "cardsPerSession", "randomizeDueCards", "randomizeBucketHours",
        "practiceDays", "maximumInterval", "fsrsRetention", "fsrsEnableFuzzing"
    )

    /** The stable identifier the file gives each group it lists, by name. */
    var lastParsedGroupUids: Map<String, String> = emptyMap()
        private set

    /** The stable identifier the file gives each learning group it declares, by name. */
    var lastParsedLearningGroupUids: Map<String, String> = emptyMap()
        private set

    /** Parsed review history from an import file (populated after calling parseCards) */
    var lastParsedReviewHistory: List<ParsedReviewLog> = emptyList()
        private set

    /** Parsed opponents from an import file (populated after calling parseCards) */
    var lastParsedOpponents: List<ParsedOpponent> = emptyList()
        private set

    /** Parsed learning concepts from an import file (populated after calling parseCards) */
    var lastParsedConcepts: List<ParsedConcept> = emptyList()
        private set

    /**
     * A learning concept an import found. States carry no ids yet; the
     * learning-group ones are keyed by learning group name.
     */
    data class ParsedConcept(
        val uid: String? = null,
        val inGlobal: Boolean = false,
        /** Null when the file does not say where it is available; then it is left as it is. */
        val availableIn: List<String>? = null,
        val name: String,
        val description: String = "",
        val globalState: com.fencing.spacedrepetition.data.model.ConceptLearningState? = null,
        val learningStates: Map<String, com.fencing.spacedrepetition.data.model.ConceptLearningState> = emptyMap()
    )

    /** Parsed practice sessions from an import file (populated after calling parseCards) */
    var lastParsedSessions: List<ParsedSession> = emptyList()
        private set

    /**
     * A practice session an import found: when the fight was, and what was
     * noted about it. Its reviews name it by [startTime].
     */
    data class ParsedSession(
        val startTime: Long,
        val endTime: Long? = null,
        val notes: String = "",
        val imageData: List<String> = emptyList(), // base64-encoded images
        val uid: String? = null
    )

    /** An opponent an import found, in either format. */
    data class ParsedOpponent(
        val name: String,
        val skillMultiplier: Double,
        val notes: String,
        val uid: String? = null
    )

    /**
     * Reads a chosen export, whichever of the two formats it is in.
     *
     * The cards come back as the return value and everything else an archive
     * carries -- the group settings, the opponents, the review history -- in
     * the three `lastParsed` properties above, which is how the view models
     * have always collected them.
     *
     * Only YAML is read. A file whose first line opens with `#FSR_EXPORT_V`
     * is one of the old tab-separated exports (V1-V4), which are no longer
     * read; it gets [LEGACY_FORMAT_ERROR] rather than a YAML parse error.
     */
    fun parseCards(lines: List<String>): Pair<List<ParsedCard>, List<String>> {
        lastParsedGroupSettings = emptyMap()
        lastParsedLearningGroups = emptyMap()
        lastParsedLearningGroupSettings = emptyMap()
        lastParsedConceptLearningGroups = emptySet()
        lastParsedGroupUids = emptyMap()
        lastParsedLearningGroupUids = emptyMap()
        lastParsedOpponents = emptyList()
        lastParsedReviewHistory = emptyList()
        lastParsedSessions = emptyList()
        lastParsedConcepts = emptyList()

        val firstContentLine = lines.firstOrNull { it.isNotBlank() }?.trim()
            ?: return Pair(emptyList(), emptyList())

        if (firstContentLine.startsWith(LEGACY_HEADER_PREFIX)) {
            return Pair(emptyList(), listOf(LEGACY_FORMAT_ERROR))
        }
        return parseYamlCards(lines)
    }

    /**
     * Reads a YAML export.
     *
     * A file that is valid YAML but says nothing about cards is rejected
     * rather than reported as empty: someone who picked the wrong file is
     * better told that than told their deck had nothing in it.
     */
    private fun parseYamlCards(lines: List<String>): Pair<List<ParsedCard>, List<String>> {
        val document = try {
            ArchiveYaml.format.parseToYamlNode(lines.joinToString("\n"))
        } catch (e: Exception) {
            return Pair(emptyList(), listOf(yamlFailure(e)))
        }

        if (!ArchiveYaml.isArchive(document)) {
            return Pair(
                emptyList(),
                listOf("Invalid file format: expected a YAML export with a \"cards:\" list")
            )
        }

        val errors = mutableListOf<String>()

        val learningGroups = ArchiveYaml
            .decodeSection(document, KEY_LEARNING_GROUPS, ArchiveLearningGroup.serializer(), errors)
            .filter { it.name.isNotBlank() }
        lastParsedLearningGroups = learningGroups.associate { it.name.trim() to it.description }
        lastParsedLearningGroupSettings = learningGroups
            .associate { it.name.trim() to ArchiveYaml.learningGroupSettings(it) }
            .filterValues { it.isNotEmpty() }
        lastParsedConceptLearningGroups = learningGroups.filter { it.useConcepts }.map { it.name.trim() }.toSet()
        lastParsedLearningGroupUids = learningGroups
            .mapNotNull { lg -> lg.uid?.takeIf { it.isNotBlank() }?.let { lg.name.trim() to it } }.toMap()
        val groups = ArchiveYaml
            .decodeSection(document, KEY_GROUPS, ArchiveGroup.serializer(), errors)
            .filter { it.name.isNotBlank() }
        lastParsedGroupSettings = groups.associate { it.name.trim() to ArchiveYaml.groupSettings(it) }
        lastParsedGroupUids = groups
            .mapNotNull { g -> g.uid?.takeIf { it.isNotBlank() }?.let { g.name.trim() to it } }.toMap()
        lastParsedOpponents = ArchiveYaml
            .decodeSection(document, KEY_OPPONENTS, ArchiveOpponent.serializer(), errors)
            .map { ArchiveYaml.parsedOpponent(it) }
            .filter { it.name.isNotEmpty() }
        lastParsedReviewHistory = ArchiveYaml
            .decodeSection(document, ArchiveYaml.KEY_REVIEW_HISTORY, ArchiveReviewLog.serializer(), errors)
            .map { ArchiveYaml.parsedReviewLog(it) }
            .filter { it.cardQuestion.isNotBlank() }
        lastParsedSessions = ArchiveYaml
            .decodeSection(document, ArchiveYaml.KEY_SESSIONS, ArchiveSession.serializer(), errors)
            .map { ArchiveYaml.parsedSession(it) }
        lastParsedConcepts = ArchiveYaml
            .decodeSection(document, ArchiveYaml.KEY_CONCEPTS, ArchiveConcept.serializer(), errors)
            .map { ArchiveYaml.parsedConcept(it) }
            .filter { it.name.isNotEmpty() }

        val cards = mutableListOf<ParsedCard>()
        ArchiveYaml.cardEntries(document).forEach { entry ->
            val decoded = ArchiveYaml
                .decodeEntries(listOf(entry), ArchiveCard.serializer(), errors)
                .singleOrNull() ?: return@forEach
            if (decoded.questionText.isEmpty()) {
                errors.add("Line ${entry.location.line}: Empty question")
                return@forEach
            }
            cards.addAll(ArchiveYaml.parsedCards(decoded, entry.location.line))
        }
        return Pair(cards, errors)
    }

    /**
     * What to say about a file the YAML parser would not read at all.
     *
     * kaml's own message names the line and the column and then draws a caret
     * under the offending character, which is more use than anything this
     * could add -- so it is passed through rather than replaced, with the
     * pointer line dropped because the import dialog shows one line per
     * error.
     */
    private fun yamlFailure(e: Exception): String {
        val message = e.message ?: return "Failed to read file"
        val lines = message.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val where = lines.firstOrNull { it.startsWith("at line ") }
            ?.removePrefix("at line ")
            ?.substringBefore(",")
        return if (where == null) lines.first() else "Line $where: ${lines.first()}"
    }




    // ======================================================================
    // Export
    //
    // The whole document is encoded in one go rather than a card at a time.
    // Assembling it by hand would keep a large collection's photographs out
    // of memory all at once, and was tried: indenting each card's encoding
    // under "cards:" gets one card in four hundred wrong, because a literal
    // block ending in a blank line does not survive being trimmed and
    // re-indented. Letting kaml write the document is the correct answer and
    // costs memory that every export path except the scheduled backup was
    // already spending -- ImageStore.readerFor loads every picture up front.
    // ======================================================================

    /**
     * The two lines an export opens with, before the encoded document.
     *
     * Written here rather than encoded because neither can be: kaml emits no
     * comments, and `version` is left out of the encoding by the same rule
     * that leaves out every other field still holding its default. Putting
     * the line in by hand is what keeps a file saying which version it is.
     */
    private fun archivePreamble(): String =
        "$YAML_HEADER_COMMENT\n${ArchiveYaml.KEY_VERSION}: ${ArchiveYaml.VERSION}\n"

    /**
     * Writes [document] out, preamble and all.
     *
     * An archive holding nothing at all encodes as `{}`, which is a flow
     * mapping and cannot follow the `version:` line -- so an empty document
     * is written as an empty card list instead, which is both valid and true.
     */
    private fun writeArchive(out: Appendable, document: ArchiveDocument) {
        out.append(archivePreamble())
        val body = ArchiveYaml.format
            .encodeToString(ArchiveDocument.serializer(), document)
            .trimEnd('\n')
        if (body.isEmpty() || body == "{}") {
            out.append(ArchiveYaml.KEY_CARDS).append(": []\n")
        } else {
            out.append(body).append('\n')
        }
    }

    /**
     * Exports cards with their groups, without per-group learning states and
     * without their pictures.
     *
     * The narrow export: what a card is and how it is scheduled, for the
     * paths that have no image reader to hand. [exportCardsWithGroupStates]
     * is the whole thing.
     */
    fun exportCardsWithGroups(
        cardsWithGroups: List<CardWithGroupNames>,
        out: Appendable
    ): ExportResult {
        return try {
            writeArchive(
                out,
                ArchiveDocument(
                    cards = cardsWithGroups.map { (card, groupNames) ->
                        ArchiveYaml.cardNodeWithoutImages(card, groupNames)
                    }
                )
            )
            ExportResult.Success(cardsWithGroups.size)
        } catch (e: Exception) {
            ExportResult.Error("Failed to write file: ${e.message}")
        }
    }

    /**
     * Exports everything an archive holds: the cards with their pictures and
     * every learning state they keep, the settings of the groups they are in,
     * the opponents, and -- when it is asked for -- the review history.
     *
     * The count reported back is rows rather than cards, as it always has
     * been: a card's own state and one per group that learns it
     * independently.
     */
    fun exportCardsWithGroupStates(
        cardsWithStates: List<CardWithGroupStates>,
        out: Appendable,
        images: ImageReader,
        groupSettings: List<Group> = emptyList(),
        learningGroups: List<LearningGroup> = emptyList(),
        reviewLogs: List<ReviewLog> = emptyList(),
        cardQuestions: Map<Long, String> = emptyMap(),
        opponents: List<Opponent> = emptyList(),
        opponentNamesById: Map<Long, String> = emptyMap(),
        sessions: List<PracticeSession> = emptyList(),
        concepts: List<ConceptWithStates> = emptyList()
    ): ExportResult {
        return try {
            val cards = cardsWithStates.map { (card, groupNames, learningStates, conceptNames, conceptWeights) ->
                ArchiveYaml.cardNode(card, groupNames, learningStates, images, conceptNames, conceptWeights)
            }
            // Only the concepts the written cards carry.
            val writtenConceptNames = cardsWithStates.flatMap { it.conceptNames }.toSet()
            val writtenConcepts = concepts.filter { it.concept.name in writtenConceptNames }
            val learningGroupNames = learningGroups.associate { it.id to it.name }
            // Every group a written card is in -- so the file carries each
            // one's stable identifier -- plus any with settings or a learning
            // group of their own.
            val referencedGroupNames = cardsWithStates.flatMap { it.groupNames }.toSet()
            val writtenGroups = groupSettings.filter {
                it.name in referencedGroupNames || it.hasCustomSettings() || it.learningGroupId in learningGroupNames
            }
            // Only the learning groups the written groups use.
            val conceptLearningGroupNames =
                writtenConcepts.flatMap { it.learningStates.keys + it.availableIn }.toSet()
            val writtenLearningGroupIds = writtenGroups.mapNotNull { it.learningGroupId }.toSet() +
                learningGroups.filter { it.name in conceptLearningGroupNames }.map { it.id }

            val startBySessionId = sessions.associate { it.id to it.startTime }
            val uidBySessionId = sessions.associate { it.id to it.uid }
            val uidByCardId = cardsWithStates.associate { it.card.id to it.card.uid }
            val historyLogs = if (reviewLogs.isEmpty() || cardQuestions.isEmpty()) emptyList() else {
                reviewLogs.filter { it.cardId in cardQuestions }
            }
            val history = historyLogs.map { log ->
                ArchiveYaml.reviewLogNode(
                    log,
                    cardQuestions.getValue(log.cardId),
                    log.opponentId?.let { opponentNamesById[it] },
                    images,
                    sessionStart = log.sessionId?.let { startBySessionId[it] },
                    cardUid = uidByCardId[log.cardId],
                    sessionUid = log.sessionId?.let { uidBySessionId[it] }
                )
            }
            // Only the sessions the written history belongs to: a session is
            // where the notes on a fight live, and one with no reviews in this
            // file would be a note about nothing the file contains.
            val writtenSessionIds = historyLogs.mapNotNull { it.sessionId }.toSet()
            val sessionNodes = sessions
                .filter { it.id in writtenSessionIds }
                .sortedBy { it.startTime }
                .map { ArchiveYaml.sessionNode(it, images) }

            writeArchive(
                out,
                ArchiveDocument(
                    learningGroups = learningGroups.filter { it.id in writtenLearningGroupIds }
                        .map { ArchiveYaml.learningGroupNode(it) },
                    groups = writtenGroups.map {
                        ArchiveYaml.groupNode(it, it.learningGroupId?.let(learningGroupNames::get))
                    },
                    // The opponents, so the review logs can name them and an
                    // import can find them again.
                    concepts = writtenConcepts.map { ArchiveYaml.conceptNode(it.concept, it.learningStates, it.availableIn) },
                    opponents = opponents.map { ArchiveYaml.opponentNode(it) },
                    cards = cards,
                    sessions = sessionNodes,
                    reviewHistory = history
                )
            )
            // Cards, not cards plus their learning-group states.
            ExportResult.Success(cardsWithStates.size)
        } catch (e: Exception) {
            ExportResult.Error("Failed to write file: ${e.message}")
        }
    }

    /** Cards with no groups and no pictures -- [exportCardsWithGroups] of one list. */
    fun exportCards(cards: List<Card>, out: Appendable): ExportResult {
        return exportCardsWithGroups(
            cards.map { CardWithGroupNames(it, emptyList()) },
            out
        )
    }

    /**
     * Converts a ParsedCard to a Card entity (without Context - for tests)
     * Note: This version cannot decode base64 images
     */
    fun parsedCardToCard(parsed: ParsedCard): Card {
        val now = Time.now()

        return if (parsed.hasFullState) {
            Card(
                question = parsed.concept,
                answer = parsed.answer,
                imagePaths = parsed.imagePaths,
                nextReview = parsed.nextReview ?: 0L,
                lastReview = parsed.lastReview ?: 0L,
                fsrsStability = parsed.fsrsStability ?: 0.0,
                fsrsDifficulty = parsed.fsrsDifficulty ?: 0.0,
                fsrsState = parsed.fsrsState ?: "NEW",
                fsrsReps = parsed.fsrsReps ?: 0,
                fsrsLapses = parsed.fsrsLapses ?: 0,
                fsrsScheduledDays = parsed.fsrsScheduledDays ?: 0,
                fsrsElapsedDays = parsed.fsrsElapsedDays ?: 0,
                created = now,
                modified = now,
                lastReset = parsed.lastReset ?: 0L,
                uid = parsed.uid ?: newUid()
            )
        } else {
            Card(
                question = parsed.concept,
                answer = parsed.answer,
                imagePaths = parsed.imagePaths,
                created = now,
                modified = now,
                uid = parsed.uid ?: newUid()
            )
        }
    }





    /**
     * Applies parsed settings to a Group entity.
     *
     * A setting the file does not mention is cleared rather than left alone:
     * an export carries every override a group has, so its absence means the
     * group does not have one. The learning settings in the map, if an older
     * file put them there, are for [applyLearningGroupSettings] instead.
     */
    fun applyGroupSettings(group: Group, settings: Map<String, String>): Group {
        return group.copy(
            autoShowAnswer = settings["autoShowAnswer"]?.toBooleanStrictOrNull(),
            description = settings[DESCRIPTION_SETTING] ?: group.description
        )
    }

    /**
     * Applies parsed learning settings to a LearningGroup entity, clearing
     * any the map does not mention, as [applyGroupSettings] does.
     * fsrsEnableFuzzing is in the list now that the format writes it -- the
     * tab-separated one never did, which left it the one override an export
     * quietly dropped.
     */
    fun applyLearningGroupSettings(learningGroup: LearningGroup, settings: Map<String, String>): LearningGroup {
        return learningGroup.copy(
            cardsPerSession = settings["cardsPerSession"]?.toIntOrNull(),
            randomizeDueCards = settings["randomizeDueCards"]?.toBooleanStrictOrNull(),
            randomizeBucketHours = settings["randomizeBucketHours"]?.toIntOrNull(),
            practiceDays = settings["practiceDays"],
            maximumInterval = settings["maximumInterval"]?.toIntOrNull(),
            fsrsRetention = settings["fsrsRetention"]?.toIntOrNull(),
            fsrsEnableFuzzing = settings["fsrsEnableFuzzing"]?.toBooleanStrictOrNull()
        )
    }

    /**
     * What every file this app writes is called: when it was made, then what
     * is in it -- "2026-08-26_14-05-09_all_cards.yaml.gz".
     *
     * The stamp leads so that a folder of exports sorts into the order they
     * were taken, which is the order anyone looking for one thinks in. It is
     * also what keeps a second export of the same thing from overwriting the
     * first, or arriving as "all_cards (3).yaml.gz" in a downloads folder.
     *
     * [at] and [utcOffsetSeconds] are parameters rather than read here so the
     * naming can be tested against a fixed instant.
     */
    fun exportFilename(
        contents: String,
        at: Long = Time.now(),
        utcOffsetSeconds: Int = Time.utcOffsetSeconds()
    ): String = "${fileTimestamp(at, utcOffsetSeconds)}_$contents"

    /**
     * What an archive is called from the dot onwards.
     *
     * ".yaml.gz" rather than ".tsv.gz" since the format moved: the name is
     * the only thing that says what is inside a file before it is opened, and
     * one saying tsv over a YAML document would be a lie that outlives this
     * change. Files written under the old name still import -- the format is
     * detected from the first line and never from the name -- and the backup
     * worker still recognises them when it prunes.
     */
    const val ARCHIVE_EXTENSION = ".yaml.gz"

    /** The whole collection, as a compressed archive and as a CSV. */
    fun generateAllCardsFilename(): String = exportFilename("all_cards$ARCHIVE_EXTENSION")

    fun generateAllCardsCsvFilename(): String = exportFilename("all_cards.csv")

    /** Several groups at once, chosen from the card list. */
    fun generateSelectedGroupsFilename(): String =
        exportFilename("selected_groups_cards$ARCHIVE_EXTENSION")

    fun generateSelectedGroupsCsvFilename(): String = exportFilename("selected_groups_cards.csv")

    /**
     * A backup: the whole collection with its history, taken on a schedule or
     * asked for. Named like an export because that is what it is -- the file
     * it produces is one any of the import paths will read back.
     */
    fun generateBackupFilename(): String = exportFilename("backup$ARCHIVE_EXTENSION")

    /** One group's cards, as a compressed archive. */
    fun generateExportFilename(groupName: String): String =
        exportFilename("${sanitizeForFilename(groupName)}_cards$ARCHIVE_EXTENSION")

    /**
     * A group's name, reduced to what every filesystem will take.
     *
     * Fifty characters of it: long enough to tell two decks apart, short
     * enough that the stamp and the suffix still fit inside the shortest
     * filename limit these files might land under.
     */
    internal fun sanitizeForFilename(groupName: String): String =
        groupName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(50)

    /** The whole collection's photos, as an archive a photo viewer opens. */
    fun generateAllPhotosFilename(): String = exportFilename("photos.zip")

    /**
     * Base64 codec for the images an export inlines.
     *
     * [Base64.encode] is byte-for-byte identical to `java.util.Base64`'s basic
     * encoder, so files written before and after this change interoperate.
     *
     * Decoding needs [Base64.PaddingOption.PRESENT_OPTIONAL] to stay
     * compatible: `java.util.Base64`'s decoder accepts input whose trailing
     * '=' padding has been stripped, while the Kotlin default rejects it. Our
     * own exports are always padded, but a hand-edited or third-party file
     * need not be, and those used to import fine.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private val BASE64_LENIENT = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    /**
     * Encodes image file to base64 string
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encodeImageToBase64(imagePath: String, images: ImageReader): String? {
        return try {
            images.read(imagePath)?.let { Base64.encode(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodes one inline base64 image to bytes, or null if the data is not
     * valid base64. Storing those bytes is the image store's problem, not
     * this function's -- see storeImages, which suspends because storing one
     * in a browser does.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeBase64Image(base64Data: String): ByteArray? {
        return try {
            BASE64_LENIENT.decode(base64Data)
        } catch (e: Exception) {
            null
        }
    }




    // ========== Review history ==========

    /**
     * One entry of an export's review history.
     *
     * Keyed by cardQuestion rather than by cardId, in both formats: ids are
     * the device's own and an export is read on another one.
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
        /** How hard the fight was, 1-5 (null = unrated). See FightDifficulty. */
        val fightDifficulty: Int? = null,
        val stabilityMultiplier: Double = 1.0,
        /** The startTime of the session this review was part of; null for a quick grade. */
        val sessionStart: Long? = null,
        val uid: String? = null,
        val cardUid: String? = null,
        val groupUid: String? = null,
        val sessionUid: String? = null,
        val opponentUid: String? = null,
        val selectionMode: String? = null,
        val selectionReason: String? = null,
        val selectionConcepts: List<String> = emptyList(),
        val opponentSkillMultiplier: Double? = null,
        val selectionTrigger: String? = null
    )


    /**
     * Converts ParsedReviewLogs to ReviewLog entities using a question->cardId map.
     * Skips logs for cards not found in the map.
     *
     * Carries no images: an import reaches this through the suspending
     * extension of the same name, which stores them first. Everything else
     * about a log is copied here, once, for both callers.
     */
    fun parsedReviewLogsToEntities(
        parsed: List<ParsedReviewLog>,
        questionToCardId: Map<String, Long>,
        opponentNameToId: Map<String, Long> = emptyMap(),
        sessionIdByStart: Map<Long, Long> = emptyMap(),
        cardIdByUid: Map<String, Long> = emptyMap(),
        sessionIdByUid: Map<String, Long> = emptyMap()
    ): List<ReviewLog> {
        return parsed.mapNotNull { p ->
            // By stable identifier where the file has one, by question otherwise.
            val cardId = p.cardUid?.let { cardIdByUid[it] }
                ?: questionToCardId[p.cardQuestion]
                ?: return@mapNotNull null
            ReviewLog(
                cardId = cardId,
                sessionId = p.sessionUid?.let { sessionIdByUid[it] } ?: p.sessionStart?.let { sessionIdByStart[it] },
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
                fightDifficulty = p.fightDifficulty,
                stabilityMultiplier = p.stabilityMultiplier,
                groupUid = p.groupUid,
                opponentUid = p.opponentUid,
                uid = p.uid ?: newUid(),
                selectionMode = p.selectionMode,
                selectionReason = p.selectionReason,
                selectionConcepts = p.selectionConcepts.takeIf { it.isNotEmpty() }?.joinToString(","),
                opponentSkillMultiplier = p.opponentSkillMultiplier,
                selectionTrigger = p.selectionTrigger
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
    fun parseCsvCards(content: String): Pair<List<ParsedCard>, List<String>> {
        val cards = mutableListOf<ParsedCard>()
        val errors = mutableListOf<String>()

        try {
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
        out: Appendable,
        images: ImageReader
    ): ExportResult {
        return try {
            val anyCardHasImages = cardsWithGroups.any { (card, _) ->
                card.imagePaths.isNotEmpty()
            }

            // Write header row
            val headerParts = mutableListOf(CSV_HEADER_CONCEPT, CSV_HEADER_DESCRIPTION)
            if (anyCardHasImages) {
                headerParts.add(CSV_HEADER_IMAGES)
            }
            out.append(headerParts.joinToString(CSV_DELIMITER) { escapeCsvField(it) })
            out.append('\n')

            // Write data rows
            cardsWithGroups.forEach { (card, _) ->
                val fields = mutableListOf(
                    escapeCsvField(card.question),
                    escapeCsvField(card.answer)
                )

                if (anyCardHasImages) {
                    // Encode all images and join with pipe separator
                    val encodedImages = card.imagePaths.mapNotNull { encodeImageToBase64(it, images) }
                    val imagesField = encodedImages.joinToString(CSV_IMAGE_SEPARATOR)
                    fields.add(escapeCsvField(imagesField))
                }

                out.append(fields.joinToString(CSV_DELIMITER))
                out.append('\n')
            }
            ExportResult.Success(cardsWithGroups.size)
        } catch (e: Exception) {
            ExportResult.Error("Failed to write CSV file: ${e.message}")
        }
    }

    /** One group's cards, as a CSV. */
    fun generateCsvExportFilename(groupName: String): String =
        exportFilename("${sanitizeForFilename(groupName)}_cards.csv")

    /**
     * The stamp [exportFilename] puts at the front of every file, as a
     * pattern for taking it off again.
     */
    private val FILENAME_TIMESTAMP = Regex("^\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_")

    /**
     * Derives a group name from a filename.
     * e.g. "parries_cards.csv" -> "parries cards", "My_Techniques.csv" -> "My Techniques"
     *
     * A leading timestamp comes off first: our own exports carry one, and a
     * CSV of ours imported back would otherwise suggest a group called
     * "2026 08 26 14 05 09 Parries".
     */
    fun deriveGroupNameFromFilename(filename: String): String {
        // Remove file extension(s) - loop until no more known extensions remain
        var name = filename.replaceFirst(FILENAME_TIMESTAMP, "")
        val extensions = listOf(".csv", ".tsv", ".yaml", ".yml", ".gz", ".txt")
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
