// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Images as files under filesDir, keyed by content hash.
 *
 * Reads accept a bare key or a legacy absolute path, because every image
 * stored before this existed is recorded in the database as the latter --
 * "/data/user/0/.../files/card_images/card_images_1712345678.jpg". Those rows
 * are not rewritten: an absolute path still resolves, so an upgrade does not
 * have to touch user data to keep working, and each image converts to a
 * content key the next time it is written rather than in a migration that
 * would have to read every picture in the collection at once.
 *
 * Nothing writes such a path any more. The last thing that did was the
 * import, which decoded inline base64 images to files of its own; it stores
 * them here now, so an imported picture is deduplicated against the ones
 * already in the collection instead of being a new file every time.
 *
 * The legacy branch checks the path is inside filesDir. A key comes out of the
 * database, and the database is fed by imports; without the check, a crafted
 * export could name any file the app can read and have export inline it as
 * base64.
 *
 * One flat directory, not the card_images/review_images split the old code
 * used. A key is a content hash and carries no directory, so a store opened on
 * the wrong one would simply fail to find images written by the other -- and
 * with hashes for names there is nothing a split would organise. Images
 * written before this still live in those directories and are still found,
 * because their rows hold the full path.
 */
class FileImageStore(context: Context) : ImageStore {

    private val filesDir: File = context.filesDir
    private val directory: File = imageDirectory(context)

    // One reader, held rather than made per export: it is two File fields and
    // no state, and an export asks for it once.
    private val reader = FileImageReader(context)

    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolve(key) ?: return@withContext null
        if (!file.exists() || !file.canRead()) return@withContext null
        runCatching { file.readBytes() }.getOrNull()
    }

    override suspend fun write(bytes: ByteArray, extension: String): String =
        withContext(Dispatchers.IO) {
            val key = contentKey(bytes, extension)
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, key)
            // Content-addressed, so an existing file of this name already holds
            // exactly these bytes. Rewriting it would be work for no change.
            if (!file.exists()) file.writeBytes(bytes)
            key
        }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            resolve(key)?.takeIf { it.exists() }?.delete()
        }
    }

    /**
     * The keys are ignored: files are already at hand, so an export reads them
     * as it writes each line rather than loading the collection's every image
     * into memory first, which is what the default in [ImageStore] does.
     */
    override suspend fun readerFor(keys: Collection<String>): ImageReader = reader

    private fun resolve(key: String): File? = resolveImageFile(key, directory, filesDir)
}
