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
import com.fencing.spacedrepetition.ui.navigation.AppNavigation
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database and repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = CardRepository(
            cardDao = database.cardDao(),
            sessionDao = database.practiceSessionDao(),
            reviewLogDao = database.reviewLogDao()
        )

        setContent {
            FencingSpacedRepetitionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Create ViewModels with repository
                    val cardViewModel: CardViewModel = viewModel(
                        factory = CardViewModelFactory(repository)
                    )
                    val practiceViewModel: PracticeViewModel = viewModel(
                        factory = PracticeViewModelFactory(repository)
                    )

                    AppNavigation(
                        cardViewModel = cardViewModel,
                        practiceViewModel = practiceViewModel
                    )
                }
            }
        }
    }
}

// ViewModel Factories
class CardViewModelFactory(
    private val repository: CardRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CardViewModel::class.java)) {
            return CardViewModel(repository) as T
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
