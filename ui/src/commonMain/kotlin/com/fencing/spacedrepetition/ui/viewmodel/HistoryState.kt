// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.viewmodel

import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog

/**
 * What the history screen shows, moved out of HistoryViewModel so the screen
 * can live in shared code while the view model that assembles it stays on
 * Android. Same package as before, so nothing that referred to these changed.
 */
data class ReviewLogWithCard(
    val reviewLog: ReviewLog,
    val cardQuestion: String
)

data class SessionWithReviews(
    val session: PracticeSession,
    val reviewLogs: List<ReviewLogWithCard>
)

sealed class HistoryItem {
    /** A completed practice session (may be expanded to show per-card grades). */
    data class Session(val session: PracticeSession) : HistoryItem()

    /** A single grade applied from the Add/Edit card screen (no session). */
    data class QuickGrade(val log: ReviewLogWithCard) : HistoryItem()
}

/** Sentinel filter value: show only sessions/logs that are unassigned (opponentId == null). */
const val OPPONENT_FILTER_NONE = -1L
