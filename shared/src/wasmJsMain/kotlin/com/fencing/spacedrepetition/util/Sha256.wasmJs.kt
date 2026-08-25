// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * crypto.subtle.digest, which is asynchronous and returns an ArrayBuffer.
 *
 * The hex conversion happens in JavaScript rather than by copying the buffer
 * into a Kotlin ByteArray first: the result is a short string either way, and
 * moving a few megabytes across the boundary to produce 64 characters would be
 * the expensive half of the operation.
 *
 * crypto.subtle is only defined in a secure context. Localhost and https
 * qualify; plain http on a LAN address does not, and there this throws --
 * which is correct, because without it images cannot be keyed at all.
 */
actual suspend fun sha256Hex(bytes: ByteArray): String =
    sha256HexJs(bytes.toUint8Array()).await<JsString>().toString()

private fun ByteArray.toUint8Array(): JsAny {
    val array = newUint8Array(size)
    for (i in indices) setUint8(array, i, this[i].toInt())
    return array
}

private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

private fun setUint8(array: JsAny, index: Int, value: Int) { js("array[index] = value") }

private fun sha256HexJs(data: JsAny): Promise<JsString> = js(
    """
    crypto.subtle.digest('SHA-256', data).then(function (buffer) {
        var view = new Uint8Array(buffer);
        var out = '';
        for (var i = 0; i < view.length; i++) {
            out += view[i].toString(16).padStart(2, '0');
        }
        return out;
    })
    """
)
