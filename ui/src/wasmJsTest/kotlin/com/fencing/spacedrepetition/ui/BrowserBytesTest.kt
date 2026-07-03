// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui

import com.fencing.spacedrepetition.util.ZipEntry
import com.fencing.spacedrepetition.util.zipArchive
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The photo export's bytes, crossing into the browser and back.
 *
 * The half of the export that has nothing to do with the user: given the
 * archive, hand the browser something it can make a Blob of. Worth its own
 * test for the reason BrowserFilesTest gives -- this is interop, so anywhere
 * but a real engine would exercise a different implementation than the one
 * that ships -- and for one more: Kotlin's Byte is signed and a Uint8Array's
 * elements are not, so every byte above 0x7f is handed over as a negative
 * number and is only correct because the typed array wraps it on assignment.
 *
 * A zip is where that would show. Its CRCs are arbitrary four-byte values and
 * the JPEGs inside it are arbitrary throughout, so a conversion that clamped
 * or dropped the high bit would produce an archive that downloads perfectly
 * and will not open. There is no way to catch that later: nothing in the app
 * reads its own photo export back.
 */
class BrowserBytesTest {

    @Test
    fun everyByteValueCrossesUnchanged() = runTest {
        val bytes = ByteArray(256) { it.toByte() }

        assertEquals(bytes.toList(), roundTrip(bytes).toList())
    }

    /** The high half on its own, which is the half signedness would break. */
    @Test
    fun theBytesAboveSevenBitsArriveAsThemselves() = runTest {
        val bytes = ByteArray(128) { (it + 128).toByte() }

        val back = roundTrip(bytes)

        assertEquals(bytes.toList(), back.toList())
        assertEquals(0xFF, back.last().toInt() and 0xFF)
        assertEquals(0x80, back.first().toInt() and 0xFF)
    }

    @Test
    fun anArchiveArrivesByteForByte() = runTest {
        val archive = zipArchive(
            listOf(
                ZipEntry("cards/Sixte_parry.jpg", ByteArray(300) { (it * 7).toByte() }),
                ZipEntry("reviews/Sixte_parry.jpg", ByteArray(64) { (255 - it).toByte() })
            )
        )

        val back = roundTrip(archive)

        assertEquals(archive.size, back.size)
        assertEquals(archive.toList(), back.toList())
        // Still a zip at the far end: 'P' 'K' 3 4.
        assertEquals(listOf(0x50, 0x4b, 0x03, 0x04), back.take(4).map { it.toInt() and 0xFF })
    }

    @Test
    fun anEmptyArrayCrossesWithoutComplaint() = runTest {
        assertEquals(0, roundTrip(ByteArray(0)).size)
    }

    /**
     * Into a Uint8Array and a Blob -- which is what the download does with it
     * -- then back out through the same reading a chosen file would use.
     */
    private suspend fun roundTrip(bytes: ByteArray): ByteArray {
        val view = blobBytes(bytes.toUint8Array()).await<JsAny>()
        return ByteArray(viewLength(view)) { viewByte(view, it).toByte() }
    }
}

private fun blobBytes(array: JsAny): Promise<JsAny> = js(
    """
    new Response(new Blob([array])).arrayBuffer().then(function (buffer) {
        return new Uint8Array(buffer);
    })
    """
)

private fun viewLength(view: JsAny): Int = js("view.length")
private fun viewByte(view: JsAny, index: Int): Int = js("view[index]")
