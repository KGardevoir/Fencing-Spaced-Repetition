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
import kotlin.test.assertTrue

/**
 * The first screen of the app, running end to end in a browser.
 *
 * Everything below the composable is real: the view model, the repository,
 * Room's generated wasm implementation, the Web Worker, SQLite compiled to
 * WebAssembly, and the Origin Private File System. No fake DAO, because a
 * fake would answer a much smaller question -- this is here to check that the
 * whole column holds up on a platform none of it was written for.
 *
 * None of these close the database. getDatabase() returns one shared
 * instance per page, so closing it in one test would pull the storage out
 * from under the next -- which is exactly the shape of the bug this suite
 * found in the first place.
 *
 * Deliberately three tests rather than one, in increasing order of how much
 * has to work: the database alone, then the screen drawn against it, then a
 * row travelling from the database into the composition. A single test that
 * did all three would fail identically whichever layer broke, and on wasm the
 * failure often arrives with no message at all -- so the split is what makes
 * a red run readable.
 */
class OpponentsScreenTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theDatabaseIsReachableFromThisModule() {
        // :shared declares the SQLite worker as a local npm package. This
        // asserts it survives into a different module's test bundle, which is
        // not something the dependency declaration makes obvious.
        runComposeUiTest {
            val repository = OpponentRepository(AppDatabase.getDatabase().opponentDao())
            val id = repository.insertOpponent(Opponent(name = "opponents-reachable-test"))
            assertTrue(id > 0, "the database accepted no rows")
            repository.getOpponentById(id)?.let { repository.deleteOpponent(it) }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theScreenDraws() = runComposeUiTest {
        setContent {
            FencingSpacedRepetitionTheme {
                OpponentsScreen(
                    viewModel = OpponentViewModel(
                        OpponentRepository(AppDatabase.getDatabase().opponentDao())
                    ),
                    onNavigateBack = {}
                )
            }
        }

        // The title is static, so this needs nothing from the database.
        onNodeWithText("Opponents").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aStoredOpponentReachesTheComposition() = runComposeUiTest {
        val repository = OpponentRepository(AppDatabase.getDatabase().opponentDao())
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
        }
    }
}
