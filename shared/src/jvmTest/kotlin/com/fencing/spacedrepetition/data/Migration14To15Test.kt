// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Migration 14 -> 15, which turns each group's independent-learning switch
 * into learning groups.
 *
 * What matters: every group that learned independently ends up alone in a
 * learning group of its name, holding exactly the states it held; every other
 * group learns Global; and no membership is lost on the way through the
 * rebuild of `groups`, which the cross references cascade from.
 *
 * Run against a real SQLite engine on a database migrated up from schema v11.
 */
class Migration14To15Test {

    /**
     * Three groups: "Bouting" learns independently and holds two states;
     * "Drills" learns Global but has a state left over from when it did not;
     * "Bouting" again, a second group of the same name that also learns
     * independently, to show names are not used to match rows.
     */
    private suspend fun MigrationHarness.seedV14() {
        createSchemaV11()
        migrateThrough(from = 11, to = 14)

        exec(
            """
            INSERT INTO `cards` (`id`, `question`, `answer`) VALUES
                (1, 'Parry four', ''), (2, 'Fleche', ''), (3, 'Lunge', '')
            """
        )
        exec(
            """
            INSERT INTO `groups` (`id`, `name`, `independentLearning`, `created`, `cardsPerSession`) VALUES
                (10, 'Bouting', 1, 111, 12),
                (20, 'Drills', 0, 222, NULL),
                (30, 'Bouting', 1, 333, NULL)
            """
        )
        exec(
            """
            INSERT INTO `card_group_cross_ref` (`cardId`, `groupId`) VALUES
                (1, 10), (2, 10), (1, 20), (3, 20), (3, 30)
            """
        )
        exec(
            """
            INSERT INTO `card_group_learning_state` (
                `cardId`, `groupId`, `fsrsStability`, `fsrsReps`, `fsrsState`, `nextReview`
            ) VALUES
                (1, 10, 4.5, 3, 'REVIEW', 9000),
                (2, 10, 1.5, 1, 'LEARNING', 8000),
                (1, 20, 9.9, 9, 'REVIEW', 7000),
                (3, 30, 2.0, 2, 'REVIEW', 6000)
            """
        )
    }

    @Test
    fun `each independent group gets a learning group of its own name`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV14()
            db.migrate(from = 14)

            assertEquals(2L, db.count("learning_groups"))
            assertEquals(
                listOf("Bouting", "Bouting"),
                db.queryTexts("SELECT `name` FROM `learning_groups` ORDER BY `id`")
            )
            // Separate learning groups, one each, not merged by name.
            val bouting = db.queryLong("SELECT `learningGroupId` FROM `groups` WHERE `id` = 10")
            val bouting2 = db.queryLong("SELECT `learningGroupId` FROM `groups` WHERE `id` = 30")
            assertTrue(bouting != bouting2)
            assertEquals(111L, db.queryLong("SELECT `created` FROM `learning_groups` WHERE `id` = $bouting"))
        }
    }

    @Test
    fun `a group that did not learn independently learns Global`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV14()
            db.migrate(from = 14)

            assertEquals(1L, db.queryLong("SELECT COUNT(*) FROM `groups` WHERE `id` = 20 AND `learningGroupId` IS NULL"))
        }
    }

    @Test
    fun `states move to the learning group and stale ones are dropped`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV14()
            db.migrate(from = 14)

            val bouting = db.queryLong("SELECT `learningGroupId` FROM `groups` WHERE `id` = 10")
            assertEquals(3L, db.count("card_learning_state"))
            assertEquals(
                listOf(1L, 2L),
                db.queryLongs("SELECT `cardId` FROM `card_learning_state` WHERE `learningGroupId` = $bouting ORDER BY `cardId`")
            )
            assertEquals(
                4.5,
                db.queryDouble("SELECT `fsrsStability` FROM `card_learning_state` WHERE `cardId` = 1 AND `learningGroupId` = $bouting")
            )
            assertEquals(
                9000L,
                db.queryLong("SELECT `nextReview` FROM `card_learning_state` WHERE `cardId` = 1 AND `learningGroupId` = $bouting")
            )
        }
    }

    @Test
    fun `memberships and group settings survive the rebuild`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV14()
            db.migrate(from = 14)

            assertEquals(5L, db.count("card_group_cross_ref"))
            assertEquals(12L, db.queryLong("SELECT `cardsPerSession` FROM `groups` WHERE `id` = 10"))
            assertFalse("independentLearning" in db.columnsOf("groups"))
            assertFalse("card_group_learning_state" in db.tableNames())
            assertEquals("ok", db.integrityCheck())
            assertEquals(0L, db.foreignKeyViolations())
        }
    }

    @Test
    fun `deleting a learning group sends its groups back to Global`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV14()
            db.migrate(from = 14)

            val bouting = db.queryLong("SELECT `learningGroupId` FROM `groups` WHERE `id` = 10")
            db.exec("DELETE FROM `learning_groups` WHERE `id` = $bouting")

            assertEquals(1L, db.queryLong("SELECT COUNT(*) FROM `groups` WHERE `id` = 10 AND `learningGroupId` IS NULL"))
            assertEquals(0L, db.queryLong("SELECT COUNT(*) FROM `card_learning_state` WHERE `learningGroupId` = $bouting"))
            assertEquals(5L, db.count("card_group_cross_ref"))
        }
    }

    @Test
    fun `leaves the indices the entities declare`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV14()
            db.migrate(from = 14)

            val indices = db.indexNames()
            listOf(
                "index_groups_learningGroupId",
                "index_card_learning_state_learningGroupId",
                "index_card_learning_state_cardId",
                "index_card_group_cross_ref_groupId",
            ).forEach { assertTrue(it in indices, "index `$it` is missing: $indices") }
        }
    }
}
