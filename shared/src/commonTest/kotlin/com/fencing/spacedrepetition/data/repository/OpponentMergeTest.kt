// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.OpponentDao
import com.fencing.spacedrepetition.data.model.Opponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpponentMergeTest {

    /** In-memory opponents table plus the review_logs.opponentId column it rewrites. */
    private class FakeOpponentDao : OpponentDao {
        val rows = MutableStateFlow<Map<Long, Opponent>>(emptyMap())
        /** reviewLogId -> opponentId */
        val logOpponents = mutableMapOf<Long, Long?>()
        private var nextId = 1L

        override fun getAllOpponents(): Flow<List<Opponent>> = rows.map { it.values.toList() }
        override suspend fun getAllOpponentsSync() = rows.value.values.toList()
        override suspend fun getOpponentById(id: Long) = rows.value[id]
        override suspend fun findByUid(uid: String) = rows.value.values.find { it.uid == uid }
        override suspend fun findByName(name: String) = rows.value.values.find { it.name == name }
        override suspend fun insertOpponent(opponent: Opponent): Long {
            val id = nextId++
            rows.value = rows.value + (id to opponent.copy(id = id))
            return id
        }
        override suspend fun updateOpponent(opponent: Opponent) {
            rows.value = rows.value + (opponent.id to opponent)
        }
        override suspend fun deleteOpponent(opponent: Opponent) = deleteOpponentById(opponent.id)
        override suspend fun deleteOpponentById(id: Long) {
            rows.value = rows.value - id
        }
        override suspend fun reassignReviewLogs(fromId: Long, toId: Long) {
            logOpponents.replaceAll { _, v -> if (v == fromId) toId else v }
        }
        override fun getOpponentCount(): Flow<Int> = rows.map { it.size }
    }

    @Test
    fun mergeRelinksHistoryAndDeletesSource() = runTest {
        val dao = FakeOpponentDao()
        val repo = OpponentRepository(dao)
        val bob = repo.insertOpponent(Opponent(name = "Bob", skillMultiplier = 1.25))
        val bobby = repo.insertOpponent(Opponent(name = "Bobby", skillMultiplier = 0.8, notes = "lefty"))
        val alice = repo.insertOpponent(Opponent(name = "Alice"))
        dao.logOpponents.putAll(mapOf(1L to bobby, 2L to bob, 3L to bobby, 4L to alice, 5L to null))

        assertTrue(repo.mergeOpponents(sourceId = bobby, targetId = bob))

        assertEquals(mapOf(1L to bob, 2L to bob, 3L to bob, 4L to alice, 5L to null), dao.logOpponents)
        assertNull(repo.getOpponentById(bobby))
        val kept = repo.getOpponentById(bob)!!
        assertEquals("Bob", kept.name)
        assertEquals(1.25, kept.skillMultiplier)
        assertEquals("lefty", kept.notes)
    }

    @Test
    fun mergeAppendsNotesWhenBothHaveThem() = runTest {
        val dao = FakeOpponentDao()
        val repo = OpponentRepository(dao)
        val a = repo.insertOpponent(Opponent(name = "A", notes = "tall"))
        val b = repo.insertOpponent(Opponent(name = "B", notes = "fast"))

        repo.mergeOpponents(sourceId = b, targetId = a)

        assertEquals("tall\n\nB: fast", repo.getOpponentById(a)!!.notes)
    }

    @Test
    fun mergeRejectsSelfAndMissing() = runTest {
        val dao = FakeOpponentDao()
        val repo = OpponentRepository(dao)
        val a = repo.insertOpponent(Opponent(name = "A"))

        assertFalse(repo.mergeOpponents(a, a))
        assertFalse(repo.mergeOpponents(a, 999))
        assertFalse(repo.mergeOpponents(999, a))
        assertEquals(1, dao.rows.value.size)
    }
}
