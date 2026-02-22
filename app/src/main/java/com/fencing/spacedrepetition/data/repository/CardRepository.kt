package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.algorithm.FSRSAlgorithm
import com.fencing.spacedrepetition.algorithm.SM2Algorithm
import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.dao.PracticeSessionDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.*
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CardRepository(
    private val cardDao: CardDao,
    private val sessionDao: PracticeSessionDao,
    private val reviewLogDao: ReviewLogDao,
    private val groupDao: GroupDao,
    private val preferences: ThemePreferences
) {
    private val fsrsAlgorithm = FSRSAlgorithm()
    private val sm2Algorithm = SM2Algorithm()

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

    suspend fun deleteCard(card: Card) {
        cardDao.deleteCard(card)
        reviewLogDao.deleteReviewLogsByCard(card.id)
    }

    suspend fun deleteCardById(cardId: Long) {
        cardDao.deleteCardById(cardId)
        reviewLogDao.deleteReviewLogsByCard(cardId)
    }

    suspend fun deleteAllCards() {
        reviewLogDao.deleteAllReviewLogs()
        groupDao.deleteAllLearningStates()
        groupDao.deleteAllCardGroupCrossRefs()
        cardDao.deleteAllCards()
    }

    suspend fun resetCardState(cardId: Long, resetGroupStates: Boolean = false) {
        val card = cardDao.getCardById(cardId) ?: return
        val now = System.currentTimeMillis()
        val resetCard = card.copy(
            // Reset FSRS state
            fsrsStability = 0.0,
            fsrsDifficulty = 0.0,
            fsrsElapsedDays = 0,
            fsrsScheduledDays = 0,
            fsrsReps = 0,
            fsrsLapses = 0,
            fsrsState = "NEW",
            // Reset SM2 state
            sm2EaseFactor = 2.5,
            sm2Interval = 0,
            sm2Repetitions = 0,
            // Reset review times
            lastReview = 0L,
            nextReview = 0L,
            modified = now
        )
        cardDao.updateCard(resetCard)

        // Optionally reset group-specific learning states
        if (resetGroupStates) {
            groupDao.deleteAllLearningStatesForCard(cardId)
        }
    }

    suspend fun resetCardStateInGroup(cardId: Long, groupId: Long) {
        // Reset only the group-specific learning state for this card in this group
        val learningState = groupDao.getLearningState(cardId, groupId)
        if (learningState != null) {
            val resetState = learningState.copy(
                fsrsStability = 0.0,
                fsrsDifficulty = 0.0,
                fsrsElapsedDays = 0,
                fsrsScheduledDays = 0,
                fsrsReps = 0,
                fsrsLapses = 0,
                fsrsState = "NEW",
                sm2EaseFactor = 2.5,
                sm2Interval = 0,
                sm2Repetitions = 0,
                lastReview = 0L,
                nextReview = 0L,
                modified = System.currentTimeMillis()
            )
            groupDao.updateLearningState(resetState)
        }
    }

    fun getCardCount(): Flow<Int> = cardDao.getCardCount()

    fun getAllCategories(): Flow<List<String>> = cardDao.getAllCategories()

    // Group-aware card operations
    fun getAllCardsWithGroups(): Flow<List<CardWithGroups>> = cardDao.getAllCardsWithGroups()

    fun getCardWithGroups(cardId: Long): Flow<CardWithGroups?> = cardDao.getCardWithGroups(cardId)

    suspend fun getDueCardsByGroup(groupId: Long, limit: Int = 100): List<Card> {
        val group = groupDao.getGroupById(groupId)
        val shouldRandomize = group?.randomizeDueCards ?: preferences.randomizeDueCards.first()
        val cards = if (shouldRandomize) {
            cardDao.getDueCardsByGroup(groupId, limit = Int.MAX_VALUE)
        } else {
            cardDao.getDueCardsByGroup(groupId, limit = limit)
        }
        return if (shouldRandomize) {
            val bucketHours = group?.randomizeBucketHours ?: preferences.randomizeBucketHours.first()
            randomizeCardsByBucket(cards, bucketHours).take(limit)
        } else {
            cards
        }
    }

    suspend fun getCardsByGroupSync(groupId: Long): List<Card> {
        val cards = cardDao.getCardsByGroupSync(groupId)
        val group = groupDao.getGroupById(groupId)
        val shouldRandomize = group?.randomizeDueCards ?: preferences.randomizeDueCards.first()
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
        groupId: Long,
        algorithm: AlgorithmType
    ): Int {
        var importedCount = 0
        cards.forEach { (question, answer) ->
            val existingCard = cardDao.findCardByQuestion(question)
            val cardId: Long

            if (existingCard != null) {
                // Update existing card's answer (preserve learning state)
                cardDao.updateCard(existingCard.copy(
                    answer = answer,
                    modified = System.currentTimeMillis()
                ))
                cardId = existingCard.id
            } else {
                // Create new card
                val card = Card(
                    question = question,
                    answer = answer,
                    algorithm = algorithm
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
        cards: List<Pair<String, String>>,
        algorithm: AlgorithmType
    ): Int {
        var importedCount = 0
        cards.forEach { (question, answer) ->
            val existingCard = cardDao.findCardByQuestion(question)
            if (existingCard != null) {
                // Update existing card's answer only (preserve learning state)
                cardDao.updateCard(existingCard.copy(
                    answer = answer,
                    modified = System.currentTimeMillis()
                ))
            } else {
                // Create new card
                val card = Card(
                    question = question,
                    answer = answer,
                    algorithm = algorithm
                )
                cardDao.insertCard(card)
            }
            importedCount++
        }
        return importedCount
    }

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
                // Update existing card with full state from import
                val updatedCard = card.copy(
                    id = existingCard.id,
                    created = existingCard.created, // Preserve original creation time
                    modified = System.currentTimeMillis()
                )
                cardDao.updateCard(updatedCard)
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

    suspend fun importCardsWithGroupStates(
        context: android.content.Context,
        parsedCards: List<com.fencing.spacedrepetition.util.ParsedCard>,
        existingGroups: Map<String, Long>
    ): Int {
        // Group parsed cards by concept (same card, different state contexts)
        val cardsByQuestion = parsedCards.groupBy { it.concept }
        var importedCount = 0

        cardsByQuestion.forEach { (question, states) ->
            // Find the global state (or use the first state if no global)
            val globalState = states.find { it.isGlobalState } ?: states.first()

            // Create or update the card (decode base64 images)
            val card = com.fencing.spacedrepetition.util.CardImportExport.parsedCardToCard(context, globalState)
            val existingCard = cardDao.findCardByQuestion(question)
            val cardId: Long

            if (existingCard != null) {
                val updatedCard = card.copy(
                    id = existingCard.id,
                    created = existingCard.created,
                    modified = System.currentTimeMillis()
                )
                cardDao.updateCard(updatedCard)
                cardId = existingCard.id

                // Clear existing group associations
                groupDao.deleteAllGroupsForCard(cardId)
                // Clear existing group-specific learning states
                groupDao.deleteAllLearningStatesForCard(cardId)
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

            // Import group-specific learning states
            states.filter { it.isGroupSpecificState }.forEach { groupState ->
                val groupName = groupState.stateContext!!
                existingGroups[groupName]?.let { groupId ->
                    val learningState = com.fencing.spacedrepetition.data.model.CardGroupLearningState(
                        cardId = cardId,
                        groupId = groupId,
                        fsrsStability = groupState.fsrsStability ?: 0.0,
                        fsrsDifficulty = groupState.fsrsDifficulty ?: 0.0,
                        fsrsElapsedDays = groupState.fsrsElapsedDays ?: 0,
                        fsrsScheduledDays = groupState.fsrsScheduledDays ?: 0,
                        fsrsReps = groupState.fsrsReps ?: 0,
                        fsrsLapses = groupState.fsrsLapses ?: 0,
                        fsrsState = groupState.fsrsState ?: "NEW",
                        sm2EaseFactor = groupState.sm2EaseFactor ?: 2.5,
                        sm2Interval = groupState.sm2Interval ?: 0,
                        sm2Repetitions = groupState.sm2Repetitions ?: 0,
                        lastReview = groupState.lastReview ?: 0L,
                        nextReview = groupState.nextReview ?: 0L
                    )
                    groupDao.insertLearningState(learningState)
                }
            }

            importedCount++
        }
        return importedCount
    }

    suspend fun getAllCardsWithGroupNames(): List<Pair<Card, List<String>>> {
        val cardsWithGroups = cardDao.getAllCardsWithGroups().first()
        return cardsWithGroups.map { cardWithGroups ->
            cardWithGroups.card to cardWithGroups.groups.map { it.name }
        }
    }

    suspend fun getAllCardsWithGroupStates(): List<com.fencing.spacedrepetition.util.CardWithGroupStates> {
        val cardsWithGroups = cardDao.getAllCardsWithGroups().first()
        return cardsWithGroups.map { cardWithGroups ->
            val card = cardWithGroups.card
            val groups = cardWithGroups.groups
            val groupNames = groups.map { it.name }

            // Get group-specific states for groups with independent learning
            val groupSpecificStates = mutableMapOf<String, CardGroupLearningState>()
            groups.forEach { group ->
                if (group.independentLearning) {
                    groupDao.getLearningState(card.id, group.id)?.let { state ->
                        groupSpecificStates[group.name] = state
                    }
                }
            }

            com.fencing.spacedrepetition.util.CardWithGroupStates(card, groupNames, groupSpecificStates)
        }
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

    suspend fun completeSession(sessionId: Long, grades: List<Grade>) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val updatedSession = session.copy(
            endTime = System.currentTimeMillis(),
            completed = true,
            grades = grades.joinToString(",") { it.value.toString() }
        )
        sessionDao.updateSession(updatedSession)
    }

    fun getAllSessions(): Flow<List<PracticeSession>> = sessionDao.getAllSessions()

    fun getCompletedSessions(): Flow<List<PracticeSession>> = sessionDao.getCompletedSessions()

    // Independent learning state operations
    suspend fun getLearningState(cardId: Long, groupId: Long): CardGroupLearningState? =
        groupDao.getLearningState(cardId, groupId)

    fun getLearningStateFlow(cardId: Long, groupId: Long): Flow<CardGroupLearningState?> =
        groupDao.getLearningStateFlow(cardId, groupId)

    suspend fun updateLearningState(learningState: CardGroupLearningState) =
        groupDao.updateLearningState(learningState)

    suspend fun initializeLearningState(cardId: Long, groupId: Long, algorithm: AlgorithmType) {
        val existingState = groupDao.getLearningState(cardId, groupId)
        if (existingState == null) {
            groupDao.insertLearningState(CardGroupLearningState(cardId, groupId))
        }
    }

    // Review operations
    suspend fun reviewCard(card: Card, grade: Grade, sessionId: Long? = null, groupId: Long? = null): Card {
        val now = System.currentTimeMillis()
        val elapsedDays = if (card.lastReview == 0L) 0 else
            ((now - card.lastReview) / (1000 * 60 * 60 * 24)).toInt()

        val updatedCard = when (card.algorithm) {
            AlgorithmType.FSRS -> reviewWithFSRS(card, grade, now, elapsedDays, groupId)
            AlgorithmType.SM2 -> reviewWithSM2(card, grade, now, groupId)
        }

        // Create review log
        val reviewLog = ReviewLog(
            cardId = card.id,
            sessionId = sessionId,
            reviewTime = now,
            grade = grade.value,
            algorithm = card.algorithm.name,
            stateBefore = serializeCardState(card),
            stateAfter = serializeCardState(updatedCard),
            scheduledDays = updatedCard.fsrsScheduledDays,
            elapsedDays = elapsedDays
        )

        reviewLogDao.insertReviewLog(reviewLog)
        cardDao.updateCard(updatedCard)

        return updatedCard
    }

    suspend fun reviewCardWithGroup(card: Card, grade: Grade, groupId: Long, sessionId: Long? = null): Card {
        val group = groupDao.getGroupById(groupId) ?: return reviewCard(card, grade, sessionId, groupId)

        if (!group.independentLearning) {
            // Use global learning state but apply group settings
            return reviewCard(card, grade, sessionId, groupId)
        }

        // Use group-specific learning state
        val now = System.currentTimeMillis()
        var learningState = groupDao.getLearningState(card.id, groupId)

        // Initialize learning state if it doesn't exist
        if (learningState == null) {
            learningState = CardGroupLearningState(card.id, groupId)
            groupDao.insertLearningState(learningState)
        }

        val elapsedDays = if (learningState.lastReview == 0L) 0 else
            ((now - learningState.lastReview) / (1000 * 60 * 60 * 24)).toInt()

        val updatedState = when (card.algorithm) {
            AlgorithmType.FSRS -> reviewLearningStateWithFSRS(learningState, grade, now, elapsedDays, groupId)
            AlgorithmType.SM2 -> reviewLearningStateWithSM2(learningState, grade, now, groupId)
        }

        // Create review log
        val reviewLog = ReviewLog(
            cardId = card.id,
            sessionId = sessionId,
            reviewTime = now,
            grade = grade.value,
            algorithm = card.algorithm.name,
            stateBefore = serializeLearningState(learningState, card.algorithm),
            stateAfter = serializeLearningState(updatedState, card.algorithm),
            scheduledDays = updatedState.fsrsScheduledDays,
            elapsedDays = elapsedDays
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
            sm2EaseFactor = updatedState.sm2EaseFactor,
            sm2Interval = updatedState.sm2Interval,
            sm2Repetitions = updatedState.sm2Repetitions,
            lastReview = updatedState.lastReview,
            nextReview = updatedState.nextReview
        )
    }

    /** Compute the result of grading [card] without persisting anything to the database. */
    suspend fun computeReview(card: Card, grade: Grade, groupId: Long? = null): Card {
        val now = System.currentTimeMillis()
        val elapsedDays = if (card.lastReview == 0L) 0 else
            ((now - card.lastReview) / (1000 * 60 * 60 * 24)).toInt()
        return when (card.algorithm) {
            AlgorithmType.FSRS -> reviewWithFSRS(card, grade, now, elapsedDays, groupId)
            AlgorithmType.SM2 -> reviewWithSM2(card, grade, now, groupId)
        }
    }

    /** Compute the result of grading [card] in a specific group without persisting. */
    suspend fun computeReviewWithGroup(card: Card, grade: Grade, groupId: Long): Card {
        val group = groupDao.getGroupById(groupId) ?: return computeReview(card, grade, groupId)
        if (!group.independentLearning) {
            return computeReview(card, grade, groupId)
        }
        val now = System.currentTimeMillis()
        val learningState = groupDao.getLearningState(card.id, groupId)
            ?: CardGroupLearningState(card.id, groupId)
        val elapsedDays = if (learningState.lastReview == 0L) 0 else
            ((now - learningState.lastReview) / (1000 * 60 * 60 * 24)).toInt()
        val updatedState = when (card.algorithm) {
            AlgorithmType.FSRS -> reviewLearningStateWithFSRS(learningState, grade, now, elapsedDays, groupId)
            AlgorithmType.SM2 -> reviewLearningStateWithSM2(learningState, grade, now, groupId)
        }
        return card.copy(
            fsrsStability = updatedState.fsrsStability,
            fsrsDifficulty = updatedState.fsrsDifficulty,
            fsrsElapsedDays = updatedState.fsrsElapsedDays,
            fsrsScheduledDays = updatedState.fsrsScheduledDays,
            fsrsReps = updatedState.fsrsReps,
            fsrsLapses = updatedState.fsrsLapses,
            fsrsState = updatedState.fsrsState,
            sm2EaseFactor = updatedState.sm2EaseFactor,
            sm2Interval = updatedState.sm2Interval,
            sm2Repetitions = updatedState.sm2Repetitions,
            lastReview = updatedState.lastReview,
            nextReview = updatedState.nextReview
        )
    }

    suspend fun reviewMultipleCards(cardsWithGrades: List<Pair<Card, Grade>>, sessionId: Long? = null) {
        val now = System.currentTimeMillis()
        val reviewLogs = mutableListOf<ReviewLog>()
        val updatedCards = mutableListOf<Card>()

        cardsWithGrades.forEach { (card, grade) ->
            val elapsedDays = if (card.lastReview == 0L) 0 else
                ((now - card.lastReview) / (1000 * 60 * 60 * 24)).toInt()

            val updatedCard = when (card.algorithm) {
                AlgorithmType.FSRS -> reviewWithFSRS(card, grade, now, elapsedDays)
                AlgorithmType.SM2 -> reviewWithSM2(card, grade, now)
            }

            reviewLogs.add(
                ReviewLog(
                    cardId = card.id,
                    sessionId = sessionId,
                    reviewTime = now,
                    grade = grade.value,
                    algorithm = card.algorithm.name,
                    stateBefore = serializeCardState(card),
                    stateAfter = serializeCardState(updatedCard),
                    scheduledDays = updatedCard.fsrsScheduledDays,
                    elapsedDays = elapsedDays
                )
            )

            updatedCards.add(updatedCard)
        }

        reviewLogDao.insertReviewLogs(reviewLogs)
        cardDao.updateCards(updatedCards)
    }

    private suspend fun reviewWithFSRS(card: Card, grade: Grade, now: Long, elapsedDays: Int, groupId: Long? = null): Card {
        // Resolve maximum interval and desired retention (group override or global)
        val maxInterval = resolveMaximumInterval(groupId)
        fsrsAlgorithm.setMaximumInterval(maxInterval)
        val retention = resolveFsrsRetention(groupId)
        fsrsAlgorithm.setRequestRetention(retention)

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

        val schedulingInfo = fsrsAlgorithm.schedule(fsrsCard, rating, now)
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

    private suspend fun reviewWithSM2(card: Card, grade: Grade, now: Long, groupId: Long? = null): Card {
        // Resolve maximum interval and interval modifier (group override or global)
        val maxInterval = resolveMaximumInterval(groupId)
        sm2Algorithm.setMaximumInterval(maxInterval)
        val modifier = resolveSm2IntervalModifier(groupId)
        sm2Algorithm.setIntervalModifier(modifier)

        val sm2Card = SM2Algorithm.SM2Card(
            easeFactor = card.sm2EaseFactor,
            interval = card.sm2Interval,
            repetitions = card.sm2Repetitions,
            lastReview = card.lastReview
        )

        val quality = when (grade) {
            Grade.SKIP -> throw IllegalArgumentException("SKIP grade should not be processed")
            Grade.AGAIN -> SM2Algorithm.Quality.COMPLETE_BLACKOUT
            Grade.HARD -> SM2Algorithm.Quality.DIFFICULT
            Grade.GOOD -> SM2Algorithm.Quality.EASY
            Grade.EASY -> SM2Algorithm.Quality.PERFECT
        }

        val schedulingInfo = sm2Algorithm.schedule(sm2Card, quality, now)
        val newCard = schedulingInfo.card
        val adjustedInterval = adjustForPracticeFrequency(newCard.interval, groupId)
        val adjustedNextReview = now + (adjustedInterval * 24 * 60 * 60 * 1000L)

        return card.copy(
            sm2EaseFactor = newCard.easeFactor,
            sm2Interval = adjustedInterval,
            sm2Repetitions = newCard.repetitions,
            lastReview = now,
            nextReview = adjustedNextReview,
            modified = now,
            // Update FSRS scheduled days for consistency in UI
            fsrsScheduledDays = adjustedInterval
        )
    }

    /**
     * Adjust scheduled days based on practice-days setting.
     * Snaps the interval to the nearest selected practice day so cards
     * come due on days the user will actually practice.
     *
     * Practice days use ISO-8601 convention: 1=Monday through 7=Sunday.
     */
    /** Resolve maximum interval: group override takes precedence over global. */
    private suspend fun resolveMaximumInterval(groupId: Long?): Int {
        if (groupId != null) {
            val group = groupDao.getGroupById(groupId)
            if (group?.maximumInterval != null) return group.maximumInterval
        }
        return preferences.maximumInterval.first()
    }

    /** Resolve practice days: group override takes precedence over global. */
    private suspend fun resolvePracticeDays(groupId: Long?): Set<Int> {
        if (groupId != null) {
            val group = groupDao.getGroupById(groupId)
            val groupDays = group?.parsePracticeDays()
            if (groupDays != null) return groupDays
        }
        return preferences.practiceDays.first()
    }

    /** Resolve FSRS desired retention (integer percent): group override takes precedence over global. */
    private suspend fun resolveFsrsRetention(groupId: Long?): Int {
        if (groupId != null) {
            val group = groupDao.getGroupById(groupId)
            if (group?.fsrsRetention != null) return group.fsrsRetention
        }
        return preferences.fsrsRetention.first()
    }

    /** Resolve SM-2 interval modifier (integer percent): group override takes precedence over global. */
    private suspend fun resolveSm2IntervalModifier(groupId: Long?): Int {
        if (groupId != null) {
            val group = groupDao.getGroupById(groupId)
            if (group?.sm2IntervalModifier != null) return group.sm2IntervalModifier
        }
        return preferences.sm2IntervalModifier.first()
    }

    private suspend fun adjustForPracticeFrequency(scheduledDays: Int, groupId: Long? = null): Int {
        val practiceDays = resolvePracticeDays(groupId)
        if (practiceDays.size >= 7 || practiceDays.isEmpty() || scheduledDays <= 1) return scheduledDays

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, scheduledDays)
        // Convert Calendar day-of-week (1=Sunday..7=Saturday) to ISO-8601 (1=Monday..7=Sunday)
        val calendarDow = calendar.get(Calendar.DAY_OF_WEEK)
        val targetDow = calendarDowToIso(calendarDow)

        return scheduledDays + forwardDaysToNearestPracticeDay(targetDow, practiceDays)
    }

    /**
     * Convert Calendar.DAY_OF_WEEK (1=Sunday..7=Saturday) to ISO-8601 (1=Monday..7=Sunday).
     */
    private fun calendarDowToIso(calendarDow: Int): Int {
        return if (calendarDow == Calendar.SUNDAY) 7 else calendarDow - 1
    }

    private fun serializeCardState(card: Card): String {
        return when (card.algorithm) {
            AlgorithmType.FSRS -> "S:${card.fsrsStability},D:${card.fsrsDifficulty},ST:${card.fsrsState}"
            AlgorithmType.SM2 -> "EF:${card.sm2EaseFactor},I:${card.sm2Interval},R:${card.sm2Repetitions}"
        }
    }

    private suspend fun reviewLearningStateWithFSRS(
        learningState: CardGroupLearningState,
        grade: Grade,
        now: Long,
        elapsedDays: Int,
        groupId: Long? = null
    ): CardGroupLearningState {
        // Resolve maximum interval (group override or global)
        val maxInterval = resolveMaximumInterval(groupId)
        fsrsAlgorithm.setMaximumInterval(maxInterval)

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

        val schedulingInfo = fsrsAlgorithm.schedule(fsrsCard, rating, now)
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

    private suspend fun reviewLearningStateWithSM2(
        learningState: CardGroupLearningState,
        grade: Grade,
        now: Long,
        groupId: Long? = null
    ): CardGroupLearningState {
        // Resolve maximum interval (group override or global)
        val maxInterval = resolveMaximumInterval(groupId)
        sm2Algorithm.setMaximumInterval(maxInterval)

        val sm2Card = SM2Algorithm.SM2Card(
            easeFactor = learningState.sm2EaseFactor,
            interval = learningState.sm2Interval,
            repetitions = learningState.sm2Repetitions,
            lastReview = learningState.lastReview
        )

        val quality = when (grade) {
            Grade.SKIP -> throw IllegalArgumentException("SKIP grade should not be processed")
            Grade.AGAIN -> SM2Algorithm.Quality.COMPLETE_BLACKOUT
            Grade.HARD -> SM2Algorithm.Quality.DIFFICULT
            Grade.GOOD -> SM2Algorithm.Quality.EASY
            Grade.EASY -> SM2Algorithm.Quality.PERFECT
        }

        val schedulingInfo = sm2Algorithm.schedule(sm2Card, quality, now)
        val newCard = schedulingInfo.card
        val adjustedInterval = adjustForPracticeFrequency(newCard.interval, groupId)
        val adjustedNextReview = now + (adjustedInterval * 24 * 60 * 60 * 1000L)

        return learningState.copy(
            sm2EaseFactor = newCard.easeFactor,
            sm2Interval = adjustedInterval,
            sm2Repetitions = newCard.repetitions,
            lastReview = now,
            nextReview = adjustedNextReview,
            modified = now,
            // Update FSRS scheduled days for consistency in UI
            fsrsScheduledDays = adjustedInterval
        )
    }

    private fun serializeLearningState(learningState: CardGroupLearningState, algorithm: AlgorithmType): String {
        return when (algorithm) {
            AlgorithmType.FSRS -> "S:${learningState.fsrsStability},D:${learningState.fsrsDifficulty},ST:${learningState.fsrsState}"
            AlgorithmType.SM2 -> "EF:${learningState.sm2EaseFactor},I:${learningState.sm2Interval},R:${learningState.sm2Repetitions}"
        }
    }

    // Review log operations
    fun getReviewLogsByCard(cardId: Long): Flow<List<ReviewLog>> = reviewLogDao.getReviewLogsByCard(cardId)

    fun getAllReviewLogs(): Flow<List<ReviewLog>> = reviewLogDao.getAllReviewLogs()

    /**
     * Randomize cards by grouping them into time buckets of the given size.
     * Cards within the same bucket are shuffled randomly; bucket ordering
     * is preserved so more-overdue cards still come first.
     */
    private fun randomizeCardsByBucket(cards: List<Card>, bucketHours: Int): List<Card> {
        if (cards.isEmpty()) return cards

        val millisecondsPerBucket = bucketHours * 60 * 60 * 1000L

        val grouped = cards.groupBy { card ->
            card.nextReview / millisecondsPerBucket
        }

        return grouped.entries
            .sortedBy { it.key }
            .flatMap { (_, cardsInBucket) ->
                cardsInBucket.shuffled()
            }
    }

    companion object {
        /**
         * Given a target day-of-week (ISO-8601: 1=Monday..7=Sunday) and a set of practice
         * days, returns how many days forward to advance to reach the nearest practice day.
         * Returns 0 if targetDow is already a practice day. Never rounds backward.
         */
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
