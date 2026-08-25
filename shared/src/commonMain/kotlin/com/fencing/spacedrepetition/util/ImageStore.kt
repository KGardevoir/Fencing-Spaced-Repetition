// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

/**
 * Where card and review images live.
 *
 * A card stores a list of image *keys*, not file paths. On Android a key
 * resolves to a file under filesDir; in a browser it resolves to a file in the
 * Origin Private File System, beside the database. Neither is a path the rest
 * of the app should know the shape of, which is why this is the whole surface.
 *
 * Keys are content-addressed -- see [contentKey] -- so storing the same image
 * twice stores it once, and re-importing an export does not duplicate every
 * picture in the collection. That matters more than the choice of container:
 * a deck built by importing a friend's export and then re-importing your own
 * would otherwise hold two copies of everything shared between them.
 *
 * Every method suspends because the browser's is asynchronous. On Android they
 * complete without ever yielding.
 */
interface ImageStore {

    /** The bytes behind a key, or null if it is missing or unreadable. */
    suspend fun read(key: String): ByteArray?

    /**
     * Stores [bytes] and returns the key to reach them by.
     *
     * Idempotent: the same bytes always produce the same key, and storing them
     * again overwrites an identical file rather than adding one.
     */
    suspend fun write(bytes: ByteArray, extension: String = "jpg"): String

    /** Removes the image, if it is there. Missing keys are not an error. */
    suspend fun delete(key: String)
}

/**
 * The key a given image is stored under: its SHA-256, hex, plus an extension.
 *
 * The extension is carried so the stored file is recognisable to anything that
 * looks at the storage directly (a browser's OPFS inspector, `adb shell ls`),
 * not because anything here reads it back.
 */
suspend fun contentKey(bytes: ByteArray, extension: String = "jpg"): String =
    sha256Hex(bytes) + "." + extension.lowercase().trimStart('.')

/**
 * Hex SHA-256 of [bytes].
 *
 * expect/actual because neither platform's digest is portable: Android has
 * java.security.MessageDigest, and a browser has crypto.subtle, which is
 * asynchronous. Writing one in Kotlin would be a third implementation of a
 * primitive both platforms already ship, and a slower one.
 */
expect suspend fun sha256Hex(bytes: ByteArray): String
