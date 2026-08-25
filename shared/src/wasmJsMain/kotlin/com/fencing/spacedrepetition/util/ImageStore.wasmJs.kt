// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Images in the Origin Private File System, in a directory beside the database.
 *
 * OPFS rather than IndexedDB or a base64 column, for space. All three can hold
 * the same picture, but base64 in SQLite costs a third more bytes and drags
 * that weight through every query that reads the row, while OPFS and IndexedDB
 * both store the compressed original as-is. Between those two, OPFS is where
 * the database already lives, so there is one storage story, one origin to
 * ask navigator.storage.persist() about, and one thing to explain when a
 * browser evicts it.
 *
 * The whole store is one flat directory: keys are content hashes, so there is
 * nothing to organise and no collisions to arrange around.
 */
object OpfsImageStore : ImageStore {

    private const val DIRECTORY = "images"

    override suspend fun read(key: String): ByteArray? {
        val buffer = readFile(DIRECTORY, key).await<JsAny?>() ?: return null
        return buffer.toByteArray()
    }

    override suspend fun write(bytes: ByteArray, extension: String): String {
        val key = contentKey(bytes, extension)
        writeFile(DIRECTORY, key, bytes.toUint8Array()).await<JsAny?>()
        return key
    }

    override suspend fun delete(key: String) {
        deleteFile(DIRECTORY, key).await<JsAny?>()
    }
}

private fun JsAny.toByteArray(): ByteArray {
    val length = byteLength(this)
    val out = ByteArray(length)
    for (i in 0 until length) out[i] = getUint8(this, i).toByte()
    return out
}

private fun ByteArray.toUint8Array(): JsAny {
    val array = newUint8Array(size)
    for (i in indices) setUint8(array, i, this[i].toInt())
    return array
}

private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")
private fun setUint8(array: JsAny, index: Int, value: Int) { js("array[index] = value") }
private fun byteLength(view: JsAny): Int = js("view.length")
private fun getUint8(view: JsAny, index: Int): Int = js("view[index]")

// Each of these opens the directory itself rather than caching a handle.
// Handles are cheap, and a cached one goes stale if the origin's storage is
// cleared underneath the page -- which is exactly the case where a stale
// handle would turn a missing image into a thrown error.

private fun readFile(directory: String, name: String): Promise<JsAny?> = js(
    """
    navigator.storage.getDirectory()
        .then(function (root) { return root.getDirectoryHandle(directory, { create: true }); })
        .then(function (dir) { return dir.getFileHandle(name, { create: false }); })
        .then(function (handle) { return handle.getFile(); })
        .then(function (file) { return file.arrayBuffer(); })
        .then(function (buffer) { return new Uint8Array(buffer); })
        .catch(function () { return null; })
    """
)

private fun writeFile(directory: String, name: String, data: JsAny): Promise<JsAny?> = js(
    """
    navigator.storage.getDirectory()
        .then(function (root) { return root.getDirectoryHandle(directory, { create: true }); })
        .then(function (dir) { return dir.getFileHandle(name, { create: true }); })
        .then(function (handle) { return handle.createWritable(); })
        .then(function (writable) {
            return writable.write(data).then(function () { return writable.close(); });
        })
        .then(function () { return null; })
    """
)

private fun deleteFile(directory: String, name: String): Promise<JsAny?> = js(
    """
    navigator.storage.getDirectory()
        .then(function (root) { return root.getDirectoryHandle(directory, { create: true }); })
        .then(function (dir) { return dir.removeEntry(name); })
        .then(function () { return null; })
        .catch(function () { return null; })
    """
)
