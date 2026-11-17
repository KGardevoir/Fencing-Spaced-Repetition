// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.algorithm.FSRSAlgorithm
import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.ConceptDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.dao.OpponentDao
import com.fencing.spacedrepetition.data.dao.PracticeSessionDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.*
import com.fencing.spacedrepetition.data.preferences.SchedulingPreferences
import com.fencing.spacedrepetition.util.CardWithGroupStates
import com.fencing.spacedrepetition.util.ConceptWithStates
import com.fencing.spacedrepetition.util.ParsedCard
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

const val GROUP_NAME_CARD_EDIT = "card_edit"

/**
 * Recorded on every [ReviewLog]. FSRS is the only scheduler now; the column
 * stays because logs written before SM-2 was removed still name it, and the
 * review-history export is positional.
 */
private const val ALGORITHM_NAME = "FSRS"

/** One review of a concept, as replayed from its cards' history. */
internal data class ConceptReviewEvent(
    val time: Long,
    val grade: Grade,
    val groupName: String?,
    val stabilityMultiplier: Double,
    val groupUid: String? = null
)

class CardRepository(
    private val cardDao: CardDao,
    private val sessionDao: PracticeSessionDao,
    private val reviewLogDao: ReviewLogDao,
    private val groupDao: GroupDao,
    private val opponentDao: OpponentDao,
    private val preferences: SchedulingPreferences,
    private val conceptDao: ConceptDao
) {
    private val fsrsAlgorithm = FSRSAlgorithm()

    // Card operations
    fun getAllCards(): Flow<List<Card>> = cardDao.getAllCards()

    suspend fun getAllCardsSync(): List<Card> {
        val cards = cardDao.getAllCardsSync()
        val shouldRandomize = preferences.randomizeDueCards.first()
        return if (shouldRandomize) {
            cards.shuffled()
        } else {
            cards
        }
    }

    suspend fun getCardById(cardId: Long): Card? = cardDao.getCardById(cardId)

    fun getCardByIdFlow(cardId: Long): Flow<Card?> = cardDao.getCardByIdFlow(cardId)

    suspend fun getDueCards(limit: Int = 100): List<Card> {
        val shouldRandomize = preferences.randomizeDueCards.first()
        // When randomizing, fetch all due cards so we can sample from the full pool
        val cards = if (shouldRandomize) {
            cardDao.getDueCards(limit = Int.MAX_VALUE)
        } else {
            cardDao.getDueCards(limit = limit)
        }
        return if (shouldRandomize) {
            val bucketHours = preferences.randomizeBucketHours.first()
            randomizeCardsByBucket(cards, bucketHours).take(limit)
        } else {
            cards
        }
    }

    fun getDueCardsFlow(limit: Int = 100): Flow<List<Card>> = cardDao.getDueCardsFlow(limit = limit)

    fun getDueCardCount(): Flow<Int> = cardDao.getDueCardCount()

    fun getCardsByCategory(category: String): Flow<List<Card>> = cardDao.getCardsByCategory(category)

    suspend fun insertCard(card: Card): Long = cardDao.insertCard(card)

    suspend fun findCardByQuestion(question: String): Card? = cardDao.findCardByQuestion(question)

    suspend fun updateCard(card: Card) = cardDao.updateCard(card)

    suspend fun deleteCard(card: Card) = deleteCardById(card.id)

    /** Deletes the card and its history; its concepts are rederived without either. */
    suspend fun deleteCardById(cardId: Long) {
        val conceptIds = conceptDao.getConceptsForCardSync(cardId).map { it.id }
        cardDao.deleteCardById(cardId)
        reviewLogDao.deleteReviewLogsByCard(cardId)
        if (conceptIds.isNotEmpty()) rederiveConcepts(conceptIds)
    }

    suspend fun resetCardState(cardId: Long, resetGroupStates: Boolean = false) {
        val card = cardDao.getCardById(cardId) ?: return
        val now = Time.now()
        val resetCard = card.copy(
            // Reset FSRS state
            fsrsStability = 0.0,
            fsrsDifficulty = 0.0,
            fsrsElapsedDays = 0,
            fsrsScheduledDays = 0,
            fsrsReps = 0,
            fsrsLapses = 0,
            fsrsState = "NEW",
            // Reset review times
            lastReview = 0L,
            nextReview = 0L,
            modified = now,
            // Reviews before now stop counting towards anything derived.
            lastReset = now
        )
        cardDao.updateCard(resetCard)

        // Optionally reset the learning-group progress too: every learning
        // group, held or not, gets a new state stamped with the reset, so the
        // history from before it is set aside there as well.
        if (resetGroupStates) {
            groupDao.getAllLearningGroups().first().forEach { learningGroup ->
                groupDao.insertLearningState(
                    CardLearningState(cardId, learningGroup.id, modified = now, lastReset = now)
                )
            }
        }
        rederiveConceptsForCards(listOf(cardId))
    }

    /** Resets the state [cardId] keeps in the learning group that [groupId] practises against. */
    suspend fun resetCardStateInGroup(cardId: Long, groupId: Long) {
        val learningGroupId = groupDao.getGroupById(groupId)?.learningGroupId ?: return
        resetCardStateInLearningGroup(cardId, learningGroupId)
    }

    /**
     * Back to new in one learning group, stamped with when, so the history from
     * before the reset stops counting there. Written whether or not the card
     * held a state in it yet: a reset is a fact about the progress, not the row.
     */
    suspend fun resetCardStateInLearningGroup(cardId: Long, learningGroupId: Long) {
        val now = Time.now()
        groupDao.insertLearningState(CardLearningState(cardId, learningGroupId, modified = now, lastReset = now))
        rederiveConceptsForCards(listOf(cardId))
    }

    fun getCardCount(): Flow<Int> = cardDao.getCardCount()

    fun getPracticeHistoryStats(startTime: Long): Flow<PracticeHistoryStats> =
        reviewLogDao.getPracticeHistoryStats(startTime)

    fun getAllCategories(): Flow<List<String>> = cardDao.getAllCategories()

    // History operations
    fun getCompletedSessions(): Flow<List<PracticeSession>> = sessionDao.getCompletedSessions()

    fun getReviewLogsBySession(sessionId: Long): Flow<List<ReviewLog>> =
        reviewLogDao.getReviewLogsBySession(sessionId)

    fun getReviewLogsWithoutSession(): Flow<List<ReviewLog>> =
        reviewLogDao.getReviewLogsWithoutSession()

    fun getReviewLogsByCard(cardId: Long): Flow<List<ReviewLog>> =
        reviewLogDao.getReviewLogsByCard(cardId)

    fun getAllReviewLogs(): Flow<List<ReviewLog>> = reviewLogDao.getAllReviewLogs()

    suspend fun getAllReviewLogsSync(): List<ReviewLog> = reviewLogDao.getAllReviewLogsSync()

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

    suspend fun updateReviewLog(reviewLog: ReviewLog) {
        reviewLogDao.updateReviewLog(reviewLog)
    }

    /**
     * Records a review log entry for a grade applied from the Add/Edit card screen.
     * Does not update card state (the caller is responsible for that via updateCard).
     */
    suspend fun logGradeFromEdit(
        cardBefore: Card,
        cardAfter: Card,
        grade: Grade,
        groupId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null
    ) {
        val now = Time.now()
        val elapsedDays = if (cardBefore.lastReview == 0L) 0 else
            ((now - cardBefore.lastReview) / (1000 * 60 * 60 * 24)).toInt()
        val group = groupId?.let { groupDao.getGroupById(it) }
        val groupName = group?.name
        val groupUid = group?.uid
        val stabilityMultiplier = resolveStabilityMultiplier(opponentId, fightDifficulty)
        val opponentUid = opponentId?.let { opponentDao.getOpponentById(it)?.uid }
        reviewLogDao.insertReviewLog(ReviewLog(
            cardId = cardBefore.id,
            sessionId = null,
            reviewTime = now,
            grade = grade.value,
            algorithm = ALGORITHM_NAME,
            stateBefore = serializeCardState(cardBefore),
            stateAfter = serializeCardState(cardAfter),
            scheduledDays = cardAfter.fsrsScheduledDays,
            elapsedDays = elapsedDays,
            groupName = groupName ?: GROUP_NAME_CARD_EDIT,
            groupUid = groupUid,
            opponentId = opponentId,
            opponentUid = opponentUid,
            fightDifficulty = fightDifficulty,
            stabilityMultiplier = stabilityMultiplier
        ))
        // A new review in the card's history: its concepts follow.
        rederiveConceptsForCards(listOf(cardBefore.id))
    }

    /**
     * The stability multiplier a review earns: the opponent's skill level and the
     * difficulty of the fight, multiplied together.
     *
     * Either half defaults to 1.0 (neutral) -- no opponent, an opponent that has
     * since been deleted, or an unrated fight -- so a review with neither is
     * scheduled exactly as it was before opponents existed.
     */
    private suspend fun resolveStabilityMultiplier(opponentId: Long?, fightDifficulty: Int? = null): Double {
        val opponentMultiplier =
            if (opponentId == null) 1.0
            else opponentDao.getOpponentById(opponentId)?.skillMultiplier ?: 1.0
        return FightDifficulty.combinedMultiplier(opponentMultiplier, fightDifficulty)
    }

    // Group-aware card operations
    fun getAllCardsWithGroups(): Flow<List<CardWithGroups>> = cardDao.getAllCardsWithGroups()

    fun getCardWithGroups(cardId: Long): Flow<CardWithGroups?> = cardDao.getCardWithGroups(cardId)

    suspend fun getDueCardsByGroup(groupId: Long, limit: Int = 100): List<Card> {
        val learningGroup = learningGroupFor(groupId)
        val shouldRandomize = learningGroup?.randomizeDueCards ?: preferences.randomizeDueCards.first()
        val cards = if (shouldRandomize) {
            cardDao.getDueCardsByGroup(groupId, limit = Int.MAX_VALUE)
        } else {
            cardDao.getDueCardsByGroup(groupId, limit = limit)
        }
        return if (shouldRandomize) {
            val bucketHours = learningGroup?.randomizeBucketHours ?: preferences.randomizeBucketHours.first()
            randomizeCardsByBucket(cards, bucketHours).take(limit)
        } else {
            cards
        }
    }

    suspend fun getCardsByGroupSync(groupId: Long): List<Card> {
        val cards = cardDao.getCardsByGroupSync(groupId)
        val shouldRandomize = learningGroupFor(groupId)?.randomizeDueCards ?: preferences.randomizeDueCards.first()
        return if (shouldRandomize) {
            cards.shuffled()
        } else {
            cards
        }
    }

    fun getDueCardCountByGroup(groupId: Long): Flow<Int> = cardDao.getDueCardCountByGroup(groupId)

    fun getCardCountByGroup(groupId: Long): Flow<Int> = cardDao.getCardCountByGroup(groupId)

    fun getCardsByGroup(groupId: Long): Flow<List<Card>> = cardDao.getCardsByGroup(groupId)

    suspend fun insertCardWithGroups(card: Card, groupIds: List<Long>): Long {
        val cardId = cardDao.insertCard(card)
        groupIds.forEach { groupId ->
            groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
        }
        return cardId
    }

    suspend fun updateCardGroups(cardId: Long, groupIds: List<Long>) {
        groupDao.deleteAllGroupsForCard(cardId)
        groupIds.forEach { groupId ->
            groupDao.insertCardGroupCrossRef(CardGroupCrossRef(cardId, groupId))
        }
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

    // ---------------------------------------------------------------------
    // Learning concepts
    // ---------------------------------------------------------------------

    /** The learning group [groupId] practises against; null is Global (and no group). */
    private suspend fun learningGroupIdFor(groupId: Long?): Long? =
        groupId?.let { groupDao.getGroupById(it)?.learningGroupId }

    /**
     * Whether a learning context schedules by concept: Global's setting when
     * [learningGroupId] is null, else that learning group's own switch. Off
     * everywhere by default, which keeps card-by-card scheduling.
     */
    suspend fun usesConcepts(learningGroupId: Long?): Boolean =
        if (learningGroupId == null) preferences.globalConceptScheduling.first()
        else groupDao.getLearningGroupById(learningGroupId)?.useConcepts == true

    /**
     * Draw the cards for a session: by concept where the session's learning
     * context has concept scheduling on ([drawCardsByConcept]), otherwise
     * card by card, exactly as before concepts -- due cards first, topped up
     * with the rest of the pool.
     */
    suspend fun drawCards(limit: Int, groupId: Long?): List<Card> {
        if (usesConcepts(learningGroupIdFor(groupId))) return drawCardsByConcept(limit, groupId)
        val dueCards = if (groupId != null) getDueCardsByGroup(groupId, limit) else getDueCards(limit)
        if (dueCards.size >= limit) return dueCards.take(limit)
        val allCards = if (groupId != null) getCardsByGroupSync(groupId) else getAllCardsSync()
        return allCards.take(limit)
    }

    /**
     * Every concept's state in one learning context: Global when
     * [learningGroupId] is null, else that learning group's. A concept never
     * practised in the learning group is new there -- the learning group is
     * a separate set of progress, so it does not inherit Global's.
     */
    suspend fun conceptStatesIn(learningGroupId: Long?): Map<Long, ConceptLearningState> {
        val concepts = conceptDao.getAllConceptsSync()
        if (learningGroupId == null) return concepts.associate { it.id to it.globalState() }
        val stored = conceptDao.getConceptLearningStatesForLearningGroup(learningGroupId)
            .associateBy { it.conceptId }
        return concepts.associate { concept ->
            concept.id to (stored[concept.id] ?: ConceptLearningState(concept.id, learningGroupId))
        }
    }

    /**
     * Draw the cards for a session, scheduling by concept.
     *
     * Each card in the pool (the group's cards, or the whole collection) is
     * either tagged with concepts or not. An untagged card is scheduled as it
     * always was, by its own state. A tagged card has no due date of its own:
     * what comes due is its concepts.
     *
     * The due units -- untagged cards and concepts -- are put in due order
     * (bucket-shuffled when the group randomises), and taken in that order.
     * An untagged card is simply taken. A concept that no card taken so far
     * covers picks a card for itself: of the cards carrying it, the one
     * carrying the most of the other uncovered due concepts, so each card
     * shown practises as many of the due concepts as it can. Its concepts are
     * then covered, and the walk goes on until the session is full.
     *
     * A session that the due units do not fill is topped up with the rest
     * of the pool, soonest due first -- additional study, as before.
     */
    internal suspend fun drawCardsByConcept(limit: Int, groupId: Long?): List<Card> {
        if (limit <= 0) return emptyList()
        val pool = if (groupId != null) cardDao.getCardsByGroupSync(groupId) else cardDao.getAllCardsSync()
        if (pool.isEmpty()) return emptyList()

        // How a session is drawn is the learning group's to say, as for cards.
        val learningGroup = learningGroupFor(groupId)
        val learningGroupId = learningGroup?.id
        val shouldRandomize = learningGroup?.randomizeDueCards ?: preferences.randomizeDueCards.first()
        val bucketHours = learningGroup?.randomizeBucketHours ?: preferences.randomizeBucketHours.first()

        val poolIds = pool.map { it.id }.toSet()
        val links = conceptDao.getAllCardConceptCrossRefsSync()
        // Scoring uses each card's share of each concept, not the raw weights.
        val weights = conceptShares(links)
        val conceptsByCard: Map<Long, Set<Long>> = links
            .filter { it.cardId in poolIds }
            .groupBy({ it.cardId }, { it.conceptId })
            .mapValues { it.value.toSet() }
        val conceptStates = if (conceptsByCard.isEmpty()) emptyMap() else conceptStatesIn(learningGroupId)

        // An untagged card's due date in this context.
        val cardLearningStates = if (learningGroupId == null) emptyMap() else
            pool.filter { it.id !in conceptsByCard }.mapNotNull { card ->
                groupDao.getLearningState(card.id, learningGroupId)?.let { card.id to it.nextReview }
            }.toMap()
        fun cardDue(card: Card): Long = cardLearningStates[card.id] ?: card.nextReview
        fun conceptDue(conceptId: Long): Long = conceptStates[conceptId]?.nextReview ?: 0L
        // A tagged card is as due as its most-due concept.
        fun taggedDue(card: Card): Long =
            conceptsByCard[card.id].orEmpty().minOfOrNull { conceptDue(it) } ?: cardDue(card)

        val now = Time.now()
        val units = buildList {
            pool.filter { it.id !in conceptsByCard && cardDue(it) <= now }
                .forEach { add(DueUnit.OfCard(it, cardDue(it))) }
            conceptsByCard.values.flatten().toSet()
                .filter { conceptDue(it) <= now }
                .forEach { add(DueUnit.OfConcept(it, conceptDue(it))) }
        }.sortedBy { it.due }
        val ordered = if (shouldRandomize) shuffleByBucket(units, bucketHours) { it.due } else units

        val selected = LinkedHashMap<Long, Card>()
        val uncovered = units.filterIsInstance<DueUnit.OfConcept>().map { it.conceptId }.toMutableSet()
        val tagged = pool.filter { it.id in conceptsByCard }

        for (unit in ordered) {
            if (selected.size >= limit) break
            when (unit) {
                is DueUnit.OfCard -> selected.getOrPut(unit.card.id) { unit.card }
                is DueUnit.OfConcept -> {
                    if (unit.conceptId !in uncovered) continue
                    val candidates = tagged.filter {
                        it.id !in selected && unit.conceptId in conceptsByCard.getValue(it.id)
                    }
                    if (candidates.isEmpty()) {
                        uncovered -= unit.conceptId
                        continue
                    }
                    val best = pickBestCoveringCard(candidates, conceptsByCard, uncovered, shouldRandomize) { card, concept ->
                        weights[card to concept] ?: 1.0
                    }
                    selected[best.id] = best
                    uncovered -= conceptsByCard.getValue(best.id)
                }
            }
        }

        if (selected.size < limit) {
            val rest = pool.filter { it.id !in selected }
                .sortedBy { if (it.id in conceptsByCard) taggedDue(it) else cardDue(it) }
            val fill = if (shouldRandomize) rest.shuffled() else rest
            fill.take(limit - selected.size).forEach { selected[it.id] = it }
        }
        return selected.values.toList()
    }

    private sealed class DueUnit(val due: Long) {
        class OfCard(val card: Card, due: Long) : DueUnit(due)
        class OfConcept(val conceptId: Long, due: Long) : DueUnit(due)
    }

    // ---------- Deriving concept state from card history ----------
    //
    // A concept's learning state is not something a concept accumulates on
    // its own: it is a function of the review history of the cards that carry
    // it. Every learning context's state is rebuilt by replaying those cards'
    // reviews, oldest first, so tagging a card with a concept immediately
    // gives the concept what that card has already been through, untagging
    // takes it away again, and a concept whose cards have no history is
    // simply new. What is stored on the concept and in
    // concept_learning_state is that replay's result, kept so that drawing a
    // session does not have to replay anything.
    //
    // Which context a review belongs to is read from the group its log
    // names, as it stands now: a group in a learning group counts towards
    // that learning group, a group learning Global -- or no group, or a grade
    // from the card editor -- towards Global. A log naming a group that no
    // longer exists cannot be placed and is left out.
    //
    // The state is derived for every context the history reaches, whether
    // or not that context schedules by concept; the switch only decides
    // whether a session draws by it.

    /** Rederive every concept carried by any of [cardIds]. */
    suspend fun rederiveConceptsForCards(cardIds: Collection<Long>) {
        if (cardIds.isEmpty()) return
        val ids = conceptDao.getAllCardConceptCrossRefsSync()
            .filter { it.cardId in cardIds }
            .map { it.conceptId }
            .toSet()
        if (ids.isNotEmpty()) rederiveConcepts(ids)
    }

    /**
     * Make [conceptIds] exactly the concepts [cardId] carries, each with its
     * weight in [weights] (1 when absent), and rederive every concept that changed.
     */
    suspend fun setCardConcepts(
        cardId: Long,
        conceptIds: Collection<Long>,
        weights: Map<Long, Double> = emptyMap()
    ) {
        val before = conceptDao.getCardConceptLinksSync(cardId).associate { it.conceptId to it.weight }
        val after = conceptIds.associateWith { CardConceptCrossRef.sanitize(weights[it]) }
        if (before == after) return
        conceptDao.deleteAllConceptsForCard(cardId)
        after.forEach { (conceptId, weight) -> conceptDao.insertCardConceptCrossRef(CardConceptCrossRef(cardId, conceptId, weight)) }
        // Shares are proportions within the card, so any change to its weights
        // can move every one of its concepts: rederive them all.
        rederiveConcepts(before.keys + after.keys)
    }

    /**
     * Rebuild the state of [conceptIds] (every concept when null) in every
     * learning context from their cards' review history.
     *
     * @param onlyWithHistory leave a concept whose cards have no history at
     *   all as it is, rather than making it new. For imports, which may bring
     *   a concept's state in a file without the history behind it.
     */
    suspend fun rederiveConcepts(conceptIds: Collection<Long>? = null, onlyWithHistory: Boolean = false) {
        val concepts = if (conceptIds == null) conceptDao.getAllConceptsSync()
            else conceptDao.getConceptsByIds(conceptIds.toList())
        if (concepts.isEmpty()) return
        // Each concept's cards, with each card's share of the concept.
        val links = conceptDao.getAllCardConceptCrossRefsSync()
        val shares = conceptShares(links)
        val cardsByConcept = links
            .groupBy({ it.conceptId }, { it.cardId to shares.getValue(it.cardId to it.conceptId) })
            .mapValues { it.value.toMap() }
        val logsByCard = reviewLogDao.getAllReviewLogsSync().groupBy { it.cardId }
        val groups = GroupLookup(groupDao.getAllGroups().first())
        val learningGroupIds = groupDao.getAllLearningGroups().first().map { it.id }.toSet()
        // When each card's progress was last reset, per context: the card's
        // own for Global, its learning-group state's for each learning group.
        val globalResets = cardDao.getAllCardsAsList().associate { it.id to it.lastReset }
        val learningGroupResets = groupDao.getAllLearningStatesSync()
            .associate { (it.cardId to it.learningGroupId) to it.lastReset }
        fun resetOf(cardId: Long, context: ContextKey): Long = when (val lg = context.learningGroupId) {
            null -> globalResets[cardId] ?: 0L
            else -> learningGroupResets[cardId to lg] ?: 0L
        }

        concepts.forEach { concept ->
            val weights = cardsByConcept[concept.id].orEmpty()
            val logs = weights.keys.flatMap { logsByCard[it].orEmpty() }
                .filter { it.grade != Grade.SKIP.value }
            if (onlyWithHistory && logs.isEmpty()) return@forEach

            val byContext = logs.mapNotNull { log ->
                val context = when {
                    log.groupUid == null && (log.groupName == null || log.groupName == GROUP_NAME_CARD_EDIT) ->
                        ContextKey(null)
                    else -> groups.of(log)?.let { ContextKey(it.learningGroupId) } ?: return@mapNotNull null
                }
                // Only progress since the card was last reset in that context.
                if (log.reviewTime <= resetOf(log.cardId, context)) null else context to log
            }.groupBy({ it.first }, { it.second })

            val weightOf = { cardId: Long -> weights[cardId] ?: 1.0 }
            val global = replayConceptHistory(concept.id, 0L, byContext[ContextKey(null)].orEmpty(), groups, weightOf)
                ?: ConceptLearningState(concept.id, 0L)
            conceptDao.updateConcept(concept.withGlobalState(global))

            conceptDao.deleteAllLearningStatesForConcept(concept.id)
            byContext.forEach { (key, contextLogs) ->
                val learningGroupId = key.learningGroupId ?: return@forEach
                if (learningGroupId !in learningGroupIds) return@forEach
                replayConceptHistory(concept.id, learningGroupId, contextLogs, groups, weightOf)
                    ?.let { conceptDao.upsertConceptLearningState(it) }
            }
        }
    }

    /** A learning context: a learning group's id, or null for Global. */
    private data class ContextKey(val learningGroupId: Long?)

    /**
     * Finds the group a review was made in: by its stable identifier, or --
     * for a review recorded or imported without one -- by the name it noted,
     * the oldest group of that name winning, as the migration did.
     */
    private class GroupLookup(groups: List<Group>) {
        private val byUid = groups.associateBy { it.uid }
        private val byName = groups.sortedBy { it.id }.groupBy { it.name }.mapValues { it.value.first() }
        fun of(log: ReviewLog): Group? = of(log.groupUid, log.groupName)
        fun of(uid: String?, name: String?): Group? =
            if (uid != null) byUid[uid] else name?.let { byName[it] }
    }

    /** Replay one context's logs into a state, or null when they hold no review. */
    private suspend fun replayConceptHistory(
        conceptId: Long,
        learningGroupId: Long,
        logs: List<ReviewLog>,
        groups: GroupLookup,
        weightOf: (cardId: Long) -> Double
    ): ConceptLearningState? {
        val events = conceptReviewEvents(logs, weightOf)
        if (events.isEmpty()) return null
        var state = ConceptLearningState(conceptId, learningGroupId)
        events.forEach { event ->
            val groupId = groups.of(event.groupUid, event.groupName)?.id
            state = scheduleConceptState(state, event.grade, event.time, groupId, event.stabilityMultiplier)
        }
        return state
    }

    /**
     * One FSRS step of a concept at [now].
     *
     * Fuzz is always off here: a replay has to land on the same state every
     * time it is run, or rederiving would move concepts about at random.
     */
    private suspend fun scheduleConceptState(
        state: ConceptLearningState,
        grade: Grade,
        now: Long,
        groupId: Long?,
        stabilityMultiplier: Double
    ): ConceptLearningState {
        fsrsAlgorithm.setMaximumInterval(resolveMaximumInterval(groupId))
        fsrsAlgorithm.setRequestRetention(resolveFsrsRetention(groupId))
        fsrsAlgorithm.setEnableFuzzing(false)

        val elapsedDays = if (state.lastReview == 0L) 0 else
            ((now - state.lastReview) / (1000 * 60 * 60 * 24)).toInt()
        val fsrsCard = FSRSAlgorithm.FSRSCard(
            stability = state.fsrsStability,
            difficulty = state.fsrsDifficulty,
            elapsedDays = elapsedDays,
            scheduledDays = state.fsrsScheduledDays,
            reps = state.fsrsReps,
            lapses = state.fsrsLapses,
            state = FSRSAlgorithm.CardState.valueOf(state.fsrsState),
            lastReview = state.lastReview
        )
        val rating = when (grade) {
            Grade.SKIP -> throw IllegalArgumentException("SKIP grade should not be processed")
            Grade.AGAIN -> FSRSAlgorithm.Rating.AGAIN
            Grade.HARD -> FSRSAlgorithm.Rating.HARD
            Grade.GOOD -> FSRSAlgorithm.Rating.GOOD
            Grade.EASY -> FSRSAlgorithm.Rating.EASY
        }
        val newCard = fsrsAlgorithm.schedule(fsrsCard, rating, now, stabilityMultiplier).card
        val adjustedDays = adjustForPracticeFrequency(newCard.scheduledDays, groupId, now)
        return state.copy(
            fsrsStability = newCard.stability,
            fsrsDifficulty = newCard.difficulty,
            fsrsElapsedDays = newCard.elapsedDays,
            fsrsScheduledDays = adjustedDays,
            fsrsReps = newCard.reps,
            fsrsLapses = newCard.lapses,
            fsrsState = newCard.state.name,
            lastReview = now,
            nextReview = now + (adjustedDays * 24 * 60 * 60 * 1000L),
            modified = now
        )
    }

    // Practice session operations
    suspend fun createPracticeSession(cardIds: List<Long>): Long {
        val session = PracticeSession(
            cardIds = cardIds.joinToString(","),
            completed = false
        )
        return sessionDao.insertSession(session)
    }

    suspend fun getActiveSession(): PracticeSession? = sessionDao.getActiveSession()

    fun getActiveSessionFlow(): Flow<PracticeSession?> = sessionDao.getActiveSessionFlow()

    suspend fun getSessionById(sessionId: Long): PracticeSession? = sessionDao.getSessionById(sessionId)

    suspend fun completeSession(
        sessionId: Long,
        grades: List<Grade>,
        notes: String = "",
        imagePaths: List<String> = emptyList()
    ) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val updatedSession = session.copy(
            endTime = Time.now(),
            completed = true,
            grades = grades.joinToString(",") { it.value.toString() },
            notes = notes,
            imagePaths = imagePaths.joinToString(",")
        )
        sessionDao.updateSession(updatedSession)
    }

    /** Rewrite what was noted about a session's fight, from the history screen. */
    suspend fun updateSessionNotes(sessionId: Long, notes: String, imagePaths: List<String>) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.updateSession(session.copy(notes = notes, imagePaths = imagePaths.joinToString(",")))
    }

    /**
     * Reassign the opponent of every review in a session at once.
     *
     * The opponent belongs to the fight, and the session is the fight, so it is
     * changed for the whole session or not at all. Metadata only, like any
     * history correction: the reviews were scheduled with the multiplier they
     * recorded, and they keep it.
     */
    suspend fun updateSessionOpponent(sessionId: Long, opponentId: Long?) {
        val opponentUid = opponentId?.let { opponentDao.getOpponentById(it)?.uid }
        reviewLogDao.updateOpponentForSession(sessionId, opponentId, opponentUid)
    }

    /** Set one review's opponent, by local id and by identifier together. */
    suspend fun updateReviewLogOpponent(reviewLog: ReviewLog, opponentId: Long?) {
        val opponentUid = opponentId?.let { opponentDao.getOpponentById(it)?.uid }
        reviewLogDao.updateReviewLog(reviewLog.copy(opponentId = opponentId, opponentUid = opponentUid))
    }

    /**
     * Bring every review's opponent reference into line after opponents or
     * history arrive by import: see [ReviewLogDao.fillOpponentUids] and
     * [ReviewLogDao.relinkOpponentIds].
     */
    suspend fun reconcileOpponentReferences() {
        reviewLogDao.fillOpponentUids()
        reviewLogDao.relinkOpponentIds()
    }

    fun getSessionByIdFlow(sessionId: Long): Flow<PracticeSession?> =
        sessionDao.getSessionByIdFlow(sessionId)

    suspend fun getAllSessionsSync(): List<PracticeSession> = sessionDao.getAllSessionsSync()

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

    /** The card groups, for naming them as they are now (history, by stable identifier). */
    fun getAllGroups(): Flow<List<Group>> = groupDao.getAllGroups()

    /** Local card id by stable identifier, for placing imported history. */
    suspend fun cardIdsByUid(): Map<String, Long> = cardDao.getAllCardsAsList().associate { it.uid to it.id }

    fun getAllSessions(): Flow<List<PracticeSession>> = sessionDao.getAllSessions()

    // Learning group state operations
    suspend fun updateLearningState(learningState: CardLearningState) =
        groupDao.updateLearningState(learningState)

    // Review operations
    suspend fun reviewCard(
        card: Card,
        grade: Grade,
        sessionId: Long? = null,
        groupId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null
    ): Card {
        val now = Time.now()
        val elapsedDays = if (card.lastReview == 0L) 0 else
            ((now - card.lastReview) / (1000 * 60 * 60 * 24)).toInt()

        val group = groupId?.let { groupDao.getGroupById(it) }
        val groupName = group?.name
        val groupUid = group?.uid
        val stabilityMultiplier = resolveStabilityMultiplier(opponentId, fightDifficulty)
        val opponentUid = opponentId?.let { opponentDao.getOpponentById(it)?.uid }

        if (grade == Grade.SKIP) {
            val stateBefore = serializeCardState(card)
            reviewLogDao.insertReviewLog(ReviewLog(
                cardId = card.id,
                sessionId = sessionId,
                reviewTime = now,
                grade = grade.value,
                algorithm = ALGORITHM_NAME,
                stateBefore = stateBefore,
                stateAfter = stateBefore,
                scheduledDays = card.fsrsScheduledDays,
                elapsedDays = elapsedDays,
                groupName = groupName,
                groupUid = groupUid,
                opponentId = opponentId,
                opponentUid = opponentUid,
                fightDifficulty = fightDifficulty,
                stabilityMultiplier = stabilityMultiplier
            ))
            return card
        }

        val updatedCard = reviewWithFSRS(card, grade, now, elapsedDays, groupId, stabilityMultiplier)

        // Create review log
        val reviewLog = ReviewLog(
            cardId = card.id,
            sessionId = sessionId,
            reviewTime = now,
            grade = grade.value,
            algorithm = ALGORITHM_NAME,
            stateBefore = serializeCardState(card),
            stateAfter = serializeCardState(updatedCard),
            scheduledDays = updatedCard.fsrsScheduledDays,
            elapsedDays = elapsedDays,
            groupName = groupName,
            groupUid = groupUid,
            opponentId = opponentId,
            opponentUid = opponentUid,
            fightDifficulty = fightDifficulty,
            stabilityMultiplier = stabilityMultiplier
        )

        reviewLogDao.insertReviewLog(reviewLog)
        cardDao.updateCard(updatedCard)

        return updatedCard
    }

    suspend fun reviewCardWithGroup(
        card: Card,
        grade: Grade,
        groupId: Long,
        sessionId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null
    ): Card {
        val group = groupDao.getGroupById(groupId)
            ?: return reviewCard(card, grade, sessionId, groupId, opponentId, fightDifficulty)

        val learningGroupId = group.learningGroupId
            // Global learning state, with the group's settings applied
            ?: return reviewCard(card, grade, sessionId, groupId, opponentId, fightDifficulty)

        // The state kept in the group's learning group
        val now = Time.now()
        var learningState = groupDao.getLearningState(card.id, learningGroupId)

        // Initialize learning state if it doesn't exist
        if (learningState == null) {
            learningState = CardLearningState(card.id, learningGroupId)
            groupDao.insertLearningState(learningState)
        }

        val elapsedDays = if (learningState.lastReview == 0L) 0 else
            ((now - learningState.lastReview) / (1000 * 60 * 60 * 24)).toInt()

        val stabilityMultiplier = resolveStabilityMultiplier(opponentId, fightDifficulty)
        val opponentUid = opponentId?.let { opponentDao.getOpponentById(it)?.uid }

        if (grade == Grade.SKIP) {
            val stateBefore = serializeLearningState(learningState)
            reviewLogDao.insertReviewLog(ReviewLog(
                cardId = card.id,
                sessionId = sessionId,
                reviewTime = now,
                grade = grade.value,
                algorithm = ALGORITHM_NAME,
                stateBefore = stateBefore,
                stateAfter = stateBefore,
                scheduledDays = learningState.fsrsScheduledDays,
                elapsedDays = elapsedDays,
                groupName = group.name,
                groupUid = group.uid,
                opponentId = opponentId,
                opponentUid = opponentUid,
                fightDifficulty = fightDifficulty,
                stabilityMultiplier = stabilityMultiplier
            ))
            return card
        }

        val updatedState = reviewLearningStateWithFSRS(learningState, grade, now, elapsedDays, groupId, stabilityMultiplier)

        // Create review log
        val reviewLog = ReviewLog(
            cardId = card.id,
            sessionId = sessionId,
            reviewTime = now,
            grade = grade.value,
            algorithm = ALGORITHM_NAME,
            stateBefore = serializeLearningState(learningState),
            stateAfter = serializeLearningState(updatedState),
            scheduledDays = updatedState.fsrsScheduledDays,
            elapsedDays = elapsedDays,
            groupName = group.name,
            groupUid = group.uid,
            opponentId = opponentId,
            opponentUid = opponentUid,
            fightDifficulty = fightDifficulty,
            stabilityMultiplier = stabilityMultiplier
        )

        reviewLogDao.insertReviewLog(reviewLog)
        groupDao.updateLearningState(updatedState)

        // Return a virtual card with the group-specific state (for UI display)
        return card.copy(
            fsrsStability = updatedState.fsrsStability,
            fsrsDifficulty = updatedState.fsrsDifficulty,
            fsrsElapsedDays = updatedState.fsrsElapsedDays,
            fsrsScheduledDays = updatedState.fsrsScheduledDays,
            fsrsReps = updatedState.fsrsReps,
            fsrsLapses = updatedState.fsrsLapses,
            fsrsState = updatedState.fsrsState,
            lastReview = updatedState.lastReview,
            nextReview = updatedState.nextReview
        )
    }

    /** Compute the result of grading [card] without persisting anything to the database. */
    suspend fun computeReview(
        card: Card,
        grade: Grade,
        groupId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null
    ): Card {
        val now = Time.now()
        val elapsedDays = if (card.lastReview == 0L) 0 else
            ((now - card.lastReview) / (1000 * 60 * 60 * 24)).toInt()
        val stabilityMultiplier = resolveStabilityMultiplier(opponentId, fightDifficulty)
        return reviewWithFSRS(card, grade, now, elapsedDays, groupId, stabilityMultiplier)
    }

    /** Compute the result of grading [card] in a specific group without persisting. */
    suspend fun computeReviewWithGroup(
        card: Card,
        grade: Grade,
        groupId: Long,
        opponentId: Long? = null,
        fightDifficulty: Int? = null
    ): Card {
        val group = groupDao.getGroupById(groupId)
            ?: return computeReview(card, grade, groupId, opponentId, fightDifficulty)
        val learningGroupId = group.learningGroupId
            ?: return computeReview(card, grade, groupId, opponentId, fightDifficulty)
        val now = Time.now()
        val learningState = groupDao.getLearningState(card.id, learningGroupId)
            ?: CardLearningState(card.id, learningGroupId)
        val elapsedDays = if (learningState.lastReview == 0L) 0 else
            ((now - learningState.lastReview) / (1000 * 60 * 60 * 24)).toInt()
        val stabilityMultiplier = resolveStabilityMultiplier(opponentId, fightDifficulty)
        val updatedState = reviewLearningStateWithFSRS(learningState, grade, now, elapsedDays, groupId, stabilityMultiplier)
        return card.copy(
            fsrsStability = updatedState.fsrsStability,
            fsrsDifficulty = updatedState.fsrsDifficulty,
            fsrsElapsedDays = updatedState.fsrsElapsedDays,
            fsrsScheduledDays = updatedState.fsrsScheduledDays,
            fsrsReps = updatedState.fsrsReps,
            fsrsLapses = updatedState.fsrsLapses,
            fsrsState = updatedState.fsrsState,
            lastReview = updatedState.lastReview,
            nextReview = updatedState.nextReview
        )
    }

    /**
     * Batch-review the cards of one fight.
     *
     * The opponent and the fight's difficulty are arguments to the batch rather
     * than to each card in it, because that is what they describe: one fight,
     * whose every card is graded against it. The multiplier they earn is
     * therefore resolved once here, not per card.
     */
    suspend fun reviewMultipleCards(
        cardsWithGrades: List<Pair<Card, Grade>>,
        sessionId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null
    ) {
        val now = Time.now()
        val reviewLogs = mutableListOf<ReviewLog>()
        val updatedCards = mutableListOf<Card>()
        val stabilityMultiplier = resolveStabilityMultiplier(opponentId, fightDifficulty)
        val opponentUid = opponentId?.let { opponentDao.getOpponentById(it)?.uid }

        cardsWithGrades.forEach { (card, grade) ->
            val elapsedDays = if (card.lastReview == 0L) 0 else
                ((now - card.lastReview) / (1000 * 60 * 60 * 24)).toInt()

            if (grade == Grade.SKIP) {
                val stateBefore = serializeCardState(card)
                reviewLogs.add(ReviewLog(
                    cardId = card.id,
                    sessionId = sessionId,
                    reviewTime = now,
                    grade = grade.value,
                    algorithm = ALGORITHM_NAME,
                    stateBefore = stateBefore,
                    stateAfter = stateBefore,
                    scheduledDays = card.fsrsScheduledDays,
                    elapsedDays = elapsedDays,
                    opponentId = opponentId,
                    opponentUid = opponentUid,
                    fightDifficulty = fightDifficulty,
                    stabilityMultiplier = stabilityMultiplier
                ))
                return@forEach
            }

            val updatedCard = reviewWithFSRS(card, grade, now, elapsedDays, null, stabilityMultiplier)

            reviewLogs.add(
                ReviewLog(
                    cardId = card.id,
                    sessionId = sessionId,
                    reviewTime = now,
                    grade = grade.value,
                    algorithm = ALGORITHM_NAME,
                    stateBefore = serializeCardState(card),
                    stateAfter = serializeCardState(updatedCard),
                    scheduledDays = updatedCard.fsrsScheduledDays,
                    elapsedDays = elapsedDays,
                    opponentId = opponentId,
                    opponentUid = opponentUid,
                    fightDifficulty = fightDifficulty,
                    stabilityMultiplier = stabilityMultiplier
                )
            )

            updatedCards.add(updatedCard)
        }

        reviewLogDao.insertReviewLogs(reviewLogs)
        cardDao.updateCards(updatedCards)
    }


    private suspend fun reviewWithFSRS(
        card: Card,
        grade: Grade,
        now: Long,
        elapsedDays: Int,
        groupId: Long? = null,
        stabilityMultiplier: Double = 1.0
    ): Card {
        // Resolve maximum interval, desired retention, and fuzzing (group override or global)
        val maxInterval = resolveMaximumInterval(groupId)
        fsrsAlgorithm.setMaximumInterval(maxInterval)
        val retention = resolveFsrsRetention(groupId)
        fsrsAlgorithm.setRequestRetention(retention)
        val fuzzing = resolveFsrsEnableFuzzing(groupId)
        fsrsAlgorithm.setEnableFuzzing(fuzzing)

        val fsrsCard = FSRSAlgorithm.FSRSCard(
            stability = card.fsrsStability,
            difficulty = card.fsrsDifficulty,
            elapsedDays = elapsedDays,
            scheduledDays = card.fsrsScheduledDays,
            reps = card.fsrsReps,
            lapses = card.fsrsLapses,
            state = FSRSAlgorithm.CardState.valueOf(card.fsrsState),
            lastReview = card.lastReview
        )

        val rating = when (grade) {
            Grade.SKIP -> throw IllegalArgumentException("SKIP grade should not be processed")
            Grade.AGAIN -> FSRSAlgorithm.Rating.AGAIN
            Grade.HARD -> FSRSAlgorithm.Rating.HARD
            Grade.GOOD -> FSRSAlgorithm.Rating.GOOD
            Grade.EASY -> FSRSAlgorithm.Rating.EASY
        }

        val schedulingInfo = fsrsAlgorithm.schedule(fsrsCard, rating, now, stabilityMultiplier)
        val newCard = schedulingInfo.card
        val adjustedDays = adjustForPracticeFrequency(newCard.scheduledDays, groupId)

        return card.copy(
            fsrsStability = newCard.stability,
            fsrsDifficulty = newCard.difficulty,
            fsrsElapsedDays = newCard.elapsedDays,
            fsrsScheduledDays = adjustedDays,
            fsrsReps = newCard.reps,
            fsrsLapses = newCard.lapses,
            fsrsState = newCard.state.name,
            lastReview = now,
            nextReview = now + (adjustedDays * 24 * 60 * 60 * 1000L),
            modified = now
        )
    }

    /**
     * Adjust scheduled days based on practice-days setting.
     * Snaps the interval to the nearest selected practice day so cards
     * come due on days the user will actually practice.
     *
     * Practice days use ISO-8601 convention: 1=Monday through 7=Sunday.
     */
    /**
     * The learning group [groupId] practises against, whose settings schedule
     * its learning progress; null for a Global group, or no group at all,
     * which the app-wide settings schedule.
     */
    private suspend fun learningGroupFor(groupId: Long?): LearningGroup? {
        val learningGroupId = groupId?.let { groupDao.getGroupById(it) }?.learningGroupId ?: return null
        return groupDao.getLearningGroupById(learningGroupId)
    }

    /** Resolve maximum interval: learning group override takes precedence over global. */
    private suspend fun resolveMaximumInterval(groupId: Long?): Int =
        learningGroupFor(groupId)?.maximumInterval ?: preferences.maximumInterval.first()

    /** Resolve practice days: learning group override takes precedence over global. */
    private suspend fun resolvePracticeDays(groupId: Long?): Set<Int> =
        learningGroupFor(groupId)?.parsePracticeDays() ?: preferences.practiceDays.first()

    /** Resolve FSRS desired retention (integer percent): learning group override takes precedence over global. */
    private suspend fun resolveFsrsRetention(groupId: Long?): Int =
        learningGroupFor(groupId)?.fsrsRetention ?: preferences.fsrsRetention.first()

    /** Resolve FSRS interval fuzzing flag: learning group override takes precedence over global. */
    private suspend fun resolveFsrsEnableFuzzing(groupId: Long?): Boolean =
        learningGroupFor(groupId)?.fsrsEnableFuzzing ?: preferences.fsrsEnableFuzzing.first()

    private suspend fun adjustForPracticeFrequency(
        scheduledDays: Int,
        groupId: Long? = null,
        now: Long = Time.now()
    ): Int {
        val practiceDays = resolvePracticeDays(groupId)
        if (practiceDays.size >= 7 || practiceDays.isEmpty() || scheduledDays <= 1) return scheduledDays

        val targetDow = isoDayOfWeekAfter(now, scheduledDays, Time.utcOffsetSeconds())

        return scheduledDays + forwardDaysToNearestPracticeDay(targetDow, practiceDays)
    }

    private fun serializeCardState(card: Card): String =
        "S:${card.fsrsStability},D:${card.fsrsDifficulty},ST:${card.fsrsState}"

    private suspend fun reviewLearningStateWithFSRS(
        learningState: CardLearningState,
        grade: Grade,
        now: Long,
        elapsedDays: Int,
        groupId: Long? = null,
        stabilityMultiplier: Double = 1.0
    ): CardLearningState {
        // Resolve maximum interval, desired retention, and fuzzing (group override or global)
        val maxInterval = resolveMaximumInterval(groupId)
        fsrsAlgorithm.setMaximumInterval(maxInterval)
        val retention = resolveFsrsRetention(groupId)
        fsrsAlgorithm.setRequestRetention(retention)
        val fuzzing = resolveFsrsEnableFuzzing(groupId)
        fsrsAlgorithm.setEnableFuzzing(fuzzing)

        val fsrsCard = FSRSAlgorithm.FSRSCard(
            stability = learningState.fsrsStability,
            difficulty = learningState.fsrsDifficulty,
            elapsedDays = elapsedDays,
            scheduledDays = learningState.fsrsScheduledDays,
            reps = learningState.fsrsReps,
            lapses = learningState.fsrsLapses,
            state = FSRSAlgorithm.CardState.valueOf(learningState.fsrsState),
            lastReview = learningState.lastReview
        )

        val rating = when (grade) {
            Grade.SKIP -> throw IllegalArgumentException("SKIP grade should not be processed")
            Grade.AGAIN -> FSRSAlgorithm.Rating.AGAIN
            Grade.HARD -> FSRSAlgorithm.Rating.HARD
            Grade.GOOD -> FSRSAlgorithm.Rating.GOOD
            Grade.EASY -> FSRSAlgorithm.Rating.EASY
        }

        val schedulingInfo = fsrsAlgorithm.schedule(fsrsCard, rating, now, stabilityMultiplier)
        val newCard = schedulingInfo.card
        val adjustedDays = adjustForPracticeFrequency(newCard.scheduledDays, groupId)

        return learningState.copy(
            fsrsStability = newCard.stability,
            fsrsDifficulty = newCard.difficulty,
            fsrsElapsedDays = newCard.elapsedDays,
            fsrsScheduledDays = adjustedDays,
            fsrsReps = newCard.reps,
            fsrsLapses = newCard.lapses,
            fsrsState = newCard.state.name,
            lastReview = now,
            nextReview = now + (adjustedDays * 24 * 60 * 60 * 1000L),
            modified = now
        )
    }

    private fun serializeLearningState(learningState: CardLearningState): String =
        "S:${learningState.fsrsStability},D:${learningState.fsrsDifficulty},ST:${learningState.fsrsState}"

    /**
     * Randomize cards by grouping them into time buckets of the given size.
     * Cards within the same bucket are shuffled randomly; bucket ordering
     * is preserved so more-overdue cards still come first.
     */
    private fun randomizeCardsByBucket(cards: List<Card>, bucketHours: Int): List<Card> =
        shuffleByBucket(cards, bucketHours) { it.nextReview }

    companion object {
        /**
         * Given a target day-of-week (ISO-8601: 1=Monday..7=Sunday) and a set of practice
         * days, returns how many days forward to advance to reach the nearest practice day.
         * Returns 0 if targetDow is already a practice day. Never rounds backward.
         */
        /**
         * ISO-8601 day of week (1=Monday..7=Sunday) of the local calendar date
         * [days] days after the instant [nowMillis], for a zone currently
         * [utcOffsetSeconds] from UTC.
         *
         * Replaces a `java.util.Calendar` computation. Calendar does not exist
         * on Kotlin/Wasm, and the obvious multiplatform substitute --
         * kotlinx-datetime -- pulls in `java.time`, which on this app's
         * minSdk of 24 would require core library desugaring to avoid crashing
         * on API 24/25. Plain arithmetic on the day number avoids both
         * problems and needs no dependency at all.
         *
         * Shifting the instant by the offset and then dividing gives the local
         * day number; adding whole days to that is calendar-day arithmetic, so
         * a span crossing a daylight-saving boundary still lands on the
         * intended weekday, exactly as `Calendar.add(DAY_OF_YEAR, n)` did.
         *
         * The +3 offset is the epoch's weekday: 1970-01-01 was a Thursday,
         * ISO day 4.
         */
        internal fun isoDayOfWeekAfter(nowMillis: Long, days: Int, utcOffsetSeconds: Int): Int {
            val localMillis = nowMillis + utcOffsetSeconds * 1000L
            val localDay = localMillis.floorDiv(MILLIS_PER_DAY) + days
            return ((localDay + 3).mod(7L) + 1).toInt()
        }

        private const val MILLIS_PER_DAY = 86_400_000L

        /**
         * Shuffle [items] within time buckets of [bucketHours], keeping the
         * buckets in order so the more overdue still come first.
         */
        internal fun <T> shuffleByBucket(items: List<T>, bucketHours: Int, due: (T) -> Long): List<T> {
            if (items.isEmpty()) return items
            val millisecondsPerBucket = bucketHours.coerceAtLeast(1) * 60 * 60 * 1000L
            return items.groupBy { due(it) / millisecondsPerBucket }
                .entries
                .sortedBy { it.key }
                .flatMap { (_, inBucket) -> inBucket.shuffled() }
        }

        /**
         * The grade a concept gets from the grades of the cards carrying it:
         * their mean, rounded half up to the nearest grade. Callers leave
         * skipped cards out.
         */
        /**
         * The concept reviews a set of card review logs implies, oldest first.
         *
         * The reviews of one practice session are one fight, so all of a
         * session's logs make a single concept review, graded with the
         * rounded mean of its cards' grades, each weighted by how much of the
         * concept its card contains ([weightOf], by card id) -- a concept on
         * three cards of one session is reviewed once, not three times in the
         * same minute. A log outside any session (graded from the card editor,
         * or imported without one) is a review of its own. Skips say nothing
         * and are left out, as are cards that contain none of the concept
         * (weight 0); a session of nothing else is no review at all.
         */
        internal fun conceptReviewEvents(
            logs: List<ReviewLog>,
            weightOf: (cardId: Long) -> Double = { 1.0 }
        ): List<ConceptReviewEvent> =
            logs.filter { it.grade != Grade.SKIP.value && Grade.fromValue(it.grade) != null && weightOf(it.cardId) > 0.0 }
                .groupBy { log -> log.sessionId?.let { "s$it" } ?: "l${log.id}:${log.reviewTime}" }
                .values
                .map { session ->
                    val first = session.minBy { it.reviewTime }
                    ConceptReviewEvent(
                        time = first.reviewTime,
                        grade = combineWeightedConceptGrades(
                            session.map { Grade.fromValue(it.grade)!! to weightOf(it.cardId) }
                        ),
                        groupName = first.groupName,
                        groupUid = first.groupUid,
                        stabilityMultiplier = first.stabilityMultiplier
                    )
                }
                .sortedBy { it.time }

        internal fun combineConceptGrades(grades: List<Grade>): Grade =
            combineWeightedConceptGrades(grades.map { it to 1.0 })

        /**
         * The weighted mean of [grades] -- each grade with how much of the
         * concept its card contains -- rounded half up to the nearest grade.
         * Weights need not add up to anything; at least one must be above 0.
         */
        internal fun combineWeightedConceptGrades(grades: List<Pair<Grade, Double>>): Grade {
            require(grades.isNotEmpty() && grades.none { it.first == Grade.SKIP })
            val total = grades.sumOf { it.second }
            require(total > 0.0) { "No card contains any of the concept" }
            val mean = grades.sumOf { (grade, weight) -> grade.value * weight } / total
            val rounded = kotlin.math.floor(mean + 0.5).toInt().coerceIn(Grade.AGAIN.value, Grade.EASY.value)
            return Grade.fromValue(rounded)!!
        }

        /**
         * Of [candidates], the card covering the most [uncovered] concepts.
         * Ties go to the card whose share is most in them ([weightOf], by card
         * and concept: its normalized weights), so of two cards each touching
         * one due concept the one mostly about it wins; then to the card
         * carrying the fewest concepts that are not due; then to a random one
         * when randomising, else the lowest id.
         */
        internal fun pickBestCoveringCard(
            candidates: List<Card>,
            conceptsByCard: Map<Long, Set<Long>>,
            uncovered: Set<Long>,
            randomize: Boolean,
            weightOf: (cardId: Long, conceptId: Long) -> Double = { _, _ -> 1.0 }
        ): Card {
            val ordered = if (randomize) candidates.shuffled() else candidates.sortedBy { it.id }
            return ordered.maxWith(
                compareBy<Card> { card -> conceptsByCard.getValue(card.id).count { it in uncovered } }
                    .thenBy { card ->
                        conceptsByCard.getValue(card.id).filter { it in uncovered }.sumOf { weightOf(card.id, it) }
                    }
                    .thenByDescending { card -> conceptsByCard.getValue(card.id).count { it !in uncovered } }
            )
        }

        internal fun forwardDaysToNearestPracticeDay(targetDow: Int, practiceDays: Set<Int>): Int {
            if (practiceDays.contains(targetDow)) return 0
            var best = 7
            for (day in practiceDays) {
                val forward = (day - targetDow + 7) % 7
                if (forward in 1 until best) best = forward
            }
            return best
        }
    }
}
