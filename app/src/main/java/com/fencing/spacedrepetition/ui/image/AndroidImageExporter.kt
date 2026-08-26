// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.image

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.fencing.spacedrepetition.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Android half of [ImageExporter]: a CreateDocument activity result.
 *
 * Held between composition and the click the same way [rememberAndroidImagePicker]
 * holds its callback, and for the same reason -- a launcher has to exist before
 * the tap that uses it. What is held here is the key, because the document is
 * only chosen after the user has picked a name, and by then the button that
 * knew which photo it was has been dismissed along with the dialog.
 *
 * The result is a toast rather than the import/export dialog the deck exports
 * use: this is one file saved from a full-screen picture, and the picture is
 * still on screen underneath. A dialog would be a second thing to dismiss.
 */
@Composable
fun rememberAndroidImageExporter(store: ImageStore): ImageExporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { arrayOfNulls<String>(1) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*")
    ) { uri ->
        val key = pending[0]
        pending[0] = null
        if (key == null || uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = store.read(key) ?: return@runCatching false
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                if (saved) "Photo exported" else "Failed to export photo",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    return remember(launcher, store) {
        ImageExporter { key ->
            pending[0] = key
            // The key is a content hash, which is no name to offer anyone, so
            // the suggestion is the word and the extension the store recorded.
            launcher.launch("photo.${key.substringAfterLast('.', "jpg")}")
        }
    }
}
