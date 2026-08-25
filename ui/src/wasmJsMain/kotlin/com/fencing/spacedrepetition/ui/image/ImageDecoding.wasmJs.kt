// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * Skia, which Compose already carries in order to draw anything at all.
 *
 * makeFromEncoded reads PNG, JPEG, WebP and GIF, so no decoder is added to the
 * bundle -- this is the same code path that puts every other pixel on screen,
 * reached directly.
 *
 * maxDimension is ignored, and that is a real limitation rather than an
 * oversight: Skia has no subsampled decode to match Android's inSampleSize, so
 * a large image is decoded at full size and then scaled down when it is drawn.
 * Capping images on the way into storage would fix it on both platforms at
 * once; until that is decided, the browser holds full-resolution bitmaps for
 * whatever a card references.
 */
actual suspend fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
