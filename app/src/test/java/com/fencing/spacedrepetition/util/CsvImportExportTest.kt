// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Unit tests for CSV import/export functionality.
 */
class CsvImportExportTest {

    // ==================== CSV PARSING TESTS ====================

    @Test
    fun `parseCsvLines - simple two-column CSV`() {
        val csv = "Concept,Description\nParry 4,Blade defense to inside line\n"
        val rows = CardImportExport.parseCsvLines(csv)

        assertEquals(2, rows.size)
        assertEquals(listOf("Concept", "Description"), rows[0])
        assertEquals(listOf("Parry 4", "Blade defense to inside line"), rows[1])
    }

    @Test
    fun `parseCsvLines - quoted fields with commas`() {
        val csv = "\"Hello, World\",Description\n"
        val rows = CardImportExport.parseCsvLines(csv)

        assertEquals(1, rows.size)
        assertEquals("Hello, World", rows[0][0])
        assertEquals("Description", rows[0][1])
    }

    @Test
    fun `parseCsvLines - quoted fields with newlines`() {
        val csv = "\"Line1\nLine2\",Description\n"
        val rows = CardImportExport.parseCsvLines(csv)

        assertEquals(1, rows.size)
        assertEquals("Line1\nLine2", rows[0][0])
        assertEquals("Description", rows[0][1])
    }

    @Test
    fun `parseCsvLines - escaped double quotes`() {
        val csv = "\"He said \"\"hello\"\"\",Value\n"
        val rows = CardImportExport.parseCsvLines(csv)

        assertEquals(1, rows.size)
        assertEquals("He said \"hello\"", rows[0][0])
        assertEquals("Value", rows[0][1])
    }

    @Test
    fun `parseCsvLines - empty fields`() {
        val csv = "Concept,,\nValue1,Value2,Value3\n"
        val rows = CardImportExport.parseCsvLines(csv)

        assertEquals(2, rows.size)
        assertEquals(listOf("Concept", "", ""), rows[0])
        assertEquals(listOf("Value1", "Value2", "Value3"), rows[1])
    }

    @Test
    fun `parseCsvLines - CRLF line endings`() {
        val csv = "A,B\r\nC,D\r\n"
        val rows = CardImportExport.parseCsvLines(csv)

        assertEquals(2, rows.size)
        assertEquals(listOf("A", "B"), rows[0])
        assertEquals(listOf("C", "D"), rows[1])
    }

    @Test
    fun `parseCsvLines - no trailing newline`() {
        val csv = "A,B\nC,D"
        val rows = CardImportExport.parseCsvLines(csv)

        assertEquals(2, rows.size)
        assertEquals(listOf("A", "B"), rows[0])
        assertEquals(listOf("C", "D"), rows[1])
    }

    @Test
    fun `parseCsvLines - three columns with pipe-separated images`() {
        val csv = "Concept,Description,Images\nTest,Desc,abc|def|ghi\n"
        val rows = CardImportExport.parseCsvLines(csv)

        assertEquals(2, rows.size)
        assertEquals(3, rows[0].size)
        assertEquals(3, rows[1].size)
        assertEquals("abc|def|ghi", rows[1][2])
    }

    // ==================== CSV CARD PARSING TESTS ====================

    @Test
    fun `parseCsvCards - with header row`() {
        val csv = "Concept,Description\nParry 4,Blade defense\nRiposte,Attack after parry\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(0, errors.size)
        assertEquals("Parry 4", cards[0].concept)
        assertEquals("Blade defense", cards[0].answer)
        assertEquals("Riposte", cards[1].concept)
        assertEquals("Attack after parry", cards[1].answer)
    }

    @Test
    fun `parseCsvCards - without header row is rejected`() {
        val csv = "Parry 4,Blade defense\nRiposte,Attack after parry\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        // Files without a 'Concept' header row are now rejected
        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Concept"))
    }

