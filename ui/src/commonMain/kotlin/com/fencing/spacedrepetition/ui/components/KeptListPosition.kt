// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable

/**
 * A list's scroll position, held by whoever outlives the screen -- App, for
 * a screen that is left for an editor and come back to -- so returning to
 * the list finds it where it was. Navigation here does not keep a screen's
 * own remembered state while another screen is showing.
 */
@Stable
class KeptListPosition {
    val list = LazyListState()

    /** The sort the list was last shown in; see [ScrollToTopOnSortChange]. */
    internal var sortedFor: Any? = null
}

/**
 * Back to the top when the list's sort changes -- but not when the list is
 * merely shown again in the same sort, which would lose the place kept in
 * [position].
 */
@Composable
fun ScrollToTopOnSortChange(position: KeptListPosition, sortKey: Any) {
    LaunchedEffect(sortKey) {
        if (position.sortedFor != null && position.sortedFor != sortKey) position.list.scrollToItem(0)
        position.sortedFor = sortKey
    }
}
