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

/** The 1-5 rating row: what a tap reports, and how it reads back. */
class FightDifficultyPickerTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingARatingReportsIt() {
        val chosen = mutableListOf<Int?>()
        runComposeUiTest {
            setContent {
                FencingSpacedRepetitionTheme {
                    FightDifficultyPicker(selected = null, onSelected = { chosen += it })
                }
            }

            onNodeWithText("Unrated").assertIsDisplayed()
            onNodeWithText("4").performClick()
        }
        assertEquals(listOf<Int?>(4), chosen)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingTheChosenRatingClearsIt() {
        val chosen = mutableListOf<Int?>()
        runComposeUiTest {
            setContent {
                FencingSpacedRepetitionTheme {
                    FightDifficultyPicker(selected = 4, onSelected = { chosen += it })
                }
            }

            // The chosen rating names itself, multiplier and all.
            onNodeWithText("Testing · ×1.25").assertIsDisplayed()
            onNodeWithText("4").performClick()
        }
        assertEquals(listOf<Int?>(null), chosen)
    }
}
