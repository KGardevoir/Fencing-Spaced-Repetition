// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun keyboardIsAvailableForEditing(): Boolean {
    val configuration = LocalConfiguration.current
    val hasPhysicalKeyboard =
        configuration.keyboard == Configuration.KEYBOARD_QWERTY ||
            configuration.keyboard == Configuration.KEYBOARD_12KEY
    return WindowInsets.isImeVisible || hasPhysicalKeyboard
}
