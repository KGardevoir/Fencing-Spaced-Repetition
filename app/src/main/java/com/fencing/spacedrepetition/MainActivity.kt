package com.fencing.spacedrepetition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.ui.navigation.AppNavigation
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database and repositories
        val database = AppDatabase.getDatabase(applicationContext)

        val groupRepository = GroupRepository(
            groupDao = database.groupDao(),
            cardDao = database.cardDao()
        )

        val repository = CardRepository(
            cardDao = database.cardDao(),
            sessionDao = database.practiceSessionDao(),
            reviewLogDao = database.reviewLogDao(),
            groupDao = database.groupDao()
        )

        setContent {
            FencingSpacedRepetitionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Create ViewModels with repositories
                    val cardViewModel: CardViewModel = viewModel(
                        factory = CardViewModelFactory(repository, groupRepository)
                    )
                    val practiceViewModel: PracticeViewModel = viewModel(
                        factory = PracticeViewModelFactory(repository)
                    )
                    val groupViewModel: GroupViewModel = viewModel(
                        factory = GroupViewModelFactory(groupRepository)
                    )

                    AppNavigation(
                        cardViewModel = cardViewModel,
                        practiceViewModel = practiceViewModel,
                        groupViewModel = groupViewModel
                    )
                }
            }
        }
    }
}

// ViewModel Factories
class CardViewModelFactory(
    private val repository: CardRepository,
    private val groupRepository: GroupRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CardViewModel::class.java)) {
            return CardViewModel(repository, groupRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class PracticeViewModelFactory(
    private val repository: CardRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PracticeViewModel::class.java)) {
            return PracticeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class GroupViewModelFactory(
    private val repository: GroupRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupViewModel::class.java)) {
            return GroupViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
