// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.AlgorithmType
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.Group
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Tests for group settings export/import functionality added to CardImportExport.
 * Covers:
 * - parseGroupSettingsLine
 * - applyGroupSettings
 * - Group settings round-trip through V3 export/import
 * - lastParsedGroupSettings populated during parseCards
 */
class GroupSettingsExportTest {

    // ==================== parseGroupSettingsLine TESTS ====================

    @Test
    fun `parseGroupSettingsLine - full settings line`() {
        val line = "#GROUP_SETTINGS:MyGroup\tcardsPerSession=5\tautoShowAnswer=true\trandomizeDueCards=false\trandomizeBucketHours=72\tpracticeDays=1,3,5\tmaximumInterval=365"

        val result = CardImportExport.parseGroupSettingsLine(line)

        assertNotNull(result)
        val (name, settings) = result!!
        assertEquals("MyGroup", name)
        assertEquals("5", settings["cardsPerSession"])
        assertEquals("true", settings["autoShowAnswer"])
        assertEquals("false", settings["randomizeDueCards"])
        assertEquals("72", settings["randomizeBucketHours"])
        assertEquals("1,3,5", settings["practiceDays"])
        assertEquals("365", settings["maximumInterval"])
    }

    @Test
    fun `parseGroupSettingsLine - partial settings`() {
        val line = "#GROUP_SETTINGS:StudyGroup\tcardsPerSession=3\tmaximumInterval=180"

        val result = CardImportExport.parseGroupSettingsLine(line)

        assertNotNull(result)
        val (name, settings) = result!!
        assertEquals("StudyGroup", name)
        assertEquals(2, settings.size)
        assertEquals("3", settings["cardsPerSession"])
        assertEquals("180", settings["maximumInterval"])
        assertNull(settings["autoShowAnswer"])
    }

    @Test
    fun `parseGroupSettingsLine - group name only, no settings`() {
        val line = "#GROUP_SETTINGS:EmptyGroup"

        val result = CardImportExport.parseGroupSettingsLine(line)

        assertNotNull(result)
        val (name, settings) = result!!
        assertEquals("EmptyGroup", name)
        assertTrue(settings.isEmpty())
    }

    @Test
    fun `parseGroupSettingsLine - non-settings line returns null`() {
        val result = CardImportExport.parseGroupSettingsLine("#FSR_EXPORT_V3")
        assertNull(result)
    }

    @Test
    fun `parseGroupSettingsLine - empty prefix returns null`() {
        val result = CardImportExport.parseGroupSettingsLine("")
        assertNull(result)
    }

    @Test
    fun `parseGroupSettingsLine - malformed key-value pairs are ignored`() {
        val line = "#GROUP_SETTINGS:TestGroup\tnoEquals\tvalid=yes\t=noKey"

        val result = CardImportExport.parseGroupSettingsLine(line)

        assertNotNull(result)
        val (_, settings) = result!!
        // "noEquals" has no = so ignored, "=noKey" has eqIndex==0 so ignored
        assertEquals(1, settings.size)
        assertEquals("yes", settings["valid"])
    }

    @Test
    fun `parseGroupSettingsLine - group name with newline placeholder`() {
        val line = "#GROUP_SETTINGS:Multi<br>Line\tcardsPerSession=2"

        val result = CardImportExport.parseGroupSettingsLine(line)

        assertNotNull(result)
        val (name, settings) = result!!
        assertEquals("Multi\nLine", name)
        assertEquals("2", settings["cardsPerSession"])
    }

    // ==================== applyGroupSettings TESTS ====================

    @Test
    fun `applyGroupSettings - applies all settings`() {
        val group = Group(id = 1, name = "Test")
        val settings = mapOf(
            "cardsPerSession" to "5",
            "autoShowAnswer" to "true",
            "randomizeDueCards" to "false",
            "randomizeBucketHours" to "72",
            "practiceDays" to "1,3,5",
            "maximumInterval" to "365"
        )

        val result = CardImportExport.applyGroupSettings(group, settings)

        assertEquals(5, result.cardsPerSession)
        assertEquals(true, result.autoShowAnswer)
        assertEquals(false, result.randomizeDueCards)
        assertEquals(72, result.randomizeBucketHours)
        assertEquals("1,3,5", result.practiceDays)
        assertEquals(365, result.maximumInterval)
    }

    @Test
    fun `applyGroupSettings - invalid values become null`() {
        val group = Group(id = 1, name = "Test")
        val settings = mapOf(
            "cardsPerSession" to "notANumber",
            "autoShowAnswer" to "maybe",
            "maximumInterval" to "abc"
        )

        val result = CardImportExport.applyGroupSettings(group, settings)

        assertNull(result.cardsPerSession)
        assertNull(result.autoShowAnswer)
        assertNull(result.maximumInterval)
    }

