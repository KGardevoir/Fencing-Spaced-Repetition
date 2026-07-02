package com.fencing.spacedrepetition.data.preferences

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SettingsConstants utility functions and data.
 */
class SettingsConstantsTest {

    // ==================== findPresetIndex TESTS ====================

    @Test
    fun `findPresetIndex - exact match returns correct index`() {
        val presets = SettingsConstants.INTERVAL_PRESETS
        assertEquals(0, SettingsConstants.findPresetIndex(presets, 7))    // 1 week
        assertEquals(2, SettingsConstants.findPresetIndex(presets, 30))   // 1 month
        assertEquals(6, SettingsConstants.findPresetIndex(presets, 365))  // 1 year
        assertEquals(9, SettingsConstants.findPresetIndex(presets, 3650)) // 10 years
    }

    @Test
    fun `findPresetIndex - value between presets snaps to next higher`() {
        val presets = SettingsConstants.INTERVAL_PRESETS
        // 10 is between 7 (index 0) and 14 (index 1), should snap to index 1
        assertEquals(1, SettingsConstants.findPresetIndex(presets, 10))
        // 20 is between 14 (index 1) and 30 (index 2), should snap to index 2
        assertEquals(2, SettingsConstants.findPresetIndex(presets, 20))
    }

    @Test
    fun `findPresetIndex - value below minimum returns first index`() {
        val presets = SettingsConstants.INTERVAL_PRESETS
        assertEquals(0, SettingsConstants.findPresetIndex(presets, 1))
        assertEquals(0, SettingsConstants.findPresetIndex(presets, 0))
    }

    @Test
    fun `findPresetIndex - value above maximum returns last index`() {
        val presets = SettingsConstants.INTERVAL_PRESETS
        assertEquals(9, SettingsConstants.findPresetIndex(presets, 5000))
        assertEquals(9, SettingsConstants.findPresetIndex(presets, 99999))
    }

    @Test
    fun `findPresetIndex - works with bucket presets`() {
        val presets = SettingsConstants.BUCKET_PRESETS
        assertEquals(0, SettingsConstants.findPresetIndex(presets, 24))   // 1 day
        assertEquals(2, SettingsConstants.findPresetIndex(presets, 168))  // 1 week
        assertEquals(4, SettingsConstants.findPresetIndex(presets, 672))  // 4 weeks
        // Value above max
        assertEquals(4, SettingsConstants.findPresetIndex(presets, 1000))
    }

    // ==================== Constants validity TESTS ====================

    @Test
    fun `bucket presets are sorted ascending`() {
        val values = SettingsConstants.BUCKET_PRESETS.map { it.first }
        assertEquals(values, values.sorted())
    }

    @Test
    fun `interval presets are sorted ascending`() {
        val values = SettingsConstants.INTERVAL_PRESETS.map { it.first }
        assertEquals(values, values.sorted())
    }

