// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui

import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading a chosen file, in a real browser engine.
 *
 * This is the half of the browser's import path that has nothing to do with
 * the user: given a file, produce its text. It is worth a test of its own
 * because it decides between two ways of reading one -- an export of this
 * app's is gzipped and one written by hand is not -- from two bytes, and
 * because getting that wrong looks like an import that finds no cards rather
 * than like an error.
 *
 * A browser test rather than a common one: DecompressionStream is the
 * browser's own, so anywhere else would exercise a different implementation
 * than the one that ships.
 */
class BrowserFilesTest {

    private val export = "#FSR_EXPORT_V3\nQuestion\tAnswer\n"

    @Test
    fun readsPlainText() = runTest {
        assertEquals(export, blobText(plainBlob(export)))
    }

    @Test
    fun readsGzippedText() = runTest {
        assertEquals(export, blobText(gzipped(export).await<JsAny>()))
    }

    /** Text that is not gzipped is not fed to the decompressor. */
    @Test
    fun readsTextThatDoesNotBeginWithTheGzipMarker() = runTest {
        assertEquals("Question\tAnswer", blobText(plainBlob("Question\tAnswer")))
    }

    /** Characters outside ASCII survive the round trip, as UTF-8 either way. */
    @Test
    fun readsGzippedTextWithNonAsciiCharacters() = runTest {
        val text = "Sixte parry — riposte à la tête"
        assertEquals(text, blobText(gzipped(text).await<JsAny>()))
        assertEquals(text, blobText(plainBlob(text)))
    }

    /** A gzip header with nothing behind it: the sniff passes, the inflate does not. */
    @Test
    fun reportsNothingForAFileThatCannotBeRead() = runTest {
        assertNull(blobText(truncatedGzipBlob()))
    }
}

private fun plainBlob(text: String): JsAny = js("new Blob([text])")

private fun gzipped(text: String): Promise<JsAny> = js(
    """
    new Response(new Blob([text]).stream().pipeThrough(new CompressionStream('gzip'))).blob()
    """
)

private fun truncatedGzipBlob(): JsAny = js("new Blob([new Uint8Array([0x1f, 0x8b, 0x08, 0x00])])")
