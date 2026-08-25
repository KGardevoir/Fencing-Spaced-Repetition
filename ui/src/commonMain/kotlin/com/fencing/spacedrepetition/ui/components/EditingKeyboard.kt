// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.runtime.Composable

/**
 * Whether a keyboard is available to type with right now.
 *
 * The markdown toolbar sits above the keyboard, so it should appear only when
 * there is one -- and "is there a keyboard" is a question each platform
 * answers differently. Android asks whether the IME is showing or a hardware
 * keyboard is attached; a browser has no equivalent of either, so on the web
 * this is simply true and the toolbar follows focus alone.
 */
@Composable
expect fun keyboardIsAvailableForEditing(): Boolean
