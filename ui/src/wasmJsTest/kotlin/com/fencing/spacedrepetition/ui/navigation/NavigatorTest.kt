// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.navigation

import com.fencing.spacedrepetition.data.model.Group
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The back stack, which replaces androidx.navigation on both platforms.
 *
 * Small enough to be obviously correct and important enough to pin down
 * anyway: it is now the only thing deciding what the app shows, and two of
 * its four rules exist because the naive version of each is wrong.
 */
class NavigatorTest {

    private fun navigator() = Navigator(Destination.Home)

    @Test
    fun startsAtItsInitialDestination() {
        val nav = navigator()
        assertEquals(Destination.Home, nav.current)
        assertFalse(nav.canGoBack, "nothing has been pushed yet")
    }

    @Test
    fun goPushesAndBackPops() {
        val nav = navigator()
        nav.go(Destination.CardList)
        assertEquals(Destination.CardList, nav.current)
        assertTrue(nav.canGoBack)

        nav.back()
        assertEquals(Destination.Home, nav.current)
        assertFalse(nav.canGoBack)
    }

    /**
     * On the web there is no activity behind the last screen, so a back with
     * nothing to pop has to leave the app where it is rather than empty the
     * stack and leave nothing to draw.
     */
    @Test
    fun backAtTheRootDoesNothing() {
        val nav = navigator()
        nav.back()
        nav.back()
        assertEquals(Destination.Home, nav.current)
    }

    /**
     * The reason goUpTo exists. Navigating Home from four screens deep by
     * pushing would leave five entries and require four presses of back to
     * undo one press of a home button.
     */
    @Test
    fun goUpToUnwindsRatherThanPushingAgain() {
        val nav = navigator()
        nav.go(Destination.CardList)
        nav.go(Destination.AddCard(initialGroupId = null))
        nav.go(Destination.Settings)

        nav.goUpTo(Destination.Home)

        assertEquals(Destination.Home, nav.current)
        assertFalse(nav.canGoBack, "Home should be the only entry left")
    }

    @Test
    fun goUpToPushesWhenTheDestinationIsNotOnTheStack() {
        val nav = navigator()
        nav.goUpTo(Destination.History)
        assertEquals(Destination.History, nav.current)
        assertTrue(nav.canGoBack)
    }

    /**
     * Destinations carrying an entity compare by that entity, so returning to
     * "the card list" does not accidentally match "editing card 7".
     */
    @Test
    fun destinationsCarryingDataCompareByIt() {
        val nav = navigator()
        val one = Group(id = 1, name = "Sabre")
        val two = Group(id = 2, name = "Epee")

        nav.go(Destination.EditGroup(one))
        nav.go(Destination.EditGroup(two))
        nav.goUpTo(Destination.EditGroup(one))

        assertEquals(Destination.EditGroup(one), nav.current)
        assertTrue(nav.canGoBack, "Home is still underneath")
    }

    @Test
    fun resetToClearsEverything() {
        val nav = navigator()
        nav.go(Destination.Practice)
        nav.go(Destination.Grading)

        nav.resetTo(Destination.Home)

        assertEquals(Destination.Home, nav.current)
        assertFalse(nav.canGoBack)
    }
}
