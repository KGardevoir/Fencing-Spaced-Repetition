// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.fencing.spacedrepetition.BuildInfo
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.navigation.Destination
import com.fencing.spacedrepetition.ui.navigation.Navigator
import com.fencing.spacedrepetition.ui.navigation.rememberNavigator
import com.fencing.spacedrepetition.ui.screen.AddEditCardScreen
import com.fencing.spacedrepetition.ui.screen.CardListScreen
import com.fencing.spacedrepetition.ui.screen.GradingScreen
import com.fencing.spacedrepetition.ui.screen.GroupEditScreen
import com.fencing.spacedrepetition.ui.screen.GroupListScreen
import com.fencing.spacedrepetition.ui.screen.HistoryScreen
import com.fencing.spacedrepetition.ui.screen.HomeScreen
import com.fencing.spacedrepetition.ui.screen.OpponentsScreen
import com.fencing.spacedrepetition.ui.screen.PracticeScreen
import com.fencing.spacedrepetition.ui.screen.SettingsScreen
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel
import com.fencing.spacedrepetition.util.backupReminder
import kotlinx.coroutines.flow.flowOf

/**
 * The whole app: what to draw, and what each screen's callbacks do.
 *
 * Shared, not per-platform, because none of it is platform-specific any more.
 * The screens take plain values, the view models take repositories, the back
 * stack is a list, and the two things that genuinely differ -- choosing files
 * and scheduling backups -- arrive as [FileTransfer] and are handled inside
 * the view models respectively.
 *
 * This holds the state collection that the screens used to do for themselves.
 * It reads as a lot of collectAsState, and that is the point: it is the only
 * place in the app where a Flow becomes a value, so a screen can be looked at
 * and understood without knowing where its data comes from.
 */
