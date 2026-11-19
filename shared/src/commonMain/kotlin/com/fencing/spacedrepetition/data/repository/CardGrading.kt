// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.dao.OpponentDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.*
import com.fencing.spacedrepetition.util.Time

/**
 * Recorded on every [ReviewLog]. FSRS is the only scheduler now; the column
 * stays because logs written before SM-2 was removed still name it, and the
 * review-history export is positional.
 */
private const val ALGORITHM_NAME = "FSRS"

/**
 * Grading cards: on their own (Global) progress or a learning group's, for
 * real or only to see the result, and the review log every grade writes.
 */
class CardGrading internal constructor(
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val groupDao: GroupDao,
    private val opponentDao: OpponentDao,
    private val scheduler: Scheduler,
    private val conceptDerivation: ConceptDerivation
) {
    /**
     * Logs a grade staged in the card editor: [before] and [after] are the
     * progress where it was graded, in [groupId]'s learning context (Global
     * for none). Changes no progress; the editor saves that itself.
     */
    suspend fun logGradeFromEdit(
        cardId: Long,
        before: FsrsState,
        after: FsrsState,
        grade: Grade,
        groupId: Long? = null,
        opponentId: Long? = null,
        fightDifficulty: Int? = null
    ) {
        val now = Time.now()
        val group = groupId?.let { groupDao.getGroupById(it) }
        val mode = if (scheduler.usesConcepts(group?.learningGroupId)) SelectionMode.CONCEPT else SelectionMode.CARD
        reviewLogDao.insertReviewLog(
            reviewLogOf(
                cardId, null, now, grade, before, after, group,
                fightOf(opponentId, fightDifficulty), CardSelection(mode, SelectionReason.EDITOR)
            ).let { if (group == null) it.copy(groupName = LearningContexts.CARD_EDITOR) else it }
        )
        // A new review in the card's history: its concepts follow.
        conceptDerivation.rederiveConceptsForCards(listOf(cardId))
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
        val after = if (grade == Grade.SKIP) before else scheduler.schedule(before, grade, now, groupId, fight.stabilityMultiplier)
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
        val after = if (grade == Grade.SKIP) before else scheduler.schedule(before, grade, now, groupId, fight.stabilityMultiplier)
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
        val after = scheduler.schedule(card.fsrs, grade, now, groupId, fightOf(opponentId, fightDifficulty).stabilityMultiplier)
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
        val after = scheduler.schedule(state.fsrs, grade, Time.now(), groupId, fightOf(opponentId, fightDifficulty).stabilityMultiplier)
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
            val after = if (grade == Grade.SKIP) before else scheduler.schedule(before, grade, now, null, fight.stabilityMultiplier)
            logs += reviewLogOf(card.id, sessionId, now, grade, before, after, null, fight, selections[card.id])
            if (grade != Grade.SKIP) updated += card.withFsrs(after).copy(modified = now)
        }
        reviewLogDao.insertReviewLogs(logs)
        cardDao.updateCards(updated)
    }
}
