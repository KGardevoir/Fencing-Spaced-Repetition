// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.fencing.spacedrepetition.BuildInfo
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.getDatabase
import com.fencing.spacedrepetition.data.preferences.LocalStoragePreferences
import com.fencing.spacedrepetition.data.repository.CardRepository
import com.fencing.spacedrepetition.data.repository.GroupRepository
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.ui.App
import com.fencing.spacedrepetition.ui.DownloadBackups
import com.fencing.spacedrepetition.ui.browserFileTransfer
import com.fencing.spacedrepetition.ui.image.ImageCache
import com.fencing.spacedrepetition.ui.image.LocalImageCache
import com.fencing.spacedrepetition.ui.image.LocalImageExporter
import com.fencing.spacedrepetition.ui.image.LocalImagePicker
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import com.fencing.spacedrepetition.ui.viewmodel.CardViewModel
import com.fencing.spacedrepetition.ui.viewmodel.GroupViewModel
import com.fencing.spacedrepetition.ui.viewmodel.HistoryViewModel
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.ui.viewmodel.PracticeViewModel
import com.fencing.spacedrepetition.ui.viewmodel.SettingsViewModel
import com.fencing.spacedrepetition.util.OpfsImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * The app, in a browser.
 *
 * Everything below this file is shared with the Android build: the same
 * screens, the same view models, the same repositories, the same scheduling.
 * What is here is the answers the browser has to give -- where the database
 * is, where settings are, where images are, what a file chooser is, and what
 * "back up" means without a scheduler -- and then it gets out of the way.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val database = AppDatabase.getDatabase()
    val preferences = LocalStoragePreferences()
    val cardRepository = CardRepository(
        cardDao = database.cardDao(),
        sessionDao = database.practiceSessionDao(),
        reviewLogDao = database.reviewLogDao(),
        groupDao = database.groupDao(),
        opponentDao = database.opponentDao(),
        preferences = preferences
    )
    val groupRepository = GroupRepository(database.groupDao(), database.cardDao())
    val opponentRepository = OpponentRepository(database.opponentDao())

    val imageCache = ImageCache(OpfsImageStore)
    // One scope for the page's file dialogs and downloads -- picking an image,
    // picking a deck to import, saving a photo. None belongs to a composition:
    // a chooser outlives whatever screen opened it, and so does a download.
    val scope = CoroutineScope(Dispatchers.Main)
    val imagePicker = browserImagePicker(scope, OpfsImageStore)
    val imageExporter = browserImageExporter(scope, OpfsImageStore)

    ComposeViewport(viewportContainerId = "app") {
        val cardViewModel = remember {
            CardViewModel(cardRepository, groupRepository, opponentRepository, OpfsImageStore)
        }
        val groupViewModel = remember {
            GroupViewModel(groupRepository, cardRepository, OpfsImageStore)
        }
        val practiceViewModel = remember {
            PracticeViewModel(cardRepository, opponentRepository)
        }
        val historyViewModel = remember { HistoryViewModel(cardRepository, opponentRepository) }
        val opponentViewModel = remember { OpponentViewModel(opponentRepository) }
        // The browser's answer to "back up now" is a download, and it needs
        // the card view model to produce one -- so the settings view model is
        // created after it rather than beside the others.
        val settingsViewModel = remember {
            SettingsViewModel(preferences, DownloadBackups(cardViewModel, preferences))
        }

        val themeMode by settingsViewModel.themeMode.collectAsState()

        CompositionLocalProvider(
            LocalImageCache provides imageCache,
            LocalImagePicker provides imagePicker,
            LocalImageExporter provides imageExporter
        ) {
            FencingSpacedRepetitionTheme(themeMode = themeMode) {
                App(
                    cardViewModel = cardViewModel,
                    practiceViewModel = practiceViewModel,
                    groupViewModel = groupViewModel,
                    settingsViewModel = settingsViewModel,
                    historyViewModel = historyViewModel,
                    opponentViewModel = opponentViewModel,
                    buildInfo = webBuildInfo(),
                    transfer = remember {
                        browserFileTransfer(scope, cardViewModel, groupViewModel)
                    },
                    onOpenLink = { url -> openInNewTab(url) },
                    // No storage-framework folder to name, and nothing to
                    // schedule a backup with -- see BackupScheduling.
                    backupFolderName = null,
                    onPickBackupFolder = {}
                )
            }
        }
    }
}

/**
 * What this bundle says it is, in the About section.
 *
 * There is no BuildConfig on the web -- it is generated per Android
 * application module -- so :web generates the equivalent instead, and
 * GENERATED_BUILD_INFO is that file. The version is the one :app ships, read
 * from the same gradle.properties, so a browser and a phone running the same
 * commit report the same version rather than two unrelated answers.
 */
private fun webBuildInfo(): BuildInfo = GENERATED_BUILD_INFO

private fun openInNewTab(url: String) {
    js("window.open(url, '_blank', 'noopener')")
}