@Composable
fun App(
    cardViewModel: CardViewModel,
    practiceViewModel: PracticeViewModel,
    groupViewModel: GroupViewModel,
    settingsViewModel: SettingsViewModel,
    historyViewModel: HistoryViewModel,
    opponentViewModel: OpponentViewModel,
    buildInfo: BuildInfo,
    transfer: FileTransfer,
    onOpenLink: (String) -> Unit,
    /**
     * The backup folder's display name, and how to ask for a different one.
     *
     * Not read from the settings, which hold a storage-framework URI that
     * only Android can turn into a name a person recognises. The browser has
     * no folder to choose at all and passes null.
     */
    backupFolderName: String?,
    onPickBackupFolder: () -> Unit,
    navigator: Navigator = rememberNavigator()
) {
    when (val destination = navigator.current) {

        Destination.Home -> {
            val groups by groupViewModel.allGroups.collectAsState()
            val savedGroupId by settingsViewModel.selectedGroupId.collectAsState()
            val globalCardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
            val totalCardCount by cardViewModel.cardCount.collectAsState()
            val totalDueCount by cardViewModel.dueCardCount.collectAsState()
            val reminderEnabled by settingsViewModel.backupReminderEnabled.collectAsState()
            val reminderIntervalDays by settingsViewModel.backupReminderIntervalDays.collectAsState()
            val lastBackupTime by settingsViewModel.lastBackupTime.collectAsState()
            val reminderDismissedTime by
                settingsViewModel.backupReminderDismissedTime.collectAsState()

            // Only where nothing backs up on its own. Read at composition
            // rather than on a timer: a reminder measured in days does not
            // need to appear the moment it comes due, and the home screen is
            // composed every time the user comes back to it.
            val reminder = if (settingsViewModel.automaticBackups) null else backupReminder(
                enabled = reminderEnabled,
                cardCount = totalCardCount,
                lastBackupTime = lastBackupTime,
                dismissedTime = reminderDismissedTime,
                intervalDays = reminderIntervalDays
            )

            val selectedGroup = groups.find { it.id == savedGroupId } ?: groups.firstOrNull()

            // Persist a fallback selection once groups exist and the saved one
            // does not. A write, so it belongs here rather than in the screen.
            LaunchedEffect(groups, savedGroupId) {
                if (groups.isNotEmpty() && groups.none { it.id == savedGroupId }) {
                    settingsViewModel.setSelectedGroupId(groups.first().id)
                }
            }

            // A group's own cards-per-session overrides the global setting.
            val cardsPerSession = selectedGroup?.cardsPerSession ?: globalCardsPerSession

            val cardsToPractise = selectedGroup?.let { group ->
                cardViewModel.getCardCountByGroup(group.id).collectAsState(initial = 0).value
            } ?: totalCardCount

            val dueCount = selectedGroup?.let { group ->
                cardViewModel.getDueCardCountByGroup(group.id).collectAsState(initial = 0).value
            } ?: totalDueCount

            HomeScreen(
                groups = groups,
                selectedGroup = selectedGroup,
                cardsPerSession = cardsPerSession,
                totalCardCount = totalCardCount,
                totalDueCount = totalDueCount,
                cardsToPractise = cardsToPractise,
                dueCount = dueCount,
                backupReminder = reminder,
                onBackUpNow = settingsViewModel::runBackupNow,
                onDismissBackupReminder = settingsViewModel::dismissBackupReminder,
                onSelectGroup = { settingsViewModel.setSelectedGroupId(it.id) },
                onStartPractice = {
                    practiceViewModel.startNewSession(cardsPerSession, selectedGroup?.id)
                    navigator.go(Destination.Practice)
                },
                onNavigateToCards = { navigator.go(Destination.CardList) },
                onNavigateToGroups = { navigator.go(Destination.GroupList) },
                onNavigateToSettings = { navigator.go(Destination.Settings) },
                onNavigateToHistory = { navigator.go(Destination.History) },
                onNavigateToOpponents = { navigator.go(Destination.Opponents) }
            )
        }

        Destination.Practice -> {
            val uiState by practiceViewModel.uiState.collectAsState()
            val sessionCards by practiceViewModel.sessionCards.collectAsState()
            val currentCardIndex by practiceViewModel.currentCardIndex.collectAsState()
            val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()

            PracticeScreen(
                uiState = uiState,
                sessionCards = sessionCards,
                currentCardIndex = currentCardIndex,
                autoShowAnswer = autoShowAnswer,
                onUpdateCard = practiceViewModel::updateCardComplete,
                onNextCard = practiceViewModel::nextCard,
                onPreviousCard = practiceViewModel::previousCard,
                onFinishPractice = practiceViewModel::finishPractice,
                onNavigateToGrading = { navigator.go(Destination.Grading) },
                onNavigateBack = {
                    practiceViewModel.resetSession()
                    navigator.back()
                }
            )
        }

        Destination.Grading -> {
            val uiState by practiceViewModel.uiState.collectAsState()
            val sessionCards by practiceViewModel.sessionCards.collectAsState()
            val opponents by practiceViewModel.opponents.collectAsState()
            val sessionOpponentId by practiceViewModel.sessionOpponentId.collectAsState()

            GradingScreen(
                uiState = uiState,
                sessionCards = sessionCards,
                opponents = opponents,
                sessionOpponentId = sessionOpponentId,
                onSetSessionOpponent = practiceViewModel::setSessionOpponent,
                onCreateOpponent = practiceViewModel::createOpponent,
                onUpdateOpponentDifficulty = practiceViewModel::updateOpponentDifficulty,
                onUpdateGrade = practiceViewModel::updateGrade,
                onUpdateNotes = practiceViewModel::updateNotes,
                onUpdateOpponent = practiceViewModel::updateOpponent,
                onSubmitGrades = practiceViewModel::submitGrades,
                onComplete = {
                    practiceViewModel.resetSession()
                    navigator.resetTo(Destination.Home)
                },
                onNavigateBack = {
                    practiceViewModel.backToPracticing()
                    navigator.back()
                }
            )
        }

        Destination.CardList -> {
            val allCards by cardViewModel.filteredCards.collectAsState()
            val allCardsWithGroups by cardViewModel.allCardsWithGroups.collectAsState()
            val groups by groupViewModel.allGroups.collectAsState()
            val selectedGroupFilters by cardViewModel.selectedGroupFilters.collectAsState()
            val showDisabledFilter by cardViewModel.showDisabledFilter.collectAsState()
            val searchQuery by cardViewModel.searchQuery.collectAsState()
            val cardCount by cardViewModel.cardCount.collectAsState()
            val importExportState by cardViewModel.importExportState.collectAsState()
            val cardSortOption by cardViewModel.cardSortOption.collectAsState()
            val sortDirection by cardViewModel.sortDirection.collectAsState()
            val isSelectionMode by cardViewModel.isSelectionMode.collectAsState()
            val selectedCardIds by cardViewModel.selectedCardIds.collectAsState()

            CardListScreen(
                allCards = allCards,
                allCardsWithGroups = allCardsWithGroups,
                groups = groups,
                selectedGroupFilters = selectedGroupFilters,
                showDisabledFilter = showDisabledFilter,
                searchQuery = searchQuery,
                cardCount = cardCount,
                importExportState = importExportState,
                cardSortOption = cardSortOption,
                sortDirection = sortDirection,
                isSelectionMode = isSelectionMode,
                selectedCardIds = selectedCardIds,
                learningStatesFor = { cardId ->
                    remember(cardId) { cardViewModel.getLearningStatesForCard(cardId) }
                        .collectAsState(initial = emptyList()).value
                },
                onUpdateSearchQuery = cardViewModel::updateSearchQuery,
                onClearGroupFilters = cardViewModel::clearGroupFilters,
                onToggleGroupFilter = cardViewModel::toggleGroupFilter,
                onToggleDisabledFilter = cardViewModel::toggleDisabledFilter,
                onSetCardSortOption = cardViewModel::setCardSortOption,
                onToggleSortDirection = cardViewModel::toggleSortDirection,
                onToggleSelectionMode = cardViewModel::toggleSelectionMode,
                onExitSelectionMode = cardViewModel::exitSelectionMode,
                onSelectAllCards = cardViewModel::selectAllCards,
                onToggleCardSelection = cardViewModel::toggleCardSelection,
                onSetSelectedCardsDisabled = { cardViewModel.setSelectedCardsDisabled(it) },
                onToggleCardDisabled = cardViewModel::toggleCardDisabled,
                onDeleteCard = cardViewModel::deleteCard,
                onDeleteSelectedCards = { cardViewModel.deleteSelectedCards() },
                onUpdateSelectedCardsGroups = { cardViewModel.updateSelectedCardsGroups(it) },
                onResetSelectedCardsGlobalState = { cardViewModel.resetSelectedCardsGlobalState() },
                onResetSelectedCardsInGroups = { cardViewModel.resetSelectedCardsInGroups(it) },
                onResetSelectedCardsBothStates = { cardViewModel.resetSelectedCardsBothStates(it) },
                onResetImportExportState = cardViewModel::resetImportExportState,
                onCsvImportInto = transfer::csvImportInto,
                onCsvImportIntoNewGroup = transfer::csvImportIntoNewGroup,
                onImportArchive = transfer::importCards,
                onExportAllArchive = transfer::exportAllCards,
                onExportGroupsArchive = transfer::exportGroups,
                onImportCsv = transfer::importCardsCsv,
                onExportAllCsv = transfer::exportAllCardsCsv,
                onExportGroupsCsv = transfer::exportGroupsCsv,
                onExportAllPhotos = transfer::exportAllPhotos,
                onNavigateToAddCard = { navigator.go(Destination.AddCard(it)) },
                onNavigateToEditCard = { navigator.go(Destination.EditCard(it)) },
                onNavigateBack = { navigator.back() }
            )
        }

        is Destination.AddCard -> CardEditor(
            cardViewModel = cardViewModel,
            groupViewModel = groupViewModel,
            cardToEdit = null,
            initialGroupId = destination.initialGroupId,
            onNavigateBack = { navigator.back() }
        )

        is Destination.EditCard -> CardEditor(
            cardViewModel = cardViewModel,
            groupViewModel = groupViewModel,
            cardToEdit = destination.card,
            initialGroupId = null,
            onNavigateBack = { navigator.back() }
        )

        Destination.GroupList -> {
            val sortedGroups by groupViewModel.sortedGroups.collectAsState()
            val importExportState by groupViewModel.importExportState.collectAsState()
            val groupSortOption by groupViewModel.groupSortOption.collectAsState()

            GroupListScreen(
                groups = sortedGroups,
                sortOption = groupSortOption,
                importExportState = importExportState,
                dueCardCountFor = { groupId ->
                    remember(groupId) { groupViewModel.getDueCardCountForGroup(groupId) }
                        .collectAsState(initial = 0).value
                },
                onSetSortOption = groupViewModel::setGroupSortOption,
                onDeleteGroup = groupViewModel::deleteGroup,
                onDismissImportExport = groupViewModel::resetImportExportState,
                onImportTsv = transfer::importIntoGroup,
                onExportTsv = transfer::exportGroup,
                onImportCsv = transfer::importCsvIntoGroup,
                onExportCsv = transfer::exportGroupCsv,
                onCsvImportInto = transfer::csvImportInto,
                onCsvImportIntoNewGroup = transfer::csvImportIntoNewGroup,
                onNavigateBack = { navigator.back() },
                onNavigateToEdit = { groupId ->
                    sortedGroups.find { it.id == groupId }
                        ?.let { navigator.go(Destination.EditGroup(it)) }
                },
                onNavigateToAdd = { navigator.go(Destination.AddGroup) }
            )
        }

        Destination.AddGroup -> GroupEditor(
            group = Group(name = ""),
            groupCardCount = 0,
            cardViewModel = cardViewModel,
            settingsViewModel = settingsViewModel,
            onSave = { newGroup -> groupViewModel.addGroupWithSettings(newGroup) { navigator.back() } },
            onNavigateBack = { navigator.back() }
        )

        is Destination.EditGroup -> {
            val group = destination.group
            val groupCardCount by remember(group.id) {
                cardViewModel.getCardCountForGroup(group.id)
            }.collectAsState(initial = 0)

            GroupEditor(
                group = group,
                groupCardCount = groupCardCount,
                cardViewModel = cardViewModel,
                settingsViewModel = settingsViewModel,
                onSave = { updated ->
                    // Toggling independent learning initialises per-group
                    // learning states, so it is a separate call rather than
                    // part of the row update.
                    if (updated.independentLearning != group.independentLearning) {
                        groupViewModel.toggleIndependentLearning(group.id, updated.independentLearning)
                    }
                    groupViewModel.updateGroup(updated) { navigator.back() }
                },
                onNavigateBack = { navigator.back() }
            )
        }

        Destination.History -> {
            val historyItems by historyViewModel.historyItems.collectAsState()
            val opponents by historyViewModel.opponents.collectAsState()
            val opponentFilter by historyViewModel.opponentFilter.collectAsState()
            val searchQuery by historyViewModel.searchQuery.collectAsState()
            val selectedGroup by historyViewModel.selectedGroup.collectAsState()
            val availableGroups by historyViewModel.availableGroups.collectAsState()

            HistoryScreen(
                historyItems = historyItems,
                opponents = opponents,
                opponentFilter = opponentFilter,
                searchQuery = searchQuery,
                selectedGroup = selectedGroup,
                availableGroups = availableGroups,
                reviewLogsForSession = { sessionId ->
                    remember(sessionId) { historyViewModel.getReviewLogsForSession(sessionId) }
                        .collectAsState(initial = emptyList()).value
                },
                onSearchQueryChange = { historyViewModel.searchQuery.value = it },
                onSelectedGroupChange = { historyViewModel.selectedGroup.value = it },
                onSetOpponentFilter = historyViewModel::setOpponentFilter,
                onUpdateReviewLogNotes = historyViewModel::updateReviewLogNotes,
                onUpdateReviewLogOpponent = historyViewModel::updateReviewLogOpponent,
                onCreateOpponent = historyViewModel::createOpponent,
                onNavigateBack = { navigator.back() }
            )
        }

        Destination.Opponents -> {
            val opponents by opponentViewModel.opponents.collectAsState()
            val error by opponentViewModel.error.collectAsState()

            OpponentsScreen(
                opponents = opponents,
                error = error,
                onAddOpponent = { name, multiplier, notes, onDone ->
                    opponentViewModel.addOpponent(name, multiplier, notes, onDone)
                },
                onUpdateOpponent = { opponent, onDone ->
                    opponentViewModel.updateOpponent(opponent, onDone)
                },
                onDeleteOpponent = { opponentViewModel.deleteOpponent(it) },
                onClearError = opponentViewModel::clearError,
                onNavigateBack = { navigator.back() }
            )
        }

        Destination.Settings -> {
            val totalCards by cardViewModel.cardCount.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()
            val cardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
            val randomizeDueCards by settingsViewModel.randomizeDueCards.collectAsState()
            val randomizeBucketHours by settingsViewModel.randomizeBucketHours.collectAsState()
            val maximumInterval by settingsViewModel.maximumInterval.collectAsState()
            val practiceDays by settingsViewModel.practiceDays.collectAsState()
            val fsrsRetention by settingsViewModel.fsrsRetention.collectAsState()
            val practiceScheduleEstimate by cardViewModel.practiceScheduleEstimate.collectAsState()
            val historyWindowDays by cardViewModel.historyWindowDays.collectAsState()
            val sm2IntervalModifier by settingsViewModel.sm2IntervalModifier.collectAsState()
            val fsrsEnableFuzzing by settingsViewModel.fsrsEnableFuzzing.collectAsState()
            val autoBackupEnabled by settingsViewModel.autoBackupEnabled.collectAsState()
            val autoBackupIntervalDays by settingsViewModel.autoBackupIntervalDays.collectAsState()
            val lastBackupTime by settingsViewModel.lastBackupTime.collectAsState()
            val maxBackupsKept by settingsViewModel.maxBackupsKept.collectAsState()
            val backupReminderEnabled by settingsViewModel.backupReminderEnabled.collectAsState()
            val backupReminderIntervalDays by
                settingsViewModel.backupReminderIntervalDays.collectAsState()

            SettingsScreen(
                totalCards = totalCards,
                buildInfo = buildInfo,
                themeMode = themeMode,
                autoShowAnswer = autoShowAnswer,
                cardsPerSession = cardsPerSession,
                randomizeDueCards = randomizeDueCards,
                randomizeBucketHours = randomizeBucketHours,
                maximumInterval = maximumInterval,
                practiceDays = practiceDays,
                fsrsRetention = fsrsRetention,
                practiceScheduleEstimate = practiceScheduleEstimate,
                historyWindowDays = historyWindowDays,
                sm2IntervalModifier = sm2IntervalModifier,
                fsrsEnableFuzzing = fsrsEnableFuzzing,
                autoBackupEnabled = autoBackupEnabled,
                autoBackupIntervalDays = autoBackupIntervalDays,
                lastBackupTime = lastBackupTime,
                maxBackupsKept = maxBackupsKept,
                automaticBackupAvailable = settingsViewModel.automaticBackups,
                backupReminderEnabled = backupReminderEnabled,
                backupReminderIntervalDays = backupReminderIntervalDays,
                backupFolderName = backupFolderName,
                onPickBackupFolder = onPickBackupFolder,
                onSetThemeMode = settingsViewModel::setThemeMode,
                onSetAutoShowAnswer = settingsViewModel::setAutoShowAnswer,
                onSetCardsPerSession = settingsViewModel::setCardsPerSession,
                onSetRandomizeDueCards = settingsViewModel::setRandomizeDueCards,
                onSetRandomizeBucketHours = settingsViewModel::setRandomizeBucketHours,
                onSetMaximumInterval = settingsViewModel::setMaximumInterval,
                onTogglePracticeDay = settingsViewModel::togglePracticeDay,
                onSetFsrsRetention = settingsViewModel::setFsrsRetention,
                onSetHistoryWindowDays = cardViewModel::setHistoryWindowDays,
                onSetSm2IntervalModifier = settingsViewModel::setSm2IntervalModifier,
                onSetFsrsEnableFuzzing = settingsViewModel::setFsrsEnableFuzzing,
                onSetAutoBackupEnabled = settingsViewModel::setAutoBackupEnabled,
                onSetAutoBackupIntervalDays = settingsViewModel::setAutoBackupIntervalDays,
                onSetMaxBackupsKept = settingsViewModel::setMaxBackupsKept,
                onSetBackupReminderEnabled = settingsViewModel::setBackupReminderEnabled,
                onSetBackupReminderIntervalDays = settingsViewModel::setBackupReminderIntervalDays,
                onRunBackupNow = settingsViewModel::runBackupNow,
                onDeleteAllCards = { cardViewModel.deleteAllCards() },
                onOpenLink = onOpenLink,
                onNavigateBack = { navigator.back() }
            )
        }
    }
}

