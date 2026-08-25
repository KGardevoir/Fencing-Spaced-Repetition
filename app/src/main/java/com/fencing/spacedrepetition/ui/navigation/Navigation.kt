// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.screen.*
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel
import com.fencing.spacedrepetition.ui.screen.getFilenameFromUri
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Practice : Screen("practice")
    object Grading : Screen("grading")
    object CardList : Screen("card_list")
    object AddCard : Screen("add_card")
    object EditCard : Screen("edit_card/{cardId}")
    object GroupList : Screen("group_list")
    object GroupEdit : Screen("group_edit/{groupId}")
    object GroupAdd : Screen("group_add")
    object Settings : Screen("settings")
    object History : Screen("history")
    object Opponents : Screen("opponents")
}

@Composable
fun AppNavigation(
    cardViewModel: CardViewModel,
    practiceViewModel: PracticeViewModel,
    groupViewModel: GroupViewModel,
    settingsViewModel: SettingsViewModel,
    historyViewModel: HistoryViewModel,
    opponentViewModel: OpponentViewModel,
    navController: NavHostController = rememberNavController()
) {
    // Store card to edit
    var cardToEdit: Card? = null
    // Store initial group for new cards
    var initialGroupIdForNewCard: Long? = null
    // Store group to edit
    var groupToEdit: Group? = null

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            // Everything HomeScreen used to work out for itself. It lives here
            // now because it is state management rather than presentation, and
            // because the view models it reads cannot follow that screen into
            // shared code.
            val groups by groupViewModel.allGroups.collectAsState()
            val savedGroupId by settingsViewModel.selectedGroupId.collectAsState()
            val globalCardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
            val totalCardCount by cardViewModel.cardCount.collectAsState()
            val totalDueCount by cardViewModel.dueCardCount.collectAsState()

            val selectedGroup = groups.find { it.id == savedGroupId } ?: groups.firstOrNull()

            // Persist a fallback selection once groups exist and the saved one
            // does not. A write, so it belongs with the view models.
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
                onSelectGroup = { group ->
                    settingsViewModel.setSelectedGroupId(group.id)
                },
                onStartPractice = {
                    practiceViewModel.startNewSession(cardsPerSession, selectedGroup?.id)
                    navController.navigate(Screen.Practice.route)
                },
                onNavigateToCards = {
                    navController.navigate(Screen.CardList.route)
                },
                onNavigateToGroups = {
                    navController.navigate(Screen.GroupList.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToOpponents = {
                    navController.navigate(Screen.Opponents.route)
                }
            )
        }

        composable(Screen.Opponents.route) {
            OpponentsScreen(
                viewModel = opponentViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.History.route) {
            val historyItems by historyViewModel.historyItems.collectAsState()
            val historyOpponents by historyViewModel.opponents.collectAsState()
            val historyOpponentFilter by historyViewModel.opponentFilter.collectAsState()
            val historySearchQuery by historyViewModel.searchQuery.collectAsState()
            val historySelectedGroup by historyViewModel.selectedGroup.collectAsState()
            val historyAvailableGroups by historyViewModel.availableGroups.collectAsState()

            HistoryScreen(
                historyItems = historyItems,
                opponents = historyOpponents,
                opponentFilter = historyOpponentFilter,
                searchQuery = historySearchQuery,
                selectedGroup = historySelectedGroup,
                availableGroups = historyAvailableGroups,
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
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Practice.route) {
            val practiceUiState by practiceViewModel.uiState.collectAsState()
            val practiceSessionCards by practiceViewModel.sessionCards.collectAsState()
            val currentCardIndex by practiceViewModel.currentCardIndex.collectAsState()
            val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()

            PracticeScreen(
                uiState = practiceUiState,
                sessionCards = practiceSessionCards,
                currentCardIndex = currentCardIndex,
                autoShowAnswer = autoShowAnswer,
                onUpdateCard = practiceViewModel::updateCardComplete,
                onNextCard = practiceViewModel::nextCard,
                onPreviousCard = practiceViewModel::previousCard,
                onFinishPractice = practiceViewModel::finishPractice,
                onNavigateToGrading = {
                    navController.navigate(Screen.Grading.route) {
                        popUpTo(Screen.Practice.route) { inclusive = false }
                    }
                },
                onNavigateBack = {
                    practiceViewModel.resetSession()
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Grading.route) {
            val gradingUiState by practiceViewModel.uiState.collectAsState()
            val gradingSessionCards by practiceViewModel.sessionCards.collectAsState()
            val gradingOpponents by practiceViewModel.opponents.collectAsState()
            val gradingSessionOpponentId by practiceViewModel.sessionOpponentId.collectAsState()

            GradingScreen(
                uiState = gradingUiState,
                sessionCards = gradingSessionCards,
                opponents = gradingOpponents,
                sessionOpponentId = gradingSessionOpponentId,
                onSetSessionOpponent = practiceViewModel::setSessionOpponent,
                onCreateOpponent = practiceViewModel::createOpponent,
                onUpdateOpponentDifficulty = practiceViewModel::updateOpponentDifficulty,
                onUpdateGrade = practiceViewModel::updateGrade,
                onUpdateNotes = practiceViewModel::updateNotes,
                onUpdateOpponent = practiceViewModel::updateOpponent,
                onSubmitGrades = practiceViewModel::submitGrades,
                onComplete = {
                    practiceViewModel.resetSession()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    practiceViewModel.backToPracticing()
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.CardList.route) {
            val cardListContext = LocalContext.current
            val allCards by cardViewModel.filteredCards.collectAsState()
            val allCardsWithGroups by cardViewModel.allCardsWithGroups.collectAsState()
            val cardListGroups by groupViewModel.allGroups.collectAsState()
            val selectedGroupFilters by cardViewModel.selectedGroupFilters.collectAsState()
            val showDisabledFilter by cardViewModel.showDisabledFilter.collectAsState()
            val cardSearchQuery by cardViewModel.searchQuery.collectAsState()
            val cardListCount by cardViewModel.cardCount.collectAsState()
            val cardImportExportState by cardViewModel.importExportState.collectAsState()
            val cardSortOption by cardViewModel.cardSortOption.collectAsState()
            val sortDirection by cardViewModel.sortDirection.collectAsState()
            val isSelectionMode by cardViewModel.isSelectionMode.collectAsState()
            val selectedCardIds by cardViewModel.selectedCardIds.collectAsState()

            // What each picker is for, once it comes back with a Uri. The
            // screen asks for a file operation and never sees the Uri.
            var archiveExportIncludesHistory by remember { mutableStateOf(false) }
            var archiveExportGroupIds by remember { mutableStateOf<List<Long>?>(null) }
            var csvExportGroupIds by remember { mutableStateOf<List<Long>?>(null) }

            val archiveImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let { cardViewModel.importCards(it, cardListContext.contentResolver) }
            }
            val archiveExportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/gzip")
            ) { uri: Uri? ->
                uri?.let {
                    val groupIds = archiveExportGroupIds
                    if (groupIds == null) {
                        cardViewModel.exportAllCards(
                            it, cardListContext.contentResolver, archiveExportIncludesHistory
                        )
                    } else {
                        cardViewModel.exportSelectedGroups(
                            groupIds, it, cardListContext.contentResolver, archiveExportIncludesHistory
                        )
                    }
                }
            }
            val cardCsvImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let {
                    val filename = getFilenameFromUri(cardListContext, it) ?: "import.csv"
                    cardViewModel.csvImportParseFile(it, cardListContext.contentResolver, filename)
                }
            }
            val cardCsvExportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/csv")
            ) { uri: Uri? ->
                uri?.let {
                    val groupIds = csvExportGroupIds
                    if (groupIds == null) {
                        cardViewModel.exportAllCardsCsv(it, cardListContext.contentResolver)
                    } else {
                        cardViewModel.exportSelectedGroupsCsv(
                            groupIds, it, cardListContext.contentResolver
                        )
                    }
                }
            }

            CardListScreen(
                allCards = allCards,
                allCardsWithGroups = allCardsWithGroups,
                groups = cardListGroups,
                selectedGroupFilters = selectedGroupFilters,
                showDisabledFilter = showDisabledFilter,
                searchQuery = cardSearchQuery,
                cardCount = cardListCount,
                importExportState = cardImportExportState,
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
                onCsvImportInto = cardViewModel::csvImportComplete,
                onCsvImportIntoNewGroup = { parsed, errors, groupName ->
                    groupViewModel.addGroup(groupName) { newGroupId ->
                        cardViewModel.csvImportComplete(parsed, errors, newGroupId)
                    }
                },
                onImportArchive = {
                    archiveImportLauncher.launch(
                        arrayOf(
                            "application/gzip", "application/x-gzip", "text/plain",
                            "text/tab-separated-values", "application/octet-stream", "*/*"
                        )
                    )
                },
                onExportAllArchive = { includeHistory ->
                    archiveExportGroupIds = null
                    archiveExportIncludesHistory = includeHistory
                    archiveExportLauncher.launch("all_cards.tsv.gz")
                },
                onExportGroupsArchive = { groupIds, includeHistory ->
                    archiveExportGroupIds = groupIds
                    archiveExportIncludesHistory = includeHistory
                    archiveExportLauncher.launch("selected_groups_cards.tsv.gz")
                },
                onImportCsv = {
                    cardCsvImportLauncher.launch(
                        arrayOf(
                            "text/csv", "text/comma-separated-values", "text/plain",
                            "application/octet-stream", "*/*"
                        )
                    )
                },
                onExportAllCsv = {
                    csvExportGroupIds = null
                    cardCsvExportLauncher.launch("all_cards.csv")
                },
                onExportGroupsCsv = { groupIds ->
                    csvExportGroupIds = groupIds
                    cardCsvExportLauncher.launch("selected_groups_cards.csv")
                },
                onNavigateToAddCard = { groupId ->
                    initialGroupIdForNewCard = groupId
                    navController.navigate(Screen.AddCard.route)
                },
                onNavigateToEditCard = { card ->
                    cardToEdit = card
                    navController.navigate("edit_card/${card.id}")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AddCard.route) {
            AddEditCardScreen(
                viewModel = cardViewModel,
                groupViewModel = groupViewModel,
                cardToEdit = null,
                initialGroupId = initialGroupIdForNewCard,
                onNavigateBack = {
                    initialGroupIdForNewCard = null
                    navController.popBackStack()
                }
            )
        }

        composable("edit_card/{cardId}") {
            AddEditCardScreen(
                viewModel = cardViewModel,
                groupViewModel = groupViewModel,
                cardToEdit = cardToEdit,
                onNavigateBack = {
                    cardToEdit = null
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.GroupList.route) {
            val groups by groupViewModel.allGroups.collectAsState()
            // The file pickers GroupListScreen used to own. They are Android
            // activity results, so they stay on this side of the boundary; the
            // screen only says which group the user asked to import or export.
            val sortedGroups by groupViewModel.sortedGroups.collectAsState()
            val importExportState by groupViewModel.importExportState.collectAsState()
            val groupSortOption by groupViewModel.groupSortOption.collectAsState()
            val context = LocalContext.current

            var groupForImport by remember { mutableStateOf<Group?>(null) }
            var groupForExport by remember { mutableStateOf<Group?>(null) }
            var groupForCsvImport by remember { mutableStateOf<Group?>(null) }
            var groupForCsvExport by remember { mutableStateOf<Group?>(null) }

            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let {
                    groupForImport?.let { group ->
                        groupViewModel.importCardsToGroup(group.id, uri, context.contentResolver)
                    }
                }
                groupForImport = null
            }

            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/gzip")
            ) { uri: Uri? ->
                uri?.let {
                    groupForExport?.let { group ->
                        groupViewModel.exportGroupCards(group.id, uri, context.contentResolver)
                    }
                }
                groupForExport = null
            }

            val csvImportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                uri?.let {
                    groupForCsvImport?.let {
                        val filename = getFilenameFromUri(context, uri) ?: "import.csv"
                        groupViewModel.csvImportParseFile(uri, context.contentResolver, filename)
                    }
                }
                if (uri == null) {
                    groupForCsvImport = null
                }
            }

            val csvExportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/csv")
            ) { uri: Uri? ->
                uri?.let {
                    groupForCsvExport?.let { group ->
                        groupViewModel.exportGroupCardsCsv(group.id, uri, context.contentResolver)
                    }
                }
                groupForCsvExport = null
            }

            GroupListScreen(
                groups = sortedGroups,
                sortOption = groupSortOption,
                importExportState = importExportState,
                dueCardCountFor = { groupId ->
                    groupViewModel.getDueCardCountForGroup(groupId)
                        .collectAsState(initial = 0).value
                },
                onSetSortOption = { groupViewModel.setGroupSortOption(it) },
                onDeleteGroup = { groupViewModel.deleteGroup(it) },
                onDismissImportExport = { groupViewModel.resetImportExportState() },
                onImportTsv = { group ->
                    groupForImport = group
                    importLauncher.launch(
                        arrayOf(
                            "application/gzip", "application/x-gzip", "text/plain",
                            "text/tab-separated-values", "application/octet-stream", "*/*"
                        )
                    )
                },
                onExportTsv = { group ->
                    groupForExport = group
                    exportLauncher.launch(groupViewModel.generateExportFilename(group.name))
                },
                onImportCsv = { group ->
                    groupForCsvImport = group
                    csvImportLauncher.launch(
                        arrayOf(
                            "text/csv", "text/comma-separated-values", "text/plain",
                            "application/octet-stream", "*/*"
                        )
                    )
                },
                onExportCsv = { group ->
                    groupForCsvExport = group
                    csvExportLauncher.launch(groupViewModel.generateCsvExportFilename(group.name))
                },
                pendingCsvTargetGroup = groupForCsvImport,
                onCsvImportInto = { parsedCards, parseErrors, groupId ->
                    groupViewModel.csvImportComplete(parsedCards, parseErrors, groupId)
                    groupForCsvImport = null
                },
                onCsvImportIntoNewGroup = { parsedCards, parseErrors, groupName ->
                    groupViewModel.addGroup(groupName) { newGroupId ->
                        groupViewModel.csvImportComplete(parsedCards, parseErrors, newGroupId)
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEdit = { groupId ->
                    groupToEdit = groups.find { it.id == groupId }
                    navController.navigate("group_edit/$groupId")
                },
                onNavigateToAdd = {
                    navController.navigate(Screen.GroupAdd.route)
                }
            )
        }

        composable(Screen.GroupAdd.route) {
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
                group = Group(name = ""),
                globalCardsPerSession = globalCardsPerSession,
                globalAutoShowAnswer = globalAutoShowAnswer,
                globalRandomizeDueCards = globalRandomizeDueCards,
                globalRandomizeBucketHours = globalRandomizeBucketHours,
                globalPracticeDays = globalPracticeDays,
                globalMaximumInterval = globalMaximumInterval,
                globalFsrsRetention = globalFsrsRetention,
                globalSm2IntervalModifier = globalSm2IntervalModifier,
                globalFsrsEnableFuzzing = globalFsrsEnableFuzzing,
                groupCardCount = 0,
                practiceScheduleEstimate = practiceScheduleEstimate,
                historyWindowDays = historyWindowDays,
                onHistoryWindowDaysChange = { cardViewModel.setHistoryWindowDays(it) },
                onSave = { newGroup ->
                    groupViewModel.addGroupWithSettings(newGroup) {
                        navController.popBackStack()
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("group_edit/{groupId}") {
            val group = groupToEdit
            if (group != null) {
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
                val groupCardCount by remember(group.id) {
                    cardViewModel.getCardCountForGroup(group.id)
                }.collectAsState(initial = 0)

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
                    onSave = { updatedGroup ->
                        // Handle independent learning toggle specially (initializes learning states)
                        if (updatedGroup.independentLearning != group.independentLearning) {
                            groupViewModel.toggleIndependentLearning(group.id, updatedGroup.independentLearning)
                        }
                        groupViewModel.updateGroup(updatedGroup) {
                            navController.popBackStack()
                        }
                    },
                    onNavigateBack = {
                        groupToEdit = null
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Screen.Settings.route) {
            val settingsContext = LocalContext.current
            val totalCards by cardViewModel.cardCount.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val autoShowAnswer by settingsViewModel.autoShowAnswer.collectAsState()
            val settingsCardsPerSession by settingsViewModel.cardsPerSession.collectAsState()
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
            val autoBackupUri by settingsViewModel.autoBackupUri.collectAsState()
            val autoBackupIntervalDays by settingsViewModel.autoBackupIntervalDays.collectAsState()
            val lastBackupTime by settingsViewModel.lastBackupTime.collectAsState()
            val maxBackupsKept by settingsViewModel.maxBackupsKept.collectAsState()

            // Resolving a tree URI to a folder name, and asking for a new one,
            // are both storage-framework work; the screen only shows the name.
            val backupFolderName = autoBackupUri?.let { uriString ->
                DocumentFile.fromTreeUri(settingsContext, Uri.parse(uriString))?.name
            }
            val folderPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    settingsContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    settingsViewModel.setAutoBackupUri(uri.toString())
                }
            }

            SettingsScreen(
                totalCards = totalCards,
                themeMode = themeMode,
                autoShowAnswer = autoShowAnswer,
                cardsPerSession = settingsCardsPerSession,
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
                backupFolderName = backupFolderName,
                onPickBackupFolder = { folderPickerLauncher.launch(null) },
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
                onRunBackupNow = settingsViewModel::runBackupNow,
                onDeleteAllCards = { cardViewModel.deleteAllCards() },
                onOpenLink = { url ->
                    settingsContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
