// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.fencing.spacedrepetition.ui.theme.FencingSpacedRepetitionTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The 1-5 rating row: what a tap reports, and how the choice reads back.
 *
 * Expression-bodied, like every other test here, so the function returns the
 * promise `runComposeUiTest` hands back. A block body returns Unit, the promise
 * is dropped rather than awaited, and the assertions then run before the
 * composition exists -- which passes or fails for reasons that have nothing to
 * do with the picker.
 */
class FightDifficultyPickerTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingARatingReportsIt() = runComposeUiTest {
        val chosen = mutableListOf<Int?>()

        setContent {
            FencingSpacedRepetitionTheme {
                FightDifficultyPicker(selected = null, onSelected = { chosen += it })
            }
        }

        onNodeWithText("Unrated").assertIsDisplayed()
        onNodeWithText("4").performClick()

        assertEquals(listOf<Int?>(4), chosen, "tapping a rating did not report it")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingTheChosenRatingClearsIt() = runComposeUiTest {
        val chosen = mutableListOf<Int?>()

        setContent {
            FencingSpacedRepetitionTheme {
                FightDifficultyPicker(selected = 4, onSelected = { chosen += it })
            }
        }

        // The chosen rating names itself, multiplier and all.
        onNodeWithText("Testing · ×1.25").assertIsDisplayed()
        onNodeWithText("4").performClick()

        assertEquals(listOf<Int?>(null), chosen, "tapping the chosen rating did not clear it")
    }
}
