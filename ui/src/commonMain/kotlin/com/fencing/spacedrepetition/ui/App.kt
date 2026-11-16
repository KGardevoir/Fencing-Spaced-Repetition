// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.fencing.spacedrepetition.BuildInfo
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.conceptContexts
import com.fencing.spacedrepetition.data.model.learningContextsOf
import com.fencing.spacedrepetition.data.model.offeredConceptIds
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.navigation.Destination
import com.fencing.spacedrepetition.ui.navigation.Navigator
import com.fencing.spacedrepetition.ui.navigation.rememberNavigator
import com.fencing.spacedrepetition.ui.screen.AddEditCardScreen
import com.fencing.spacedrepetition.ui.screen.CardListScreen
import com.fencing.spacedrepetition.ui.screen.ConceptsScreen
import com.fencing.spacedrepetition.ui.screen.GradingScreen
import com.fencing.spacedrepetition.ui.screen.GroupEditScreen
import com.fencing.spacedrepetition.ui.screen.GroupListScreen
import com.fencing.spacedrepetition.ui.screen.HistoryScreen
import com.fencing.spacedrepetition.ui.screen.HomeScreen
import com.fencing.spacedrepetition.ui.screen.GlobalLearningSettingsScreen
import com.fencing.spacedrepetition.ui.screen.LearningGroupEditScreen
import com.fencing.spacedrepetition.ui.screen.LearningGroupsScreen
import com.fencing.spacedrepetition.ui.screen.OpponentsScreen
import com.fencing.spacedrepetition.ui.screen.PracticeScreen
import com.fencing.spacedrepetition.ui.screen.RetentionReportScreen
import com.fencing.spacedrepetition.ui.screen.SettingsScreen
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.ConceptViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.RetentionReportViewModel
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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    cardViewModel: CardViewModel,
    practiceViewModel: PracticeViewModel,
    groupViewModel: GroupViewModel,
    settingsViewModel: SettingsViewModel,
    historyViewModel: HistoryViewModel,
    opponentViewModel: OpponentViewModel,
    retentionReportViewModel: RetentionReportViewModel,
    conceptViewModel: ConceptViewModel,
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
    // The platform's back gesture, doing what the screen's own back arrow
    // does. Without this it leaves the app from wherever you happen to be,
    // which on Android is the whole app closing four screens deep.
    //
    // Outermost on purpose. BackHandler hands the event to the innermost
    // enabled handler and propagates outward from there, so the screens that
    // guard something -- unsaved edits, a selection, a practice session --
    // still answer first, and this only runs when none of them wanted it.
    //
    // Disabled at the root rather than made a no-op, so that back on Home
    // still leaves the app instead of appearing to have stopped working.
    //
    // Android only, in practice. Compose's back event on the web comes from
    // the Escape key and nothing else -- the browser's own Back button is
    // never routed here, because Compose does not touch session history. See
    // BackNavigationEventInput in compose-ui, which is the whole of it.
    BackHandler(enabled = navigator.canGoBack) { navigator.back() }

    when (val destination = navigator.current) {

        Destination.Home -> {
            val groups by groupViewModel.allGroups.collectAsState()
            val learningGroups by groupViewModel.learningGroups.collectAsState()
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

            // The learning group's cards-per-session overrides the global setting.
            val cardsPerSession =
                learningGroups.find { it.id == selectedGroup?.learningGroupId }?.cardsPerSession
                    ?: globalCardsPerSession

            val cardsToPractise = selectedGroup?.let { group ->
                cardViewModel.getCardCountByGroup(group.id).collectAsState(initial = 0).value
            } ?: totalCardCount

            val dueCount = selectedGroup?.let { group ->
                cardViewModel.getDueCardCountByGroup(group.id).collectAsState(initial = 0).value
            } ?: totalDueCount

            // The selected group's own lock; each group keeps its own.
            val cardLocks by practiceViewModel.cardLocks.collectAsState()
            val cardLock = cardLocks[selectedGroup?.id]

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
                cardLockEnabled = cardLock != null,
                lockedCardCount = cardLock?.size ?: 0,
                onCardLockChange = { practiceViewModel.setCardLock(selectedGroup?.id, it) },
                onSelectGroup = { settingsViewModel.setSelectedGroupId(it.id) },
                onStartPractice = {
                    practiceViewModel.startNewSession(cardsPerSession, selectedGroup?.id)
                    navigator.go(Destination.Practice)
                },
                onNavigateToCards = { navigator.go(Destination.CardList) },
                onNavigateToGroups = { navigator.go(Destination.GroupList) },
                onNavigateToLearningGroups = { navigator.go(Destination.LearningGroups) },
                onNavigateToConcepts = { navigator.go(Destination.Concepts) },
                onNavigateToSettings = { navigator.go(Destination.Settings) },
                onNavigateToHistory = { navigator.go(Destination.History) },
                onNavigateToRetention = { navigator.go(Destination.RetentionReport) },
                onNavigateToOpponents = { navigator.go(Destination.Opponents) }
            )
        }

        Destination.Practice -> {
            val uiState by practiceViewModel.uiState.collectAsState()
            val sessionCards by practiceViewModel.sessionCards.collectAsState()
            val currentCardIndex by practiceViewModel.currentCardIndex.collectAsState()
            val globalAutoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()
            val groups by groupViewModel.allGroups.collectAsState()
            val savedGroupId by settingsViewModel.selectedGroupId.collectAsState()
            // The session is drawn from the selected group, the same fallback
            // as the home screen's; its own auto-show overrides the global one.
            val practisedGroup = groups.find { it.id == savedGroupId } ?: groups.firstOrNull()
            val autoShowAnswer = practisedGroup?.autoShowAnswer ?: globalAutoShowAnswer

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
            val sessionFightDifficulty by practiceViewModel.sessionFightDifficulty.collectAsState()
            val sessionNotes by practiceViewModel.sessionNotes.collectAsState()
            val sessionNoteImages by practiceViewModel.sessionNoteImages.collectAsState()

            GradingScreen(
                uiState = uiState,
                sessionCards = sessionCards,
                opponents = opponents,
                sessionOpponentId = sessionOpponentId,
                sessionFightDifficulty = sessionFightDifficulty,
                onSetSessionOpponent = practiceViewModel::setSessionOpponent,
                onSetSessionFightDifficulty = practiceViewModel::setSessionFightDifficulty,
                onCreateOpponent = practiceViewModel::createOpponent,
                onUpdateOpponentDifficulty = practiceViewModel::updateOpponentDifficulty,
                onUpdateGrade = practiceViewModel::updateGrade,
                sessionNotes = sessionNotes,
                sessionNoteImages = sessionNoteImages,
                onUpdateSessionNotes = practiceViewModel::setSessionNotes,
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
            val selectedConceptFilters by cardViewModel.selectedConceptFilters.collectAsState()
            val conceptsForFilter by cardViewModel.allConcepts.collectAsState()
            val learningGroups by groupViewModel.learningGroups.collectAsState()
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
                concepts = conceptsForFilter,
                selectedConceptFilters = selectedConceptFilters,
                onToggleConceptFilter = cardViewModel::toggleConceptFilter,
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
                learningGroups = learningGroups,
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
            settingsViewModel = settingsViewModel,
            cardToEdit = null,
            initialGroupId = destination.initialGroupId,
            onNavigateBack = { navigator.back() }
        )

        is Destination.EditCard -> CardEditor(
            cardViewModel = cardViewModel,
            groupViewModel = groupViewModel,
            settingsViewModel = settingsViewModel,
            cardToEdit = destination.card,
            initialGroupId = null,
            onNavigateBack = { navigator.back() }
        )

        Destination.GroupList -> {
            val sortedGroups by groupViewModel.sortedGroups.collectAsState()
            val importExportState by groupViewModel.importExportState.collectAsState()
            val groupSortOption by groupViewModel.groupSortOption.collectAsState()
            val learningGroups by groupViewModel.learningGroups.collectAsState()

            GroupListScreen(
                groups = sortedGroups,
                learningGroups = learningGroups,
                sortOption = groupSortOption,
                importExportState = importExportState,
                dueCardCountFor = { groupId ->
                    remember(groupId) { groupViewModel.getDueCardCountForGroup(groupId) }
                        .collectAsState(initial = 0).value
                },
                onSetSortOption = groupViewModel::setGroupSortOption,
                onDeleteGroups = groupViewModel::deleteGroups,
                onDismissImportExport = groupViewModel::resetImportExportState,
                onImportArchive = transfer::importIntoGroup,
                onExportArchive = transfer::exportGroup,
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
            groupViewModel = groupViewModel,
            settingsViewModel = settingsViewModel,
            onSave = { newGroup -> groupViewModel.addGroupWithSettings(newGroup) { navigator.back() } },
            onNavigateBack = { navigator.back() }
        )

        is Destination.EditGroup -> {
            val group = destination.group

            GroupEditor(
                group = group,
                groupViewModel = groupViewModel,
                settingsViewModel = settingsViewModel,
                onSave = { updated -> groupViewModel.saveGroup(updated, group) { navigator.back() } },
                onNavigateBack = { navigator.back() }
            )
        }

        Destination.LearningGroups -> {
            val learningGroups by groupViewModel.learningGroups.collectAsState()
            val groups by groupViewModel.allGroups.collectAsState()

            val globalConceptScheduling by settingsViewModel.globalConceptScheduling.collectAsState()

            LearningGroupsScreen(
                learningGroups = learningGroups,
                groups = groups,
                globalConceptScheduling = globalConceptScheduling,
                onEditLearningGroup = { navigator.go(Destination.EditLearningGroup(it)) },
                onEditGlobal = { navigator.go(Destination.GlobalLearningSettings) },
                onDeleteLearningGroups = groupViewModel::deleteLearningGroups,
                onNavigateBack = { navigator.back() }
            )
        }

        Destination.GlobalLearningSettings -> {
            val totalCards by cardViewModel.cardCount.collectAsState()
            val cardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
            val randomizeDueCards by settingsViewModel.randomizeDueCards.collectAsState()
            val randomizeBucketHours by settingsViewModel.randomizeBucketHours.collectAsState()
            val practiceDays by settingsViewModel.practiceDays.collectAsState()
            val maximumInterval by settingsViewModel.maximumInterval.collectAsState()
            val fsrsRetention by settingsViewModel.fsrsRetention.collectAsState()
            val fsrsEnableFuzzing by settingsViewModel.fsrsEnableFuzzing.collectAsState()
            val conceptScheduling by settingsViewModel.globalConceptScheduling.collectAsState()
            val practiceScheduleEstimate by cardViewModel.practiceScheduleEstimate.collectAsState()
            val historyWindowDays by cardViewModel.historyWindowDays.collectAsState()

            GlobalLearningSettingsScreen(
                totalCards = totalCards,
                cardsPerSession = cardsPerSession,
                randomizeDueCards = randomizeDueCards,
                randomizeBucketHours = randomizeBucketHours,
                practiceDays = practiceDays,
                maximumInterval = maximumInterval,
                fsrsRetention = fsrsRetention,
                fsrsEnableFuzzing = fsrsEnableFuzzing,
                conceptScheduling = conceptScheduling,
                practiceScheduleEstimate = practiceScheduleEstimate,
                historyWindowDays = historyWindowDays,
                onSetCardsPerSession = settingsViewModel::setCardsPerSession,
                onSetRandomizeDueCards = settingsViewModel::setRandomizeDueCards,
                onSetRandomizeBucketHours = settingsViewModel::setRandomizeBucketHours,
                onSetPracticeDays = settingsViewModel::setPracticeDays,
                onSetMaximumInterval = settingsViewModel::setMaximumInterval,
                onSetFsrsRetention = settingsViewModel::setFsrsRetention,
                onSetFsrsEnableFuzzing = settingsViewModel::setFsrsEnableFuzzing,
                onSetConceptScheduling = settingsViewModel::setGlobalConceptScheduling,
                onSetHistoryWindowDays = cardViewModel::setHistoryWindowDays,
                onNavigateBack = { navigator.back() }
            )
        }

        is Destination.EditLearningGroup -> {
            val learningGroups by groupViewModel.learningGroups.collectAsState()
            val groups by groupViewModel.allGroups.collectAsState()
            val globalCardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
            val globalRandomizeDueCards by settingsViewModel.randomizeDueCards.collectAsState()
            val globalRandomizeBucketHours by settingsViewModel.randomizeBucketHours.collectAsState()
            val globalPracticeDays by settingsViewModel.practiceDays.collectAsState()
            val globalMaximumInterval by settingsViewModel.maximumInterval.collectAsState()
            val globalFsrsRetention by settingsViewModel.fsrsRetention.collectAsState()
            val globalFsrsEnableFuzzing by settingsViewModel.fsrsEnableFuzzing.collectAsState()
            val practiceScheduleEstimate by cardViewModel.practiceScheduleEstimate.collectAsState()
            val historyWindowDays by cardViewModel.historyWindowDays.collectAsState()

            LearningGroupEditScreen(
                learningGroup = destination.learningGroup,
                learningGroups = learningGroups,
                groups = groups,
                globalCardsPerSession = globalCardsPerSession,
                globalRandomizeDueCards = globalRandomizeDueCards,
                globalRandomizeBucketHours = globalRandomizeBucketHours,
                globalPracticeDays = globalPracticeDays,
                globalMaximumInterval = globalMaximumInterval,
                globalFsrsRetention = globalFsrsRetention,
                globalFsrsEnableFuzzing = globalFsrsEnableFuzzing,
                practiceScheduleEstimate = practiceScheduleEstimate,
                historyWindowDays = historyWindowDays,
                onHistoryWindowDaysChange = { cardViewModel.setHistoryWindowDays(it) },
                countCards = groupViewModel::countCardsInGroups,
                onSave = { updated, memberIds ->
                    groupViewModel.saveLearningGroup(updated, memberIds) { navigator.back() }
                },
                onNavigateBack = { navigator.back() }
            )
        }

        Destination.Concepts -> {
            val concepts by conceptViewModel.concepts.collectAsState()
            val conceptLearningGroups by conceptViewModel.learningGroups.collectAsState()
            val error by conceptViewModel.error.collectAsState()
            val globalConceptScheduling by settingsViewModel.globalConceptScheduling.collectAsState()

            ConceptsScreen(
                concepts = concepts,
                globalConceptScheduling = globalConceptScheduling,
                onNavigateToLearningGroups = { navigator.go(Destination.LearningGroups) },
                learningGroups = conceptLearningGroups,
                error = error,
                onSaveConcept = conceptViewModel::saveConcept,
                onDeleteConcept = conceptViewModel::deleteConcept,
                onRederiveAll = conceptViewModel::rederiveAll,
                onClearError = conceptViewModel::clearError,
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
                onUpdateSessionNotes = historyViewModel::updateSessionNotes,
                onUpdateSessionOpponent = historyViewModel::updateSessionOpponent,
                onCreateOpponent = historyViewModel::createOpponent,
                onMergeOpponents = historyViewModel::mergeOpponents,
                onNavigateBack = { navigator.back() }
            )
        }

        Destination.RetentionReport -> {
            val report by retentionReportViewModel.report.collectAsState()
            val period by retentionReportViewModel.period.collectAsState()

            RetentionReportScreen(
                report = report,
                period = period,
                onPeriodChange = retentionReportViewModel::setPeriod,
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
                onDeleteOpponents = { opponentViewModel.deleteOpponents(it) },
                onClearError = opponentViewModel::clearError,
                onNavigateBack = { navigator.back() },
                onMergeOpponents = { source, target -> opponentViewModel.mergeOpponents(source, target) }
            )
        }

        Destination.Settings -> {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()
            val autoBackupEnabled by settingsViewModel.autoBackupEnabled.collectAsState()
            val autoBackupIntervalDays by settingsViewModel.autoBackupIntervalDays.collectAsState()
            val lastBackupTime by settingsViewModel.lastBackupTime.collectAsState()
            val maxBackupsKept by settingsViewModel.maxBackupsKept.collectAsState()
            val backupReminderEnabled by settingsViewModel.backupReminderEnabled.collectAsState()
            val backupReminderIntervalDays by
                settingsViewModel.backupReminderIntervalDays.collectAsState()

            SettingsScreen(
                buildInfo = buildInfo,
                themeMode = themeMode,
                autoShowAnswer = autoShowAnswer,
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
                onSetAutoBackupEnabled = settingsViewModel::setAutoBackupEnabled,
                onSetAutoBackupIntervalDays = settingsViewModel::setAutoBackupIntervalDays,
                onSetMaxBackupsKept = settingsViewModel::setMaxBackupsKept,
                onSetBackupReminderEnabled = settingsViewModel::setBackupReminderEnabled,
                onSetBackupReminderIntervalDays = settingsViewModel::setBackupReminderIntervalDays,
                onRunBackupNow = settingsViewModel::runBackupNow,
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
    settingsViewModel: SettingsViewModel,
    cardToEdit: com.fencing.spacedrepetition.data.model.Card?,
    initialGroupId: Long?,
    onNavigateBack: () -> Unit
) {
    val allGroups by groupViewModel.allGroups.collectAsState()
    val learningGroups by groupViewModel.learningGroups.collectAsState()
    val cardGroupsFlow = remember(cardToEdit?.id) {
        cardToEdit?.let { cardViewModel.getGroupsForCard(it.id) } ?: flowOf(emptyList<Group>())
    }
    val cardGroups by cardGroupsFlow.collectAsState(initial = emptyList())
    val learningStatesFlow = remember(cardToEdit?.id) {
        cardToEdit?.let { cardViewModel.getLearningStatesForCard(it.id) }
            ?: flowOf(emptyList<CardLearningState>())
    }
    val learningStates by learningStatesFlow.collectAsState(initial = emptyList())
    val allConcepts by cardViewModel.allConcepts.collectAsState()
    val cardConceptsFlow = remember(cardToEdit?.id) {
        cardToEdit?.let { cardViewModel.getConceptsForCard(it.id) } ?: flowOf(emptyList<Concept>())
    }
    val cardConcepts by cardConceptsFlow.collectAsState(initial = emptyList())
    val conceptLinks by cardViewModel.conceptLearningGroups.collectAsState()
    val globalConceptScheduling by settingsViewModel.globalConceptScheduling.collectAsState()
    // Where this card can take concepts: its groups' learning contexts that
    // schedule by concept, and the concepts available in them.
    val conceptContextsFor = { groupIds: Set<Long> ->
        conceptContexts(learningContextsOf(groupIds, allGroups), globalConceptScheduling, learningGroups)
    }
    val offeredConceptIdsFor = { groupIds: Set<Long> ->
        offeredConceptIds(
            allConcepts, conceptLinks, learningContextsOf(groupIds, allGroups),
            globalConceptScheduling, learningGroups
        )
    }

    AddEditCardScreen(
        allGroups = allGroups,
        cardGroups = cardGroups,
        learningStates = learningStates,
        learningGroups = learningGroups,
        allConcepts = allConcepts,
        cardConcepts = cardConcepts,
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
        onAddCard = { question, answer, groupIds, imagePaths, conceptIds, onSuccess ->
            cardViewModel.addCard(question, answer, groupIds, imagePaths, conceptIds, onSuccess)
        },
        onCreateGroup = { name, onCreated -> groupViewModel.addGroup(name, onSuccess = onCreated) },
        conceptContextsFor = conceptContextsFor,
        offeredConceptIdsFor = offeredConceptIdsFor,
        onCreateConcept = cardViewModel::createConcept,
        onNavigateBack = onNavigateBack
    )
}

/** The global defaults a group can override, collected once for both editors. */
@Composable
private fun GroupEditor(
    group: Group,
    groupViewModel: GroupViewModel,
    settingsViewModel: SettingsViewModel,
    onSave: (Group) -> Unit,
    onNavigateBack: () -> Unit
) {
    val learningGroups by groupViewModel.learningGroups.collectAsState()
    val globalAutoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()

    GroupEditScreen(
        group = group,
        learningGroups = learningGroups,
        onCreateLearningGroup = { name, onCreated -> groupViewModel.addLearningGroup(name, onCreated) },
        globalAutoShowAnswer = globalAutoShowAnswer,
        onSave = onSave,
        onNavigateBack = onNavigateBack
    )
}
