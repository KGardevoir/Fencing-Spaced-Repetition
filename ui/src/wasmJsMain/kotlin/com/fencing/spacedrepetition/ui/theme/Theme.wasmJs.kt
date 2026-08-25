// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Nothing to do. The browser chrome around the page is the browser's, not the
 * app's; the one piece an installed web app does control is the theme-color
 * meta tag, and that belongs to the document rather than to a composition.
 */
@Composable
internal actual fun ApplySystemBarStyle(darkTheme: Boolean, colorScheme: ColorScheme) {
}
