// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.image

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Asks the user for an image and puts it in the store.
 *
 * A composition local rather than a parameter, for the same reason as
 * [LocalImageCache] and with one extra: the screens that attach images do it
 * per row. Every card in the grading list and every note in the history has
 * its own attach button, so a callback would have to be threaded through the
 * list item and the note editor to reach one icon -- and the screens still
 * take their own data as plain values, which is what that rule is protecting.
 *
 * [onPicked] receives a store key, never a file path or a URL, and is not
 * called at all if the user cancels. There is no directory to choose: keys are
 * content hashes and the store keeps one flat directory. Choosing the file is the platform's job:
 * Android opens a document picker, a browser opens a file input. Neither
 * appears in a screen.
 */
fun interface ImagePicker {
    fun pick(onPicked: (String) -> Unit)
}

val LocalImagePicker = staticCompositionLocalOf<ImagePicker> {
    error("No ImagePicker provided. Wrap the app in CompositionLocalProvider(LocalImagePicker provides ...)")
}
