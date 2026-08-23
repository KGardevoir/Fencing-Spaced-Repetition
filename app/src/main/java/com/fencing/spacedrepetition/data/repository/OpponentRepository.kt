// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.OpponentDao
import com.fencing.spacedrepetition.data.model.Opponent
import kotlinx.coroutines.flow.Flow

class OpponentRepository(private val opponentDao: OpponentDao) {

    fun getAllOpponents(): Flow<List<Opponent>> = opponentDao.getAllOpponents()

    suspend fun getAllOpponentsSync(): List<Opponent> = opponentDao.getAllOpponentsSync()

    suspend fun getOpponentById(id: Long): Opponent? = opponentDao.getOpponentById(id)

    suspend fun findByName(name: String): Opponent? = opponentDao.findByName(name.trim())

    /** Inserts a new opponent. Returns the new id, or -1 if a duplicate name exists. */
    suspend fun insertOpponent(opponent: Opponent): Long {
        val trimmed = opponent.copy(name = opponent.name.trim())
        if (trimmed.name.isEmpty()) return -1
        if (opponentDao.findByName(trimmed.name) != null) return -1
        return opponentDao.insertOpponent(trimmed)
    }

    suspend fun updateOpponent(opponent: Opponent) {
        val trimmed = opponent.copy(
            name = opponent.name.trim(),
            modified = System.currentTimeMillis()
        )
        opponentDao.updateOpponent(trimmed)
    }

    suspend fun deleteOpponent(opponent: Opponent) = opponentDao.deleteOpponent(opponent)

    suspend fun deleteOpponentById(id: Long) = opponentDao.deleteOpponentById(id)

    /**
     * Ensures each named opponent exists, creating missing ones with the supplied
     * skill multiplier and notes. Existing opponents (matched by name) are left
     * untouched — their current local values take precedence over imported ones.
     * Returns a map of opponent name -> id for relinking imported review logs.
     */
    suspend fun ensureOpponentsExist(
        parsed: List<Triple<String, Double, String>>
    ): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        parsed.forEach { (name, skillMultiplier, notes) ->
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@forEach
            val existing = opponentDao.findByName(trimmed)
            if (existing != null) {
                result[trimmed] = existing.id
            } else {
                val newId = opponentDao.insertOpponent(
                    Opponent(name = trimmed, skillMultiplier = skillMultiplier, notes = notes)
                )
                if (newId >= 0) result[trimmed] = newId
            }
        }
        return result
    }
}