    @Test
    fun `applyGroupSettings - empty map clears overrides`() {
        val group = Group(
            id = 1,
            name = "Test",
            cardsPerSession = 5,
            autoShowAnswer = true,
            maximumInterval = 365
        )

        val result = CardImportExport.applyGroupSettings(group, emptyMap())

        assertNull(result.cardsPerSession)
        assertNull(result.autoShowAnswer)
        assertNull(result.maximumInterval)
    }

    @Test
    fun `applyGroupSettings - preserves non-settings fields`() {
        val group = Group(id = 42, name = "MyGroup", description = "desc", independentLearning = true, created = 12345L)
        val settings = mapOf("cardsPerSession" to "3")

        val result = CardImportExport.applyGroupSettings(group, settings)

        assertEquals(42L, result.id)
        assertEquals("MyGroup", result.name)
        assertEquals("desc", result.description)
        assertEquals(true, result.independentLearning)
        assertEquals(12345L, result.created)
        assertEquals(3, result.cardsPerSession)
    }

    // ==================== Group settings in V3 export/import TESTS ====================

    @Test
    fun `V3 export includes group settings metadata`() {
        val card = Card(
            id = 1, question = "Q1", answer = "A1",
            algorithm = AlgorithmType.FSRS, created = 0, modified = 0
        )

        val groups = listOf(
            Group(id = 1, name = "CustomGroup", cardsPerSession = 5, maximumInterval = 365)
        )

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("CustomGroup"), emptyMap())),
            output,
            groupSettings = groups
        )

        val content = output.toString(Charsets.UTF_8.name())
        assertTrue(content.contains("#GROUP_SETTINGS:CustomGroup"))
        assertTrue(content.contains("cardsPerSession=5"))
        assertTrue(content.contains("maximumInterval=365"))
    }

    @Test
    fun `V3 export excludes groups without custom settings`() {
        val card = Card(
            id = 1, question = "Q1", answer = "A1",
            algorithm = AlgorithmType.FSRS, created = 0, modified = 0
        )

        val groups = listOf(
            Group(id = 1, name = "DefaultGroup"),  // No custom settings
            Group(id = 2, name = "CustomGroup", cardsPerSession = 3)
        )

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("DefaultGroup", "CustomGroup"), emptyMap())),
            output,
            groupSettings = groups
        )

        val content = output.toString(Charsets.UTF_8.name())
        assertFalse(content.contains("#GROUP_SETTINGS:DefaultGroup"))
        assertTrue(content.contains("#GROUP_SETTINGS:CustomGroup"))
    }

    @Test
    fun `parseCards populates lastParsedGroupSettings`() {
        val input = """
            #FSR_EXPORT_V3
            #GROUP_SETTINGS:FencingDrills	cardsPerSession=10	maximumInterval=180
            #GROUP_SETTINGS:Footwork	autoShowAnswer=true	practiceDays=1,3,5
            #Headers...
            Q1	A1		FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0	FencingDrills|Footwork
        """.trimIndent()

        val (cards, errors) = CardImportExport.parseCards(input.byteInputStream())

        assertEquals(1, cards.size)
        assertEquals(0, errors.size)

        val groupSettings = CardImportExport.lastParsedGroupSettings
        assertEquals(2, groupSettings.size)

        val drillsSettings = groupSettings["FencingDrills"]!!
        assertEquals("10", drillsSettings["cardsPerSession"])
        assertEquals("180", drillsSettings["maximumInterval"])

        val footworkSettings = groupSettings["Footwork"]!!
        assertEquals("true", footworkSettings["autoShowAnswer"])
        assertEquals("1,3,5", footworkSettings["practiceDays"])
    }

    @Test
    fun `parseCards clears lastParsedGroupSettings between calls`() {
        // First parse with group settings
        val input1 = """
            #FSR_EXPORT_V3
            #GROUP_SETTINGS:Group1	cardsPerSession=5
            #Headers...
            Q1	A1		FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0	Group1
        """.trimIndent()
        CardImportExport.parseCards(input1.byteInputStream())
        assertEquals(1, CardImportExport.lastParsedGroupSettings.size)

        // Second parse without group settings
        val input2 = """
            #FSR_EXPORT_V3
            #Headers...
            Q2	A2		FSRS	GLOBAL	0	0	0	0	NEW	0	0	0	0	2.5	0	0
        """.trimIndent()
        CardImportExport.parseCards(input2.byteInputStream())
        assertEquals(0, CardImportExport.lastParsedGroupSettings.size)
    }

    @Test
    fun `V3 export and import round-trip preserves group settings`() {
        val card = Card(
            id = 1, question = "Test Card", answer = "Test Answer",
            algorithm = AlgorithmType.FSRS,
            fsrsStability = 5.0, fsrsDifficulty = 3.0, fsrsState = "REVIEW",
            fsrsReps = 4, fsrsLapses = 1,
            created = 0, modified = 0
        )

        val groups = listOf(
            Group(
                id = 1, name = "Epee Drills",
                cardsPerSession = 8,
                autoShowAnswer = false,
                randomizeDueCards = true,
                randomizeBucketHours = 168,
                practiceDays = "1,2,3,4,5",
                maximumInterval = 365
            )
        )

        // Export
        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("Epee Drills"), emptyMap())),
            output,
            groupSettings = groups
        )

        // Import
        val (parsedCards, errors) = CardImportExport.parseCards(
            ByteArrayInputStream(output.toByteArray())
        )

        assertEquals(0, errors.size)
        assertEquals(1, parsedCards.size)

        // Verify group settings were captured
        val settings = CardImportExport.lastParsedGroupSettings
        assertEquals(1, settings.size)
        val drillSettings = settings["Epee Drills"]!!
        assertEquals("8", drillSettings["cardsPerSession"])
        assertEquals("false", drillSettings["autoShowAnswer"])
        assertEquals("true", drillSettings["randomizeDueCards"])
        assertEquals("168", drillSettings["randomizeBucketHours"])
        assertEquals("1,2,3,4,5", drillSettings["practiceDays"])
        assertEquals("365", drillSettings["maximumInterval"])

        // Apply and verify
        val baseGroup = Group(id = 1, name = "Epee Drills")
        val applied = CardImportExport.applyGroupSettings(baseGroup, drillSettings)
        assertEquals(8, applied.cardsPerSession)
        assertEquals(false, applied.autoShowAnswer)
        assertEquals(true, applied.randomizeDueCards)
        assertEquals(168, applied.randomizeBucketHours)
        assertEquals("1,2,3,4,5", applied.practiceDays)
        assertEquals(365, applied.maximumInterval)
    }

    @Test
    fun `V3 export with group-specific states and settings round-trip`() {
        val card = Card(
            id = 1, question = "Parry-Riposte", answer = "4-6",
            algorithm = AlgorithmType.FSRS,
            fsrsStability = 2.0, fsrsState = "LEARNING",
            created = 0, modified = 0
        )

        val groupState = CardGroupLearningState(
            cardId = 1, groupId = 1,
            fsrsStability = 8.0, fsrsDifficulty = 0.3,
            fsrsState = "REVIEW", fsrsReps = 5
        )

        val groups = listOf(
            Group(id = 1, name = "Advanced", independentLearning = true, cardsPerSession = 10)
        )

        // Export with both group states and group settings
        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("Advanced"), mapOf("Advanced" to groupState))),
            output,
            groupSettings = groups
        )

        // Import
        val (parsedCards, errors) = CardImportExport.parseCards(
            ByteArrayInputStream(output.toByteArray())
        )

        assertEquals(0, errors.size)
        assertEquals(2, parsedCards.size)  // 1 global + 1 group-specific

        // Verify global state
        val global = parsedCards.find { it.isGlobalState }!!
        assertEquals("Parry-Riposte", global.concept)
        assertEquals(2.0, global.fsrsStability!!, 0.001)

        // Verify group-specific state
        val groupRow = parsedCards.find { it.isGroupSpecificState }!!
        assertEquals("Advanced", groupRow.stateContext)
        assertEquals(8.0, groupRow.fsrsStability!!, 0.001)
        assertEquals(5, groupRow.fsrsReps)

        // Verify group settings
        val settings = CardImportExport.lastParsedGroupSettings
        assertEquals("10", settings["Advanced"]?.get("cardsPerSession"))
    }

    // ==================== Retention settings export/import TESTS ====================

    @Test
    fun `parseGroupSettingsLine - parses fsrsRetention and sm2IntervalModifier`() {
        val line = "#GROUP_SETTINGS:RetentionGroup\tfsrsRetention=85\tsm2IntervalModifier=75"

        val result = CardImportExport.parseGroupSettingsLine(line)

        assertNotNull(result)
        val (name, settings) = result!!
        assertEquals("RetentionGroup", name)
        assertEquals("85", settings["fsrsRetention"])
        assertEquals("75", settings["sm2IntervalModifier"])
    }

    @Test
    fun `parseGroupSettingsLine - parses retention alongside other settings`() {
        val line = "#GROUP_SETTINGS:MyGroup\tcardsPerSession=5\tmaximumInterval=365\tfsrsRetention=90\tsm2IntervalModifier=100"

        val result = CardImportExport.parseGroupSettingsLine(line)!!
        val (_, settings) = result

        assertEquals("5",   settings["cardsPerSession"])
        assertEquals("365", settings["maximumInterval"])
        assertEquals("90",  settings["fsrsRetention"])
        assertEquals("100", settings["sm2IntervalModifier"])
    }

    @Test
    fun `applyGroupSettings - applies fsrsRetention and sm2IntervalModifier`() {
        val group = Group(id = 1, name = "Test")
        val settings = mapOf(
            "fsrsRetention" to "85",
            "sm2IntervalModifier" to "75"
        )

        val result = CardImportExport.applyGroupSettings(group, settings)

        assertEquals(85, result.fsrsRetention)
        assertEquals(75, result.sm2IntervalModifier)
    }

    @Test
    fun `applyGroupSettings - invalid retention values become null`() {
        val group = Group(id = 1, name = "Test")
        val settings = mapOf(
            "fsrsRetention" to "notANumber",
            "sm2IntervalModifier" to "abc"
        )

        val result = CardImportExport.applyGroupSettings(group, settings)

        assertNull(result.fsrsRetention)
        assertNull(result.sm2IntervalModifier)
    }

    @Test
    fun `applyGroupSettings - empty map clears retention overrides`() {
        val group = Group(id = 1, name = "Test", fsrsRetention = 85, sm2IntervalModifier = 75)

        val result = CardImportExport.applyGroupSettings(group, emptyMap())

        assertNull(result.fsrsRetention)
        assertNull(result.sm2IntervalModifier)
    }

    @Test
    fun `V3 export includes fsrsRetention and sm2IntervalModifier in group settings line`() {
        val card = Card(
            id = 1, question = "Q1", answer = "A1",
            algorithm = AlgorithmType.FSRS,
            created = 0, modified = 0
        )
        val group = Group(id = 1, name = "RetentionGroup", fsrsRetention = 85, sm2IntervalModifier = 75)

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("RetentionGroup"))),
            output,
            groupSettings = listOf(group)
        )

        val content = output.toString(Charsets.UTF_8.name())
        assertTrue(content.contains("#GROUP_SETTINGS:RetentionGroup"))
        assertTrue(content.contains("fsrsRetention=85"))
        assertTrue(content.contains("sm2IntervalModifier=75"))
    }

    @Test
    fun `V3 export omits retention fields when they are null`() {
        val card = Card(
            id = 1, question = "Q1", answer = "A1",
            algorithm = AlgorithmType.FSRS,
            created = 0, modified = 0
        )
        // Group has cardsPerSession set (so the settings line is emitted) but no retention
        val group = Group(id = 1, name = "NoRetentionGroup", cardsPerSession = 5)

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("NoRetentionGroup"))),
            output,
            groupSettings = listOf(group)
        )

        val content = output.toString(Charsets.UTF_8.name())
        assertTrue(content.contains("#GROUP_SETTINGS:NoRetentionGroup"))
        assertFalse(content.contains("fsrsRetention"))
        assertFalse(content.contains("sm2IntervalModifier"))
    }

    @Test
    fun `retention settings round-trip through V3 export and import`() {
        val card = Card(
            id = 1, question = "Fleche", answer = "Running attack",
            algorithm = AlgorithmType.FSRS,
            created = 0, modified = 0
        )
        val group = Group(
            id = 1, name = "FencingDrills",
            fsrsRetention = 85,
            sm2IntervalModifier = 75,
            cardsPerSession = 4
        )

        // Export
        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("FencingDrills"))),
            output,
            groupSettings = listOf(group)
        )

        // Import
        CardImportExport.parseCards(ByteArrayInputStream(output.toByteArray()))
        val parsedSettings = CardImportExport.lastParsedGroupSettings

        assertNotNull(parsedSettings["FencingDrills"])
        val drillSettings = parsedSettings["FencingDrills"]!!
        assertEquals("85", drillSettings["fsrsRetention"])
        assertEquals("75", drillSettings["sm2IntervalModifier"])
        assertEquals("4",  drillSettings["cardsPerSession"])

        // Apply settings to a blank group and verify
        val baseGroup = Group(id = 1, name = "FencingDrills")
        val applied = CardImportExport.applyGroupSettings(baseGroup, drillSettings)
        assertEquals(85, applied.fsrsRetention)
        assertEquals(75, applied.sm2IntervalModifier)
        assertEquals(4,  applied.cardsPerSession)
    }
}
