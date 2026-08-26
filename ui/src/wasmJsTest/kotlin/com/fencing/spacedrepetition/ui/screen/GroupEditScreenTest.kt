// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The group editor, in a browser.
 *
 * No database and no view model: this screen takes plain values and
 * callbacks, which is why it was the cheapest of the remaining screens to
 * move and why its test can drive it directly. That also makes it the first
 * test here that presses a control and checks what came back out, rather than
 * only checking that something rendered.
 */
class GroupEditScreenTest {

    private fun group() = Group(id = 7, name = "Sabre footwork", description = "warm-up")

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun drawsAnExistingGroupForEditing() = runComposeUiTest {
        setContent {
            FencingSpacedRepetitionTheme {
                GroupEditScreen(
                    group = group(),
                    globalCardsPerSession = 3,
                    globalAutoShowAnswer = false,
                    globalRandomizeDueCards = true,
                    globalRandomizeBucketHours = 24,
                    globalPracticeDays = setOf(1, 3, 5),
                    globalMaximumInterval = 365,
                    globalFsrsRetention = 90,
                    globalFsrsEnableFuzzing = true,
                    groupCardCount = 12,
                    practiceScheduleEstimate = null,
                    historyWindowDays = 28,
                    onHistoryWindowDaysChange = {},
                    onSave = {},
                    onNavigateBack = {}
                )
            }
        }

        // An existing group edits rather than adds, and its values are on screen.
        onNodeWithText("Edit Group").assertIsDisplayed()
        onNodeWithText("Sabre footwork").assertIsDisplayed()
        onNodeWithText("warm-up").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun savingHandsBackTheEditedGroup() = runComposeUiTest {
        var saved: Group? = null

        setContent {
            FencingSpacedRepetitionTheme {
                GroupEditScreen(
                    group = group(),
                    globalCardsPerSession = 3,
                    globalAutoShowAnswer = false,
                    globalRandomizeDueCards = true,
                    globalRandomizeBucketHours = 24,
                    globalPracticeDays = setOf(1, 3, 5),
                    globalMaximumInterval = 365,
                    globalFsrsRetention = 90,
                    globalFsrsEnableFuzzing = true,
                    groupCardCount = 12,
                    practiceScheduleEstimate = null,
                    historyWindowDays = 28,
                    onHistoryWindowDaysChange = {},
                    onSave = { saved = it },
                    onNavigateBack = {}
                )
            }
        }

        onNodeWithText("Save").performClick()

        val result = assertNotNull(saved, "Save did not call back")
        assertEquals(7L, result.id, "the edited group lost its identity")
        assertEquals("Sabre footwork", result.name)
    }
}
