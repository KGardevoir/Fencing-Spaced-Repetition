package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupViewModel(
    private val groupRepository: GroupRepository
) : ViewModel() {

    val allGroups: StateFlow<List<Group>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupCount: StateFlow<Int> = groupRepository.getGroupCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedGroupForPractice = MutableStateFlow<Group?>(null)
    val selectedGroupForPractice: StateFlow<Group?> = _selectedGroupForPractice.asStateFlow()

    fun selectGroupForPractice(group: Group?) {
        _selectedGroupForPractice.value = group
    }

    fun addGroup(name: String, description: String = "", onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val group = Group(name = name, description = description)
                groupRepository.insertGroup(group)
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

    fun getDueCardCountForGroup(groupId: Long): Flow<Int> =
        groupRepository.getDueCardCountByGroup(groupId)
}
