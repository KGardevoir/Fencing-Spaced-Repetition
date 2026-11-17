// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.ConceptDao
import com.fencing.spacedrepetition.data.model.CardConceptCrossRef
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.ConceptLearningGroupCrossRef
import com.fencing.spacedrepetition.data.model.ConceptLearningState
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.Flow

/**
 * Learning concepts and which cards carry them. Scheduling -- drawing cards
 * by concept and grading concepts -- lives in [CardRepository], next to the
 * scheduling settings it shares with cards.
 */
class ConceptRepository(private val conceptDao: ConceptDao) {

    fun getAllConcepts(): Flow<List<Concept>> = conceptDao.getAllConcepts()

    suspend fun getAllConceptsSync(): List<Concept> = conceptDao.getAllConceptsSync()

    suspend fun getConceptByName(name: String): Concept? = conceptDao.getConceptByName(name.trim())

    fun getAllCardConceptCrossRefs(): Flow<List<CardConceptCrossRef>> = conceptDao.getAllCardConceptCrossRefs()

    suspend fun getAllCardConceptCrossRefsSync(): List<CardConceptCrossRef> =
        conceptDao.getAllCardConceptCrossRefsSync()

    fun getAllConceptLearningStates(): Flow<List<ConceptLearningState>> = conceptDao.getAllConceptLearningStates()

    suspend fun getAllConceptLearningStatesSync(): List<ConceptLearningState> =
        conceptDao.getAllConceptLearningStatesSync()

    fun getConceptsForCard(cardId: Long): Flow<List<Concept>> = conceptDao.getConceptsForCard(cardId)

    suspend fun getConceptsForCardSync(cardId: Long): List<Concept> = conceptDao.getConceptsForCardSync(cardId)

