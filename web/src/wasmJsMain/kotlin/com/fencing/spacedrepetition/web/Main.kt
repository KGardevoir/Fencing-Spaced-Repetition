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
import com.fencing.spacedrepetition.ui.UnavailableFileTransfer
import com.fencing.spacedrepetition.ui.image.ImageCache
import com.fencing.spacedrepetition.ui.image.LocalImageCache
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
 * What is here is the four answers the browser has to give -- where the
 * database is, where settings are, where images are, and what happens when
 * the user asks for a file -- and then it gets out of the way.
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
    val imagePicker = browserImagePicker(CoroutineScope(Dispatchers.Main), OpfsImageStore)

    ComposeViewport(viewportContainerId = "app") {
        val cardViewModel = remember {
            CardViewModel(cardRepository, groupRepository, opponentRepository)
        }
        val groupViewModel = remember { GroupViewModel(groupRepository, cardRepository) }
        val practiceViewModel = remember {
            PracticeViewModel(cardRepository, opponentRepository)
        }
        val historyViewModel = remember { HistoryViewModel(cardRepository, opponentRepository) }
        val opponentViewModel = remember { OpponentViewModel(opponentRepository) }
        val settingsViewModel = remember { SettingsViewModel(preferences) }

        val themeMode by settingsViewModel.themeMode.collectAsState()

        CompositionLocalProvider(
            LocalImageCache provides imageCache,
            LocalImagePicker provides imagePicker
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
                        UnavailableFileTransfer { message ->
                            cardViewModel.reportImportExportError(message)
                        }
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
 * There is no BuildConfig on the web -- it is generated per Android
 * application module -- so the build's identity is what the bundle can know
 * about itself. Version and commit would have to be fed in at build time to
 * be real, and claiming a number nobody set would be worse than saying so.
 */
private fun webBuildInfo(): BuildInfo = BuildInfo(
    versionName = "web",
    versionCode = 0,
    buildType = "release",
    gitCommit = "unknown"
)

private fun openInNewTab(url: String) {
    js("window.open(url, '_blank', 'noopener')")
}
