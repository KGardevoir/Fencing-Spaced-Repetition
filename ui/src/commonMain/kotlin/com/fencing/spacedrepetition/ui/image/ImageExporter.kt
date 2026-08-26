// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.image

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Saves one stored image to wherever the user keeps files.
 *
 * The counterpart to [ImagePicker], and a composition local for the same
 * reason: the button that uses it is inside the full-screen image dialog,
 * which is opened from the practice, grading, history and card screens and
 * from the card editor. Threading a callback to it would mean a new parameter
 * on all five and on the list rows between, to reach one icon.
 *
 * Takes a store key, never a path -- the platform reads the bytes back out of
 * the store itself, because only it knows what a key resolves to. Choosing
 * where they land is the platform's too: Android opens a document creator, a
 * browser starts a download.
 */
fun interface ImageExporter {
    fun export(key: String)
}

/**
 * Null where no platform has provided one, rather than an error.
 *
 * [LocalImagePicker] fails loudly when unprovided, and should: a screen with
 * an attach button and nothing behind it is broken. This one is different --
 * the dialog it belongs to is opened from everywhere, including the screen
 * tests, and the button is worth hiding rather than worth crashing for. A
 * platform that cannot save a file still shows photos.
 */
val LocalImageExporter = staticCompositionLocalOf<ImageExporter?> { null }
