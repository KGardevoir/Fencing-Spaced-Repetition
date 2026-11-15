// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.algorithm.RetentionPeriod
import com.fencing.spacedrepetition.algorithm.RetentionReport
import com.fencing.spacedrepetition.algorithm.RetentionReportBuilder
import com.fencing.spacedrepetition.data.model.Card
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
    groupRepository: GroupRepository
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

    /** null until the first computation finishes. */
    val report: StateFlow<RetentionReport?> = combine(
        inputs,
        cardRepository.getAllReviewLogs(),
        _period
    ) { i: Inputs, logs: List<ReviewLog>, period: RetentionPeriod ->
        RetentionReportBuilder.build(
            cards = i.cards,
            groups = i.groups,
            crossRefs = i.crossRefs,
            learningGroups = i.learningGroups,
            learningStates = i.learningStates,
            logs = logs,
            now = Time.now(),
            utcOffsetSeconds = Time.utcOffsetSeconds(),
            period = period
        )
    }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
