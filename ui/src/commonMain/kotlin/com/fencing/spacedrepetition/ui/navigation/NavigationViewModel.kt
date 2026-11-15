// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.navigation

import androidx.lifecycle.ViewModel

/**
 * Holds the back stack across Activity recreation.
 *
 * [rememberNavigator] keeps the stack in composition, which on Android is
 * thrown away whenever the Activity is recreated -- a rotation, a dark-mode
 * switch, a locale change -- and the app came back on Home. A ViewModel
 * outlives that, so the stack lives here instead. Process death still resets
 * it, which is the trade the comment on [Navigator] already accepts.
 */
class NavigationViewModel : ViewModel() {
    val navigator = Navigator(Destination.Home)
}
