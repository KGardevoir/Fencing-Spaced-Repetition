// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.ConceptDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.dao.PracticeSessionDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.*
import com.fencing.spacedrepetition.util.CardWithGroupStates
import com.fencing.spacedrepetition.util.ConceptWithStates
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.first

/**
 * Cards in and out of the collection in bulk: importing cards, their
 * learning-group progress, concepts, sessions and history, and reading it
 * all back out for an export. See docs/DESIGN.md, "Archive".
 */
class CardArchive internal constructor(
    private val cardDao: CardDao,
    private val sessionDao: PracticeSessionDao,
    private val reviewLogDao: ReviewLogDao,
    private val groupDao: GroupDao,
    private val conceptDao: ConceptDao
) {
    /**
     * Add imported reviews to the history, leaving out any already in it.
     *
     * The history is what card and concept progress is derived from, so a
     * review imported twice would count twice. A review is already here when
     * its stable identifier is, or -- for files from before identifiers --
     * when the same card has a review at the same moment with the same grade.
     *
     * @return how many were added.
     */
    suspend fun importReviewLogs(reviewLogs: List<ReviewLog>): Int {
        val existing = reviewLogDao.getAllReviewLogsSync()
        val byUid = existing.associateBy { it.uid }
        val byMoment = existing.associateBy { Triple(it.cardId, it.reviewTime, it.grade) }
        val knownUids = existing.map { it.uid }.toMutableSet()
        val knownMoments = existing.map { Triple(it.cardId, it.reviewTime, it.grade) }.toMutableSet()
        val fresh = reviewLogs.filter { log ->
            val moment = Triple(log.cardId, log.reviewTime, log.grade)
            val isNew = log.uid !in knownUids && moment !in knownMoments
            if (isNew) {
                knownUids += log.uid
                knownMoments += moment
            } else {
                // Already here: what was noted about it, and who it was
                // against, are the file's. Its grade, time, group and
                // multiplier are history that progress is derived from, and stay.
                (byUid[log.uid] ?: byMoment[moment])?.let { here ->
                    val updated = here.copy(
                        notes = log.notes,
                        imagePaths = log.imagePaths,
                        opponentId = log.opponentId,
                        opponentUid = log.opponentUid
                    )
                    if (updated != here) reviewLogDao.updateReviewLog(updated)
                }
            }
            isNew
        }.map { it.copy(id = 0) }
        if (fresh.isNotEmpty()) reviewLogDao.insertReviewLogs(fresh)
        return fresh.size
    }


    suspend fun importCardsToGroup(
        cards: List<Pair<String, String>>,
        groupId: Long
    ): Int {
        var importedCount = 0
        cards.forEach { (question, answer) ->
            val existingCard = cardDao.findCardByQuestion(question)
            val cardId: Long

            if (existingCard != null) {
                // Update existing card's answer (preserve learning state)
                cardDao.updateCard(existingCard.copy(
                    answer = answer,
                    modified = Time.now()
                ))
                cardId = existingCard.id
            } else {
                // Create new card
                val card = Card(
                    question = question,
                    answer = answer
                )
                cardId = cardDao.insertCard(card)
            }

            // Add to group if not already in it (ignore conflict)
            try {
                groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
            } catch (e: Exception) {
                // Card already in group, ignore
            }
            importedCount++
        }
        return importedCount
    }

    suspend fun importCards(
        cards: List<Pair<String, String>>
    ): Int {
        var importedCount = 0
        cards.forEach { (question, answer) ->
            val existingCard = cardDao.findCardByQuestion(question)
            if (existingCard != null) {
                // Update existing card's answer only (preserve learning state)
                cardDao.updateCard(existingCard.copy(
                    answer = answer,
                    modified = Time.now()
                ))
            } else {
                // Create new card
                val card = Card(
                    question = question,
                    answer = answer
                )
                cardDao.insertCard(card)
            }
            importedCount++
        }
        return importedCount
    }

    /**
     * This card with what an import may change -- its question, description
     * and pictures -- taken from [imported], and everything that is progress
     * kept: its scheduling state, when it was last reviewed and reset, and
     * (elsewhere) its learning-group states. So a file edited by another
     * program can rename, rewrite or regroup cards without resetting anyone's
     * learning. Identity, creation time and whether it is disabled are this
     * device's and stay too. See docs/DESIGN.md, "Archive".
     */
    private fun Card.withImportedContent(imported: Card): Card = copy(
        question = imported.question,
        answer = imported.answer,
        imagePaths = imported.imagePaths,
        modified = Time.now()
    )

    suspend fun importFullCards(
        cards: List<Card>,
        groupNamesPerCard: List<List<String>>,
        existingGroups: Map<String, Long>
    ): Int {
        var importedCount = 0
        cards.forEachIndexed { index, card ->
            val existingCard = cardDao.findCardByQuestion(card.question)
            val cardId: Long

            if (existingCard != null) {
                // Everything but its progress: see [withImportedContent].
                cardDao.updateCard(existingCard.withImportedContent(card))
                cardId = existingCard.id

                // Clear existing group associations before re-adding
                groupDao.deleteAllGroupsForCard(cardId)
            } else {
                // Create new card
                cardId = cardDao.insertCard(card)
            }

            // Add group associations
            val groupNames = groupNamesPerCard.getOrNull(index) ?: emptyList()
            groupNames.forEach { groupName ->
                existingGroups[groupName]?.let { groupId ->
                    groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
                }
            }
            importedCount++
        }
        return importedCount
    }

    /**
     * @param toCard turns a parsed record into a Card. Passed in rather than
     *   called directly because the conversion decodes inline base64 images
     *   and puts them in the image store, which is the one step of an import
     *   that has to wait on something -- a browser's storage is asynchronous,
     *   which is why the parameter suspends.
     * @param learningGroups the learning group each non-global state context
     *   resolves to; a state whose context is not in it is skipped.
     */
    suspend fun importCardsWithGroupStates(
        parsedCards: List<ParsedCard>,
        existingGroups: Map<String, Long>,
        learningGroups: Map<String, Long> = emptyMap(),
        toCard: suspend (ParsedCard) -> Card
    ): Int {
        // Group parsed cards by concept (same card, different state contexts)
        val cardsByQuestion = parsedCards.groupBy { it.concept }
        var importedCount = 0

        cardsByQuestion.forEach { (question, states) ->
            // Find the global state (or use the first state if no global)
            val globalState = states.find { it.isGlobalState } ?: states.first()

            // Create or update the card (decode base64 images). The same card
            // is the one with the file's stable identifier, else the one with
            // its question; a card found keeps its own identifier for life.
            val card = toCard(globalState)
            val existingCard = states.firstNotNullOfOrNull { it.uid }?.let { cardDao.findCardByUid(it) }
                ?: cardDao.findCardByQuestion(question)
            val cardId: Long

            if (existingCard != null) {
                // Everything but its progress: see [withImportedContent].
                cardDao.updateCard(existingCard.withImportedContent(card))
                cardId = existingCard.id

                // Its groups are content, and are the file's now.
                groupDao.deleteAllGroupsForCard(cardId)
            } else {
                cardId = cardDao.insertCard(card)
            }

            // Collect all unique group names from all state contexts
            val allGroupNames = states.flatMap { it.groupNames }.toSet()

            // Add group associations
            allGroupNames.forEach { groupName ->
                existingGroups[groupName]?.let { groupId ->
                    groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
                }
            }

            // Learning-group progress comes in only with a card that is new
            // here; a card already here keeps its own.
            if (existingCard == null) states.filter { it.isGroupSpecificState }.forEach { groupState ->
                learningGroups[groupState.stateContext!!]?.let { learningGroupId ->
                    val learningState = CardLearningState(
                        cardId = cardId,
                        learningGroupId = learningGroupId,
                        fsrsStability = groupState.fsrsStability ?: 0.0,
                        fsrsDifficulty = groupState.fsrsDifficulty ?: 0.0,
                        fsrsElapsedDays = groupState.fsrsElapsedDays ?: 0,
                        fsrsScheduledDays = groupState.fsrsScheduledDays ?: 0,
                        fsrsReps = groupState.fsrsReps ?: 0,
                        fsrsLapses = groupState.fsrsLapses ?: 0,
                        fsrsState = groupState.fsrsState ?: "NEW",
                        lastReview = groupState.lastReview ?: 0L,
                        nextReview = groupState.nextReview ?: 0L,
                        lastReset = groupState.lastReset ?: 0L
                    )
                    groupDao.insertLearningState(learningState)
                }
            }

            importedCount++
        }
        return importedCount
    }

    /**
     * Tag imported cards with the concepts their records list, finding each
     * card by stable identifier, else question. A card whose record says
     * which concepts it has -- even none, since exports always say -- has
     * exactly those afterwards; one from a file from before concepts keeps
     * whatever it carries here.
     */
    suspend fun linkImportedConcepts(parsedCards: List<ParsedCard>, conceptIds: Map<String, Long>) {
        parsedCards.groupBy { it.concept }.forEach { (question, records) ->
            val names = records.flatMap { it.conceptNames }.toSet()
            // A file that says which concepts a card has -- even none -- sets
            // them; one that does not (from before concepts) leaves them.
            val stated = records.any { it.conceptsStated }
            if (!stated && names.isEmpty()) return@forEach
            val card = records.firstNotNullOfOrNull { it.uid }?.let { cardDao.findCardByUid(it) }
                ?: cardDao.findCardByQuestion(question)
                ?: return@forEach
            conceptDao.deleteAllConceptsForCard(card.id)
            val weights = records.flatMap { it.conceptWeights.entries }.associate { it.key to it.value }
            names.forEach { name ->
                conceptIds[name]?.let {
                    val weight = CardConceptCrossRef.sanitize(weights[name])
                    conceptDao.insertCardConceptCrossRef(CardConceptCrossRef(card.id, it, weight))
                }
            }
        }
    }

    suspend fun getAllCardsWithGroupNames(): List<Pair<Card, List<String>>> {
        val cardsWithGroups = cardDao.getAllCardsWithGroups().first()
        return cardsWithGroups.map { cardWithGroups ->
            cardWithGroups.card to cardWithGroups.groups.map { it.name }
        }
    }

    suspend fun getAllCardsWithGroupStates(): List<CardWithGroupStates> {
        val cardsWithGroups = cardDao.getAllCardsWithGroups().first()
        val learningGroupNames = groupDao.getAllLearningGroups().first().associate { it.id to it.name }
        val conceptNames = conceptDao.getAllConceptsSync().associate { it.id to it.name }
        val links = conceptDao.getAllCardConceptCrossRefsSync()
        val conceptsByCard = links
            .groupBy({ it.cardId }, { conceptNames[it.conceptId] })
            .mapValues { (_, names) -> names.filterNotNull().sorted() }
        val weightsByCard = links.filter { it.weight != 1.0 }
            .groupBy { it.cardId }
            .mapValues { (_, l) -> l.mapNotNull { link -> conceptNames[link.conceptId]?.let { it to link.weight } }.toMap() }
        return cardsWithGroups.map { cardWithGroups ->
            val card = cardWithGroups.card
            val groups = cardWithGroups.groups
            val groupNames = groups.map { it.name }

            // The states kept in the learning groups this card's groups use
            val learningGroupIds = groups.mapNotNull { it.learningGroupId }.toSet()
            val learningStates = if (learningGroupIds.isEmpty()) emptyMap() else {
                groupDao.getAllLearningStatesForCard(card.id)
                    .filter { it.learningGroupId in learningGroupIds }
                    .mapNotNull { state -> learningGroupNames[state.learningGroupId]?.let { it to state } }
                    .toMap()
            }

            CardWithGroupStates(
                card, groupNames, learningStates, conceptsByCard[card.id].orEmpty(), weightsByCard[card.id].orEmpty()
            )
        }
    }

    /** Every concept with its states, learning-group ones keyed by learning group name. */
    suspend fun getAllConceptsWithStates(): List<ConceptWithStates> {
        val learningGroupNames = groupDao.getAllLearningGroups().first().associate { it.id to it.name }
        val statesByConcept = conceptDao.getAllConceptLearningStatesSync().groupBy { it.conceptId }
        val availability = conceptDao.getAllConceptLearningGroupsSync().groupBy({ it.conceptId }, { it.learningGroupId })
        return conceptDao.getAllConceptsSync().map { concept ->
            ConceptWithStates(
                concept,
                statesByConcept[concept.id].orEmpty()
                    .mapNotNull { state -> learningGroupNames[state.learningGroupId]?.let { it to state } }
                    .toMap(),
                availableIn = availability[concept.id].orEmpty().mapNotNull { learningGroupNames[it] }.sorted()
            )
        }
    }

    /**
     * The session a restored review belongs to, found by stable identifier,
     * else by when it started, or created from what an archive carried.
     *
     * A session already here takes the file's notes and their pictures -- an
     * import updates everything but progress -- and keeps its times, cards
     * and grades, which are the history its progress was made from.
     */
    suspend fun ensureSession(session: PracticeSession): Long {
        val existing = session.uid.takeIf { it.isNotBlank() }?.let { sessionDao.getSessionByUid(it) }
            ?: sessionDao.getSessionByStartTime(session.startTime)
            ?: return sessionDao.insertSession(session.copy(id = 0))
        if (existing.notes != session.notes || existing.imagePaths != session.imagePaths) {
            sessionDao.updateSession(existing.copy(notes = session.notes, imagePaths = session.imagePaths))
        }
        return existing.id
    }


    /** Local card id by stable identifier, for placing imported history. */
    suspend fun cardIdsByUid(): Map<String, Long> = cardDao.getAllCardsAsList().associate { it.uid to it.id }
}
