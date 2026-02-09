package com.fencing.spacedrepetition.ui.navigation

import androidx.compose.runtime.Composable
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
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Practice : Screen("practice")
    object Grading : Screen("grading")
    object CardList : Screen("card_list")
    object AddCard : Screen("add_card")
    object EditCard : Screen("edit_card/{cardId}")
    object GroupList : Screen("group_list")
    object GroupEdit : Screen("group_edit/{groupId}")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    cardViewModel: CardViewModel,
    practiceViewModel: PracticeViewModel,
    groupViewModel: GroupViewModel,
    settingsViewModel: SettingsViewModel,
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
            HomeScreen(
                cardViewModel = cardViewModel,
                practiceViewModel = practiceViewModel,
                groupViewModel = groupViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateToPractice = {
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
                }
            )
        }

        composable(Screen.Practice.route) {
            PracticeScreen(
                viewModel = practiceViewModel,
                settingsViewModel = settingsViewModel,
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
            GradingScreen(
                viewModel = practiceViewModel,
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
            CardListScreen(
                viewModel = cardViewModel,
                groupViewModel = groupViewModel,
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
            GroupListScreen(
                groupViewModel = groupViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEdit = { groupId ->
                    groupToEdit = groups.find { it.id == groupId }
                    navController.navigate("group_edit/$groupId")
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

                GroupEditScreen(
                    group = group,
                    globalCardsPerSession = globalCardsPerSession,
                    globalAutoShowAnswer = globalAutoShowAnswer,
                    globalRandomizeDueCards = globalRandomizeDueCards,
                    globalRandomizeBucketHours = globalRandomizeBucketHours,
                    globalPracticeDays = globalPracticeDays,
                    globalMaximumInterval = globalMaximumInterval,
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
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
