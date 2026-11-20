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
import com.fencing.spacedrepetition.data.repository.ConceptRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.util.FileImageStore
import com.fencing.spacedrepetition.util.ImageStore
import com.fencing.spacedrepetition.ui.image.ImageCache
import com.fencing.spacedrepetition.ui.image.LocalImageCache
import com.fencing.spacedrepetition.ui.image.LocalImageExporter
import com.fencing.spacedrepetition.ui.image.LocalImagePicker
import com.fencing.spacedrepetition.ui.image.rememberAndroidImageExporter
import com.fencing.spacedrepetition.ui.image.rememberAndroidImagePicker
import com.fencing.spacedrepetition.ui.navigation.AppNavigation
import com.fencing.spacedrepetition.ui.navigation.NavigationViewModel
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.RetentionReportViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.ui.viewmodel.ConceptViewModel
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
            preferences = themePreferences,
            conceptDao = database.conceptDao()
        )
        val conceptRepository = ConceptRepository(database.conceptDao())

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
            val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(themePreferences, WorkManagerBackups(application)) }
            val themeMode by settingsViewModel.themeMode.collectAsState()

            CompositionLocalProvider(
                LocalImageCache provides imageCache,
                LocalImagePicker provides rememberAndroidImagePicker(imageStore),
                LocalImageExporter provides rememberAndroidImageExporter(imageStore)
            ) {
            FencingSpacedRepetitionTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Create ViewModels with repositories
                    val cardViewModel: CardViewModel = viewModel { CardViewModel(repository, groupRepository, opponentRepository, imageStore, conceptRepository) }
                    val practiceViewModel: PracticeViewModel = viewModel { PracticeViewModel(repository, opponentRepository) }
                    val groupViewModel: GroupViewModel = viewModel { GroupViewModel(groupRepository, repository, imageStore, conceptRepository) }
                    val historyViewModel: HistoryViewModel = viewModel { HistoryViewModel(repository, opponentRepository, conceptRepository) }
                    val opponentViewModel: OpponentViewModel = viewModel { OpponentViewModel(opponentRepository) }
                    val retentionReportViewModel: RetentionReportViewModel = viewModel { RetentionReportViewModel(repository, groupRepository, conceptRepository, themePreferences.globalConceptScheduling) }
                    // Owns the back stack so it survives Activity recreation.
                    val navigationViewModel: NavigationViewModel = viewModel()
                    val conceptViewModel: ConceptViewModel = viewModel { ConceptViewModel(conceptRepository, repository, groupRepository) }

                    AppNavigation(
                        cardViewModel = cardViewModel,
                        practiceViewModel = practiceViewModel,
                        groupViewModel = groupViewModel,
                        settingsViewModel = settingsViewModel,
                        historyViewModel = historyViewModel,
                        opponentViewModel = opponentViewModel,
                        retentionReportViewModel = retentionReportViewModel,
                        conceptViewModel = conceptViewModel,
                        navigator = navigationViewModel.navigator
                    )
                }
            }
            }
        }
    }
}

// ViewModel Factories







/**
 * The Android half of [BackupScheduling]: WorkManager.
 *
 * Here rather than in the view model because WorkManager is Android's, and
 * the settings screen that drives it is not.
 */
class WorkManagerBackups(
    private val application: android.app.Application
) : com.fencing.spacedrepetition.ui.viewmodel.BackupScheduling {

    /** WorkManager runs whether the app is open or not, which is the point of it. */
    override val runsWhenClosed = true

    override suspend fun reschedule(enabled: Boolean, uri: String?, intervalDays: Int) {
        if (enabled && uri != null) {
            BackupScheduler.schedule(application, intervalDays)
        } else {
            BackupScheduler.cancel(application)
        }
    }

    override suspend fun runNow() {
        BackupScheduler.runNow(application)
    }
}

