// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.fencing.spacedrepetition.algorithm.FSRSAlgorithm
import com.fencing.spacedrepetition.algorithm.SM2Algorithm
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.getDatabase
import com.fencing.spacedrepetition.data.repository.OpponentRepository
import com.fencing.spacedrepetition.ui.screen.OpponentsScreen
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import com.fencing.spacedrepetition.ui.viewmodel.OpponentViewModel
import com.fencing.spacedrepetition.util.Time

/**
 * Smoke page for the browser build.
 *
 * This is not the app. It runs the real scheduling code from :shared -- the
 * same FSRS and SM-2 classes the Android build uses, unmodified -- and prints
 * what they compute, so the port has something a person can open and check
 * rather than only a green CI job.
 *
 * It deliberately touches the two things most likely to behave differently on
 * a new platform: floating-point scheduling maths, and the clock and timezone
 * seam in Time, whose wasmJs actual is the only platform-specific line in the
 * shared module.
 */

private fun render(html: String) {
    js("document.getElementById('output').innerHTML = html")
}

private fun row(vararg cells: String): String =
    cells.joinToString("", "<tr>", "</tr>") { "<td>$it</td>" }

private fun fsrsTable(): String {
    val algorithm = FSRSAlgorithm()
    val now = Time.now()

    // A settled card, so the intervals are representative rather than the
    // short steps a brand new card produces.
    val card = FSRSAlgorithm.FSRSCard(
        stability = 20.0,
        difficulty = 5.0,
        state = FSRSAlgorithm.CardState.REVIEW,
        scheduledDays = 20,
        reps = 5,
        lastReview = now - 20L * 24 * 60 * 60 * 1000
    )

    val rows = FSRSAlgorithm.Rating.entries.joinToString("") { rating ->
        val result = algorithm.schedule(card, rating, now)
        row(
            rating.name,
            "${result.card.scheduledDays} days",
            fixed(result.card.stability),
            fixed(result.card.difficulty),
            result.card.state.name
        )
    }
    return """
        <table>
          <thead><tr><th>Rating</th><th>Next review</th><th>Stability</th><th>Difficulty</th><th>State</th></tr></thead>
          <tbody>$rows</tbody>
        </table>
    """
}

private fun sm2Table(): String {
    val algorithm = SM2Algorithm()
    val now = Time.now()
    val card = SM2Algorithm.SM2Card(easeFactor = 2.5, interval = 10, repetitions = 3)

    val rows = SM2Algorithm.Quality.entries.joinToString("") { quality ->
        val result = algorithm.schedule(card, quality, now)
        row(
            "${quality.ordinal} &middot; ${quality.name}",
            "${result.card.interval} days",
            fixed(result.card.easeFactor),
            result.card.repetitions.toString()
        )
    }
    return """
        <table>
          <thead><tr><th>Quality</th><th>Next review</th><th>Ease factor</th><th>Reps</th></tr></thead>
          <tbody>$rows</tbody>
        </table>
    """
}

/** Two decimal places, without depending on any platform formatter. */
private fun fixed(value: Double): String {
    val scaled = kotlin.math.round(value * 100).toLong()
    return "${scaled / 100}.${(scaled % 100).toString().padStart(2, '0')}"
}

private fun clockTable(): String {
    val now = Time.now()
    val offsetSeconds = Time.utcOffsetSeconds()
    val sign = if (offsetSeconds < 0) "-" else "+"
    val absolute = if (offsetSeconds < 0) -offsetSeconds else offsetSeconds
    val hours = (absolute / 3600).toString().padStart(2, '0')
    val minutes = (absolute % 3600 / 60).toString().padStart(2, '0')
    return """
        <table>
          <tbody>
            ${row("Time.now()", "$now")}
            ${row("Digits", "${now.toString().length} (13 = milliseconds)")}
            ${row("Time.utcOffsetSeconds()", "$offsetSeconds ($sign$hours:$minutes)")}
          </tbody>
        </table>
    """
}

// ComposeViewport is opt-in in Compose Multiplatform 1.11: it is the entry
// point for the web target and its shape is still settling.
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    render(
        """
        <p class="lede">
          The scheduling core below is running in your browser as WebAssembly.
          It is the same Kotlin the Android app uses, compiled for a second
          target with no changes to the algorithms.
        </p>
        <h2>Clock and timezone</h2>
        <p>The one platform-specific seam in the shared module.</p>
        ${clockTable()}
        <h2>FSRS-6</h2>
        <p>A settled card (stability 20, difficulty 5), reviewed 20 days on.</p>
        ${fsrsTable()}
        <h2>SM-2</h2>
        <p>A card with ease factor 2.5 and a 10-day interval, after 3 reps.</p>
        ${sm2Table()}
        <h2>The app</h2>
        <p>
          A real screen from the app, drawn by Compose and backed by the real
          database. Opponents added below are written to SQLite in the Origin
          Private File System and are still there after a reload.
        </p>
        <div id="compose-root" class="compose-root"></div>
        <p class="footnote">
          One screen of several. The back arrow has nowhere to go yet.
        </p>
        """
    )

    ComposeViewport(viewportContainerId = "compose-root") {
        Opponents()
    }
}

/**
 * The opponents screen, wired to the browser database.
 *
 * This is the first piece of the app proper to run in a browser: the same
 * screen file the Android build draws, the same view model, the same
 * repository, over SQLite in the Origin Private File System. Nothing here is
 * a browser-specific reimplementation -- the only line that knows which
 * platform it is on is getDatabase(), whose wasm actual supplies the Web
 * Worker driver.
 *
 * The view model is constructed directly rather than through a factory
 * because there is no navigation graph yet to own it. That changes when the
 * rest of the screens arrive.
 */
@Composable
private fun Opponents() {
    val viewModel = remember {
        OpponentViewModel(OpponentRepository(AppDatabase.getDatabase().opponentDao()))
    }

    val opponents by viewModel.opponents.collectAsState()
    val error by viewModel.error.collectAsState()

    FencingSpacedRepetitionTheme {
        OpponentsScreen(
            opponents = opponents,
            error = error,
            onAddOpponent = { name, multiplier, notes, onDone ->
                viewModel.addOpponent(name, multiplier, notes, onDone)
            },
            onUpdateOpponent = { opponent, onDone -> viewModel.updateOpponent(opponent, onDone) },
            onDeleteOpponent = { viewModel.deleteOpponent(it) },
            onClearError = viewModel::clearError,
            onNavigateBack = {}
        )
    }
}
