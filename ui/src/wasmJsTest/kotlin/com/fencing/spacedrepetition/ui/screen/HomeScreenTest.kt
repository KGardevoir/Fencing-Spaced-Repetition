// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import com.fencing.spacedrepetition.util.BackupReminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The home screen, in a browser.
 *
 * Worth more than "it renders": this screen used to derive the selected group,
 * resolve which counts applied to it and persist a fallback selection, and all
 * of that moved to the caller when it took plain parameters. What is left is
 * that it shows what it is given and reports what was pressed, and that is
 * what these pin down.
 *
 * The counts are deliberately all different from each other. Collapsing the
 * whole-collection counts into the per-group ones is a mistake that reads
 * fine and shows the wrong number on a real screen, so each assertion names
 * the one it expects.
 *
 * Every test returns the result of runComposeUiTest, including the two that
 * check a callback. On wasm that result is a promise: assert after it rather
 * than inside and the assertion runs before the browser has done anything,
 * and the test passes whatever happened.
 */
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {

    private val sabre = Group(id = 1, name = "Sabre footwork")
    private val epee = Group(id = 2, name = "Epee distance")

    private fun home(
        cardsToPractise: Int = 12,
        backupReminder: BackupReminder? = null,
        onSelectGroup: (Group) -> Unit = {},
        onStartPractice: () -> Unit = {},
        onBackUpNow: () -> Unit = {},
        body: androidx.compose.ui.test.ComposeUiTest.() -> Unit
    ) = runComposeUiTest {
        setContent {
            FencingSpacedRepetitionTheme {
                HomeScreen(
                    groups = listOf(sabre, epee),
                    selectedGroup = sabre,
                    cardsPerSession = 3,
                    totalCardCount = 40,
                    totalDueCount = 9,
                    cardsToPractise = cardsToPractise,
                    dueCount = 5,
                    backupReminder = backupReminder,
                    onBackUpNow = onBackUpNow,
                    onSelectGroup = onSelectGroup,
                    onStartPractice = onStartPractice,
                    onNavigateToCards = {},
                    onNavigateToGroups = {},
                    onNavigateToSettings = {},
                    onNavigateToHistory = {},
                    onNavigateToOpponents = {}
                )
            }
        }
        body()
    }

    @Test
    fun showsTheSelectedGroupWithItsOwnDueCount() = home {
        onNodeWithText("Sabre footwork").assertIsDisplayed()
        // The group's 5, not the collection's 9.
        onNodeWithText("5 due").assertIsDisplayed()
    }

    @Test
    fun theStatCardsReportTheWholeCollection() = home {
        onNodeWithText("40").assertIsDisplayed()
        onNodeWithText("9").assertIsDisplayed()
    }

    @Test
    fun theHeaderSaysHowManyCardsASessionHolds() = home {
        onNodeWithText("Practice 3 cards, grade at the end").assertIsDisplayed()
    }

    // "Start Practice" is the button; the sentence above it is only a
    // subtitle, and asserting enablement on that Text passes whatever the
    // button is doing.
    @Test
    fun practiceIsOfferedWhenTheGroupHasCards() = home(cardsToPractise = 12) {
        onNodeWithText("Start Practice").assertIsEnabled()
    }

    @Test
    fun practiceIsRefusedWhenTheGroupIsEmpty() = home(cardsToPractise = 0) {
        onNodeWithText("Start Practice").assertIsNotEnabled()
        onNodeWithText("No cards in Sabre footwork").assertIsDisplayed()
    }

    // run { } so the body can hold a counter and the function still returns
    // the promise: a block-bodied test returns Unit, and the promise would be
    // dropped rather than awaited.
    @Test
    fun pressingPracticeReportsItOnce() = run {
        var started = 0
        home(onStartPractice = { started++ }) {
            onNodeWithText("Start Practice").performClick()
            assertEquals(1, started, "practice was not started exactly once")
        }
    }

    @Test
    fun choosingAnotherGroupReportsThatGroup() = run {
        var chosen: Group? = null
        home(onSelectGroup = { chosen = it }) {
            onNodeWithText("Sabre footwork").performClick()
            onNodeWithText("Epee distance").performClick()
            assertSame(epee, chosen, "expected Epee distance, got ${chosen?.name}")
        }
    }

    // The backup reminder. It is the browser's substitute for a backup that
    // runs on its own, so the screen has to be silent when there is nothing
    // to say -- a nag that is always there is one nobody reads.
    @Test
    fun saysNothingAboutBackupsWhenNoneIsDue() = home(backupReminder = null) {
        onNodeWithText("Time to back up").assertDoesNotExist()
    }

    @Test
    fun asksForABackupWhenOneIsOverdue() = home(backupReminder = BackupReminder(9)) {
        onNodeWithText("Time to back up").assertIsDisplayed()
        onNodeWithText("Your last backup was 9 days ago.").assertIsDisplayed()
    }

    @Test
    fun saysSoWhenThereHasNeverBeenABackup() = home(backupReminder = BackupReminder(null)) {
        onNodeWithText("Your cards have never been backed up. They live in this " +
            "browser's storage, which it is free to clear.").assertIsDisplayed()
    }

    @Test
    fun pressingTheReminderAsksForABackup() = run {
        var backups = 0
        home(backupReminder = BackupReminder(9), onBackUpNow = { backups++ }) {
            onNodeWithText("Download a Backup").performClick()
            assertEquals(1, backups, "the backup was not requested exactly once")
        }
    }
}
