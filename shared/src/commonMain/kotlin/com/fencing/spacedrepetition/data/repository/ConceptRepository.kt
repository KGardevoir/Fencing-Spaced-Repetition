// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import com.fencing.spacedrepetition.data.dao.ConceptDao
import com.fencing.spacedrepetition.data.model.CardConceptCrossRef
import com.fencing.spacedrepetition.data.model.Concept
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

    /**
     * Create [concept] when its id is 0, otherwise rename/redescribe it, and
     * make [cardIds] exactly the cards carrying it. Its learning state is
     * left alone either way.
     *
     * @return the concept's id, or null when the name belongs to another concept.
     */
    suspend fun saveConcept(concept: Concept, cardIds: Set<Long>?): Long? {
        val name = concept.name.trim()
        if (name.isEmpty()) return null
        val clash = conceptDao.getConceptByName(name)
        if (clash != null && clash.id != concept.id) return null

        val id = if (concept.id == 0L) {
            conceptDao.insertConcept(concept.copy(name = name, description = concept.description.trim()))
        } else {
            val existing = conceptDao.getConceptById(concept.id) ?: return null
            conceptDao.updateConcept(
                existing.copy(name = name, description = concept.description.trim(), modified = Time.now())
            )
            concept.id
        }
        if (cardIds != null) {
            conceptDao.deleteAllCardsForConcept(id)
            cardIds.forEach { conceptDao.insertCardConceptCrossRef(CardConceptCrossRef(it, id)) }
        }
        return id
    }

    suspend fun deleteConcept(concept: Concept) = conceptDao.deleteConcept(concept)

    suspend fun addConceptToCard(cardId: Long, conceptId: Long) =
        conceptDao.insertCardConceptCrossRef(CardConceptCrossRef(cardId, conceptId))

    /**
     * Create or update the concepts an import carries, and any it only names
     * on cards, returning name -> id for linking the cards.
     *
     * A concept described in the file has its states replaced by the file's,
     * the way an imported card's are: Global from `state` (new when absent),
     * and the learning-group states it lists, each found or created by name
     * through [learningGroupId].
     */
    suspend fun importConcepts(
        parsed: List<com.fencing.spacedrepetition.util.CardImportExport.ParsedConcept>,
        namedOnCards: Set<String>,
        learningGroupId: suspend (String) -> Long
    ): Map<String, Long> {
        val ids = mutableMapOf<String, Long>()
        parsed.forEach { concept ->
            val id = getOrCreateConcept(concept.name, concept.description)
            ids[concept.name] = id
            conceptDao.getConceptById(id)?.let { existing ->
                val global = (concept.globalState ?: ConceptLearningState(id, 0L)).copy(conceptId = id)
                conceptDao.updateConcept(
                    existing.withGlobalState(global).copy(
                        description = concept.description.ifBlank { existing.description }
                    )
                )
            }
            conceptDao.deleteAllLearningStatesForConcept(id)
            concept.learningStates.forEach { (learningGroupName, state) ->
                conceptDao.upsertConceptLearningState(
                    state.copy(conceptId = id, learningGroupId = learningGroupId(learningGroupName))
                )
            }
        }
        (namedOnCards - ids.keys).forEach { name ->
            if (name.isNotBlank()) ids[name] = getOrCreateConcept(name)
        }
        return ids
    }

    /** Import support: write a concept's Global state onto it. */
    suspend fun setGlobalState(conceptId: Long, state: ConceptLearningState) {
        val concept = conceptDao.getConceptById(conceptId) ?: return
        conceptDao.updateConcept(concept.withGlobalState(state))
    }

    /** Import support: write a concept's state in one learning group. */
    suspend fun setLearningGroupState(state: ConceptLearningState) = conceptDao.upsertConceptLearningState(state)
}
