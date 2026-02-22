package com.fencing.spacedrepetition.util

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Tests for CardImportExport.smartInputStream auto-detection of GZIP-compressed streams.
 */
class SmartInputStreamTest {

    @Test
    fun `smartInputStream - detects GZIP compressed data`() {
        val original = "Hello, compressed world!"

        // Compress the data
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { it.write(original.toByteArray()) }

        // smartInputStream should auto-detect and decompress
        val input = ByteArrayInputStream(compressed.toByteArray())
        val smart = CardImportExport.smartInputStream(input)
        val result = smart.bufferedReader().readText()

        assertEquals(original, result)
    }

    @Test
    fun `smartInputStream - passes through plain text data`() {
        val original = "Hello, plain world!"

        val input = ByteArrayInputStream(original.toByteArray())
        val smart = CardImportExport.smartInputStream(input)
        val result = smart.bufferedReader().readText()

        assertEquals(original, result)
    }

    @Test
    fun `smartInputStream - handles multi-line GZIP data`() {
        val original = "#FSR_EXPORT_V3\nQ1\tA1\n\nQ2\tA2\n"

        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { it.write(original.toByteArray()) }

        val input = ByteArrayInputStream(compressed.toByteArray())
        val smart = CardImportExport.smartInputStream(input)
        val result = smart.bufferedReader().readText()

        assertEquals(original, result)
    }

    @Test
    fun `smartInputStream - handles multi-line plain text`() {
        val original = "#FSR_EXPORT_V3\nQ1\tA1\nQ2\tA2\n"

        val input = ByteArrayInputStream(original.toByteArray())
        val smart = CardImportExport.smartInputStream(input)
        val result = smart.bufferedReader().readText()

        assertEquals(original, result)
    }

    @Test
    fun `smartInputStream - roundtrip with parseCards compressed`() {
        val exportData = "#FSR_EXPORT_V3\n#Headers\nQ1\tA1\t\tFSRS\tGLOBAL\t0\t0\t0\t0\tNEW\t0\t0\t0\t0\t2.5\t0\t0\tGroup1\n"

        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { it.write(exportData.toByteArray()) }

        val smart = CardImportExport.smartInputStream(ByteArrayInputStream(compressed.toByteArray()))
        val (cards, errors) = CardImportExport.parseCards(smart)

        assertEquals(0, errors.size)
        assertEquals(1, cards.size)
        assertEquals("Q1", cards[0].concept)
        assertEquals("A1", cards[0].answer)
    }

    @Test
    fun `smartInputStream - roundtrip with parseCards uncompressed`() {
        val exportData = "#FSR_EXPORT_V3\n#Headers\nQ1\tA1\t\tFSRS\tGLOBAL\t0\t0\t0\t0\tNEW\t0\t0\t0\t0\t2.5\t0\t0\tGroup1\n"

        val smart = CardImportExport.smartInputStream(ByteArrayInputStream(exportData.toByteArray()))
        val (cards, errors) = CardImportExport.parseCards(smart)

        assertEquals(0, errors.size)
        assertEquals(1, cards.size)
        assertEquals("Q1", cards[0].concept)
    }

    @Test
    fun `smartInputStream - empty GZIP stream`() {
        val compressed = ByteArrayOutputStream()
        GZIPOutputStream(compressed).use { /* empty */ }

        val smart = CardImportExport.smartInputStream(ByteArrayInputStream(compressed.toByteArray()))
        val result = smart.bufferedReader().readText()

        assertEquals("", result)
    }
}
