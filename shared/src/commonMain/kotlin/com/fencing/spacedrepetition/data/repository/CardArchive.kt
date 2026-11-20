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

    /**
     * Brings an archive's cards in, returning how many.
     *
     * The records for one card -- its own, and one per learning group it has
     * progress in -- share its question. The card already here is the one with
     * the file's stable identifier, else the one with its question; it takes
     * the file's content (see [withImportedContent]) and keeps its progress,
     * here and in every learning group. A card new here comes in with the
     * progress the file gives it, in Global and each learning group.
     *
     * A record that states anything beyond a question and answer (progress,
     * groups or concepts) says which groups the card is in, and the card is in
     * exactly those afterwards; a bare question-and-answer record leaves an
     * existing card's groups, and its pictures if it brings none.
     *
     * @param groupIds each group name the file uses, to its group here.
     * @param learningGroupIds each learning-group state context, to its
     *   learning group here; a state whose context is not in it is skipped.
     * @param toCard turns a record into a Card. Passed in because it decodes
     *   inline base64 images into the image store -- the one step of an import
     *   that waits on something (a browser's storage is asynchronous).
     */
    suspend fun importCards(
        parsedCards: List<ParsedCard>,
        groupIds: Map<String, Long>,
        learningGroupIds: Map<String, Long> = emptyMap(),
        toCard: suspend (ParsedCard) -> Card
    ): Int {
        val byQuestion = parsedCards.groupBy { it.concept }
        byQuestion.forEach { (question, records) ->
            val own = records.find { it.isGlobalState } ?: records.first()
            val card = toCard(own)
            val existing = records.firstNotNullOfOrNull { it.uid }?.let { cardDao.findCardByUid(it) }
                ?: cardDao.findCardByQuestion(question)
            val cardId = if (existing == null) cardDao.insertCard(card) else {
                cardDao.updateCard(existing.withImportedContent(card, keepPictures = !own.hasFullState))
                // Its groups are content, and are the file's -- when it says.
                if (own.hasFullState) groupDao.deleteAllGroupsForCard(existing.id)
                existing.id
            }
            records.flatMap { it.groupNames }.toSet().forEach { name ->
                groupIds[name]?.let { groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, it)) }
            }

            // Learning-group progress comes in only with a card that is new
            // here; a card already here keeps its own.
            if (existing == null) records.filter { it.isGroupSpecificState }.forEach { record ->
                learningGroupIds[record.stateContext!!]?.let { learningGroupId ->
                    groupDao.insertLearningState(
                        CardLearningState(
                            cardId = cardId,
                            learningGroupId = learningGroupId,
                            fsrsStability = record.fsrsStability ?: 0.0,
                            fsrsDifficulty = record.fsrsDifficulty ?: 0.0,
                            fsrsElapsedDays = record.fsrsElapsedDays ?: 0,
                            fsrsScheduledDays = record.fsrsScheduledDays ?: 0,
                            fsrsReps = record.fsrsReps ?: 0,
                            fsrsLapses = record.fsrsLapses ?: 0,
                            fsrsState = record.fsrsState ?: "NEW",
                            lastReview = record.lastReview ?: 0L,
                            nextReview = record.nextReview ?: 0L,
                            lastReset = record.lastReset ?: 0L
                        )
                    )
                }
            }
        }
        return byQuestion.size
    }

    /**
     * This card with what an import may change -- its question, description
     * and pictures -- taken from [imported], and everything that is progress
     * kept: its scheduling state, when it was last reviewed and reset, and
     * (elsewhere) its learning-group states. So a file edited by another
     * program can rename, rewrite or regroup cards without resetting anyone's
     * learning. Identity, creation time and whether it is disabled are this
     * device's and stay too. See docs/DESIGN.md, "Archive".
     *
     * @param keepPictures keep this card's pictures when [imported] has none.
     */
    private fun Card.withImportedContent(imported: Card, keepPictures: Boolean): Card = copy(
        question = imported.question,
        answer = imported.answer,
        imagePaths = if (keepPictures) imported.imagePaths.ifEmpty { imagePaths } else imported.imagePaths,
        modified = Time.now()
    )

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
