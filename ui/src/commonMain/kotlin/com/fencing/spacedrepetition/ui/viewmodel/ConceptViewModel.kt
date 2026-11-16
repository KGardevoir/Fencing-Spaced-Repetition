// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.ConceptLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.ConceptRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One concept as the concept list shows it.
 *
 * @param groups the card groups the concept reaches: every group one of its
 *   cards is in. Derived, not stored -- tag a card and the concept is in its
 *   groups; that is the only association there is.
 * @param states the concept's progress in each learning context it is used
 *   in: Global (key null) when any of its groups learns Global, and each
 *   learning group one of its groups is in -- plus any context it already
 *   holds progress in, so nothing learnt disappears from view.
 * @param learningGroupIds the learning groups the concept belongs to -- where
 *   the card editor offers it; Global is [Concept.inGlobal].
 */
data class ConceptSummary(
    val concept: Concept,
    val cardIds: Set<Long>,
    val groups: List<Group>,
    val states: List<Pair<LearningGroup?, ConceptLearningState>>,
    val learningGroupIds: Set<Long> = emptySet()
)

class ConceptViewModel(
    private val conceptRepository: ConceptRepository,
    private val cardRepository: CardRepository,
    groupRepository: GroupRepository
) : ViewModel() {

    val learningGroups: StateFlow<List<LearningGroup>> = groupRepository.getAllLearningGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val summaries = combine(
        conceptRepository.getAllConcepts(),
        conceptRepository.getAllCardConceptCrossRefs(),
        conceptRepository.getAllConceptLearningStates(),
        groupRepository.getAllGroupsWithCards(),
        groupRepository.getAllLearningGroups()
    ) { concepts, crossRefs, learningStates, groupsWithCards, learningGroups ->
        val cardsByConcept = crossRefs.groupBy({ it.conceptId }, { it.cardId }).mapValues { it.value.toSet() }
        val groupsByCard = buildMap<Long, MutableList<Group>> {
            groupsWithCards.forEach { gwc -> gwc.cards.forEach { getOrPut(it.id) { mutableListOf() } += gwc.group } }
        }
        val statesByConcept = learningStates.groupBy { it.conceptId }

        concepts.map { concept ->
            val cardIds = cardsByConcept[concept.id].orEmpty()
            val groups = cardIds.flatMap { groupsByCard[it].orEmpty() }
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
            val stored = statesByConcept[concept.id].orEmpty().associateBy { it.learningGroupId }

            val usedLearningGroupIds = groups.mapNotNull { it.learningGroupId }.toSet() + stored.keys
            val showGlobal = groups.isEmpty() || groups.any { it.learningGroupId == null } ||
                concept.fsrsReps > 0
            val states = buildList {
                if (showGlobal) add(null to concept.globalState())
                learningGroups.filter { it.id in usedLearningGroupIds }.forEach { lg ->
                    add(lg to (stored[lg.id] ?: ConceptLearningState(concept.id, lg.id)))
                }
            }

            ConceptSummary(concept, cardIds, groups, states)
        }
    }

    val concepts: StateFlow<List<ConceptSummary>> = combine(
        summaries,
        conceptRepository.getAllConceptLearningGroups()
    ) { summaries, links ->
        val learningGroupsByConcept = links.groupBy({ it.conceptId }, { it.learningGroupId })
        summaries.map { it.copy(learningGroupIds = learningGroupsByConcept[it.concept.id].orEmpty().toSet()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    /**
     * Create or update [concept] -- its name, description and whether Global
     * offers it -- and make [learningGroupIds] the learning groups that do.
     */
    fun saveConcept(concept: Concept, learningGroupIds: Set<Long>) {
        viewModelScope.launch {
            if (conceptRepository.saveConcept(concept, learningGroupIds) == null) {
                _error.value = "A concept called \"${concept.name.trim()}\" already exists"
            }
        }
    }

    fun deleteConcept(concept: Concept) {
        viewModelScope.launch { conceptRepository.deleteConcept(concept) }
    }

    /**
     * Rebuild every concept from its cards' history. Happens on its own when
     * tags, history or groups change; this is the manual way to the same place.
     */
    fun rederiveAll() {
        viewModelScope.launch { cardRepository.rederiveConcepts() }
    }
}