    /**
     * The concept named [name], created if there is none. Names are matched
     * ignoring case, so "parry 4" finds "Parry 4" rather than making a twin.
     */
    suspend fun getOrCreateConcept(name: String, description: String = ""): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A concept needs a name" }
        conceptDao.getConceptByName(trimmed)?.let { return it.id }
        return conceptDao.insertConcept(Concept(name = trimmed, description = description.trim()))
    }

    fun getAllConceptLearningGroups(): Flow<List<ConceptLearningGroupCrossRef>> =
        conceptDao.getAllConceptLearningGroups()

    /**
     * Create [concept] when its id is 0, otherwise rename/redescribe it, and
     * make [learningGroupIds] exactly the learning groups it belongs to (Global
     * is [Concept.inGlobal]). Which cards carry it, and its learning state,
     * are left alone: cards are tagged from the card editor.
     *
     * @return the concept's id, or null when the name belongs to another concept.
     */
    suspend fun saveConcept(concept: Concept, learningGroupIds: Set<Long>): Long? {
        val name = concept.name.trim()
        if (name.isEmpty()) return null
        val clash = conceptDao.getConceptByName(name)
        if (clash != null && clash.id != concept.id) return null

        val id = if (concept.id == 0L) {
            conceptDao.insertConcept(concept.copy(name = name, description = concept.description.trim()))
        } else {
            val existing = conceptDao.getConceptById(concept.id) ?: return null
            conceptDao.updateConcept(
                existing.copy(
                    name = name,
                    description = concept.description.trim(),
                    inGlobal = concept.inGlobal,
                    modified = Time.now()
                )
            )
            concept.id
        }
        conceptDao.deleteLearningGroupsForConcept(id)
        learningGroupIds.forEach { conceptDao.insertConceptLearningGroup(ConceptLearningGroupCrossRef(id, it)) }
        return id
    }

    /**
     * The concept named [name] -- found, or created -- made available in the
     * learning [contexts] (a learning group's id, or null for Global) on top
     * of wherever it already was. What the card editor's "Create New Concept"
     * does, so a concept created for a card can be picked for it.
     */
    suspend fun getOrCreateConceptIn(name: String, contexts: Set<Long?>): Long {
        val id = getOrCreateConcept(name)
        if (null in contexts) {
            conceptDao.getConceptById(id)?.takeIf { !it.inGlobal }
                ?.let { conceptDao.updateConcept(it.copy(inGlobal = true, modified = Time.now())) }
        }
        contexts.filterNotNull().forEach { conceptDao.insertConceptLearningGroup(ConceptLearningGroupCrossRef(id, it)) }
        return id
    }

    suspend fun deleteConcept(concept: Concept) = conceptDao.deleteConcept(concept)

    suspend fun addConceptToCard(cardId: Long, conceptId: Long) =
        conceptDao.insertCardConceptCrossRef(CardConceptCrossRef(cardId, conceptId))

    /**
     * Create or update the concepts an import carries, and any it only names
     * on cards, returning name -> id for linking the cards.
     *
     * Everything about a concept but its progress is the file's: its name
     * (followed by stable identifier), its description, and -- when the file
     * says -- where it is available. Its progress is not: a concept already
     * here keeps its own (it is derived from history anyway), and only a
     * concept new here starts from the states the file carries, each
     * learning group found or created by name through [learningGroupId].
     * See docs/DESIGN.md, "Archive".
     */
    suspend fun importConcepts(
        parsed: List<com.fencing.spacedrepetition.util.CardImportExport.ParsedConcept>,
        namedOnCards: Set<String>,
        learningGroupId: suspend (String) -> Long
    ): Map<String, Long> {
        val ids = mutableMapOf<String, Long>()
        parsed.forEach { concept ->
            val wasHere = concept.uid?.let { conceptDao.getConceptByUid(it) } != null ||
                conceptDao.getConceptByName(concept.name) != null
            val id = adoptConcept(concept)
            ids[concept.name] = id
            val current = conceptDao.getConceptById(id) ?: return@forEach

            // What it is, and where it is available when the file says.
            val availableIn = concept.availableIn
            var updated = current.copy(description = concept.description.trim(), modified = Time.now())
            if (availableIn != null) updated = updated.copy(inGlobal = concept.inGlobal)

            // Progress only for a concept that is new here.
            if (!wasHere) {
                val global = (concept.globalState ?: ConceptLearningState(id, 0L)).copy(conceptId = id)
                updated = updated.withGlobalState(global).copy(modified = Time.now())
                concept.learningStates.forEach { (learningGroupName, state) ->
                    conceptDao.upsertConceptLearningState(
                        state.copy(conceptId = id, learningGroupId = learningGroupId(learningGroupName))
                    )
                }
            }
            conceptDao.updateConcept(updated)

            if (availableIn != null) {
                conceptDao.deleteLearningGroupsForConcept(id)
                availableIn.forEach { name ->
                    conceptDao.insertConceptLearningGroup(ConceptLearningGroupCrossRef(id, learningGroupId(name)))
                }
            }
        }
        (namedOnCards - ids.keys).forEach { name ->
            if (name.isNotBlank()) ids[name] = getOrCreateConcept(name)
        }
        return ids
    }

    /**
     * The concept an import means: the one with the file's stable identifier
     * (renamed to the file's name if it differs and the name is free), else
     * the one with its name, else a new one carrying the file's identifier.
     */
    private suspend fun adoptConcept(concept: com.fencing.spacedrepetition.util.CardImportExport.ParsedConcept): Long {
        concept.uid?.let { uid ->
            conceptDao.getConceptByUid(uid)?.let { existing ->
                val clash = conceptDao.getConceptByName(concept.name)
                if (existing.name != concept.name && (clash == null || clash.id == existing.id)) {
                    conceptDao.updateConcept(existing.copy(name = concept.name, modified = Time.now()))
                }
                return existing.id
            }
            if (conceptDao.getConceptByName(concept.name) == null) {
                return conceptDao.insertConcept(
                    Concept(name = concept.name.trim(), description = concept.description.trim(), uid = uid)
                )
            }
        }
        return getOrCreateConcept(concept.name, concept.description)
    }

    /** Import support: write a concept's Global state onto it. */
    suspend fun setGlobalState(conceptId: Long, state: ConceptLearningState) {
        val concept = conceptDao.getConceptById(conceptId) ?: return
        conceptDao.updateConcept(concept.withGlobalState(state))
    }

    /** Import support: write a concept's state in one learning group. */
    suspend fun setLearningGroupState(state: ConceptLearningState) = conceptDao.upsertConceptLearningState(state)
}
