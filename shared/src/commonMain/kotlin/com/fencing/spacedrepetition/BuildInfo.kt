// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition

/**
 * What the About section shows about this build.
 *
 * On Android these come from BuildConfig, which is generated per application
 * module and has no equivalent on the web. Passing them in as a value keeps
 * the settings screen shared while letting each platform answer the question
 * its own way.
 */
data class BuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val gitCommit: String
)
