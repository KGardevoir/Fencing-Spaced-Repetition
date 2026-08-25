// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.fencing.spacedrepetition.data.preferences.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Composes the theme for real, in a browser.
 *
 * The reason to run this rather than compare colour constants: a theme that
 * compiles can still throw on first composition, and on the browser it can
 * compile, bundle, and then never draw. This test composes, lays out and
 * queries the result, so "it builds" and "it renders" stop being the same
 * claim -- which for the wasm target they are not.
 *
 * The colour assertions read the scheme from inside the composition rather
 * than from the private schemes in Theme.kt, so they check what a screen
 * would actually be handed.
 *
 * wasmJsTest rather than commonTest, and the reason is worth recording:
 * runComposeUiTest needs a real UI environment, and on Android that means an
 * instrumented test on a device or emulator, not the local unit-test variant
 * -- which is where :ui:allTests would put a commonTest, and where it throws
 * on the first call. Covering the Android theme the same way needs an
 * emulator this project's CI does not have, the same gap that leaves the
 * database migrations unexercised.
 */
class ThemeTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun lightModeDrawsWithTheLightScheme() = runComposeUiTest {
        var primary: Color? = null

        setContent {
            FencingSpacedRepetitionTheme(ThemeMode.LIGHT) {
                primary = MaterialTheme.colorScheme.primary
                Text("parry four")
            }
        }

        onNodeWithText("parry four").assertIsDisplayed()
        assertEquals(Color(0xFF0061A4), primary, "light mode did not get the light scheme")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun darkModeDrawsWithTheDarkScheme() = runComposeUiTest {
        var primary: Color? = null

        setContent {
            FencingSpacedRepetitionTheme(ThemeMode.DARK) {
                primary = MaterialTheme.colorScheme.primary
                Text("riposte")
            }
        }

        onNodeWithText("riposte").assertIsDisplayed()
        assertEquals(Color(0xFF90CAF9), primary, "dark mode did not get the dark scheme")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theTypeScaleReachesTheComposition() = runComposeUiTest {
        var headlineSize: Float? = null

        setContent {
            FencingSpacedRepetitionTheme {
                headlineSize = MaterialTheme.typography.headlineLarge.fontSize.value
                Text("en garde")
            }
        }

        onNodeWithText("en garde").assertIsDisplayed()
        assertEquals(32f, headlineSize, "the app's type scale was not applied")
    }
}