/**
 * Binds the view models the card editor needs.
 *
 * Both flows are remembered by card id so collectAsState does not restart --
 * and briefly re-emit emptyList() -- on every recomposition, which would
 * reset the editor's group selection under it.
 */
@Composable
private fun CardEditor(
    cardViewModel: CardViewModel,
    groupViewModel: GroupViewModel,
    cardToEdit: com.fencing.spacedrepetition.data.model.Card?,
    initialGroupId: Long?,
    onNavigateBack: () -> Unit
) {
    val allGroups by groupViewModel.allGroups.collectAsState()
    val cardGroupsFlow = remember(cardToEdit?.id) {
        cardToEdit?.let { cardViewModel.getGroupsForCard(it.id) } ?: flowOf(emptyList<Group>())
    }
    val cardGroups by cardGroupsFlow.collectAsState(initial = emptyList())
    val learningStatesFlow = remember(cardToEdit?.id) {
        cardToEdit?.let { cardViewModel.getLearningStatesForCard(it.id) }
            ?: flowOf(emptyList<CardGroupLearningState>())
    }
    val learningStates by learningStatesFlow.collectAsState(initial = emptyList())

    AddEditCardScreen(
        allGroups = allGroups,
        cardGroups = cardGroups,
        learningStates = learningStates,
        cardToEdit = cardToEdit,
        initialGroupId = initialGroupId,
        onComputeGrade = { cardId, grade, groupId, onComputed ->
            cardViewModel.computeGradeCard(cardId, grade, groupId, onComputed)
        },
        onResetCardState = { cardId, resetGroupStates, onDone ->
            cardViewModel.resetCardState(cardId, resetGroupStates, onDone)
        },
        onResetCardStateInGroup = { cardId, groupId, onDone ->
            cardViewModel.resetCardStateInGroup(cardId, groupId, onDone)
        },
        onUpdateCard = cardViewModel::updateCard,
        onRecordGradeFromEdit = { before, after, grade, groupId ->
            cardViewModel.recordGradeFromEdit(before, after, grade, groupId)
        },
        onUpdateLearningState = { cardViewModel.updateLearningState(it) },
        onAddCard = { question, answer, groupIds, algorithm, imagePaths, onSuccess ->
            cardViewModel.addCard(question, answer, groupIds, algorithm, imagePaths, onSuccess)
        },
        onCreateGroup = { name, onCreated -> groupViewModel.addGroup(name, onSuccess = onCreated) },
        onNavigateBack = onNavigateBack
    )
}

