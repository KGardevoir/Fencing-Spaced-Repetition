// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.algorithm.FSRSAlgorithm
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.model.FsrsState
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.preferences.SchedulingPreferences
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.first

/**
 * FSRS with each learning context's settings: the one place a grade becomes
 * the next progress, for cards, learning-group states and concepts alike.
 * Also answers which learning context a group practises in and whether that
 * context schedules by concept.
 */
class Scheduler internal constructor(private val groupDao: GroupDao, private val preferences: SchedulingPreferences) {
    private val fsrsAlgorithm = FSRSAlgorithm()

    /** FSRS's probability of recall [elapsedDays] after a review, at [stability]. */
    fun retrievability(elapsedDays: Double, stability: Double): Double =
        fsrsAlgorithm.retrievability(elapsedDays, stability)

    /** The learning group [groupId] practises against; null is Global (and no group). */
    internal suspend fun learningGroupIdFor(groupId: Long?): Long? =
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
     * One FSRS step: [state] graded [grade] at [now], with the settings of the
     * learning context [groupId] practises in (maximum interval, retention,
     * fuzzing, practice days), and the stability gain scaled by
     * [stabilityMultiplier]. Cards, learning-group states and concepts are
     * all scheduled here; [fuzzing] null uses the context's setting.
     */
    internal suspend fun schedule(
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
     * The learning group [groupId] practises against, whose settings schedule
     * its learning progress; null for a Global group, or no group at all,
     * which the app-wide settings schedule.
     */
    internal suspend fun learningGroupFor(groupId: Long?): LearningGroup? {
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

    /**
     * Adjust scheduled days based on practice-days setting.
     * Snaps the interval to the nearest selected practice day so cards
     * come due on days the user will actually practice.
     *
     * Practice days use ISO-8601 convention: 1=Monday through 7=Sunday.
     */
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

    companion object {
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
