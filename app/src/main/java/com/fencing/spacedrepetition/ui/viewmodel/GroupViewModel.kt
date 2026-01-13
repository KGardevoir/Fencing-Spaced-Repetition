package com.fencing.spacedrepetition.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.CardWithGroupNames
import com.fencing.spacedrepetition.util.ExportResult
import com.fencing.spacedrepetition.util.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ImportExportState {
    object Idle : ImportExportState()
    object Loading : ImportExportState()
    data class ImportSuccess(val importedCount: Int, val skippedCount: Int, val errors: List<String>) : ImportExportState()
    data class ExportSuccess(val exportedCount: Int) : ImportExportState()
    data class Error(val message: String) : ImportExportState()
}

class GroupViewModel(
    private val groupRepository: GroupRepository,
    private val cardRepository: CardRepository
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
    private val _importExportState = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val importExportState: StateFlow<ImportExportState> = _importExportState.asStateFlow()

    fun resetImportExportState() {
        _importExportState.value = ImportExportState.Idle
    }

    fun exportGroupCards(groupId: Long, uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithStates = withContext(Dispatchers.IO) {
                    groupRepository.getCardsByGroupWithStates(groupId)
                }

                if (cardsWithStates.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards to export in this group")
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        CardImportExport.exportCardsWithGroupStates(cardsWithStates, outputStream)
                    } ?: ExportResult.Error("Failed to open file for writing")
                }

                _importExportState.value = when (result) {
                    is ExportResult.Success -> ImportExportState.ExportSuccess(result.exportedCount)
                    is ExportResult.Error -> ImportExportState.Error(result.message)
                }
            } catch (e: Exception) {
                _importExportState.value = ImportExportState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun importCardsToGroup(
        groupId: Long,
        uri: Uri,
        contentResolver: ContentResolver,
        algorithm: AlgorithmType = AlgorithmType.FSRS
    ) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val parseResult = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        CardImportExport.parseCards(inputStream)
                    }
                }

                if (parseResult == null) {
                    _importExportState.value = ImportExportState.Error("Failed to open file for reading")
                    return@launch
                }

                val (parsedCards, parseErrors) = parseResult

                if (parsedCards.isEmpty() && parseErrors.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("File is empty")
                    return@launch
                }

                if (parsedCards.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No valid cards found. Errors:\n${parseErrors.joinToString("\n")}")
                    return@launch
                }

                // Collect all unique group names from parsed cards
                val allGroupNames = parsedCards
                    .flatMap { it.groupNames }
                    .filter { it.isNotBlank() }
                    .toSet()

                // Detect which groups have independent learning (groups with state-specific rows)
                val groupsWithIndependentLearning = parsedCards
                    .filter { it.isGroupSpecificState }
                    .mapNotNull { it.stateContext }
                    .toSet()

                // Ensure all groups exist (creates missing ones automatically)
                val groupNameMap = withContext(Dispatchers.IO) {
                    groupRepository.ensureGroupsExist(allGroupNames, groupsWithIndependentLearning)
                }

                // Check if this is a full format import with state
                val hasFullState = parsedCards.any { it.hasFullState }
                // Check if any cards have group-specific states (V2 format)
                val hasGroupSpecificStates = parsedCards.any { it.isGroupSpecificState }

                val importedCount = withContext(Dispatchers.IO) {
                    when {
                        hasGroupSpecificStates -> {
                            // V2 format with group-specific states
                            cardRepository.importCardsWithGroupStates(parsedCards, groupNameMap)
                        }
                        hasFullState -> {
                            // V1 full import with state - also add to target group
                            val cards = parsedCards.map { CardImportExport.parsedCardToCard(it) }
                            // Merge target group with any groups from file
                            val targetGroupName = groupRepository.getGroupById(groupId)?.name
                            val groupNamesPerCard = parsedCards.map { parsed ->
                                val fileGroups = parsed.groupNames.toMutableList()
                                // Add target group if not already present
                                if (targetGroupName != null && targetGroupName !in fileGroups) {
                                    fileGroups.add(targetGroupName)
                                }
                                fileGroups
                            }
                            cardRepository.importFullCards(cards, groupNamesPerCard, groupNameMap)
                        }
                        else -> {
                            // Simple import to specific group
                            val cardsToImport = parsedCards.map { it.question to it.answer }
                            cardRepository.importCardsToGroup(cardsToImport, groupId, algorithm)
                        }
                    }
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

    fun generateExportFilename(groupName: String): String =
        CardImportExport.generateExportFilename(groupName)
}
