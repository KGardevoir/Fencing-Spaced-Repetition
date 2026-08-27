// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Migration 11 -> 12, the one that drops SM-2.
 *
 * It rebuilds `cards` and `groups`, and both are parents of `ON DELETE
 * CASCADE` foreign keys from `card_group_cross_ref` and
 * `card_group_learning_state`. With `PRAGMA foreign_keys` on, SQLite runs an
 * implicit DELETE before a DROP TABLE and that DELETE fires the cascades, so
 * an ordering that drops a parent while a child still exists silently deletes
 * every group membership and every independent-learning state in the
 * collection. The first two tests here are that regression, and they run with
 * foreign keys enforced because that is the case where it bites.
 *
 * Everything runs against a real SQLite engine on a schema-v11 database built
 * from [SchemaV11], so what is being checked is the SQL that ships, not a
 * description of it.
 */
class Migration11To12Test {

    /**
     * A small collection with the shapes that matter: a card in two groups, a
     * second card sharing one of them, an independent-learning state for each,
     * and per-group settings including the SM-2 override being removed.
     */
    private fun MigrationHarness.seedV11Collection() {
        createSchemaV11()

        exec(
            """
            INSERT INTO `cards` (
                `id`, `question`, `answer`, `category`, `tags`, `algorithm`,
                `fsrsStability`, `fsrsDifficulty`, `fsrsElapsedDays`, `fsrsScheduledDays`,
                `fsrsReps`, `fsrsLapses`, `fsrsState`,
                `sm2EaseFactor`, `sm2Interval`, `sm2Repetitions`,
                `lastReview`, `nextReview`, `created`, `modified`, `isDisabled`
            ) VALUES
                (1, 'Parry four', 'High outside line', 'Defense', 'blade', 'FSRS',
                 4.5, 3.2, 3, 7, 5, 1, 'REVIEW', 2.5, 0, 0,
                 1705248000000, 1705334400000, 100, 200, 0),
                (2, 'Riposte', 'After a parry', '', '', 'SM2',
                 0.0, 0.0, 0, 0, 0, 0, 'NEW', 2.8, 14, 7,
                 1705000000000, 1705593600000, 300, 400, 1)
            """
        )
        exec(
            """
            INSERT INTO `groups` (
                `id`, `name`, `description`, `independentLearning`, `created`,
                `cardsPerSession`, `autoShowAnswer`, `randomizeDueCards`,
                `randomizeBucketHours`, `practiceDays`, `maximumInterval`,
                `fsrsRetention`, `sm2IntervalModifier`, `fsrsEnableFuzzing`
            ) VALUES
                (1, 'Blade Actions', 'Parries', 1, 500, 5, 1, 0, 72, '1,3,5', 365, 85, 75, 1),
                (2, 'Footwork', '', 0, 600, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
            """
        )
        exec(
            """
            INSERT INTO `card_group_cross_ref` (`cardId`, `groupId`) VALUES
                (1, 1), (1, 2), (2, 1)
            """
        )
        exec(
            """
            INSERT INTO `card_group_learning_state` (
                `cardId`, `groupId`,
                `fsrsStability`, `fsrsDifficulty`, `fsrsElapsedDays`, `fsrsScheduledDays`,
                `fsrsReps`, `fsrsLapses`, `fsrsState`,
                `sm2EaseFactor`, `sm2Interval`, `sm2Repetitions`,
                `lastReview`, `nextReview`, `modified`
            ) VALUES
                (1, 1, 3.1, 4.2, 1, 3, 2, 0, 'LEARNING', 2.5, 0, 0, 1705100000000, 1705593600000, 700),
                (2, 1, 0.0, 0.0, 0, 0, 0, 0, 'NEW', 2.9, 21, 9, 0, 0, 800)
            """
        )
        exec(
            """
            INSERT INTO `review_logs` (
                `id`, `cardId`, `sessionId`, `reviewTime`, `grade`, `algorithm`,
                `stateBefore`, `stateAfter`, `scheduledDays`, `elapsedDays`,
                `groupName`, `notes`, `imagePaths`, `opponentId`, `stabilityMultiplier`
            ) VALUES
                (1, 1, NULL, 1705248000000, 3, 'FSRS', 'NEW', 'LEARNING', 1, 0, 'Blade Actions', 'felt slow', '', 1, 1.2),
                (2, 2, NULL, 1705000000000, 2, 'SM2',  'EF:2.8,I:14,R:7', 'EF:2.8,I:14,R:7', 14, 7, NULL, '', '', NULL, 1.0)
            """
        )
        exec(
            """
            INSERT INTO `opponents` (`id`, `name`, `skillMultiplier`, `notes`, `created`, `modified`)
            VALUES (1, 'Alex', 1.3, 'left-handed', 900, 1000)
            """
        )
    }

