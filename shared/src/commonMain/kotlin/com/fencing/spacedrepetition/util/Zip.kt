// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

/**
 * A zip archive, assembled in common code.
 *
 * Photos leave this app as a zip because that is the one container every
 * desktop opens by double-clicking, and a photo export is something the user
 * takes somewhere else by definition. Neither platform offers one that can be
 * shared: java.util.zip is Android's alone, and a browser has
 * CompressionStream, which is a compressor and not an archive format at all.
 *
 * Entries are *stored*, not deflated, which is what lets this be shared. The
 * rule the rest of the app follows -- see BrowserFiles, where gzip is the
 * browser's own -- is that no compression algorithm gets reimplemented here,
 * and a stored zip needs none: only the container's headers and a CRC. The
 * cost of not deflating is close to nothing, because what goes in is JPEG and
 * PNG, which are already compressed and give up a percent or two at best.
 *
 * Not zip64, so the archive has to stay under 4 GB. A collection of phone
 * photos does not come near that, and a browser assembles the whole file in
 * memory before it can offer it as a download anyway.
 */
class ZipEntry(val name: String, val bytes: ByteArray)

/**
 * Packs [entries] into a zip archive.
 *
 * Names are written UTF-8 with the flag that says so, so a card titled in a
 * non-Latin script survives being unpacked. Duplicate names are the caller's
 * problem: nothing here renames, because only the caller knows which of two
 * identically named photos is which.
 */
fun zipArchive(entries: List<ZipEntry>): ByteArray {
    val chunks = mutableListOf<ByteArray>()
    val central = mutableListOf<ByteArray>()
    var offset = 0

    entries.forEach { entry ->
        val name = entry.name.encodeToByteArray()
        val crc = crc32(entry.bytes)
        val size = entry.bytes.size

        val local = header(LOCAL_SIGNATURE, name, crc, size, localHeader = true, offset = 0)
        chunks.add(local)
        chunks.add(entry.bytes)

        central.add(header(CENTRAL_SIGNATURE, name, crc, size, localHeader = false, offset = offset))
        offset += local.size + size
    }

    val centralSize = central.sumOf { it.size }
    chunks.addAll(central)
    chunks.add(endOfCentralDirectory(entries.size, centralSize, offset))

    val archive = ByteArray(chunks.sumOf { it.size })
    var at = 0
    chunks.forEach { chunk ->
        chunk.copyInto(archive, at)
        at += chunk.size
    }
    return archive
}

/**
 * One entry's header, in either of the two shapes a zip needs it.
 *
 * The local header sits in front of the bytes and the central-directory entry
 * is repeated at the end; they share every field up to the name, which is why
 * they share this. The central one carries four fields the local one has no
 * room for, the last of which is where the local one starts.
 */
private fun header(
    signature: Int,
    name: ByteArray,
    crc: Int,
    size: Int,
    localHeader: Boolean,
    offset: Int
): ByteArray {
    val parts = mutableListOf<ByteArray>()
    parts.add(le32(signature))
    if (!localHeader) parts.add(le16(VERSION))    // version made by
    parts.add(le16(VERSION))                      // version needed
    parts.add(le16(UTF8_NAMES))
    parts.add(le16(STORED))
    parts.add(le16(DOS_TIME))
    parts.add(le16(DOS_DATE))
    parts.add(le32(crc))
    parts.add(le32(size))                         // compressed, and so the same
    parts.add(le32(size))
    parts.add(le16(name.size))
    parts.add(le16(0))                            // no extra field
    if (!localHeader) {
        parts.add(le16(0))                        // no comment
        parts.add(le16(0))                        // first disk
        parts.add(le16(0))                        // not a text file, as far as this says
        parts.add(le32(0))                        // no external attributes
        parts.add(le32(offset))
    }
    parts.add(name)

    val bytes = ByteArray(parts.sumOf { it.size })
    var at = 0
    parts.forEach { part ->
        part.copyInto(bytes, at)
        at += part.size
    }
    return bytes
}

private fun endOfCentralDirectory(count: Int, size: Int, offset: Int): ByteArray {
    val parts = listOf(
        le32(END_SIGNATURE),
        le16(0),        // this disk
        le16(0),        // the disk the central directory starts on
        le16(count),    // entries on this disk
        le16(count),    // entries in total
        le32(size),
        le32(offset),
        le16(0)         // no archive comment
    )
    val bytes = ByteArray(parts.sumOf { it.size })
    var at = 0
    parts.forEach { part ->
        part.copyInto(bytes, at)
        at += part.size
    }
    return bytes
}

private fun le16(value: Int) = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte()
)

private fun le32(value: Int) = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte(),
    ((value ushr 16) and 0xFF).toByte(),
    ((value ushr 24) and 0xFF).toByte()
)

/**
 * CRC-32 of [bytes], which a zip carries for every entry.
 *
 * The one piece of algorithm here, and unavoidable: an archive whose CRCs are
 * wrong is refused by every unpacker, and this is a checksum rather than a
 * compressor, so implementing it breaks no rule that deflate would.
 */
internal fun crc32(bytes: ByteArray): Int {
    var crc = -1
    bytes.forEach { byte ->
        crc = CRC_TABLE[(crc xor byte.toInt()) and 0xFF] xor (crc ushr 8)
    }
    return crc.inv()
}

private val CRC_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) {
        c = if (c and 1 != 0) CRC_POLYNOMIAL xor (c ushr 1) else c ushr 1
    }
    c
}

private const val CRC_POLYNOMIAL = -306674912 // 0xEDB88320, reversed CRC-32

private const val LOCAL_SIGNATURE = 0x04034b50
private const val CENTRAL_SIGNATURE = 0x02014b50
private const val END_SIGNATURE = 0x06054b50

private const val VERSION = 20     // 2.0: what storing and deflating need
private const val STORED = 0
private const val UTF8_NAMES = 0x0800

// Entries carry no timestamp of their own: 1980-01-01, the earliest a zip can
// express, which is what tools that want reproducible archives write. The
// moment the export was taken is on the archive itself, in its filename.
private const val DOS_TIME = 0
private const val DOS_DATE = 0x21