    @Test
    fun `day labels cover all 7 days`() {
        assertEquals(7, SettingsConstants.DAY_LABELS.size)
        val dayNumbers = SettingsConstants.DAY_LABELS.map { it.first }.toSet()
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7), dayNumbers)
    }

    @Test
    fun `day labels start with Sunday`() {
        assertEquals(7, SettingsConstants.DAY_LABELS[0].first)
        assertEquals("S", SettingsConstants.DAY_LABELS[0].second)
    }

    @Test
    fun `cards per session slider range is valid`() {
        assertTrue(SettingsConstants.CARDS_PER_SESSION_MIN < SettingsConstants.CARDS_PER_SESSION_MAX)
        assertTrue(SettingsConstants.CARDS_PER_SESSION_STEPS > 0)
    }

    // ==================== Retention preset TESTS ====================

    @Test
    fun `fsrs retention presets are sorted ascending`() {
        val values = SettingsConstants.FSRS_RETENTION_PRESETS.map { it.first }
        assertEquals(values, values.sorted())
    }

    @Test
    fun `sm2 modifier presets are sorted ascending`() {
        val values = SettingsConstants.SM2_MODIFIER_PRESETS.map { it.first }
        assertEquals(values, values.sorted())
    }

    @Test
    fun `fsrs retention presets contain default value 90`() {
        val values = SettingsConstants.FSRS_RETENTION_PRESETS.map { it.first }
        assertTrue("Default 90 % must appear in FSRS presets", 90 in values)
    }

    @Test
    fun `sm2 modifier presets contain default value 100`() {
        val values = SettingsConstants.SM2_MODIFIER_PRESETS.map { it.first }
        assertTrue("Default 100 % must appear in SM-2 modifier presets", 100 in values)
    }

    @Test
    fun `findPresetIndex - exact match works with fsrs retention presets`() {
        val presets = SettingsConstants.FSRS_RETENTION_PRESETS
        assertEquals(0, SettingsConstants.findPresetIndex(presets, 70))  // first
        assertEquals(4, SettingsConstants.findPresetIndex(presets, 90))  // default
        assertEquals(7, SettingsConstants.findPresetIndex(presets, 97))  // last
    }

    @Test
    fun `findPresetIndex - value above max returns last index for fsrs retention presets`() {
        val presets = SettingsConstants.FSRS_RETENTION_PRESETS
        val lastIndex = presets.size - 1
        assertEquals(lastIndex, SettingsConstants.findPresetIndex(presets, 99))
        assertEquals(lastIndex, SettingsConstants.findPresetIndex(presets, 200))
    }

    @Test
    fun `findPresetIndex - exact match works with sm2 modifier presets`() {
        val presets = SettingsConstants.SM2_MODIFIER_PRESETS
        assertEquals(0, SettingsConstants.findPresetIndex(presets, 50))   // first
        assertEquals(2, SettingsConstants.findPresetIndex(presets, 100))  // default
        assertEquals(5, SettingsConstants.findPresetIndex(presets, 200))  // last
    }

    @Test
    fun `findPresetIndex - value above max returns last index for sm2 modifier presets`() {
        val presets = SettingsConstants.SM2_MODIFIER_PRESETS
        val lastIndex = presets.size - 1
        assertEquals(lastIndex, SettingsConstants.findPresetIndex(presets, 300))
        assertEquals(lastIndex, SettingsConstants.findPresetIndex(presets, 1000))
    }

    @Test
    fun `fsrs retention preset labels end with percent sign`() {
        SettingsConstants.FSRS_RETENTION_PRESETS.forEach { (_, label) ->
            assertTrue("Label '$label' should end with '%'", label.endsWith("%"))
        }
    }

    @Test
    fun `sm2 modifier preset labels end with percent sign`() {
        SettingsConstants.SM2_MODIFIER_PRESETS.forEach { (_, label) ->
            assertTrue("Label '$label' should end with '%'", label.endsWith("%"))
        }
    }

    // ==================== Max backups kept preset TESTS ====================

    @Test
    fun `max backups kept presets are sorted ascending`() {
        val values = SettingsConstants.MAX_BACKUPS_KEPT_PRESETS.map { it.first }
        assertEquals(values, values.sorted())
    }

    @Test
    fun `max backups kept presets contain default value 7`() {
        val values = SettingsConstants.MAX_BACKUPS_KEPT_PRESETS.map { it.first }
        assertTrue("Default 7 must appear in max backups kept presets", 7 in values)
    }

    @Test
    fun `findPresetIndex - exact match works with max backups kept presets`() {
        val presets = SettingsConstants.MAX_BACKUPS_KEPT_PRESETS
        assertEquals(0, SettingsConstants.findPresetIndex(presets, 3))   // first
        assertEquals(2, SettingsConstants.findPresetIndex(presets, 7))   // default
        assertEquals(6, SettingsConstants.findPresetIndex(presets, 30))  // last
    }

    @Test
    fun `findPresetIndex - value above max returns last index for max backups kept presets`() {
        val presets = SettingsConstants.MAX_BACKUPS_KEPT_PRESETS
        val lastIndex = presets.size - 1
        assertEquals(lastIndex, SettingsConstants.findPresetIndex(presets, 50))
        assertEquals(lastIndex, SettingsConstants.findPresetIndex(presets, 1000))
    }
}
