// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The zip container, checked byte by byte.
 *
 * Worth this much care because nothing here can check it at runtime: an
 * archive with a wrong offset or a wrong CRC is written happily, downloads
 * happily, and fails only when the user double-clicks it somewhere else
 * entirely. The signatures, the CRC and the central directory's offsets are
 * what every unpacker reads first, so those are what these pin down.
 *
 * The CRC values are the published ones for their inputs -- "123456789" is
 * the standard CRC-32 check vector -- rather than numbers this code produced.
 */
class ZipTest {

    @Test
    fun crcMatchesTheStandardCheckVector() {
        assertEquals(0xCBF43926.toInt(), crc32("123456789".encodeToByteArray()))
    }

    @Test
    fun crcOfNothingIsZero() {
        assertEquals(0, crc32(ByteArray(0)))
    }

    @Test
    fun writesTheLocalHeaderSignatureFirst() {
        val archive = zipArchive(listOf(ZipEntry("a.jpg", byteArrayOf(1, 2, 3))))

        // 'P' 'K' 3 4
        assertEquals(listOf(0x50, 0x4b, 0x03, 0x04), archive.take(4).map { it.toInt() and 0xFF })
    }

    @Test
    fun endsWithTheEndOfCentralDirectoryRecord() {
        val archive = zipArchive(listOf(ZipEntry("a.jpg", byteArrayOf(1, 2, 3))))

        // 'P' 'K' 5 6, then eighteen more bytes and no archive comment.
        val end = archive.size - 22
        assertEquals(
            listOf(0x50, 0x4b, 0x05, 0x06),
            archive.slice(end until end + 4).map { it.toInt() and 0xFF }
        )
        assertEquals(0, archive[archive.size - 1].toInt())
        assertEquals(0, archive[archive.size - 2].toInt())
    }

    @Test
    fun countsItsEntriesInTheEndRecord() {
        val archive = zipArchive(
            listOf(
                ZipEntry("a.jpg", byteArrayOf(1)),
                ZipEntry("b.jpg", byteArrayOf(2)),
                ZipEntry("c.jpg", byteArrayOf(3))
            )
        )

        assertEquals(3, archive.le16(archive.size - 22 + 8))  // entries on this disk
        assertEquals(3, archive.le16(archive.size - 22 + 10)) // entries in total
    }

    @Test
    fun pointsTheCentralDirectoryAtTheStartOfTheFirstLocalHeader() {
        val archive = zipArchive(listOf(ZipEntry("a.jpg", byteArrayOf(1, 2, 3))))

        val directorySize = archive.le32(archive.size - 22 + 12)
        val directoryOffset = archive.le32(archive.size - 22 + 16)

        // The first entry starts at nothing, and the directory begins where
        // the entries stop and runs up to the end record.
        assertEquals(0, archive.le32(directoryOffset + 42))
        assertEquals(archive.size - 22, directoryOffset + directorySize)
        assertEquals(
            listOf(0x50, 0x4b, 0x01, 0x02),
            archive.slice(directoryOffset until directoryOffset + 4).map { it.toInt() and 0xFF }
        )
    }

    @Test
    fun storesTheBytesUncompressedAndInOrder() {
        val bytes = byteArrayOf(11, 22, 33, 44)
        val archive = zipArchive(listOf(ZipEntry("a.jpg", bytes)))

        // Local header is thirty bytes plus the name, and the entry is
        // stored, so what follows is the file itself, unchanged.
        val start = 30 + "a.jpg".length
        assertEquals(bytes.toList(), archive.slice(start until start + bytes.size))
        assertEquals(0, archive.le16(8))                  // compression: stored
        assertEquals(bytes.size, archive.le32(18))        // compressed size
        assertEquals(bytes.size, archive.le32(22))        // and the same uncompressed
        assertEquals(crc32(bytes), archive.le32(14))
    }

    @Test
    fun marksNamesAsUtf8() {
        val archive = zipArchive(listOf(ZipEntry("é.jpg", byteArrayOf(1))))

        assertEquals(0x0800, archive.le16(6))
        // The name is longer than its character count, being UTF-8.
        assertEquals("é.jpg".encodeToByteArray().size, archive.le16(26))
    }

    @Test
    fun packsAnEmptyArchive() {
        val archive = zipArchive(emptyList())

        // Nothing but the end record: no entries, an empty directory, and it
        // starting where the file does.
        assertEquals(22, archive.size)
        assertEquals(0, archive.le16(8))  // entries
        assertEquals(0, archive.le32(12)) // directory size
        assertEquals(0, archive.le32(16)) // directory offset
    }

    @Test
    fun keepsEveryEntryFindableWhenThereAreSeveral() {
        val entries = (1..5).map { ZipEntry("photo$it.jpg", ByteArray(it * 10) { b -> b.toByte() }) }

        val archive = zipArchive(entries)

        // Every local header the central directory points at is a local
        // header, which is the property a wrong running offset would break.
        val directoryOffset = archive.le32(archive.size - 22 + 16)
        var at = directoryOffset
        entries.forEach { entry ->
            val localOffset = archive.le32(at + 42)
            assertEquals(
                listOf(0x50, 0x4b, 0x03, 0x04),
                archive.slice(localOffset until localOffset + 4).map { it.toInt() and 0xFF }
            )
            assertEquals(entry.bytes.size, archive.le32(localOffset + 18))
            at += 46 + archive.le16(at + 28)
        }
        // The directory ends exactly where the end record begins.
        assertEquals(archive.size - 22, at)
    }

    private fun ByteArray.le16(at: Int): Int =
        (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.le32(at: Int): Int =
        (this[at].toInt() and 0xFF) or
            ((this[at + 1].toInt() and 0xFF) shl 8) or
            ((this[at + 2].toInt() and 0xFF) shl 16) or
            ((this[at + 3].toInt() and 0xFF) shl 24)
}
