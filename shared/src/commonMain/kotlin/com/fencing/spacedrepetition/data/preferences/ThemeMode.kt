// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.preferences

/**
 * Which colour scheme the app draws with.
 *
 * Here rather than next to the theme it drives, because it is what a
 * preferences store reads and writes -- and each platform stores preferences
 * its own way. SYSTEM means "follow the platform", which on Android is the
 * night-mode setting and in a browser is the prefers-color-scheme query.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
