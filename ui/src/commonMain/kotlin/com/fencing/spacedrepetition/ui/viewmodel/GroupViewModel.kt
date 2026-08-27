// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.ui.ExportFile
import com.fencing.spacedrepetition.ui.ImportFile
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.CardWithGroupNames
import com.fencing.spacedrepetition.util.ImageStore
import com.fencing.spacedrepetition.util.parsedCardToCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything about groups, including moving one in or out as a file.
 *
 * The same story as CardViewModel: the file-bound half was a subclass in
 * :app while it spoke in Uris, and is here now that it speaks in
 * [ImportFile] and [ExportFile]. What is different about a group transfer is
 * only its scope -- one group's cards, into or out of one file -- and where
 * an import with no groups of its own puts them.
 */
class GroupViewModel(
    private val groupRepository: GroupRepository,
    private val cardRepository: CardRepository,
    private val imageStore: ImageStore
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

    // Import and export state, rendered by the group list screen.
    private val _importExportState = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val importExportState: StateFlow<ImportExportState> = _importExportState.asStateFlow()

    fun resetImportExportState() {
        _importExportState.value = ImportExportState.Idle
    }

    fun generateExportFilename(groupName: String): String =
        CardImportExport.generateExportFilename(groupName)

    fun generateCsvExportFilename(groupName: String): String =
        CardImportExport.generateCsvExportFilename(groupName)

    /**
     * Exports one group's cards, with the settings of every group they
     * belong to.
     *
     * No review history: a group export is meant to be a deck to hand
     * someone, and the history is the part of a collection that is nobody
     * else's. The whole-collection export is where it is offered.
     */
    fun exportGroupCards(groupId: Long, file: ExportFile) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithStates = groupRepository.getCardsByGroupWithStates(groupId)
                if (cardsWithStates.isEmpty()) {
                    _importExportState.value =
                        ImportExportState.Error("No cards to export in this group")
                    return@launch
                }

                val exportedGroupNames = cardsWithStates.flatMap { it.groupNames }.toSet()
                val groups = groupRepository.getAllGroupsSync()
                    .filter { it.name in exportedGroupNames }
                val images = imageStore.exportReader(cardsWithStates.map { it.card })

                _importExportState.value = file.write { out ->
                    CardImportExport.exportCardsWithGroupStates(
                        cardsWithStates = cardsWithStates,
                        out = out,
                        images = images,
                        groupSettings = groups
                    )
                }.asImportExportState()
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("Export failed: ${e.message}")
            }
        }
    }

    /**
     * Imports an archive into one group.
     *
     * A file that names its own groups keeps them and gains this one, so
     * importing a friend's deck into "Warm-ups" does not throw away the
     * structure they exported. A file that names none -- the oldest format --
     * has nowhere else to go, and its cards land here alone.
     *
     * Review history is not restored here even when the file carries some,
     * which is what this has always done: a group import is about the cards,
     * and the whole-collection import is where a backup is put back.
     */
    fun importCardsToGroup(
        groupId: Long,
        file: ImportFile
    ) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val contents = readArchive(file)
                if (contents is ArchiveContents.Failed) {
                    _importExportState.value = ImportExportState.Error(contents.message)
                    return@launch
                }
                val (parsedCards, parseErrors) = contents as ArchiveContents.Cards

                val groupIds = groupRepository.groupsForImport(parsedCards)
                val targetGroupName = groupRepository.getGroupById(groupId)?.name

                val importedCount = when {
                    parsedCards.any { it.isGroupSpecificState } ->
                        cardRepository.importCardsWithGroupStates(parsedCards, groupIds) {
                            CardImportExport.parsedCardToCard(it, imageStore)
                        }
                    parsedCards.any { it.hasFullState } ->
                        cardRepository.importFullCards(
                            parsedCards.map { CardImportExport.parsedCardToCard(it, imageStore) },
                            parsedCards.map { parsed ->
                                if (targetGroupName == null || targetGroupName in parsed.groupNames) {
                                    parsed.groupNames
                                } else {
                                    parsed.groupNames + targetGroupName
                                }
                            },
                            groupIds
                        )
                    else -> cardRepository.importCardsToGroup(
                        parsedCards.map { it.concept to it.answer },
                        groupId
                    )
                }

                _importExportState.value = ImportExportState.ImportSuccess(
                    importedCount = importedCount,
                    skippedCount = parseErrors.size,
                    errors = parseErrors
                )
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("Import failed: ${e.message}")
            }
        }
    }

    /**
     * Parses a CSV chosen for a group.
     *
     * It still goes through the group prompt, and the screen answers it
     * without asking -- see GroupListScreen, which knows which group the
     * chooser was opened for. The prompt is the format's, not the screen's: a
     * CSV says nothing about where its cards belong.
     */
    fun csvImportParseFile(file: ImportFile) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            _importExportState.value = try {
                readCsvForGroupSelection(file)
            } catch (e: Exception) {
                ImportExportState.Error("CSV import failed: ${e.message}")
            }
        }
    }

    /** Exports one group's cards as CSV: question, answer, and any images. */
    fun exportGroupCardsCsv(groupId: Long, file: ExportFile) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithGroups = groupRepository.getCardsByGroupWithGroupNames(groupId)
                    .map { (card, groupNames) -> CardWithGroupNames(card, groupNames) }

                if (cardsWithGroups.isEmpty()) {
                    _importExportState.value =
                        ImportExportState.Error("No cards to export in this group")
                    return@launch
                }

                val images = imageStore.exportReader(cardsWithGroups.map { it.card })

                _importExportState.value = file.write { out ->
                    CardImportExport.exportCardsToCsv(cardsWithGroups, out, images)
                }.asImportExportState()
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("CSV export failed: ${e.message}")
            }
        }
    }
}
