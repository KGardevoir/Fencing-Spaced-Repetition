// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.getDatabase
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import kotlin.test.Test

/**
 * The first screen of the app, running end to end in a browser.
 *
 * Everything below the composable is real: the view model, the repository,
 * Room's generated wasm implementation, the Web Worker, SQLite compiled to
 * WebAssembly, and the Origin Private File System. No fake DAO, because a
 * fake would answer a much smaller question -- this is here to check that the
 * whole column holds up on a platform none of it was written for.
 *
 * The row is written before the screen is composed and removed afterwards,
 * so running this repeatedly against the same browser profile leaves nothing
 * behind.
 */
class OpponentsScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAnOpponentThatIsInTheDatabase() = runComposeUiTest {
        val database = AppDatabase.getDatabase()
        val repository = OpponentRepository(database.opponentDao())
        val name = "opponents-screen-test"

        val id = repository.insertOpponent(Opponent(name = name, skillMultiplier = 1.25))
        try {
            setContent {
                FencingSpacedRepetitionTheme {
                    OpponentsScreen(
                        viewModel = OpponentViewModel(repository),
                        onNavigateBack = {}
                    )
                }
            }

            // The list arrives over a Flow, so the first composition renders
            // empty and the row appears a frame or two later.
            waitUntil(timeoutMillis = 10_000) {
                onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText(name).assertIsDisplayed()
        } finally {
            repository.getOpponentById(id)?.let { repository.deleteOpponent(it) }
            database.close()
        }
    }
}
