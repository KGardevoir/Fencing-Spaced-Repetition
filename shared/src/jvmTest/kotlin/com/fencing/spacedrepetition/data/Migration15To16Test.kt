// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Migration 15 -> 16, which moves the learning settings from card groups to
 * learning groups.
 *
 * What matters: each learning group takes, setting by setting, the value of
 * the first of its groups that overrides it; the groups keep their session
 * settings, their learning group and their cards through the rebuild; and
 * nothing is left behind in the learning states.
 *
 * Run against a real SQLite engine on a database migrated up from schema v11.
 */
class Migration15To16Test {

    /**
     * Learning group 1 holds "Footwork" (retention 85, fuzzing off) and
     * "Blade" (retention 80, a 180-day maximum interval): the first wins
     * retention, the second gives the interval nobody else set. Learning group
     * 2 holds one group with no learning settings. "Global" learns Global and
     * overrides retention, which has nowhere to go.
     */
    private suspend fun MigrationHarness.seedV15() {
        createSchemaV11()
        migrateThrough(from = 11, to = 15)

        exec("INSERT INTO `cards` (`id`, `question`, `answer`) VALUES (1, 'Parry four', ''), (2, 'Fleche', '')")
        exec(
            """
            INSERT INTO `learning_groups` (`id`, `name`, `description`, `created`) VALUES
                (1, 'Bouting', '', 100), (2, 'Drills', '', 200)
            """
        )
        exec(
            """
            INSERT INTO `groups` (
                `id`, `name`, `description`, `learningGroupId`, `created`, `cardsPerSession`,
                `practiceDays`, `maximumInterval`, `fsrsRetention`, `fsrsEnableFuzzing`
            ) VALUES
                (10, 'Footwork', '', 1, 1, 12, NULL, NULL, 85, 0),
                (20, 'Blade', '', 1, 2, NULL, '1,3,5', 180, 80, NULL),
                (30, 'Lunges', '', 2, 3, NULL, NULL, NULL, NULL, NULL),
                (40, 'Global', '', NULL, 4, 7, NULL, NULL, 95, NULL)
            """
        )
        exec("INSERT INTO `card_group_cross_ref` (`cardId`, `groupId`) VALUES (1, 10), (2, 20), (1, 40)")
        exec(
            """
            INSERT INTO `card_learning_state` (
                `cardId`, `learningGroupId`, `fsrsStability`, `fsrsDifficulty`, `fsrsElapsedDays`,
                `fsrsScheduledDays`, `fsrsReps`, `fsrsLapses`, `fsrsState`, `lastReview`,
                `nextReview`, `modified`
            ) VALUES (1, 1, 4.5, 5.0, 0, 3, 2, 0, 'REVIEW', 0, 9000, 0)
            """
        )
    }

    @Test
    fun `each learning group takes the first override of each setting from its groups`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV15()
            db.migrate(from = 15)

            assertEquals(85L, db.queryLong("SELECT `fsrsRetention` FROM `learning_groups` WHERE `id` = 1"))
            assertEquals(0L, db.queryLong("SELECT `fsrsEnableFuzzing` FROM `learning_groups` WHERE `id` = 1"))
            assertEquals(180L, db.queryLong("SELECT `maximumInterval` FROM `learning_groups` WHERE `id` = 1"))
            assertEquals("1,3,5", db.queryText("SELECT `practiceDays` FROM `learning_groups` WHERE `id` = 1"))
            // Cards per session stays on the groups, as their override.
            assertEquals(1L, db.queryLong("SELECT COUNT(*) FROM `learning_groups` WHERE `id` = 1 AND `cardsPerSession` IS NULL"))
        }
    }

    @Test
    fun `a learning group whose groups override nothing overrides nothing`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV15()
            db.migrate(from = 15)

            assertEquals(
                1L,
                db.queryLong(
                    """
                    SELECT COUNT(*) FROM `learning_groups` WHERE `id` = 2
                      AND `cardsPerSession` IS NULL AND `practiceDays` IS NULL AND `maximumInterval` IS NULL
                      AND `fsrsRetention` IS NULL AND `fsrsEnableFuzzing` IS NULL
                    """
                )
            )
        }
    }

    @Test
    fun `groups keep their session settings, learning group and cards`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV15()
            db.migrate(from = 15)

            assertEquals(4L, db.count("groups"))
            assertEquals(12L, db.queryLong("SELECT `cardsPerSession` FROM `groups` WHERE `id` = 10"))
            assertEquals(7L, db.queryLong("SELECT `cardsPerSession` FROM `groups` WHERE `id` = 40"))
            assertEquals(1L, db.queryLong("SELECT `learningGroupId` FROM `groups` WHERE `id` = 20"))
            assertEquals(1L, db.queryLong("SELECT COUNT(*) FROM `groups` WHERE `id` = 40 AND `learningGroupId` IS NULL"))
            assertEquals(3L, db.count("card_group_cross_ref"))
            assertEquals(1L, db.count("card_learning_state"))
            assertFalse("fsrsRetention" in db.columnsOf("groups"))
            assertEquals("ok", db.integrityCheck())
            assertEquals(0L, db.foreignKeyViolations())
        }
    }

    @Test
    fun `deleting a group after the rebuild still cascades to its memberships`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV15()
            db.migrate(from = 15)

            db.exec("DELETE FROM `groups` WHERE `id` = 10")
            assertEquals(2L, db.count("card_group_cross_ref"))
        }
    }
}
