// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import android.content.Context
import java.io.File

/** The one directory content-addressed images are written to. */
const val IMAGE_DIRECTORY = "images"

/** Where [IMAGE_DIRECTORY] lives for this app. */
fun imageDirectory(context: Context): File = File(context.filesDir, IMAGE_DIRECTORY)

/**
 * Turns a stored-image key into the file holding it, or null if it is not a
 * file this app may read.
 *
 * Two shapes arrive here. A content key -- a hash and an extension, no
 * separators -- resolves inside [directory]. An absolute path is an image
 * stored before the content-addressed store existed, and resolves as itself.
 *
 * The absolute branch is confined to [filesDir]. Keys come out of the
 * database and the database is fed by imports, so without the check an export
 * crafted elsewhere could name any file the app can read and have the next
 * export inline it as base64. Canonicalising first is what makes that hold
 * against "..", and against a symlink planted in the images directory.
 *
 * Shared by the store and by export's reader, which resolve the same keys and
 * would otherwise be two copies of this rule that could drift apart -- and the
 * half that drifted would be the security check.
 */
fun resolveImageFile(key: String, directory: File, filesDir: File): File? {
    if (!key.startsWith("/")) {
        val candidate = File(directory, key)
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        return if (canonical.isWithin(filesDir)) canonical else null
    }
    val canonical = runCatching { File(key).canonicalFile }.getOrNull() ?: return null
    return if (canonical.isWithin(filesDir)) canonical else null
}

private fun File.isWithin(root: File): Boolean {
    val rootPath = runCatching { root.canonicalFile.path }.getOrNull() ?: return false
    return path.startsWith(rootPath + File.separator)
}