/** The global defaults a group can override, collected once for both editors. */
@Composable
private fun GroupEditor(
    group: Group,
    groupCardCount: Int,
    cardViewModel: CardViewModel,
    settingsViewModel: SettingsViewModel,
    onSave: (Group) -> Unit,
    onNavigateBack: () -> Unit
) {
    val globalCardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
    val globalAutoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()
    val globalRandomizeDueCards by settingsViewModel.randomizeDueCards.collectAsState()
    val globalRandomizeBucketHours by settingsViewModel.randomizeBucketHours.collectAsState()
    val globalPracticeDays by settingsViewModel.practiceDays.collectAsState()
    val globalMaximumInterval by settingsViewModel.maximumInterval.collectAsState()
    val globalFsrsRetention by settingsViewModel.fsrsRetention.collectAsState()
    val globalSm2IntervalModifier by settingsViewModel.sm2IntervalModifier.collectAsState()
    val globalFsrsEnableFuzzing by settingsViewModel.fsrsEnableFuzzing.collectAsState()
    val practiceScheduleEstimate by cardViewModel.practiceScheduleEstimate.collectAsState()
    val historyWindowDays by cardViewModel.historyWindowDays.collectAsState()

    GroupEditScreen(
        group = group,
        globalCardsPerSession = globalCardsPerSession,
        globalAutoShowAnswer = globalAutoShowAnswer,
        globalRandomizeDueCards = globalRandomizeDueCards,
        globalRandomizeBucketHours = globalRandomizeBucketHours,
        globalPracticeDays = globalPracticeDays,
        globalMaximumInterval = globalMaximumInterval,
        globalFsrsRetention = globalFsrsRetention,
        globalSm2IntervalModifier = globalSm2IntervalModifier,
        globalFsrsEnableFuzzing = globalFsrsEnableFuzzing,
        groupCardCount = groupCardCount,
        practiceScheduleEstimate = practiceScheduleEstimate,
        historyWindowDays = historyWindowDays,
        onHistoryWindowDaysChange = { cardViewModel.setHistoryWindowDays(it) },
        onSave = onSave,
        onNavigateBack = onNavigateBack
    )
}
