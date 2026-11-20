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

        // By local id and by stable identifier together, so the reviews -- and
        // any export of them -- name the opponent who is kept, and those that
        // only still carried the source's identifier (from a deletion and
        // re-import) follow too. See docs/DESIGN.md, "History".
        opponentDao.reassignReviewLogs(fromId = sourceId, toId = targetId, fromUid = source.uid, toUid = target.uid)

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
     * Ensures each named opponent exists, and makes each one the file
     * describes: the same person is the one with the file's stable identifier,
     * else the one with the name, else a new one carrying the identifier.
     *
     * An opponent already here takes the file's name (when no one else has
     * it), skill multiplier and notes -- an import updates everything but
     * progress, and an opponent's multiplier is not progress: every past
     * review recorded the multiplier it was scheduled with. See
     * docs/DESIGN.md, "Archive".
     *
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
            val existing = uid?.let { opponentDao.findByUid(it) } ?: opponentDao.findByName(trimmed)
            if (existing != null) {
                val nameFree = existing.name == trimmed || opponentDao.findByName(trimmed) == null
                val updated = existing.copy(
                    name = if (nameFree) trimmed else existing.name,
                    skillMultiplier = skillMultiplier,
                    notes = notes
                )
                if (updated != existing) opponentDao.updateOpponent(updated.copy(modified = Time.now()))
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