    // ==================== the cascade regression ====================

    @Test
    fun `keeps every group membership when foreign keys are enforced`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV11Collection()
            assertEquals(3L, db.count("card_group_cross_ref"))

            db.migrate(from = 11)

            // Dropping `cards` or `groups` with a child still referencing them
            // would cascade these away and leave every card group-less.
            assertEquals(
                3L,
                db.count("card_group_cross_ref"),
                "group memberships were deleted by the migration"
            )
            assertEquals(
                listOf(1L, 2L, 1L),
                db.queryLongs("SELECT `groupId` FROM `card_group_cross_ref` ORDER BY `cardId`, `groupId`")
            )
        }
    }

    @Test
    fun `keeps every independent learning state when foreign keys are enforced`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV11Collection()
            assertEquals(2L, db.count("card_group_learning_state"))

            db.migrate(from = 11)

            assertEquals(
                2L,
                db.count("card_group_learning_state"),
                "per-group learning progress was deleted by the migration"
            )
            assertEquals(
                3.1,
                db.queryDouble("SELECT `fsrsStability` FROM `card_group_learning_state` WHERE `cardId` = 1 AND `groupId` = 1"),
                0.0001
            )
            assertEquals(
                "LEARNING",
                db.queryText("SELECT `fsrsState` FROM `card_group_learning_state` WHERE `cardId` = 1 AND `groupId` = 1")
            )
            assertEquals(
                1705593600000L,
                db.queryLong("SELECT `nextReview` FROM `card_group_learning_state` WHERE `cardId` = 1 AND `groupId` = 1")
            )
        }
    }

    @Test
    fun `keeps memberships and learning states with foreign keys off too`() = runTest {
        // The other half of the pair: whichever way Room leaves the pragma,
        // the migration has to behave the same.
        MigrationHarness(foreignKeys = false).use { db ->
            db.seedV11Collection()

            db.migrate(from = 11)

            assertEquals(3L, db.count("card_group_cross_ref"))
            assertEquals(2L, db.count("card_group_learning_state"))
        }
    }

    // ==================== the data the user would notice ====================

    @Test
    fun `keeps every card and its FSRS state`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV11Collection()

            db.migrate(from = 11)

            assertEquals(2L, db.count("cards"))
            assertEquals("Parry four", db.queryText("SELECT `question` FROM `cards` WHERE `id` = 1"))
            assertEquals("High outside line", db.queryText("SELECT `answer` FROM `cards` WHERE `id` = 1"))
            assertEquals("Defense", db.queryText("SELECT `category` FROM `cards` WHERE `id` = 1"))
            assertEquals("blade", db.queryText("SELECT `tags` FROM `cards` WHERE `id` = 1"))
            assertEquals(4.5, db.queryDouble("SELECT `fsrsStability` FROM `cards` WHERE `id` = 1"), 0.0001)
            assertEquals(3.2, db.queryDouble("SELECT `fsrsDifficulty` FROM `cards` WHERE `id` = 1"), 0.0001)
            assertEquals("REVIEW", db.queryText("SELECT `fsrsState` FROM `cards` WHERE `id` = 1"))
            assertEquals(5L, db.queryLong("SELECT `fsrsReps` FROM `cards` WHERE `id` = 1"))
            assertEquals(1L, db.queryLong("SELECT `fsrsLapses` FROM `cards` WHERE `id` = 1"))
            assertEquals(7L, db.queryLong("SELECT `fsrsScheduledDays` FROM `cards` WHERE `id` = 1"))
        }
    }

    @Test
    fun `keeps review scheduling for a card that was on SM-2`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV11Collection()

            db.migrate(from = 11)

            // Card 2 was an SM-2 card. Its FSRS state is empty -- SM-2 never
            // wrote any -- but nothing may come due earlier than it already was.
            assertEquals(1705593600000L, db.queryLong("SELECT `nextReview` FROM `cards` WHERE `id` = 2"))
            assertEquals(1705000000000L, db.queryLong("SELECT `lastReview` FROM `cards` WHERE `id` = 2"))
            assertEquals("Riposte", db.queryText("SELECT `question` FROM `cards` WHERE `id` = 2"))
            // And a disabled card stays disabled.
            assertEquals(1L, db.queryLong("SELECT `isDisabled` FROM `cards` WHERE `id` = 2"))
        }
    }

    @Test
    fun `keeps group settings other than the SM-2 override`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV11Collection()

            db.migrate(from = 11)

            assertEquals(2L, db.count("groups"))
            assertEquals("Blade Actions", db.queryText("SELECT `name` FROM `groups` WHERE `id` = 1"))
            assertEquals("Parries", db.queryText("SELECT `description` FROM `groups` WHERE `id` = 1"))
            assertEquals(1L, db.queryLong("SELECT `independentLearning` FROM `groups` WHERE `id` = 1"))
            assertEquals(5L, db.queryLong("SELECT `cardsPerSession` FROM `groups` WHERE `id` = 1"))
            assertEquals(72L, db.queryLong("SELECT `randomizeBucketHours` FROM `groups` WHERE `id` = 1"))
            assertEquals("1,3,5", db.queryText("SELECT `practiceDays` FROM `groups` WHERE `id` = 1"))
            assertEquals(365L, db.queryLong("SELECT `maximumInterval` FROM `groups` WHERE `id` = 1"))
            assertEquals(85L, db.queryLong("SELECT `fsrsRetention` FROM `groups` WHERE `id` = 1"))
            assertEquals(1L, db.queryLong("SELECT `fsrsEnableFuzzing` FROM `groups` WHERE `id` = 1"))
        }
    }

    @Test
    fun `leaves review history and opponents untouched`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV11Collection()

            db.migrate(from = 11)

            assertEquals(2L, db.count("review_logs"))
            // A log written while SM-2 existed keeps saying so; it is history.
            assertEquals("SM2", db.queryText("SELECT `algorithm` FROM `review_logs` WHERE `id` = 2"))
            assertEquals("felt slow", db.queryText("SELECT `notes` FROM `review_logs` WHERE `id` = 1"))
            assertEquals(1.2, db.queryDouble("SELECT `stabilityMultiplier` FROM `review_logs` WHERE `id` = 1"), 0.0001)

            assertEquals(1L, db.count("opponents"))
            assertEquals("Alex", db.queryText("SELECT `name` FROM `opponents` WHERE `id` = 1"))
        }
    }

    // ==================== the shape it leaves behind ====================

    @Test
    fun `drops the SM-2 columns and the algorithm column`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV11Collection()

            db.migrate(from = 11)

            val cards = db.columnsOf("cards")
            assertFalse("algorithm" in cards, "cards.algorithm survived")
            assertFalse(cards.any { it.startsWith("sm2") }, "an sm2 column survived on cards: $cards")

            val learningState = db.columnsOf("card_group_learning_state")
            assertFalse(
                learningState.any { it.startsWith("sm2") },
                "an sm2 column survived on card_group_learning_state: $learningState"
            )

            assertFalse(
                "sm2IntervalModifier" in db.columnsOf("groups"),
                "groups.sm2IntervalModifier survived"
            )
        }
    }

    @Test
    fun `leaves no scratch tables behind and keeps the indices`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV11Collection()

            db.migrate(from = 11)

            val leftovers = db.tableNames().filter {
                it.endsWith("_new") || it.contains("backup")
            }
            assertEquals(emptyList(), leftovers, "the migration left scratch tables behind")

            // Rebuilding a table drops its indices with it; they have to come back.
            val indices = db.indexNames()
            assertTrue("index_card_group_cross_ref_groupId" in indices, "missing: $indices")
            assertTrue("index_card_group_learning_state_groupId" in indices, "missing: $indices")
            assertTrue("index_card_group_learning_state_cardId" in indices, "missing: $indices")
        }
    }

    @Test
    fun `leaves the database internally consistent`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV11Collection()

            db.migrate(from = 11)

            assertEquals(0L, db.foreignKeyViolations(), "a row points at a parent that is not there")
            assertEquals("ok", db.integrityCheck())
        }
    }

    // ==================== edges ====================

    @Test
    fun `migrates an empty collection`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11()

            db.migrate(from = 11)

            assertEquals(0L, db.count("cards"))
            assertEquals(0L, db.count("groups"))
            assertEquals(0L, db.count("card_group_cross_ref"))
            assertEquals("ok", db.integrityCheck())
        }
    }

    @Test
    fun `drops rows orphaned before the migration rather than failing`() = runTest {
        // A collection that drifted -- a membership whose card is gone, which
        // is possible if foreign keys were ever off. Re-inserting it under
        // enforcement would abort the migration and leave the app unable to
        // open at all, so the row is dropped instead. It was unreachable
        // either way.
        MigrationHarness(foreignKeys = false).use { db ->
            db.seedV11Collection()
            db.exec("INSERT INTO `card_group_cross_ref` (`cardId`, `groupId`) VALUES (999, 1)")
            db.exec(
                """
                INSERT INTO `card_group_learning_state` (`cardId`, `groupId`, `fsrsState`)
                VALUES (999, 1, 'NEW')
                """
            )

            db.migrate(from = 11)

            assertEquals(3L, db.count("card_group_cross_ref"), "a real membership was lost with the orphan")
            assertEquals(2L, db.count("card_group_learning_state"))
            assertEquals(0L, db.queryLong("SELECT COUNT(*) FROM `card_group_cross_ref` WHERE `cardId` = 999"))
            assertEquals(0L, db.foreignKeyViolations())
        }
    }
}
