// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import androidx.compose.runtime.Composable

/**
 * A browser reports neither an IME inset nor an attached keyboard, so there is
 * nothing here to test. Returning true shows the toolbar whenever a field has
 * focus, which is right on a desktop browser and harmless on a phone: focus is
 * what raises the soft keyboard there anyway.
 */
@Composable
actual fun keyboardIsAvailableForEditing(): Boolean = true
