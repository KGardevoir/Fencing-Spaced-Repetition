// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.OpponentDao
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.util.Time
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
            modified = Time.now()
        )
        opponentDao.updateOpponent(trimmed)
    }

    suspend fun deleteOpponent(opponent: Opponent) = opponentDao.deleteOpponent(opponent)

    suspend fun deleteOpponentById(id: Long) = opponentDao.deleteOpponentById(id)

    /**
     * Folds [sourceId] into [targetId]: every review logged against the source
     * is relinked to the target, the source's notes are appended to the
     * target's, and the source is deleted. The target keeps its name and skill
     * multiplier. Past reviews keep the stability multiplier they were graded
     * with, so FSRS state is untouched -- this is the same metadata-only
     * relabel as reassigning a session's opponent, applied to all of them.
     *
     * Relinking runs before the delete, so an interruption between the two
     * leaves a harmless leftover opponent with no history rather than
     * orphaned reviews. Returns false if either id is missing or they match.
     */
    suspend fun mergeOpponents(sourceId: Long, targetId: Long): Boolean {
        if (sourceId == targetId) return false
        val source = opponentDao.getOpponentById(sourceId) ?: return false
        val target = opponentDao.getOpponentById(targetId) ?: return false

        opponentDao.reassignReviewLogs(fromId = sourceId, toId = targetId)

        val sourceNotes = source.notes.trim()
        if (sourceNotes.isNotEmpty() && !target.notes.contains(sourceNotes)) {
            val merged = if (target.notes.isBlank()) sourceNotes
                else "${target.notes.trimEnd()}\n\n${source.name}: $sourceNotes"
            opponentDao.updateOpponent(target.copy(notes = merged, modified = Time.now()))
        }

        opponentDao.deleteOpponent(source)
        return true
    }

    /**
     * Ensures each named opponent exists, creating missing ones with the supplied
     * skill multiplier and notes. Existing opponents (matched by name) are left
     * untouched — their current local values take precedence over imported ones.
     * Returns a map of opponent name -> id for relinking imported review logs.
     */
    suspend fun ensureOpponentsExist(
        parsed: List<Triple<String, Double, String>>,
        uidsByName: Map<String, String> = emptyMap()
    ): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        parsed.forEach { (name, skillMultiplier, notes) ->
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@forEach
            val uid = uidsByName[trimmed]
            // The same person is the one with the file's stable identifier --
            // renamed to the file's name when that is free -- else the one
            // with the name. Their local multiplier is kept either way.
            val byUid = uid?.let { opponentDao.findByUid(it) }
            if (byUid != null) {
                if (byUid.name != trimmed && opponentDao.findByName(trimmed) == null) {
                    opponentDao.updateOpponent(byUid.copy(name = trimmed, modified = Time.now()))
                }
                result[trimmed] = byUid.id
                return@forEach
            }
            val existing = opponentDao.findByName(trimmed)
            if (existing != null) {
                result[trimmed] = existing.id
            } else {
                val newId = opponentDao.insertOpponent(
                    Opponent(name = trimmed, skillMultiplier = skillMultiplier, notes = notes)
                        .let { if (uid != null) it.copy(uid = uid) else it }
                )
                if (newId >= 0) result[trimmed] = newId
            }
        }
        return result
    }
}
