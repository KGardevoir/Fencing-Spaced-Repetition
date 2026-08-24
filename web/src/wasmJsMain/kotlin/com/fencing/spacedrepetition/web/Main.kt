// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.web

import com.fencing.spacedrepetition.algorithm.FSRSAlgorithm
import com.fencing.spacedrepetition.algorithm.SM2Algorithm
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
        <p class="footnote">
          Not the app -- no cards, no storage, no persistence. This page exists
          to prove the core runs in a browser.
        </p>
        """
    )
}
