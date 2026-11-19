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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
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

private const val DAY_MILLIS = 24.0 * 60 * 60 * 1000

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

    /** How many cards are due in all-cards practice (Global); see [dueCount]. */
    fun getDueCardCount(): Flow<Int> = dueCount(null)

    /**
     * How many cards are ready for practice in a session over the group's
     * cards ([groupId]), or every card (null), in the learning context the
     * session practises in -- counted the way that context draws.
     *
     * Card by card: the cards due by their own state there. By concept: how
     * many cards the dot-product draw would take, with no session limit,
     * before every due concept's and untagged card's need is met -- so a due
     * concept on thirty cards counts as the one or two it takes, not thirty.
     *
     * Recounted whenever cards, groups, concepts or their states change, and
     * every minute, so cards coming due while the screen is open are counted.
     * Disabled cards never are.
     */
    fun dueCount(groupId: Long?): Flow<Int> = combine(
        combine(cardDao.getAllCardsWithGroups(), groupDao.getAllLearningStates(), groupDao.getAllLearningGroups()) { a, b, c -> Triple(a, b, c) },
        combine(conceptDao.getAllConcepts(), conceptDao.getAllCardConceptCrossRefs(), conceptDao.getAllConceptLearningStates()) { a, b, c -> Triple(a, b, c) },
        preferences.globalConceptScheduling,
        minuteTicker()
    ) { (cardsWithGroups, cardStates, learningGroups), (concepts, links, conceptStates), globalByConcept, now ->
        val learningGroupIds = learningGroups.map { it.id }.toSet()
        val pool: List<Card>
        val learningGroupId: Long?
        if (groupId == null) {
            pool = cardsWithGroups.map { it.card }
            learningGroupId = null
        } else {
            val group = cardsWithGroups.firstNotNullOfOrNull { cwg -> cwg.groups.firstOrNull { it.id == groupId } }
            pool = cardsWithGroups.filter { cwg -> cwg.groups.any { it.id == groupId } }.map { it.card }
            learningGroupId = group?.learningGroupId?.takeIf { it in learningGroupIds }
        }
        val byConcept = if (learningGroupId == null) globalByConcept
            else learningGroups.first { it.id == learningGroupId }.useConcepts
        val enabled = pool.filter { !it.isDisabled }
        val enabledIds = enabled.map { it.id }.toSet()
        countDue(
            enabled, learningGroupId, byConcept,
            cardStates.associateBy { it.cardId to it.learningGroupId },
            conceptShares(links.filter { it.cardId in enabledIds }),
            if (learningGroupId == null) concepts.associate { it.id to it.globalState() }
            else conceptStates.filter { it.learningGroupId == learningGroupId }.associateBy { it.conceptId },
            now,
            fsrsAlgorithm::retrievability
        )
    }

    /** The time now, again every minute. */
    private fun minuteTicker(): Flow<Long> = flow {
        while (true) {
            emit(Time.now())
            delay(60_000L)
        }
    }

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
        val group = groupId?.let { groupDao.getGroupById(it) }
        val mode = if (usesConcepts(group?.let { learningGroupIdFor(it.id) })) SelectionMode.CONCEPT else SelectionMode.CARD
        reviewLogDao.insertReviewLog(
            reviewLogOf(
                cardBefore.id, null, now, grade, cardBefore.fsrs, cardAfter.fsrs, group,
                fightOf(opponentId, fightDifficulty), CardSelection(mode, SelectionReason.EDITOR)
            ).let { if (group == null) it.copy(groupName = GROUP_NAME_CARD_EDIT) else it }
        )
        // A new review in the card's history: its concepts follow.
        rederiveConceptsForCards(listOf(cardBefore.id))
    }

    /**
     * The fight a review was graded against: who (by local id and stable
     * identifier), their skill multiplier as it is now, and the fight's
     * difficulty -- and so the stability multiplier the review earns, the two
     * multiplied together. Either half is neutral (1.0) when missing: no
     * opponent, one since deleted, or an unrated fight.
     */
    private data class Fight(
        val opponentId: Long?,
        val opponentUid: String?,
        val opponentSkill: Double?,
        val difficulty: Int?
    ) {
        val stabilityMultiplier: Double get() = FightDifficulty.combinedMultiplier(opponentSkill ?: 1.0, difficulty)
    }

    private suspend fun fightOf(opponentId: Long?, fightDifficulty: Int?): Fight {
        val opponent = opponentId?.let { opponentDao.getOpponentById(it) }
        return Fight(opponentId, opponent?.uid, opponent?.skillMultiplier, fightDifficulty)
    }

    /**
     * The log of one review: [before] and [after] are the progress it was
     * graded on and left (the same for a skip). Every review written goes
     * through here, so they all record the same things.
     */
    private fun reviewLogOf(
        cardId: Long,
        sessionId: Long?,
        now: Long,
        grade: Grade,
        before: FsrsState,
        after: FsrsState,
        group: Group?,
        fight: Fight,
        selection: CardSelection?
    ) = ReviewLog(
        cardId = cardId,
        sessionId = sessionId,
        reviewTime = now,
        grade = grade.value,
        algorithm = ALGORITHM_NAME,
        stateBefore = before.serialized(),
        stateAfter = after.serialized(),
        scheduledDays = after.scheduledDays,
        elapsedDays = before.daysSinceReview(now),
        groupName = group?.name,
        groupUid = group?.uid,
        opponentId = fight.opponentId,
        opponentUid = fight.opponentUid,
        fightDifficulty = fight.difficulty,
        stabilityMultiplier = fight.stabilityMultiplier,
        opponentSkillMultiplier = fight.opponentSkill,
        selectionMode = selection?.mode?.name,
        selectionReason = selection?.reason?.name,
        selectionConcepts = selection?.conceptUids?.takeIf { it.isNotEmpty() }?.joinToString(","),
        selectionTrigger = selection?.triggerConceptUid
    )

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

    /** How many of [groupId]'s cards are due; see [dueCount]. */
    fun getDueCardCountByGroup(groupId: Long): Flow<Int> = dueCount(groupId)

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
    suspend fun drawCards(limit: Int, groupId: Long?): List<Card> = drawSession(limit, groupId).map { it.card }

    /** [drawCards], with why each card was drawn -- recorded on its review log. */
    suspend fun drawSession(limit: Int, groupId: Long?): List<DrawnCard> {
        if (usesConcepts(learningGroupIdFor(groupId))) return drawSessionByConcept(limit, groupId)
        val dueCards = if (groupId != null) getDueCardsByGroup(groupId, limit) else getDueCards(limit)
        val drawn = if (dueCards.size >= limit) dueCards.take(limit) else {
            val allCards = if (groupId != null) getCardsByGroupSync(groupId) else getAllCardsSync()
            allCards.take(limit)
        }
        val dueIds = dueCards.map { it.id }.toSet()
        return drawn.map {
            DrawnCard(
                it,
                CardSelection(SelectionMode.CARD, if (it.id in dueIds) SelectionReason.DUE else SelectionReason.EXTRA)
            )
        }
    }

    /** How a session in [groupId] is drawn now: by concept or card by card. */
    suspend fun selectionModeFor(groupId: Long?): SelectionMode =
        if (usesConcepts(learningGroupIdFor(groupId))) SelectionMode.CONCEPT else SelectionMode.CARD

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
     * Draw the cards for a session, scheduling by concept, by dot product.
     *
     * Every concept, and every untagged card, has a **need**: 1 minus FSRS's
     * predicted probability of recalling it right now, in this learning
     * context -- 1 for one never learnt -- and 0 if it is not due yet. Every
     * card is a vector of its **shares** of its concepts (its weights
     * normalised to add to 1); an untagged card is all of itself.
     *
     * A card's score is the dot product of the two -- the need it would
     * meet -- times its **staleness**: 0.5 for a card graded just now in this
     * context, rising towards 1 the longer since ([stalenessFactor]); 1 for
     * one never graded here. So of cards meeting much the same need, the one
     * left alone longest wins, and the same cards are not drawn every time.
     * The highest-scoring card is taken; then each of its concepts' need
     * drops by the card's share of it (never below 0), so a card half about
     * a concept leaves half of that concept's need for another card. Repeat
     * until the session is full or nothing left meets any need. Ties go to
     * the lowest id, or at random when the group randomises.
     *
     * A session the need does not fill is topped up with the rest of the
     * pool, soonest due first -- additional study, as before.
     */
    internal suspend fun drawCardsByConcept(limit: Int, groupId: Long?): List<Card> =
        drawSessionByConcept(limit, groupId).map { it.card }

    private suspend fun drawSessionByConcept(limit: Int, groupId: Long?): List<DrawnCard> {
        if (limit <= 0) return emptyList()
        val pool = if (groupId != null) cardDao.getCardsByGroupSync(groupId) else cardDao.getAllCardsSync()
        if (pool.isEmpty()) return emptyList()

        // How a session is drawn is the learning group's to say, as for cards.
        val learningGroup = learningGroupFor(groupId)
        val learningGroupId = learningGroup?.id
        val shouldRandomize = learningGroup?.randomizeDueCards ?: preferences.randomizeDueCards.first()

        val poolIds = pool.map { it.id }.toSet()
        val links = conceptDao.getAllCardConceptCrossRefsSync().filter { it.cardId in poolIds }
        val shares = conceptShares(links)
        val conceptsByCard: Map<Long, Set<Long>> = links
            .groupBy({ it.cardId }, { it.conceptId })
            .mapValues { it.value.toSet() }
        val conceptStates = if (conceptsByCard.isEmpty()) emptyMap() else conceptStatesIn(learningGroupId)

        // An untagged card's own state in this context: (stability, last review, next review).
        val untaggedStates: Map<Long, Triple<Double, Long, Long>> = pool.filter { it.id !in conceptsByCard }
            .associate { card ->
                val state = learningGroupId?.let { groupDao.getLearningState(card.id, it) }
                card.id to when {
                    learningGroupId == null -> Triple(card.fsrsStability, card.lastReview, card.nextReview)
                    state == null -> Triple(0.0, 0L, 0L)   // never practised there: new
                    else -> Triple(state.fsrsStability, state.lastReview, state.nextReview)
                }
            }

        val now = Time.now()
        fun need(stability: Double, lastReview: Long, nextReview: Long): Double =
            needOf(stability, lastReview, nextReview, now, fsrsAlgorithm::retrievability)
        val needs = HashMap<NeedKey, Double>()
        conceptsByCard.values.flatten().toSet().forEach { conceptId ->
            val state = conceptStates[conceptId] ?: return@forEach
            needs[NeedKey.OfConcept(conceptId)] = need(state.fsrsStability, state.lastReview, state.nextReview)
        }
        untaggedStates.forEach { (cardId, s) -> needs[NeedKey.OfCard(cardId)] = need(s.first, s.second, s.third) }

        val vectors: Map<Long, Map<NeedKey, Double>> = pool.associate { card ->
            card.id to (conceptsByCard[card.id]?.associate { NeedKey.OfConcept(it) as NeedKey to shares.getValue(card.id to it) }
                ?: mapOf(NeedKey.OfCard(card.id) to 1.0))
        }
        // When each card was last graded here: its own state's last review in this context.
        val lastGraded: Map<Long, Long> = if (learningGroupId == null) pool.associate { it.id to it.lastReview }
            else groupDao.getAllLearningStatesSync()
                .filter { it.learningGroupId == learningGroupId && it.cardId in poolIds }
                .associate { it.cardId to it.lastReview }
        val staleness = pool.associate { card ->
            val last = lastGraded[card.id] ?: 0L
            card.id to stalenessFactor(if (last <= 0L) null else (now - last).toDouble() / DAY_MILLIS)
        }

        val order = if (shouldRandomize) pool.map { it.id }.shuffled() else pool.map { it.id }.sorted()
        val picks = greedyDotProduct(order, vectors, needs, limit) { cardId -> staleness[cardId] ?: 1.0 }

        val cardsById = pool.associateBy { it.id }
        val selected = LinkedHashMap<Long, Card>()
        picks.forEach { selected[it.cardId] = cardsById.getValue(it.cardId) }
        val pickById = picks.associateBy { it.cardId }

        if (selected.size < limit) {
            fun dueOf(card: Card): Long = conceptsByCard[card.id]
                ?.minOfOrNull { conceptStates[it]?.nextReview ?: 0L }
                ?: untaggedStates[card.id]?.third ?: card.nextReview
            val rest = pool.filter { it.id !in selected }.sortedBy(::dueOf)
            val fill = if (shouldRandomize) rest.shuffled() else rest
            fill.take(limit - selected.size).forEach { selected[it.id] = it }
        }

        val conceptUids = if (picks.isEmpty()) emptyMap() else
            conceptDao.getAllConceptsSync().associate { it.id to it.uid }
        return selected.values.map { card ->
            val pick = pickById[card.id]
            // The concepts whose need it met, and the one it met most of.
            val conceptTerms = pick?.terms.orEmpty().mapNotNull { (key, term) ->
                (key as? NeedKey.OfConcept)?.let { it.conceptId to term }
            }
            DrawnCard(
                card,
                CardSelection(
                    SelectionMode.CONCEPT,
                    if (pick != null) SelectionReason.DUE else SelectionReason.EXTRA,
                    conceptTerms.mapNotNull { conceptUids[it.first] }.sorted(),
                    conceptTerms.maxWithOrNull(compareBy<Pair<Long, Double>> { it.second }.thenByDescending { it.first })
                        ?.let { conceptUids[it.first] }
                )
            )
        }
    }

    /** What has a need in a dot-product draw: a concept, or an untagged card standing for itself. */
    internal sealed interface NeedKey {
        data class OfConcept(val conceptId: Long) : NeedKey
        data class OfCard(val cardId: Long) : NeedKey
    }

    /**
     * One card a dot-product draw took: its [score] (the need it met), and
     * each term of the dot product that was more than 0 -- share times need,
     * by what had the need.
     */
    internal data class DotPick<K>(val cardId: Long, val score: Double, val terms: Map<K, Double>)

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
    ): ConceptLearningState =
        state.withFsrs(schedule(state.fsrs, grade, now, groupId, stabilityMultiplier, fuzzing = false)).copy(modified = now)

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

    /**
     * Grade [card] on its own (Global) progress, scheduled by [groupId]'s
     * settings; the review is logged as made in that group (or none).
     */
    suspend fun reviewCard(
        card: Card,
        grade: Grade,
        sessionId: Long? = null,
        groupId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null,
        selection: CardSelection? = null
    ): Card {
        val now = Time.now()
        val group = groupId?.let { groupDao.getGroupById(it) }
        val fight = fightOf(opponentId, fightDifficulty)
        val before = card.fsrs
        val after = if (grade == Grade.SKIP) before else schedule(before, grade, now, groupId, fight.stabilityMultiplier)
        reviewLogDao.insertReviewLog(reviewLogOf(card.id, sessionId, now, grade, before, after, group, fight, selection))
        if (grade == Grade.SKIP) return card
        val updated = card.withFsrs(after).copy(modified = now)
        cardDao.updateCard(updated)
        return updated
    }

    /**
     * Grade [card] in [groupId]: on the group's learning group's progress of
     * it (made if it has none yet), or -- for a group in no learning group,
     * or one gone -- on its own progress, as [reviewCard].
     *
     * @return the card with the progress it now has there, for display.
     */
    suspend fun reviewCardWithGroup(
        card: Card,
        grade: Grade,
        groupId: Long,
        sessionId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null,
        selection: CardSelection? = null
    ): Card {
        val group = groupDao.getGroupById(groupId)
        val learningGroupId = group?.learningGroupId
            ?: return reviewCard(card, grade, sessionId, groupId, opponentId, fightDifficulty, selection)

        val now = Time.now()
        val state = groupDao.getLearningState(card.id, learningGroupId)
            ?: CardLearningState(card.id, learningGroupId).also { groupDao.insertLearningState(it) }
        val fight = fightOf(opponentId, fightDifficulty)
        val before = state.fsrs
        val after = if (grade == Grade.SKIP) before else schedule(before, grade, now, groupId, fight.stabilityMultiplier)
        reviewLogDao.insertReviewLog(reviewLogOf(card.id, sessionId, now, grade, before, after, group, fight, selection))
        if (grade == Grade.SKIP) return card
        groupDao.updateLearningState(state.withFsrs(after).copy(modified = now))
        return card.withFsrs(after)
    }

    /** What grading [card] would leave its own progress at, without saving anything. */
    suspend fun computeReview(
        card: Card,
        grade: Grade,
        groupId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null
    ): Card {
        val now = Time.now()
        val after = schedule(card.fsrs, grade, now, groupId, fightOf(opponentId, fightDifficulty).stabilityMultiplier)
        return card.withFsrs(after).copy(modified = now)
    }

    /** What grading [card] in [groupId] would leave its progress there at, without saving anything. */
    suspend fun computeReviewWithGroup(
        card: Card,
        grade: Grade,
        groupId: Long,
        opponentId: Long? = null,
        fightDifficulty: Int? = null
    ): Card {
        val learningGroupId = groupDao.getGroupById(groupId)?.learningGroupId
            ?: return computeReview(card, grade, groupId, opponentId, fightDifficulty)
        val state = groupDao.getLearningState(card.id, learningGroupId) ?: CardLearningState(card.id, learningGroupId)
        val after = schedule(state.fsrs, grade, Time.now(), groupId, fightOf(opponentId, fightDifficulty).stabilityMultiplier)
        return card.withFsrs(after)
    }

    /**
     * Batch-review the cards of one fight, on their own (Global) progress.
     *
     * The opponent and the fight's difficulty are arguments to the batch rather
     * than to each card in it, because that is what they describe: one fight,
     * whose every card is graded against it.
     */
    suspend fun reviewMultipleCards(
        cardsWithGrades: List<Pair<Card, Grade>>,
        sessionId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null,
        /** Why each card was drawn, by card id. */
        selections: Map<Long, CardSelection> = emptyMap()
    ) {
        val now = Time.now()
        val fight = fightOf(opponentId, fightDifficulty)
        val logs = mutableListOf<ReviewLog>()
        val updated = mutableListOf<Card>()
        cardsWithGrades.forEach { (card, grade) ->
            val before = card.fsrs
            val after = if (grade == Grade.SKIP) before else schedule(before, grade, now, null, fight.stabilityMultiplier)
            logs += reviewLogOf(card.id, sessionId, now, grade, before, after, null, fight, selections[card.id])
            if (grade != Grade.SKIP) updated += card.withFsrs(after).copy(modified = now)
        }
        reviewLogDao.insertReviewLogs(logs)
        cardDao.updateCards(updated)
    }

    /**
     * One FSRS step: [state] graded [grade] at [now], with the settings of the
     * learning context [groupId] practises in (maximum interval, retention,
     * fuzzing, practice days), and the stability gain scaled by
     * [stabilityMultiplier]. Cards, learning-group states and concepts are
     * all scheduled here; [fuzzing] null uses the context's setting.
     */
    private suspend fun schedule(
        state: FsrsState,
        grade: Grade,
        now: Long,
        groupId: Long?,
        stabilityMultiplier: Double,
        fuzzing: Boolean? = null
    ): FsrsState {
        fsrsAlgorithm.setMaximumInterval(resolveMaximumInterval(groupId))
        fsrsAlgorithm.setRequestRetention(resolveFsrsRetention(groupId))
        fsrsAlgorithm.setEnableFuzzing(fuzzing ?: resolveFsrsEnableFuzzing(groupId))
        val rating = when (grade) {
            Grade.SKIP -> throw IllegalArgumentException("SKIP grade should not be processed")
            Grade.AGAIN -> FSRSAlgorithm.Rating.AGAIN
            Grade.HARD -> FSRSAlgorithm.Rating.HARD
            Grade.GOOD -> FSRSAlgorithm.Rating.GOOD
            Grade.EASY -> FSRSAlgorithm.Rating.EASY
        }
        val fsrsCard = FSRSAlgorithm.FSRSCard(
            stability = state.stability,
            difficulty = state.difficulty,
            elapsedDays = state.daysSinceReview(now),
            scheduledDays = state.scheduledDays,
            reps = state.reps,
            lapses = state.lapses,
            state = FSRSAlgorithm.CardState.valueOf(state.state),
            lastReview = state.lastReview
        )
        val next = fsrsAlgorithm.schedule(fsrsCard, rating, now, stabilityMultiplier).card
        val days = adjustForPracticeFrequency(next.scheduledDays, groupId, now)
        return FsrsState(
            stability = next.stability,
            difficulty = next.difficulty,
            elapsedDays = next.elapsedDays,
            scheduledDays = days,
            reps = next.reps,
            lapses = next.lapses,
            state = next.state.name,
            lastReview = now,
            nextReview = now + days * FsrsState.DAY_MILLIS
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
         * The greedy dot-product draw. Each card in [order] is a vector of
         * shares ([vectors]); each key has a need. Repeatedly take the card
         * whose dot product with the remaining need is largest (the first in
         * [order] on a tie), then lower each of its keys' need by its share,
         * not below 0. Stops at [limit] cards, or when no card meets any need.
         */
        internal fun <K> greedyDotProduct(
            order: List<Long>,
            vectors: Map<Long, Map<K, Double>>,
            need: Map<K, Double>,
            limit: Int,
            /** Multiplies a card's dot product when choosing; the need it meets is unaffected. */
            factor: (cardId: Long) -> Double = { 1.0 }
        ): List<DotPick<K>> {
            val remaining = need.toMutableMap()
            val left = order.toMutableList()
            val picks = mutableListOf<DotPick<K>>()
            while (picks.size < limit && left.isNotEmpty()) {
                var best: Long? = null
                var bestScore = 0.0
                for (cardId in left) {
                    val met = vectors[cardId].orEmpty().entries.sumOf { (key, share) -> share * (remaining[key] ?: 0.0) }
                    if (met <= 0.0) continue
                    val score = met * factor(cardId)
                    if (score > bestScore + 1e-12) { best = cardId; bestScore = score }
                }
                val chosen = best ?: break
                val vector = vectors[chosen].orEmpty()
                val terms = vector.mapNotNull { (key, share) ->
                    (share * (remaining[key] ?: 0.0)).takeIf { it > 0.0 }?.let { key to it }
                }.toMap()
                // The need it met, before staleness: what the card actually covers.
                picks += DotPick(chosen, terms.values.sum(), terms)
                vector.forEach { (key, share) -> remaining[key] = ((remaining[key] ?: 0.0) - share).coerceAtLeast(0.0) }
                left.remove(chosen)
            }
            return picks
        }

        /** How many days of rest make a card about two-thirds of the way back to full staleness. */
        internal const val STALENESS_DAYS = 7.0

        /**
         * A card's staleness factor in a dot-product draw: 0.5 just after it
         * was graded, approaching 1 as days pass (about 0.82 after a week,
         * 0.98 after three), and 1 for a card never graded ([daysSinceGraded] null).
         */
        internal fun stalenessFactor(daysSinceGraded: Double?): Double =
            if (daysSinceGraded == null) 1.0
            else 0.5 + 0.5 * (1.0 - kotlin.math.exp(-daysSinceGraded.coerceAtLeast(0.0) / STALENESS_DAYS))

        /**
         * The need of something due in a dot-product draw: 1 minus its
         * predicted recall at [now] -- 1 if never learnt -- or 0 when it is
         * not due yet.
         */
        internal fun needOf(
            stability: Double, lastReview: Long, nextReview: Long, now: Long,
            retrievability: (elapsedDays: Double, stability: Double) -> Double
        ): Double {
            if (nextReview > now) return 0.0
            val elapsedDays = if (lastReview <= 0L) 0.0 else (now - lastReview).toDouble() / DAY_MILLIS
            return 1.0 - retrievability(elapsedDays, stability)
        }

        /**
         * How many of [cards] are ready for practice at [now] in learning
         * context [learningGroupId] (null is Global). See [dueCount].
         *
         * @param shares each card's share of each of its concepts.
         * @param conceptStates each concept's state in this context; one
         *   missing is new there, so fully needed.
         */
        internal fun countDue(
            cards: List<Card>,
            learningGroupId: Long?,
            byConcept: Boolean,
            cardStates: Map<Pair<Long, Long>, CardLearningState>,
            shares: Map<Pair<Long, Long>, Double>,
            conceptStates: Map<Long, ConceptLearningState>,
            now: Long,
            retrievability: (elapsedDays: Double, stability: Double) -> Double
        ): Int {
            if (!byConcept) return cards.count { card ->
                // A card not yet practised in the learning group goes by its
                // Global state, as the group's card-by-card draw does.
                val due = learningGroupId?.let { cardStates[card.id to it] }?.nextReview ?: card.nextReview
                due <= now
            }
            val conceptsByCard = shares.keys.groupBy({ it.first }, { it.second })
            val need = HashMap<Any, Double>()
            val vectors = cards.associate { card ->
                val concepts = conceptsByCard[card.id]
                card.id to if (concepts != null) {
                    concepts.associate { conceptId ->
                        val state = conceptStates[conceptId]
                        need[conceptId to "concept"] = if (state == null) 1.0
                            else needOf(state.fsrsStability, state.lastReview, state.nextReview, now, retrievability)
                        (conceptId to "concept") as Any to shares.getValue(card.id to conceptId)
                    }
                } else {
                    val own = learningGroupId?.let { cardStates[card.id to it] }
                    need[card.id to "card"] = when {
                        learningGroupId == null ->
                            needOf(card.fsrsStability, card.lastReview, card.nextReview, now, retrievability)
                        own == null -> 1.0   // never practised in the learning group: new
                        else -> needOf(own.fsrsStability, own.lastReview, own.nextReview, now, retrievability)
                    }
                    mapOf((card.id to "card") as Any to 1.0)
                }
            }
            return greedyDotProduct(cards.map { it.id }.sorted(), vectors, need, cards.size).size
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
