package com.fencing.spacedrepetition.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the Card data model, focusing on the isDisabled field added for
 * the card disable/enable feature (commit 81c573d).
 */
class CardModelTest {

    // ==================== isDisabled default value TESTS ====================

    @Test
    fun `isDisabled defaults to false for a new card`() {
        val card = Card(question = "What is a lunge?", answer = "A forward attack")
        assertFalse(card.isDisabled)
    }

    @Test
    fun `isDisabled can be set to true at construction`() {
        val card = Card(question = "Q", answer = "A", isDisabled = true)
        assertTrue(card.isDisabled)
    }

    // ==================== copy / immutability TESTS ====================

    @Test
    fun `copy preserves isDisabled when not specified`() {
        val original = Card(question = "Q", answer = "A", isDisabled = true)
        val updated = original.copy(question = "Q2")
        assertTrue(updated.isDisabled)
    }

    @Test
    fun `copy can enable a disabled card`() {
        val disabled = Card(question = "Q", answer = "A", isDisabled = true)
        val enabled = disabled.copy(isDisabled = false)
        assertFalse(enabled.isDisabled)
        assertTrue(disabled.isDisabled) // original unchanged
    }

    @Test
    fun `copy can disable an enabled card`() {
        val enabled = Card(question = "Q", answer = "A")
        val disabled = enabled.copy(isDisabled = true)
        assertTrue(disabled.isDisabled)
        assertFalse(enabled.isDisabled) // original unchanged
    }

    // ==================== equality TESTS ====================

    @Test
    fun `two cards differing only in isDisabled are not equal`() {
        val base = Card(id = 1L, question = "Q", answer = "A")
        val disabledVersion = base.copy(isDisabled = true)
        assertNotEquals(base, disabledVersion)
    }
}
