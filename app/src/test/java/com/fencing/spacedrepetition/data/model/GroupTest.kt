package com.fencing.spacedrepetition.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Group model utility methods.
 */
class GroupTest {

    // ==================== hasCustomSettings TESTS ====================

    @Test
    fun `hasCustomSettings - default group has no custom settings`() {
        val group = Group(name = "Test")
        assertFalse(group.hasCustomSettings())
    }

    @Test
    fun `hasCustomSettings - cardsPerSession override`() {
        val group = Group(name = "Test", cardsPerSession = 5)
        assertTrue(group.hasCustomSettings())
    }

    @Test
    fun `hasCustomSettings - autoShowAnswer override`() {
        val group = Group(name = "Test", autoShowAnswer = true)
        assertTrue(group.hasCustomSettings())
    }

    @Test
    fun `hasCustomSettings - randomizeDueCards override`() {
        val group = Group(name = "Test", randomizeDueCards = false)
        assertTrue(group.hasCustomSettings())
    }

    @Test
    fun `hasCustomSettings - randomizeBucketHours override`() {
        val group = Group(name = "Test", randomizeBucketHours = 72)
        assertTrue(group.hasCustomSettings())
    }

    @Test
    fun `hasCustomSettings - practiceDays override`() {
        val group = Group(name = "Test", practiceDays = "1,3,5")
        assertTrue(group.hasCustomSettings())
    }

    @Test
    fun `hasCustomSettings - maximumInterval override`() {
        val group = Group(name = "Test", maximumInterval = 365)
        assertTrue(group.hasCustomSettings())
    }

    @Test
    fun `hasCustomSettings - fsrsRetention override`() {
        val group = Group(name = "Test", fsrsRetention = 85)
        assertTrue(group.hasCustomSettings())
    }

    @Test
    fun `hasCustomSettings - sm2IntervalModifier override`() {
        val group = Group(name = "Test", sm2IntervalModifier = 75)
        assertTrue(group.hasCustomSettings())
    }

    @Test
    fun `hasCustomSettings - multiple overrides`() {
        val group = Group(
            name = "Test",
            cardsPerSession = 10,
            autoShowAnswer = false,
            maximumInterval = 180
        )
        assertTrue(group.hasCustomSettings())
    }

    // ==================== parsePracticeDays TESTS ====================

    @Test
    fun `parsePracticeDays - null returns null`() {
        val group = Group(name = "Test", practiceDays = null)
        assertNull(group.parsePracticeDays())
    }

    @Test
    fun `parsePracticeDays - valid days`() {
        val group = Group(name = "Test", practiceDays = "1,3,5")
        assertEquals(setOf(1, 3, 5), group.parsePracticeDays())
    }

    @Test
    fun `parsePracticeDays - all days`() {
        val group = Group(name = "Test", practiceDays = "1,2,3,4,5,6,7")
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), group.parsePracticeDays())
    }

    @Test
    fun `parsePracticeDays - single day`() {
        val group = Group(name = "Test", practiceDays = "7")
        assertEquals(setOf(7), group.parsePracticeDays())
    }

    @Test
    fun `parsePracticeDays - ignores out of range values`() {
        val group = Group(name = "Test", practiceDays = "0,1,8,3")
        assertEquals(setOf(1, 3), group.parsePracticeDays())
    }

    @Test
    fun `parsePracticeDays - ignores non-numeric values`() {
        val group = Group(name = "Test", practiceDays = "1,abc,3")
        assertEquals(setOf(1, 3), group.parsePracticeDays())
    }

    @Test
    fun `parsePracticeDays - handles whitespace`() {
        val group = Group(name = "Test", practiceDays = " 1 , 3 , 5 ")
        assertEquals(setOf(1, 3, 5), group.parsePracticeDays())
    }

    @Test
    fun `parsePracticeDays - empty string returns empty set`() {
        val group = Group(name = "Test", practiceDays = "")
        assertEquals(emptySet<Int>(), group.parsePracticeDays())
    }

    @Test
    fun `parsePracticeDays - handles duplicate values`() {
        val group = Group(name = "Test", practiceDays = "1,1,3,3,5")
        assertEquals(setOf(1, 3, 5), group.parsePracticeDays())
    }
}
