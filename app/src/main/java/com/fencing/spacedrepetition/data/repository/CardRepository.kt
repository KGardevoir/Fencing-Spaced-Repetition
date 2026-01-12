package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.algorithm.FSRSAlgorithm
import com.fencing.spacedrepetition.algorithm.SM2Algorithm
import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.dao.PracticeSessionDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CardRepository(
    private val cardDao: CardDao,
    private val sessionDao: PracticeSessionDao,
    private val reviewLogDao: ReviewLogDao,
    private val groupDao: GroupDao
) {
    private val fsrsAlgorithm = FSRSAlgorithm()
    private val sm2Algorithm = SM2Algorithm()

    // Card operations
    fun getAllCards(): Flow<List<Card>> = cardDao.getAllCards()

    suspend fun getAllCardsSync(): List<Card> = cardDao.getAllCardsSync()

    suspend fun getCardById(cardId: Long): Card? = cardDao.getCardById(cardId)

    fun getCardByIdFlow(cardId: Long): Flow<Card?> = cardDao.getCardByIdFlow(cardId)

    suspend fun getDueCards(limit: Int = 100): List<Card> = cardDao.getDueCards(limit = limit)

    fun getDueCardsFlow(limit: Int = 100): Flow<List<Card>> = cardDao.getDueCardsFlow(limit = limit)

    fun getDueCardCount(): Flow<Int> = cardDao.getDueCardCount()

    fun getCardsByCategory(category: String): Flow<List<Card>> = cardDao.getCardsByCategory(category)

    suspend fun insertCard(card: Card): Long = cardDao.insertCard(card)

    suspend fun updateCard(card: Card) = cardDao.updateCard(card)

    suspend fun deleteCard(card: Card) {
        cardDao.deleteCard(card)
        reviewLogDao.deleteReviewLogsByCard(card.id)
    }

    suspend fun deleteCardById(cardId: Long) {
        cardDao.deleteCardById(cardId)
        reviewLogDao.deleteReviewLogsByCard(cardId)
    }

    suspend fun resetCardState(cardId: Long) {
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
    }

    fun getCardCount(): Flow<Int> = cardDao.getCardCount()

    fun getAllCategories(): Flow<List<String>> = cardDao.getAllCategories()

    // Group-aware card operations
    fun getAllCardsWithGroups(): Flow<List<CardWithGroups>> = cardDao.getAllCardsWithGroups()

    fun getCardWithGroups(cardId: Long): Flow<CardWithGroups?> = cardDao.getCardWithGroups(cardId)

    suspend fun getDueCardsByGroup(groupId: Long, limit: Int = 100): List<Card> =
        cardDao.getDueCardsByGroup(groupId, limit = limit)

    suspend fun getCardsByGroupSync(groupId: Long): List<Card> =
        cardDao.getCardsByGroupSync(groupId)

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

    suspend fun getAllCardsWithGroupNames(): List<Pair<Card, List<String>>> {
        val cardsWithGroups = cardDao.getAllCardsWithGroups().first()
        return cardsWithGroups.map { cardWithGroups ->
            cardWithGroups.card to cardWithGroups.groups.map { it.name }
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
    suspend fun reviewCard(card: Card, grade: Grade, sessionId: Long? = null): Card {
        val now = System.currentTimeMillis()
        val elapsedDays = if (card.lastReview == 0L) 0 else
            ((now - card.lastReview) / (1000 * 60 * 60 * 24)).toInt()

        val updatedCard = when (card.algorithm) {
            AlgorithmType.FSRS -> reviewWithFSRS(card, grade, now, elapsedDays)
            AlgorithmType.SM2 -> reviewWithSM2(card, grade, now)
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
        val group = groupDao.getGroupById(groupId) ?: return reviewCard(card, grade, sessionId)

        if (!group.independentLearning) {
            // Use global learning state
            return reviewCard(card, grade, sessionId)
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
            AlgorithmType.FSRS -> reviewLearningStateWithFSRS(learningState, grade, now, elapsedDays)
            AlgorithmType.SM2 -> reviewLearningStateWithSM2(learningState, grade, now)
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

    private fun reviewWithFSRS(card: Card, grade: Grade, now: Long, elapsedDays: Int): Card {
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

        return card.copy(
            fsrsStability = newCard.stability,
            fsrsDifficulty = newCard.difficulty,
            fsrsElapsedDays = newCard.elapsedDays,
            fsrsScheduledDays = newCard.scheduledDays,
            fsrsReps = newCard.reps,
            fsrsLapses = newCard.lapses,
            fsrsState = newCard.state.name,
            lastReview = now,
            nextReview = now + (newCard.scheduledDays * 24 * 60 * 60 * 1000L),
            modified = now
        )
    }

    private fun reviewWithSM2(card: Card, grade: Grade, now: Long): Card {
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

        return card.copy(
            sm2EaseFactor = newCard.easeFactor,
            sm2Interval = newCard.interval,
            sm2Repetitions = newCard.repetitions,
            lastReview = now,
            nextReview = schedulingInfo.nextReviewDate,
            modified = now,
            // Update FSRS scheduled days for consistency in UI
            fsrsScheduledDays = newCard.interval
        )
    }

    private fun serializeCardState(card: Card): String {
        return when (card.algorithm) {
            AlgorithmType.FSRS -> "S:${card.fsrsStability},D:${card.fsrsDifficulty},ST:${card.fsrsState}"
            AlgorithmType.SM2 -> "EF:${card.sm2EaseFactor},I:${card.sm2Interval},R:${card.sm2Repetitions}"
        }
    }

    private fun reviewLearningStateWithFSRS(
        learningState: CardGroupLearningState,
        grade: Grade,
        now: Long,
        elapsedDays: Int
    ): CardGroupLearningState {
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

        return learningState.copy(
            fsrsStability = newCard.stability,
            fsrsDifficulty = newCard.difficulty,
            fsrsElapsedDays = newCard.elapsedDays,
            fsrsScheduledDays = newCard.scheduledDays,
            fsrsReps = newCard.reps,
            fsrsLapses = newCard.lapses,
            fsrsState = newCard.state.name,
            lastReview = now,
            nextReview = now + (newCard.scheduledDays * 24 * 60 * 60 * 1000L),
            modified = now
        )
    }

    private fun reviewLearningStateWithSM2(
        learningState: CardGroupLearningState,
        grade: Grade,
        now: Long
    ): CardGroupLearningState {
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

        return learningState.copy(
            sm2EaseFactor = newCard.easeFactor,
            sm2Interval = newCard.interval,
            sm2Repetitions = newCard.repetitions,
            lastReview = now,
            nextReview = schedulingInfo.nextReviewDate,
            modified = now,
            // Update FSRS scheduled days for consistency in UI
            fsrsScheduledDays = newCard.interval
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
}
