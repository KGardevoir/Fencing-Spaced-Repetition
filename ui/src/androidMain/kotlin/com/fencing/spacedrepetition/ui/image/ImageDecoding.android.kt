// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.image

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BitmapFactory, in two passes.
 *
 * The first pass reads only the header (inJustDecodeBounds) to learn the real
 * size; the second decodes subsampled to roughly the size actually wanted.
 * This is what an image library does for you and the reason one is usually
 * worth having -- decoding a phone photo at full resolution to draw it 80dp
 * high costs tens of megabytes for pixels that are thrown away immediately.
 *
 * inSampleSize only takes powers of two, so the result is at worst under
 * twice the requested edge. That is deliberate: rounding down instead would
 * decode below the target and show a soft image.
 */
actual suspend fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    withContext(Dispatchers.Default) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }.getOrNull()
    }

internal fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    var sample = 1
    var longest = maxOf(width, height)
    while (longest / 2 >= maxDimension) {
        longest /= 2
        sample *= 2
    }
    return sample
}
