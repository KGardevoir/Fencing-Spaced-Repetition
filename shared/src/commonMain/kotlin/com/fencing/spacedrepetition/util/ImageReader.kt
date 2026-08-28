// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

/**
 * Reads a stored image back as bytes.
 *
 * An export inlines images as base64, so exporting has to read them
 * from wherever the platform put them. On Android that is a file path under
 * filesDir; in a browser it will not be a file at all. This is the whole of
 * that dependency -- one function, no writing, because export never writes an
 * image.
 *
 * Returns null when the image is missing or unreadable, which callers treat as
 * "skip this image" rather than as a failed export.
 */
fun interface ImageReader {
    fun read(path: String): ByteArray?
}
