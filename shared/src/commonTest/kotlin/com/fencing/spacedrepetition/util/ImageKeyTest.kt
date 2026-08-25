// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Runs on every target, which is the entire point.
 *
 * Android hashes with java.security.MessageDigest and a browser hashes with
 * crypto.subtle, and the keys they produce have to be the same string -- an
 * export written on a phone is imported in a browser, and if the two disagreed
 * the same picture would be stored twice and neither side would ever notice.
 * So these assert against published SHA-256 vectors rather than against each
 * other: two implementations can agree and still both be wrong.
 */
class ImageKeyTest {

    @Test
    fun matchesTheKnownVectorForEmptyInput() = runTest {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0))
        )
    }

    @Test
    fun matchesTheKnownVectorForAbc() = runTest {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex(byteArrayOf(0x61, 0x62, 0x63))
        )
    }

    /**
     * The bytes either side of Kotlin's signed-byte boundary. A digest fed
     * through a JavaScript array is the place a sign error hides: 0x80 as -128
     * rather than 128 hashes to something entirely different, and only bytes
     * above 0x7f would be affected -- so text-like input would pass.
     */
    @Test
    fun handlesBytesAboveTheSignBoundary() = runTest {
        assertEquals(
            "0150a92bb1212cd00516b65fde0704614760000963874fcbb11eaa734ee87809",
            sha256Hex(byteArrayOf(0, 1, 127, -128, -1))
        )
    }

    @Test
    fun theKeyIsTheHashPlusTheExtension() = runTest {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad.jpg",
            contentKey(byteArrayOf(0x61, 0x62, 0x63))
        )
    }

    @Test
    fun theExtensionIsNormalised() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        assertEquals(contentKey(bytes, "png"), contentKey(bytes, ".PNG"))
        assertTrue(contentKey(bytes, ".PNG").endsWith(".png"))
    }

    @Test
    fun differentBytesGetDifferentKeys() = runTest {
        assertNotEquals(contentKey(byteArrayOf(1, 2, 3)), contentKey(byteArrayOf(1, 2, 4)))
    }

    /** The dedup property, stated as a test rather than left implied. */
    @Test
    fun theSameBytesAlwaysGetTheSameKey() = runTest {
        assertEquals(contentKey(byteArrayOf(9, 8, 7)), contentKey(byteArrayOf(9, 8, 7)))
    }
}
