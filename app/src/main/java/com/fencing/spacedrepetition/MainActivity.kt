package com.fencing.spacedrepetition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.ui.navigation.AppNavigation
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database and repositories
        val database = AppDatabase.getDatabase(applicationContext)

        // Initialize theme preferences (needed by CardRepository)
        val themePreferences = ThemePreferences(applicationContext)

        val groupRepository = GroupRepository(
            groupDao = database.groupDao(),
            cardDao = database.cardDao()
        )

        val opponentRepository = OpponentRepository(
            opponentDao = database.opponentDao()
        )

        val repository = CardRepository(
            cardDao = database.cardDao(),
            sessionDao = database.practiceSessionDao(),
            reviewLogDao = database.reviewLogDao(),
            groupDao = database.groupDao(),
            opponentDao = database.opponentDao(),
            preferences = themePreferences
        )

        setContent {
            // Create settings ViewModel
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(themePreferences)
            )
            val themeMode by settingsViewModel.themeMode.collectAsState()

            FencingSpacedRepetitionTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Create ViewModels with repositories
                    val cardViewModel: CardViewModel = viewModel(
                        factory = CardViewModelFactory(application, repository, groupRepository, opponentRepository)
                    )
                    val practiceViewModel: PracticeViewModel = viewModel(
                        factory = PracticeViewModelFactory(repository, opponentRepository, themePreferences)
                    )
                    val groupViewModel: GroupViewModel = viewModel(
                        factory = GroupViewModelFactory(application, groupRepository, repository)
                    )
                    val historyViewModel: HistoryViewModel = viewModel(
                        factory = HistoryViewModelFactory(repository, opponentRepository)
                    )
                    val opponentViewModel: OpponentViewModel = viewModel(
                        factory = OpponentViewModelFactory(opponentRepository)
                    )

                    AppNavigation(
                        cardViewModel = cardViewModel,
                        practiceViewModel = practiceViewModel,
                        groupViewModel = groupViewModel,
                        settingsViewModel = settingsViewModel,
                        historyViewModel = historyViewModel,
                        opponentViewModel = opponentViewModel
                    )
                }
            }
        }
    }
}

// ViewModel Factories
class CardViewModelFactory(
    private val application: android.app.Application,
    private val repository: CardRepository,
    private val groupRepository: GroupRepository,
    private val opponentRepository: OpponentRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CardViewModel::class.java)) {
            return CardViewModel(application, repository, groupRepository, opponentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class PracticeViewModelFactory(
    private val repository: CardRepository,
    private val opponentRepository: OpponentRepository,
    private val themePreferences: ThemePreferences
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PracticeViewModel::class.java)) {
            return PracticeViewModel(repository, opponentRepository, themePreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class GroupViewModelFactory(
    private val application: android.app.Application,
    private val groupRepository: GroupRepository,
    private val cardRepository: CardRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupViewModel::class.java)) {
            return GroupViewModel(application, groupRepository, cardRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SettingsViewModelFactory(
    private val themePreferences: ThemePreferences
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(themePreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class HistoryViewModelFactory(
    private val repository: CardRepository,
    private val opponentRepository: OpponentRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            return HistoryViewModel(repository, opponentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class OpponentViewModelFactory(
    private val opponentRepository: OpponentRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OpponentViewModel::class.java)) {
            return OpponentViewModel(opponentRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
