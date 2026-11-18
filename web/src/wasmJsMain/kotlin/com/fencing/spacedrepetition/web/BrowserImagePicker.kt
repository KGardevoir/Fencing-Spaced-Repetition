// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.web

import com.fencing.spacedrepetition.ui.image.ImagePicker
import com.fencing.spacedrepetition.ui.image.PickedImage
import com.fencing.spacedrepetition.util.ImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import com.fencing.spacedrepetition.ui.fileBytes
import com.fencing.spacedrepetition.ui.fileType
import com.fencing.spacedrepetition.ui.openFileDialog

/**
 * The browser half of [ImagePicker]: the same hidden file input an import
 * opens, through [openFileDialog], hinted to images.
 *
 * A cancelled dialog never resolves anything, matching Android, where the
 * result callback simply carries no URI.
 */
fun browserImagePicker(scope: CoroutineScope, store: ImageStore): ImagePicker =
    ImagePicker { onPicked ->
        scope.launch {
            val file = openFileDialog("image/*").await<JsAny?>() ?: return@launch
            val data = fileBytes(file).await<JsAny?>() ?: return@launch
            val bytes = data.bytes()
            val key = store.write(bytes, extension(fileType(file)))
            onPicked(PickedImage(key, bytes.size))
        }
    }

private fun JsAny.bytes(): ByteArray {
    val length = dataLength(this)
    val out = ByteArray(length)
    for (i in 0 until length) out[i] = dataByte(this, i).toByte()
    return out
}

private fun extension(type: String): String {
    val extension = type.substringAfterLast('/', "")
    return if (extension.isBlank()) "jpg" else extension
}

private fun dataLength(data: JsAny): Int = js("data.length")
private fun dataByte(data: JsAny, index: Int): Int = js("data[index]")
