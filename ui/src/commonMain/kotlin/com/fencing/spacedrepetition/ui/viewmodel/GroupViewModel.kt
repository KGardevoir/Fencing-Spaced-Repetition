// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.GroupWithCards
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything about groups that does not involve a file.
 *
 * Split for the same reason as CardViewModel: import and export take a Uri
 * and a ContentResolver, so that half subclasses this in :app, and the rest
 * runs in a browser unchanged.
 */
open class GroupViewModel(
    protected val groupRepository: GroupRepository,
    protected val cardRepository: CardRepository
) : ViewModel() {

    val allGroups: StateFlow<List<Group>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _groupSortOption = MutableStateFlow(GroupSortOption.NAME)
    val groupSortOption: StateFlow<GroupSortOption> = _groupSortOption.asStateFlow()

    fun setGroupSortOption(option: GroupSortOption) {
        _groupSortOption.value = option
    }

    val sortedGroups: StateFlow<List<Group>> = combine(
        groupRepository.getAllGroupsWithCards(),
        _groupSortOption
    ) { groupsWithCards, sortOption ->
        when (sortOption) {
            GroupSortOption.NAME -> groupsWithCards.map { it.group }.sortedBy { it.name.lowercase() }
            GroupSortOption.CARD_COUNT -> groupsWithCards.sortedByDescending { it.cards.size }.map { it.group }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupCount: StateFlow<Int> = groupRepository.getGroupCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedGroupForPractice = MutableStateFlow<Group?>(null)
    val selectedGroupForPractice: StateFlow<Group?> = _selectedGroupForPractice.asStateFlow()

    fun selectGroupForPractice(group: Group?) {
        _selectedGroupForPractice.value = group
    }

    fun addGroup(name: String, description: String = "", onSuccess: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val group = Group(name = name, description = description)
                val newId = groupRepository.insertGroup(group)
                onSuccess(newId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun addGroupWithSettings(group: Group, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val newId = groupRepository.insertGroup(group)
                if (group.independentLearning) {
                    groupRepository.toggleIndependentLearning(newId, true)
                }
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateGroup(group: Group, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                groupRepository.updateGroup(group)
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteGroup(group: Group) {
        viewModelScope.launch {
            groupRepository.deleteGroup(group)
        }
    }

    fun toggleIndependentLearning(groupId: Long, enabled: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                groupRepository.toggleIndependentLearning(groupId, enabled)
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun getDueCardCountForGroup(groupId: Long): Flow<Int> =
        groupRepository.getDueCardCountByGroup(groupId)

    // Import/Export state
    // protected, not private: the file-bound half lives in :app -- see
    // AndroidGroupViewModel -- and reports its progress through this.
    protected val _importExportState = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val importExportState: StateFlow<ImportExportState> = _importExportState.asStateFlow()

    fun resetImportExportState() {
        _importExportState.value = ImportExportState.Idle
    }
}
