// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.image

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Turns encoded image bytes into something Compose can draw.
 *
 * No image-loading library, on either platform. It is tempting to think a
 * browser renders images for free, and in a page of HTML it does -- but
 * Compose on wasm draws the entire interface into one canvas through Skia,
 * so there is no <img> element to hand a URL to and something has to produce
 * pixels. Skia is already in the bundle for that reason and decodes PNG and
 * JPEG itself, and Android has had BitmapFactory since the beginning. What a
 * library would have added is caching and downsampling, which are [ImageCache]
 * and [maxDimension] here.
 *
 * [maxDimension] is a hint, in pixels, for the longest edge actually needed.
 * Android honours it by decoding subsampled, so a 12-megapixel photo shown as
 * an 80dp thumbnail never becomes a 48MB bitmap. Skia has no equivalent and
 * decodes in full, so on the web the saving has to come from not storing
 * enormous images in the first place.
 *
 * Returns null rather than throwing: a card can reference an image that was
 * deleted, truncated by a failed import, or written by a browser that encoded
 * something Skia will not read, and none of those should take down the screen
 * the card is on.
 */
expect suspend fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap?
