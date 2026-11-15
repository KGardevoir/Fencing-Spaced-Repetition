// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Migration 16 -> 17, which moves cards per session, shuffling and bucket
 * size from card groups to learning groups, leaving groups only auto-show.
 *
 * Also run against the version 16 a development build briefly had, whose
 * learning groups already carried `cardsPerSession`: a database from that
 * build must upgrade too, keeping the value it already holds.
 */
class Migration16To17Test {

    /**
     * Learning group 1 holds "Footwork" (12 cards, auto-show on) and "Blade"
     * (20 cards, shuffled in 6-hour buckets): the first wins cards, the
     * second gives the shuffling nobody else set. "Global" learns Global.
     */
    private suspend fun MigrationHarness.seedV16(withLearningGroupCards: Boolean = false) {
        createSchemaV11()
        migrateThrough(from = 11, to = 16)
        if (withLearningGroupCards) {
            exec("ALTER TABLE `learning_groups` ADD COLUMN `cardsPerSession` INTEGER")
        }

        exec("INSERT INTO `cards` (`id`, `question`, `answer`) VALUES (1, 'Parry four', ''), (2, 'Fleche', '')")
        exec(
            """
            INSERT INTO `learning_groups` (`id`, `name`, `description`, `created`, `fsrsRetention`) VALUES
                (1, 'Bouting', '', 100, 85), (2, 'Drills', '', 200, NULL)
            """
        )
        if (withLearningGroupCards) exec("UPDATE `learning_groups` SET `cardsPerSession` = 5 WHERE `id` = 1")
        exec(
            """
            INSERT INTO `groups` (
                `id`, `name`, `description`, `learningGroupId`, `created`,
                `cardsPerSession`, `autoShowAnswer`, `randomizeDueCards`, `randomizeBucketHours`
            ) VALUES
                (10, 'Footwork', '', 1, 1, 12, 1, NULL, NULL),
                (20, 'Blade', '', 1, 2, 20, NULL, 1, 6),
                (30, 'Lunges', '', 2, 3, NULL, NULL, NULL, NULL),
                (40, 'Global', '', NULL, 4, 7, 0, 1, NULL)
            """
        )
        exec("INSERT INTO `card_group_cross_ref` (`cardId`, `groupId`) VALUES (1, 10), (2, 20), (1, 40)")
    }

    @Test
    fun `each learning group takes the first override of each setting from its groups`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV16()
            db.migrate(from = 16)

            assertEquals(12L, db.queryLong("SELECT `cardsPerSession` FROM `learning_groups` WHERE `id` = 1"))
            assertEquals(1L, db.queryLong("SELECT `randomizeDueCards` FROM `learning_groups` WHERE `id` = 1"))
            assertEquals(6L, db.queryLong("SELECT `randomizeBucketHours` FROM `learning_groups` WHERE `id` = 1"))
            // What version 16 already moved stays where it is.
            assertEquals(85L, db.queryLong("SELECT `fsrsRetention` FROM `learning_groups` WHERE `id` = 1"))
            assertEquals(
                1L,
                db.queryLong(
                    """
                    SELECT COUNT(*) FROM `learning_groups` WHERE `id` = 2 AND `cardsPerSession` IS NULL
                      AND `randomizeDueCards` IS NULL AND `randomizeBucketHours` IS NULL
                    """
                )
            )
        }
    }

    @Test
    fun `groups keep auto-show, their learning group and their cards`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV16()
            db.migrate(from = 16)

            assertEquals(4L, db.count("groups"))
            assertEquals(1L, db.queryLong("SELECT `autoShowAnswer` FROM `groups` WHERE `id` = 10"))
            assertEquals(0L, db.queryLong("SELECT `autoShowAnswer` FROM `groups` WHERE `id` = 40"))
            assertEquals(1L, db.queryLong("SELECT `learningGroupId` FROM `groups` WHERE `id` = 20"))
            assertEquals(3L, db.count("card_group_cross_ref"))
            assertFalse("cardsPerSession" in db.columnsOf("groups"))
            assertEquals("ok", db.integrityCheck())
            assertEquals(0L, db.foreignKeyViolations())

            db.exec("DELETE FROM `groups` WHERE `id` = 10")
            assertEquals(2L, db.count("card_group_cross_ref"))
        }
    }

    @Test
    fun `the development build's version 16 upgrades and keeps its learning group's cards`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV16(withLearningGroupCards = true)
            db.migrate(from = 16)

            assertEquals(5L, db.queryLong("SELECT `cardsPerSession` FROM `learning_groups` WHERE `id` = 1"))
            assertEquals(1L, db.queryLong("SELECT `randomizeDueCards` FROM `learning_groups` WHERE `id` = 1"))
            assertEquals("ok", db.integrityCheck())
        }
    }
}
