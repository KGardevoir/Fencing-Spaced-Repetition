// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.Group

/**
 * Where the app can be.
 *
 * A sealed class rather than string routes, because every destination that
 * carries anything carries a whole entity -- the card being edited, the group
 * being edited -- and the Android graph was already smuggling those past the
 * router in variables captured outside it rather than encoding them in a
 * route. Naming them here makes that explicit and type-checked.
 */
sealed interface Destination {
    data object Home : Destination
    data object Practice : Destination
    data object Grading : Destination
    data object CardList : Destination
    data class AddCard(val initialGroupId: Long?) : Destination
    data class EditCard(val card: Card) : Destination
    data object GroupList : Destination
    data class EditGroup(val group: Group) : Destination
    data object AddGroup : Destination
    data object Settings : Destination
    data object History : Destination
    data object Opponents : Destination
}

/**
 * A back stack, and nothing else.
 *
 * Deliberately not androidx.navigation. The multiplatform artifact exists,
 * but its version is capped by this build's AGP 9.0 pin -- the same ceiling
 * that holds lifecycle at 2.9.x -- and what it would buy here is deep links,
 * route parsing and saved-state restoration across process death, none of
 * which this app uses. What it actually needs is a list it can push and pop,
 * so that is what this is.
 *
 * The stack is never empty: popping the last entry is a no-op rather than an
 * error, because on the web there is no enclosing activity to fall back to
 * and a screen with nothing behind it should simply stay put.
 */
@Stable
class Navigator internal constructor(initial: Destination) {

    private val stack: SnapshotStateList<Destination> = mutableStateListOf(initial)

    /** What to draw. */
    val current: Destination get() = stack.last()

    /** Whether there is anywhere to go back to. */
    val canGoBack: Boolean get() = stack.size > 1

    fun go(destination: Destination) {
        stack.add(destination)
    }

    /**
     * Returns to [destination] if it is already on the stack, rather than
     * pushing a second copy. Going Home from four screens deep should leave
     * one entry behind, not five.
     */
    fun goUpTo(destination: Destination) {
        val index = stack.indexOfLast { it == destination }
        if (index == -1) {
            go(destination)
            return
        }
        while (stack.size > index + 1) stack.removeAt(stack.size - 1)
    }

    fun back() {
        if (canGoBack) stack.removeAt(stack.size - 1)
    }

    /** Replaces everything with [destination] -- for finishing a session. */
    fun resetTo(destination: Destination) {
        stack.clear()
        stack.add(destination)
    }
}

@Composable
fun rememberNavigator(initial: Destination = Destination.Home): Navigator =
    remember { Navigator(initial) }