    @Test
    fun `parseCsvCards - rejects file whose first column header is not Concept`() {
        val csv = "Name,Value\nSword,A weapon\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Concept"))
    }

    @Test
    fun `parseCsvCards - with pipe-separated images`() {
        val csv = "Concept,Description,Images\nTest,Desc,abc123|def456\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)
        assertEquals("Test", cards[0].concept)
        assertEquals("Desc", cards[0].answer)
        assertEquals(2, cards[0].imageData.size)
        assertEquals("abc123", cards[0].imageData[0])
        assertEquals("def456", cards[0].imageData[1])
    }

    @Test
    fun `parseCsvCards - single image in images column`() {
        val csv = "Concept,Description,Images\nTest,Desc,abc123\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)
        assertEquals(1, cards[0].imageData.size)
        assertEquals("abc123", cards[0].imageData[0])
    }

    @Test
    fun `parseCsvCards - empty images column`() {
        val csv = "Concept,Description,Images\nTest,Desc,\nTest2,Desc2,\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(0, cards[0].imageData.size)
        assertEquals(0, cards[1].imageData.size)
    }

    @Test
    fun `parseCsvCards - empty file`() {
        val csv = ""
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun `parseCsvCards - header only`() {
        val csv = "Concept,Description\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(0, errors.size)
    }

    @Test
    fun `parseCsvCards - missing description column`() {
        val csv = "Concept,Description\nJust a concept\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Missing description"))
    }

    @Test
    fun `parseCsvCards - empty concept`() {
        val csv = "Concept,Description\n,Some description\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(0, cards.size)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Empty concept"))
    }

    @Test
    fun `parseCsvCards - quoted fields with commas in content`() {
        val csv = "Concept,Description\n\"Parry 4, 6, 7\",\"Defense to inside, outside, and high line\"\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)
        assertEquals("Parry 4, 6, 7", cards[0].concept)
        assertEquals("Defense to inside, outside, and high line", cards[0].answer)
    }

    @Test
    fun `parseCsvCards - multiline description`() {
        val csv = "Concept,Description\n\"Attack\",\"Step 1: Extend arm\nStep 2: Lunge forward\"\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)
        assertEquals("Attack", cards[0].concept)
        assertTrue(cards[0].answer.contains("\n"))
        assertEquals("Step 1: Extend arm\nStep 2: Lunge forward", cards[0].answer)
    }

    @Test
    fun `parseCsvCards - cards have no full state`() {
        val csv = "Concept,Description\nTest,Answer\n"
        val (cards, _) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(1, cards.size)
        assertFalse(cards[0].hasFullState)
        assertNull(cards[0].algorithm)
    }

    @Test
    fun `parseCsvCards - skip blank lines`() {
        val csv = "Concept,Description\nTest1,Answer1\n\n\nTest2,Answer2\n"
        val (cards, errors) = CardImportExport.parseCsvCards(csv.byteInputStream())

        assertEquals(2, cards.size)
        assertEquals(0, errors.size)
    }

    // ==================== CSV EXPORT TESTS ====================

    @Test
    fun `exportCardsToCsv - basic export`() {
        val card = Card(
            id = 1,
            question = "Parry 4",
            answer = "Blade defense to inside line",
            imagePaths = emptyList(),
            algorithm = AlgorithmType.FSRS
        )

        val cardsWithGroups = listOf(CardWithGroupNames(card, listOf("Foil")))
        val outputStream = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsToCsv(cardsWithGroups, outputStream, images = NoImages)

        assertTrue(result is ExportResult.Success)
        assertEquals(1, (result as ExportResult.Success).exportedCount)

        val content = outputStream.toString(Charsets.UTF_8.name())
        val lines = content.trim().lines()
        assertEquals(2, lines.size) // header + 1 data row

        assertEquals("Concept,Description", lines[0])
        assertEquals("Parry 4,Blade defense to inside line", lines[1])
    }

    @Test
    fun `exportCardsToCsv - with commas in content`() {
        val card = Card(
            id = 1,
            question = "Parry 4, 6, 7",
            answer = "Defense to inside, outside, and high line",
            imagePaths = emptyList(),
            algorithm = AlgorithmType.FSRS
        )

        val cardsWithGroups = listOf(CardWithGroupNames(card, emptyList()))
        val outputStream = ByteArrayOutputStream()
        CardImportExport.exportCardsToCsv(cardsWithGroups, outputStream, images = NoImages)

        val content = outputStream.toString(Charsets.UTF_8.name())
        val lines = content.trim().lines()
        // Fields with commas should be quoted
        assertTrue(lines[1].contains("\"Parry 4, 6, 7\""))
        assertTrue(lines[1].contains("\"Defense to inside, outside, and high line\""))
    }

    @Test
    fun `exportCardsToCsv - with newlines in content`() {
        val card = Card(
            id = 1,
            question = "Attack",
            answer = "Step 1: Extend\nStep 2: Lunge",
            imagePaths = emptyList(),
            algorithm = AlgorithmType.FSRS
        )

        val cardsWithGroups = listOf(CardWithGroupNames(card, emptyList()))
        val outputStream = ByteArrayOutputStream()
        CardImportExport.exportCardsToCsv(cardsWithGroups, outputStream, images = NoImages)

        val content = outputStream.toString(Charsets.UTF_8.name())
        // Should contain quoted field with newline
        assertTrue(content.contains("\"Step 1: Extend\nStep 2: Lunge\""))
    }

    @Test
    fun `exportCardsToCsv - no images omits Images column`() {
        val card = Card(
            id = 1,
            question = "Test",
            answer = "Desc",
            imagePaths = emptyList(),
            algorithm = AlgorithmType.FSRS
        )

        val cardsWithGroups = listOf(CardWithGroupNames(card, emptyList()))
        val outputStream = ByteArrayOutputStream()
        CardImportExport.exportCardsToCsv(cardsWithGroups, outputStream, images = NoImages)

        val content = outputStream.toString(Charsets.UTF_8.name())
        val header = content.lines().first()
        assertEquals("Concept,Description", header)
        assertFalse(header.contains("Images"))
    }

    // Note: image encoding in CSV export is covered by the V3 tests, which
    // share the same encodeImageToBase64. The other half -- storing what an
    // import decodes -- is ImportExportImagesTest in :shared, where it can be
    // run against a store on every platform.

    @Test
    fun `exportCardsToCsv - empty card list`() {
        val outputStream = ByteArrayOutputStream()
        val result = CardImportExport.exportCardsToCsv(emptyList(), outputStream, images = NoImages)

        assertTrue(result is ExportResult.Success)
        assertEquals(0, (result as ExportResult.Success).exportedCount)
    }

    // ==================== ROUND TRIP TESTS ====================

    @Test
    fun `round trip - basic export then import`() {
        val card1 = Card(
            id = 1, question = "Parry 4", answer = "Blade defense",
            imagePaths = emptyList(), algorithm = AlgorithmType.FSRS
        )
        val card2 = Card(
            id = 2, question = "Riposte", answer = "Attack after parry",
            imagePaths = emptyList(), algorithm = AlgorithmType.FSRS
        )

        // Export
        val cardsWithGroups = listOf(
            CardWithGroupNames(card1, emptyList()),
            CardWithGroupNames(card2, emptyList())
        )
        val exportStream = ByteArrayOutputStream()
        CardImportExport.exportCardsToCsv(cardsWithGroups, exportStream, images = NoImages)

        // Import
        val importStream = ByteArrayInputStream(exportStream.toByteArray())
        val (parsedCards, errors) = CardImportExport.parseCsvCards(importStream)

        assertEquals(0, errors.size)
        assertEquals(2, parsedCards.size)
        assertEquals("Parry 4", parsedCards[0].concept)
        assertEquals("Blade defense", parsedCards[0].answer)
        assertEquals("Riposte", parsedCards[1].concept)
        assertEquals("Attack after parry", parsedCards[1].answer)
    }

    @Test
    fun `round trip - with commas and newlines`() {
        val card = Card(
            id = 1,
            question = "Complex, question",
            answer = "Line 1\nLine 2\nLine 3",
            imagePaths = emptyList(),
            algorithm = AlgorithmType.FSRS
        )

        val exportStream = ByteArrayOutputStream()
        CardImportExport.exportCardsToCsv(
            listOf(CardWithGroupNames(card, emptyList())),
            exportStream,
            images = NoImages
        )

        val importStream = ByteArrayInputStream(exportStream.toByteArray())
        val (parsedCards, errors) = CardImportExport.parseCsvCards(importStream)

        assertEquals(0, errors.size)
        assertEquals(1, parsedCards.size)
        assertEquals("Complex, question", parsedCards[0].concept)
        assertEquals("Line 1\nLine 2\nLine 3", parsedCards[0].answer)
    }

    @Test
    fun `round trip - with quotes in content`() {
        val card = Card(
            id = 1,
            question = "He said \"hello\"",
            answer = "She replied \"goodbye\"",
            imagePaths = emptyList(),
            algorithm = AlgorithmType.FSRS
        )

        val exportStream = ByteArrayOutputStream()
        CardImportExport.exportCardsToCsv(
            listOf(CardWithGroupNames(card, emptyList())),
            exportStream,
            images = NoImages
        )

        val importStream = ByteArrayInputStream(exportStream.toByteArray())
        val (parsedCards, errors) = CardImportExport.parseCsvCards(importStream)

        assertEquals(0, errors.size)
        assertEquals(1, parsedCards.size)
        assertEquals("He said \"hello\"", parsedCards[0].concept)
        assertEquals("She replied \"goodbye\"", parsedCards[0].answer)
    }

    // ==================== ESCAPE/UNESCAPE TESTS ====================

    @Test
    fun `escapeCsvField - no special characters`() {
        assertEquals("hello", CardImportExport.escapeCsvField("hello"))
    }

    @Test
    fun `escapeCsvField - with comma`() {
        assertEquals("\"hello, world\"", CardImportExport.escapeCsvField("hello, world"))
    }

    @Test
    fun `escapeCsvField - with double quote`() {
        assertEquals("\"he said \"\"hi\"\"\"", CardImportExport.escapeCsvField("he said \"hi\""))
    }

    @Test
    fun `escapeCsvField - with newline`() {
        assertEquals("\"line1\nline2\"", CardImportExport.escapeCsvField("line1\nline2"))
    }

    @Test
    fun `escapeCsvField - empty string`() {
        assertEquals("", CardImportExport.escapeCsvField(""))
    }

    // ==================== FILENAME DERIVATION TESTS ====================

    @Test
    fun `deriveGroupNameFromFilename - simple csv filename`() {
        assertEquals("Parries", CardImportExport.deriveGroupNameFromFilename("parries.csv"))
    }

    @Test
    fun `deriveGroupNameFromFilename - with underscores`() {
        assertEquals("My Techniques", CardImportExport.deriveGroupNameFromFilename("my_techniques.csv"))
    }

    @Test
    fun `deriveGroupNameFromFilename - with hyphens`() {
        assertEquals("Foil Attacks", CardImportExport.deriveGroupNameFromFilename("foil-attacks.csv"))
    }

    @Test
    fun `deriveGroupNameFromFilename - with _cards suffix`() {
        assertEquals("Parries", CardImportExport.deriveGroupNameFromFilename("parries_cards.csv"))
    }

    @Test
    fun `deriveGroupNameFromFilename - already capitalized`() {
        assertEquals("MyGroup", CardImportExport.deriveGroupNameFromFilename("MyGroup.csv"))
    }

    @Test
    fun `deriveGroupNameFromFilename - txt extension`() {
        assertEquals("Techniques", CardImportExport.deriveGroupNameFromFilename("techniques.txt"))
    }

    @Test
    fun `deriveGroupNameFromFilename - multiple spaces collapsed`() {
        assertEquals("My Group", CardImportExport.deriveGroupNameFromFilename("my__group.csv"))
    }

    // ==================== CSV EXPORT FILENAME TESTS ====================

    @Test
    fun `generateCsvExportFilename - basic`() {
        assertEquals("My_Group_cards.csv", CardImportExport.generateCsvExportFilename("My Group"))
    }

    @Test
    fun `generateCsvExportFilename - special characters`() {
        assertEquals("Test_Group_cards.csv", CardImportExport.generateCsvExportFilename("Test/Group"))
    }

    /**
     * Reads no images.
     *
     * These tests export cards that have none, and the real reader needs a
     * Context and confines itself to filesDir. Passed explicitly rather than
     * defaulted: a default that quietly reads nothing is how images would go
     * missing from a real export without anyone noticing.
     */
    private val NoImages = ImageReader { null }
}
