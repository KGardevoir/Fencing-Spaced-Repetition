// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import android.content.Context
import java.io.File

/**
 * Reads images off the filesystem for export.
 *
 * Resolves a key the same two ways [FileImageStore] does, and has to: images
 * attached since the store arrived are content keys with no directory in them,
 * and images from before it are absolute paths. Reading only absolute paths
 * would export every old image and silently drop every new one -- the export
 * would succeed, be short, and say nothing about it.
 *
 * Synchronous, and so not the store itself, because the export format code is
 * synchronous all the way down. That is what [ImageStore.readerFor] exists to
 * bridge: a browser answers it from a preloaded map, and this answers it from
 * the disk it is already sitting on, one image at a time, so an export of a
 * large collection streams instead of being held in memory.
 *
 * Resolution itself is shared with the store -- see [resolveImageFile] -- so
 * the containment check cannot drift between the two.
 */
class FileImageReader(context: Context) : ImageReader {

    private val filesDir: File = context.filesDir
    private val directory: File = imageDirectory(context)

    override fun read(path: String): ByteArray? {
        val file = resolve(path) ?: return null
        if (!file.exists() || !file.canRead()) return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            null
        }
    }

    private fun resolve(key: String): File? = resolveImageFile(key, directory, filesDir)
}
