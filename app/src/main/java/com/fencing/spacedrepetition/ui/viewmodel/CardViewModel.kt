package com.fencing.spacedrepetition.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.CardWithGroupNames
import com.fencing.spacedrepetition.util.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardViewModel(
    private val repository: CardRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    val allCards: StateFlow<List<Card>> = repository.getAllCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCardsWithGroups: StateFlow<List<CardWithGroups>> = repository.getAllCardsWithGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueCardCount: StateFlow<Int> = repository.getDueCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cardCount: StateFlow<Int> = repository.getCardCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Groups for filtering
    val allGroups: StateFlow<List<Group>> = groupRepository.getAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGroupFilter = MutableStateFlow<Group?>(null)
    val selectedGroupFilter: StateFlow<Group?> = _selectedGroupFilter.asStateFlow()

    val filteredCards: StateFlow<List<Card>> = combine(
        allCardsWithGroups,
        selectedGroupFilter
    ) { cardsWithGroups, group ->
        if (group == null) {
            cardsWithGroups.map { it.card }
        } else {
            cardsWithGroups
                .filter { cardWithGroups -> cardWithGroups.groups.any { it.id == group.id } }
                .map { it.card }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectGroupFilter(group: Group?) {
        _selectedGroupFilter.value = group
    }

    fun getGroupsForCard(cardId: Long): Flow<List<Group>> =
        groupRepository.getGroupsForCard(cardId)

    fun getDueCardCountByGroup(groupId: Long): Flow<Int> =
        repository.getDueCardCountByGroup(groupId)

    fun addCard(
        question: String,
        answer: String,
        groupIds: List<Long>,
        algorithm: AlgorithmType,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val card = Card(
                    question = question,
                    answer = answer,
                    algorithm = algorithm
                )
                repository.insertCardWithGroups(card, groupIds)
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateCard(card: Card, groupIds: List<Long>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateCard(card.copy(modified = System.currentTimeMillis()))
                repository.updateCardGroups(card.id, groupIds)
                onSuccess()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun deleteCard(card: Card) {
        viewModelScope.launch {
            repository.deleteCard(card)
        }
    }

    fun initializeSampleData() {
        viewModelScope.launch {
            repository.initializeSampleData()
        }
    }

    // Import/Export state
    private val _importExportState = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val importExportState: StateFlow<ImportExportState> = _importExportState.asStateFlow()

    fun resetImportExportState() {
        _importExportState.value = ImportExportState.Idle
    }

    fun exportAllCards(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _importExportState.value = ImportExportState.Loading
            try {
                val cardsWithGroups = withContext(Dispatchers.IO) {
                    repository.getAllCardsWithGroupNames()
                }

                if (cardsWithGroups.isEmpty()) {
                    _importExportState.value = ImportExportState.Error("No cards to export")
                    return@launch
                }

                val exportData = cardsWithGroups.map { (card, groupNames) ->
                    CardWithGroupNames(card, groupNames)
                }

                val result = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        CardImportExport.exportCardsWithGroups(exportData, outputStream)
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

    fun importCards(
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

                // Get existing groups for mapping
                val groupNameMap = withContext(Dispatchers.IO) {
                    groupRepository.getGroupNameMap()
                }

                // Check if this is a full format import
                val hasFullState = parsedCards.any { it.hasFullState }

                val importedCount = withContext(Dispatchers.IO) {
                    if (hasFullState) {
                        // Full import with state and groups
                        val cards = parsedCards.map { CardImportExport.parsedCardToCard(it) }
                        val groupNamesPerCard = parsedCards.map { it.groupNames }
                        repository.importFullCards(cards, groupNamesPerCard, groupNameMap)
                    } else {
                        // Simple import
                        val cardsToImport = parsedCards.map { it.question to it.answer }
                        repository.importCards(cardsToImport, algorithm)
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
}
