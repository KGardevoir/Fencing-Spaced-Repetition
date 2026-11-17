// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.algorithm.RetentionPeriod
import com.fencing.spacedrepetition.algorithm.RetentionReport
import com.fencing.spacedrepetition.algorithm.RetentionReportBuilder
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardConceptCrossRef
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.ConceptLearningState
import com.fencing.spacedrepetition.data.repository.ConceptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.util.Time
import com.fencing.spacedrepetition.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Assembles the retention report from every table it reads, recomputing
 * whenever any of them changes -- grading a card in another screen and
 * coming back shows the new numbers without a refresh.
 */
class RetentionReportViewModel(
    cardRepository: CardRepository,
    groupRepository: GroupRepository,
    conceptRepository: ConceptRepository? = null,
    /** Whether Global schedules by concept; each learning group carries its own switch. */
    globalConceptScheduling: Flow<Boolean> = flowOf(false)
) : ViewModel() {

    private val _period = MutableStateFlow(RetentionPeriod.WEEK)
    val period: StateFlow<RetentionPeriod> = _period.asStateFlow()

    fun setPeriod(period: RetentionPeriod) {
        _period.value = period
    }

    private data class Inputs(
        val cards: List<Card>,
        val groups: List<Group>,
        val crossRefs: List<CardGroupCrossRef>,
        val learningGroups: List<LearningGroup>,
        val learningStates: List<CardLearningState>
    )

    private val inputs = combine(
        cardRepository.getAllCards(),
        groupRepository.getAllGroups(),
        groupRepository.getAllCardGroupCrossRefs(),
        groupRepository.getAllLearningGroups(),
        groupRepository.getAllLearningStates(),
        ::Inputs
    )

    private data class ConceptInputs(
        val concepts: List<Concept>,
        val links: List<CardConceptCrossRef>,
        val states: List<ConceptLearningState>,
        val globalScheduling: Boolean
    )

    private val conceptInputs: Flow<ConceptInputs> = if (conceptRepository == null) {
        globalConceptScheduling.map { ConceptInputs(emptyList(), emptyList(), emptyList(), it) }
    } else combine(
        conceptRepository.getAllConcepts(),
        conceptRepository.getAllCardConceptCrossRefs(),
        conceptRepository.getAllConceptLearningStates(),
        globalConceptScheduling,
        ::ConceptInputs
    )

    /** null until the first computation finishes. */
    val report: StateFlow<RetentionReport?> = combine(
        inputs,
        cardRepository.getAllReviewLogs(),
        _period,
        conceptInputs
    ) { i: Inputs, logs: List<ReviewLog>, period: RetentionPeriod, c: ConceptInputs ->
        RetentionReportBuilder.build(
            cards = i.cards,
            groups = i.groups,
            crossRefs = i.crossRefs,
            learningGroups = i.learningGroups,
            learningStates = i.learningStates,
            logs = logs,
            now = Time.now(),
            utcOffsetSeconds = Time.utcOffsetSeconds(),
            period = period,
            concepts = c.concepts,
            cardConceptLinks = c.links,
            conceptStates = c.states,
            globalConceptScheduling = c.globalScheduling
        )
    }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
