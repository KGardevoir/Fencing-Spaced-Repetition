// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Unit tests for V3 format (with base64-encoded images and compression)
 */
class CardImportExportV3Test {

    // Create a simple test image (1x1 red pixel PNG)
    private fun createTestImageBytes(): ByteArray {
        // Minimal valid PNG: 1x1 red pixel
        return byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53.toByte(),
            0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
            0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00, 0x00,
            0x03, 0x01, 0x01, 0x00, 0x18.toByte(), 0xDD.toByte(), 0x8D.toByte(), 0xB4.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
    }

    /**
     * Reads whatever path it is given.
     *
     * Not FileImageReader: that one needs a Context and deliberately refuses
     * paths outside filesDir, and these tests read temp files. What is under
     * test here is encodeImageToBase64's behaviour given a reader -- that it
     * base64s what it gets and returns null when it gets nothing -- which is
     * exactly why the reader is a parameter.
     */
    private object TempFileReader : ImageReader {
        override fun read(path: String): ByteArray? =
            File(path).takeIf { it.exists() }?.readBytes()
    }

    @Test
    fun `test base64 image encoding`() {
        // Create a temporary file
        val tempFile = File.createTempFile("test_image", ".png")
        try {
            tempFile.writeBytes(createTestImageBytes())

            val encoded = CardImportExport.encodeImageToBase64(tempFile.absolutePath, TempFileReader)
            assertNotNull(encoded)
            assertTrue(encoded!!.isNotEmpty())

            // Verify it can be decoded
            val decoded = java.util.Base64.getDecoder().decode(encoded)
            assertArrayEquals(createTestImageBytes(), decoded)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test base64 image encoding - nonexistent file`() {
        val encoded = CardImportExport.encodeImageToBase64("/nonexistent/file.png", TempFileReader)
        assertNull(encoded)
    }

    @Test
    fun `test GZIP compression and decompression`() {
        // Use a long repetitive string so GZIP overhead is outweighed by compression savings
        val originalData = "This is a test string that should be compressed. ".repeat(20)
        val originalBytes = originalData.toByteArray(Charsets.UTF_8)

        // Compress
        val compressedStream = ByteArrayOutputStream()
        val gzipOut = CardImportExport.createCompressedOutputStream(compressedStream)
        gzipOut.write(originalBytes)
        gzipOut.close()

        val compressedBytes = compressedStream.toByteArray()
        assertTrue(compressedBytes.size < originalBytes.size) // Should be smaller

        // Decompress
        val inputStream = ByteArrayInputStream(compressedBytes)
        val gzipIn = CardImportExport.createDecompressedInputStream(inputStream)
        val decompressedBytes = gzipIn.readBytes()
        gzipIn.close()

        assertEquals(originalData, String(decompressedBytes, Charsets.UTF_8))
    }

    @Test
    fun `test export with base64 encoded images`() {
        val tempFile1 = File.createTempFile("test_image1", ".png")
        val tempFile2 = File.createTempFile("test_image2", ".png")

        try {
            tempFile1.writeBytes(createTestImageBytes())
            tempFile2.writeBytes(createTestImageBytes())

            val card = Card(
                id = 1,
                question = "What is this?",
                answer = "A test card",
                imagePaths = listOf(tempFile1.absolutePath, tempFile2.absolutePath),
                algorithm = AlgorithmType.FSRS
            )

            val cardsWithStates = listOf(
                CardWithGroupStates(
                    card = card,
                    groupNames = listOf("TestGroup"),
                    groupSpecificStates = emptyMap()
                )
            )

            val outputStream = ByteArrayOutputStream()
            val gzipOut = CardImportExport.createCompressedOutputStream(outputStream)
            val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, gzipOut)
            gzipOut.close()

            assertTrue(result is ExportResult.Success)
            assertEquals(1, (result as ExportResult.Success).exportedCount)

            // Decompress and verify
            val inputStream = ByteArrayInputStream(outputStream.toByteArray())
            val gzipIn = CardImportExport.createDecompressedInputStream(inputStream)
            val content = gzipIn.readBytes().toString(Charsets.UTF_8)
            gzipIn.close()

            // Should contain base64-encoded image data
            assertTrue(content.contains("#FSR_EXPORT_V3"))
            assertTrue(content.contains("What is this?"))
            assertTrue(content.contains("A test card"))
            // Should have two base64-encoded images separated by ||
            val lines = content.lines().filter { it.isNotBlank() && !it.startsWith("#") }
            assertTrue(lines.isNotEmpty())
            val imagePart = lines[0].split("\t")[2] // Third column is images
            assertTrue(imagePart.contains("||")) // Multiple images
        } finally {
            tempFile1.delete()
            tempFile2.delete()
        }
    }

    @Test
    fun `test round-trip export and import with images and compression`() {
        val tempFile = File.createTempFile("test_image", ".png")

        try {
            tempFile.writeBytes(createTestImageBytes())

            val originalCard = Card(
                id = 1,
                question = "Test Question",
                answer = "Test Answer",
                imagePaths = listOf(tempFile.absolutePath),
                algorithm = AlgorithmType.FSRS,
                fsrsStability = 5.0,
                fsrsDifficulty = 3.5,
                fsrsState = "REVIEW",
                fsrsReps = 3,
                fsrsLapses = 1
            )

            // Export with compression
            val exportStream = ByteArrayOutputStream()
            val gzipOut = CardImportExport.createCompressedOutputStream(exportStream)
            CardImportExport.exportCardsWithGroupStates(
                listOf(
                    CardWithGroupStates(
                        card = originalCard,
                        groupNames = listOf("TestGroup"),
                        groupSpecificStates = emptyMap()
                    )
                ),
                gzipOut
            )
            gzipOut.close()

            // Import with decompression
            val importStream = ByteArrayInputStream(exportStream.toByteArray())
            val gzipIn = CardImportExport.createDecompressedInputStream(importStream)
            val (parsedCards, errors) = CardImportExport.parseCards(gzipIn)
            gzipIn.close()

            assertEquals(0, errors.size)
            assertEquals(1, parsedCards.size)

            val parsed = parsedCards[0]
            assertEquals("Test Question", parsed.concept)
            assertEquals("Test Answer", parsed.answer)
            assertEquals(1, parsed.imageData.size) // Should have one base64-encoded image
            assertNotNull(parsed.imageData[0])
            assertTrue(parsed.imageData[0].isNotEmpty())

            // Verify the image data is valid base64
            val decodedImage = java.util.Base64.getDecoder().decode(parsed.imageData[0])
            assertArrayEquals(createTestImageBytes(), decodedImage)

            // Verify state was preserved
            assertEquals(AlgorithmType.FSRS, parsed.algorithm)
            assertEquals(5.0, parsed.fsrsStability!!, 0.001)
            assertEquals(3.5, parsed.fsrsDifficulty!!, 0.001)
            assertEquals("REVIEW", parsed.fsrsState)
            assertEquals(3, parsed.fsrsReps)
            assertEquals(1, parsed.fsrsLapses)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test export without images`() {
        val card = Card(
            id = 1,
            question = "No images",
            answer = "Just text",
            imagePaths = emptyList(),
            algorithm = AlgorithmType.SM2
        )

        val cardsWithStates = listOf(
            CardWithGroupStates(
                card = card,
                groupNames = listOf("Group1"),
                groupSpecificStates = emptyMap()
            )
        )

        val outputStream = ByteArrayOutputStream()
        val gzipOut = CardImportExport.createCompressedOutputStream(outputStream)
        val result = CardImportExport.exportCardsWithGroupStates(cardsWithStates, gzipOut)
        gzipOut.close()

        assertTrue(result is ExportResult.Success)

        // Decompress and verify
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val gzipIn = CardImportExport.createDecompressedInputStream(inputStream)
        val content = gzipIn.readBytes().toString(Charsets.UTF_8)
        gzipIn.close()

        assertTrue(content.contains("No images"))
        assertTrue(content.contains("Just text"))
        assertTrue(content.contains("SM2"))
    }

    @Test
    fun `test compression reduces file size significantly`() {
        // Create a card with repetitive content that compresses well
        val largeAnswer = "This is a repetitive answer. " * 100
        val card = Card(
            id = 1,
            question = "Large card",
            answer = largeAnswer,
            imagePaths = emptyList(),
            algorithm = AlgorithmType.FSRS
        )

        val cardsWithStates = listOf(
            CardWithGroupStates(
                card = card,
                groupNames = listOf("Group1"),
                groupSpecificStates = emptyMap()
            )
        )

        // Export without compression
        val uncompressedStream = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(cardsWithStates, uncompressedStream)
        val uncompressedSize = uncompressedStream.toByteArray().size

        // Export with compression
        val compressedStream = ByteArrayOutputStream()
        val gzipOut = CardImportExport.createCompressedOutputStream(compressedStream)
        CardImportExport.exportCardsWithGroupStates(cardsWithStates, gzipOut)
        gzipOut.close()
        val compressedSize = compressedStream.toByteArray().size

        // Compression should reduce size significantly
        assertTrue("Compressed size ($compressedSize) should be less than uncompressed ($uncompressedSize)",
            compressedSize < uncompressedSize)
        // Should be at least 50% smaller for this repetitive content
        assertTrue("Compression ratio should be at least 50%",
            compressedSize < uncompressedSize / 2)
    }

    @Test
    fun `test filename generation includes gz extension`() {
        val filename = CardImportExport.generateExportFilename("My Test Group")
        assertEquals("My_Test_Group_cards.tsv.gz", filename)
    }

    private operator fun String.times(n: Int): String {
        return this.repeat(n)
    }
}
