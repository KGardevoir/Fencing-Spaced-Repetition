// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.image

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.fencing.spacedrepetition.util.ImageStore
import kotlinx.coroutines.launch

/**
 * The Android half of [ImagePicker]: a GetContent activity result.
 *
 * One launcher for the whole app rather than one per attach button. The
 * launcher must be created during composition while pick() is called from a
 * click, so the request in flight is held between the two. Single flight is
 * not a limitation here: the picker is full-screen system UI, so a second
 * request cannot be started while one is open.
 *
 * The bytes are read and stored before the key is handed back, so the caller
 * receives a key that already resolves rather than one that will shortly.
 */
@Composable
fun rememberAndroidImagePicker(store: ImageStore): ImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<(PickedImage) -> Unit>(1) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val onPicked = pending[0]
        pending[0] = null
        if (onPicked == null || uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@launch

            // The extension comes from the MIME type the provider reports,
            // because a content URI often has no file name to take one from.
            val extension = context.contentResolver.getType(uri)
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
                ?: "jpg"

            onPicked(PickedImage(store.write(bytes, extension), bytes.size))
        }
    }

    return remember(launcher, store) {
        ImagePicker { onPicked ->
            pending[0] = onPicked
            launcher.launch("image/*")
        }
    }
}
