// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.getDatabase
import com.fencing.spacedrepetition.data.preferences.ThemePreferences
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.util.FileImageStore
import com.fencing.spacedrepetition.ui.image.ImageCache
import com.fencing.spacedrepetition.ui.image.LocalImageCache
import com.fencing.spacedrepetition.ui.image.LocalImagePicker
import com.fencing.spacedrepetition.ui.image.rememberAndroidImagePicker
import com.fencing.spacedrepetition.ui.navigation.AppNavigation
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel
import com.fencing.spacedrepetition.worker.BackupScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

        // Ensure the auto-backup schedule matches the persisted setting on every launch,
        // since WorkManager's periodic work registration can be lost (e.g. data cleared).
        lifecycleScope.launch {
            if (themePreferences.autoBackupEnabled.first() && themePreferences.autoBackupUri.first() != null) {
                BackupScheduler.schedule(applicationContext, themePreferences.autoBackupIntervalDays.first())
            }
        }

        // One store and one cache for the process. Both are cheap to hold and
        // the cache is only useful if it outlives a single screen.
        val imageStore = FileImageStore(applicationContext)
        val imageCache = ImageCache(imageStore)

        setContent {
            // Create settings ViewModel
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(application, themePreferences)
            )
            val themeMode by settingsViewModel.themeMode.collectAsState()

            CompositionLocalProvider(
                LocalImageCache provides imageCache,
                LocalImagePicker provides rememberAndroidImagePicker(imageStore)
            ) {
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
                        factory = PracticeViewModelFactory(repository, opponentRepository)
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
    private val opponentRepository: OpponentRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PracticeViewModel::class.java)) {
            return PracticeViewModel(repository, opponentRepository) as T
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
    private val application: android.app.Application,
    private val themePreferences: ThemePreferences
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(application, themePreferences) as T
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
