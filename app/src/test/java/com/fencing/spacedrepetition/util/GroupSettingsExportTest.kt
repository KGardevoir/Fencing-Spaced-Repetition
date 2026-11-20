// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
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

        assertEquals(true, result.autoShowAnswer)

        // The rest of the same map is for the learning group.
        val learning = CardImportExport.applyLearningGroupSettings(LearningGroup(id = 1, name = "Test"), settings)
        assertEquals(5, learning.cardsPerSession)
        assertEquals(false, learning.randomizeDueCards)
        assertEquals(72, learning.randomizeBucketHours)
        assertEquals("1,3,5", learning.practiceDays)
        assertEquals(365, learning.maximumInterval)
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

        assertNull(result.autoShowAnswer)
        val learning = CardImportExport.applyLearningGroupSettings(LearningGroup(id = 1, name = "Test"), settings)
        assertNull(learning.cardsPerSession)
        assertNull(learning.maximumInterval)
    }

    @Test
    fun `applyGroupSettings - empty map clears overrides`() {
        val group = Group(
            id = 1,
            name = "Test",
            autoShowAnswer = true
        )

        val result = CardImportExport.applyGroupSettings(group, emptyMap())

        assertNull(result.autoShowAnswer)
    }

    @Test
    fun `applyGroupSettings - preserves non-settings fields`() {
        val group = Group(id = 42, name = "MyGroup", description = "desc", learningGroupId = 7L, created = 12345L)
        val settings = mapOf("autoShowAnswer" to "true")

        val result = CardImportExport.applyGroupSettings(group, settings)

        assertEquals(42L, result.id)
        assertEquals("MyGroup", result.name)
        assertEquals("desc", result.description)
        assertEquals(7L, result.learningGroupId)
        assertEquals(12345L, result.created)
        assertEquals(true, result.autoShowAnswer)
    }

    // ==================== Group settings in the export ====================

    @Test
    fun `export includes group settings`() {
        val card = Card(
            id = 1, question = "Q1", answer = "A1",
            created = 0, modified = 0
        )

        val groups = listOf(
            Group(id = 1, name = "CustomGroup", autoShowAnswer = true)
        )

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("CustomGroup"), emptyMap())),
            output,
            groupSettings = groups,
            images = NoImages,
            learningGroups = listOf(LearningGroup(id = 5, name = "Advanced"))
        )

        val content = output.toString(Charsets.UTF_8.name())
        assertTrue(
            content.contains(
                "groups:\n  - name: \"CustomGroup\"\n    autoShowAnswer: true\n"
            )
        )
    }

    @Test
    fun `export excludes groups without custom settings`() {
        val card = Card(
            id = 1, question = "Q1", answer = "A1",
            created = 0, modified = 0
        )

        val groups = listOf(
            Group(id = 1, name = "DefaultGroup"),  // No custom settings
            Group(id = 2, name = "CustomGroup", autoShowAnswer = false)
        )

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("DefaultGroup", "CustomGroup"), emptyMap())),
            output,
            groupSettings = groups,
            images = NoImages,
            learningGroups = listOf(LearningGroup(id = 5, name = "Advanced"))
        )

        val content = output.toString(Charsets.UTF_8.name())
        // Listed even with no settings: the groups section is where each
        // group's stable identifier travels. It carries nothing else.
        assertTrue(content.contains("- name: \"DefaultGroup\"\n    uid: \"${groups[0].uid}\"\n"))
        assertTrue(content.contains("- name: \"CustomGroup\""))
        // The group is still on the card that belongs to it.
        assertTrue(content.contains("groups:\n      - \"DefaultGroup\"\n      - \"CustomGroup\"\n"))
    }

    @Test
    fun `export and import round-trip preserves group settings`() {
        val card = Card(
            id = 1, question = "Test Card", answer = "Test Answer",
            fsrsStability = 5.0, fsrsDifficulty = 3.0, fsrsState = "REVIEW",
            fsrsReps = 4, fsrsLapses = 1,
            created = 0, modified = 0
        )

        val groups = listOf(
            Group(
                id = 1, name = "Epee Drills",
                autoShowAnswer = false
            )
        )

        // Export
        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("Epee Drills"), emptyMap())),
            output,
            groupSettings = groups,
            images = NoImages,
            learningGroups = listOf(LearningGroup(id = 5, name = "Advanced"))
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
        assertEquals("false", drillSettings["autoShowAnswer"])
        assertNull(drillSettings["cardsPerSession"])
        assertNull(drillSettings["practiceDays"])

        // Apply and verify
        val baseGroup = Group(id = 1, name = "Epee Drills")
        val applied = CardImportExport.applyGroupSettings(baseGroup, drillSettings)
        assertEquals(false, applied.autoShowAnswer)
    }

    @Test
    fun `export with group-specific states and settings round-trip`() {
        val card = Card(
            id = 1, question = "Parry-Riposte", answer = "4-6",
            fsrsStability = 2.0, fsrsState = "LEARNING",
            created = 0, modified = 0
        )

        val groupState = CardLearningState(
            cardId = 1, learningGroupId = 1,
            fsrsStability = 8.0, fsrsDifficulty = 0.3,
            fsrsState = "REVIEW", fsrsReps = 5
        )

        val groups = listOf(
            Group(id = 1, name = "Advanced", learningGroupId = 5, autoShowAnswer = true)
        )

        // Export with both group states and group settings
        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("Advanced"), mapOf("Advanced" to groupState))),
            output,
            groupSettings = groups,
            images = NoImages,
            learningGroups = listOf(LearningGroup(id = 5, name = "Advanced"))
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
        assertEquals("true", settings["Advanced"]?.get("autoShowAnswer"))
        assertEquals("Advanced", settings["Advanced"]?.get(CardImportExport.LEARNING_GROUP_SETTING))
        assertTrue(groupRow.learningGroupState)
        assertEquals(mapOf("Advanced" to ""), CardImportExport.lastParsedLearningGroups)
    }

    // ==================== Retention settings export/import TESTS ====================

    @Test
    fun `applyLearningGroupSettings - applies fsrsRetention`() {
        val learningGroup = LearningGroup(id = 1, name = "Test")
        val settings = mapOf("fsrsRetention" to "85", "fsrsEnableFuzzing" to "false")

        val result = CardImportExport.applyLearningGroupSettings(learningGroup, settings)

        assertEquals(85, result.fsrsRetention)
        assertEquals(false, result.fsrsEnableFuzzing)
    }

    @Test
    fun `applyLearningGroupSettings - invalid retention values become null`() {
        val learningGroup = LearningGroup(id = 1, name = "Test")
        val settings = mapOf("fsrsRetention" to "notANumber")

        val result = CardImportExport.applyLearningGroupSettings(learningGroup, settings)

        assertNull(result.fsrsRetention)
    }

    @Test
    fun `applyLearningGroupSettings - empty map clears overrides and keeps the rest`() {
        val learningGroup = LearningGroup(
            id = 3, name = "Test", description = "desc", created = 99L,
            fsrsRetention = 85, maximumInterval = 365, practiceDays = "1,3", fsrsEnableFuzzing = true
        )

        val result = CardImportExport.applyLearningGroupSettings(learningGroup, emptyMap())

        assertEquals(
            LearningGroup(id = 3, name = "Test", description = "desc", created = 99L, uid = learningGroup.uid),
            result
        )
    }

    @Test
    fun `applyGroupSettings - ignores learning settings`() {
        val group = Group(id = 1, name = "Test", created = 0)
        val settings = mapOf("fsrsRetention" to "85", "cardsPerSession" to "4", "autoShowAnswer" to "true")

        val result = CardImportExport.applyGroupSettings(group, settings)

        assertEquals(group.copy(autoShowAnswer = true), result)
    }

    @Test
    fun `export writes learning settings on the learning group`() {
        val card = Card(
            id = 1, question = "Q1", answer = "A1",
            created = 0, modified = 0
        )
        val group = Group(id = 1, name = "RetentionGroup", learningGroupId = 5)

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("RetentionGroup"))),
            output,
            groupSettings = listOf(group),
            images = NoImages,
            learningGroups = listOf(LearningGroup(id = 5, name = "Bouting", fsrsRetention = 85, maximumInterval = 180))
        )

        val content = output.toString(Charsets.UTF_8.name())
        assertTrue(
            content,
            content.contains(
                "learningGroups:\n  - name: \"Bouting\"\n    maximumInterval: 180\n    fsrsRetention: 85\n"
            )
        )
        assertTrue(content, content.contains("groups:\n  - name: \"RetentionGroup\"\n    learningGroup: \"Bouting\"\n"))
    }

    @Test
    fun `export omits learning settings when they are null`() {
        val card = Card(
            id = 1, question = "Q1", answer = "A1",
            created = 0, modified = 0
        )
        val group = Group(id = 1, name = "NoRetentionGroup", learningGroupId = 5, autoShowAnswer = true)

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("NoRetentionGroup"))),
            output,
            groupSettings = listOf(group),
            images = NoImages,
            learningGroups = listOf(LearningGroup(id = 5, name = "Plain"))
        )

        val content = output.toString(Charsets.UTF_8.name())
        assertFalse(content.contains("fsrsRetention"))
        assertFalse(content.contains("practiceDays"))
    }

    @Test
    fun `learning settings round-trip through an export and an import`() {
        val card = Card(
            id = 1, question = "Fleche", answer = "Running attack",
            created = 0, modified = 0
        )
        val learningGroup = LearningGroup(
            id = 5, name = "Bouting", cardsPerSession = 15, randomizeDueCards = true, randomizeBucketHours = 24,
            fsrsRetention = 85, fsrsEnableFuzzing = false, practiceDays = "2,4", maximumInterval = 90, created = 0
        )
        val group = Group(id = 1, name = "FencingDrills", learningGroupId = 5, autoShowAnswer = true)

        val output = ByteArrayOutputStream()
        CardImportExport.exportCardsWithGroupStates(
            listOf(CardWithGroupStates(card, listOf("FencingDrills"))),
            output,
            groupSettings = listOf(group),
            images = NoImages,
            learningGroups = listOf(learningGroup)
        )

        CardImportExport.parseCards(ByteArrayInputStream(output.toByteArray()))

        val learningSettings = CardImportExport.lastParsedLearningGroupSettings.getValue("Bouting")
        // The identity travels too, so the import finds this learning group again.
        assertEquals(learningGroup.uid, CardImportExport.lastParsedLearningGroupUids["Bouting"])
        val restored = CardImportExport.applyLearningGroupSettings(
            LearningGroup(name = "Bouting", created = 0, uid = learningGroup.uid), learningSettings
        )
        assertEquals(learningGroup.copy(id = 0), restored)

        val drillSettings = CardImportExport.lastParsedGroupSettings.getValue("FencingDrills")
        assertEquals("true", drillSettings["autoShowAnswer"])
        assertNull(drillSettings["fsrsRetention"])
        assertNull(drillSettings["cardsPerSession"])
    }

    @Test
    fun `an older file's group-level learning settings are still read`() {
        // Written before the learning settings moved to learning groups.
        val input = """
            version: 5
            groups:
              - name: "Drills"
                learningGroup: "Bouting"
                fsrsRetention: 85
            learningGroups:
              - name: "Bouting"
            cards:
              - question: "Q1"
                groups: ["Drills"]
        """.trimIndent()

        CardImportExport.parseCards(input.byteInputStream())

        assertEquals("85", CardImportExport.lastParsedGroupSettings.getValue("Drills")["fsrsRetention"])
        assertTrue(CardImportExport.lastParsedLearningGroupSettings.isEmpty())
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
