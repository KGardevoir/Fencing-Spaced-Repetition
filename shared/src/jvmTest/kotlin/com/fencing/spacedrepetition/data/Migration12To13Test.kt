// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration 12 -> 13, which records how hard the fight was.
 *
 * A plain ADD COLUMN, so what is worth pinning down is what it does to the
 * reviews already there: they were scheduled with the opponent's multiplier
 * alone, and must stay that way. A NULL rating is neutral, so an untouched row
 * reads back as the review it always was.
 *
 * Run against a real SQLite engine on a database migrated up from schema v11,
 * so this is the SQL that ships and not a description of it.
 */
class Migration12To13Test {

    /** Two reviews as version 12 left them: one against an opponent, one solo. */
    private fun MigrationHarness.seedV12ReviewLogs() {
        createSchemaV11()
        exec(
            """
            INSERT INTO `review_logs` (
                `id`, `cardId`, `sessionId`, `reviewTime`, `grade`, `algorithm`,
                `stateBefore`, `stateAfter`, `scheduledDays`, `elapsedDays`,
                `groupName`, `notes`, `imagePaths`, `opponentId`, `stabilityMultiplier`
            ) VALUES
                (1, 1, NULL, 1705248000000, 3, 'FSRS', 'NEW', 'LEARNING', 1, 0,
                 'Blade Actions', 'felt slow', '', 1, 1.3),
                (2, 2, NULL, 1705000000000, 4, 'FSRS', 'REVIEW', 'REVIEW', 14, 7,
                 NULL, '', '', NULL, 1.0)
            """
        )
    }

    @Test
    fun `adds the fight difficulty column`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV12ReviewLogs()
            db.migrateThrough(from = 11, to = 13)

            assertTrue(
                "fightDifficulty" in db.columnsOf("review_logs"),
                "review_logs has no fightDifficulty column: ${db.columnsOf("review_logs")}"
            )
        }
    }

    @Test
    fun `leaves every review recorded before it unrated`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV12ReviewLogs()
            db.migrateThrough(from = 11, to = 13)

            assertEquals(
                2L,
                db.queryLong("SELECT COUNT(*) FROM `review_logs` WHERE `fightDifficulty` IS NULL"),
                "an existing review came out of the migration with a rating it never had"
            )
        }
    }

    @Test
    fun `leaves the multiplier those reviews were scheduled with alone`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV12ReviewLogs()
            db.migrateThrough(from = 11, to = 13)

            // 1.3 was the opponent's skill multiplier and nothing else. An
            // unrated fight is neutral, so the product is still 1.3.
            assertEquals(
                1.3,
                db.queryDouble("SELECT `stabilityMultiplier` FROM `review_logs` WHERE `id` = 1")
            )
            assertEquals(
                2L,
                db.count("review_logs"),
                "the migration lost a review log"
            )
        }
    }

    @Test
    fun `a new review can record its rating`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV12ReviewLogs()
            db.migrateThrough(from = 11, to = 13)

            db.exec(
                """
                INSERT INTO `review_logs` (
                    `id`, `cardId`, `sessionId`, `reviewTime`, `grade`, `algorithm`,
                    `stateBefore`, `stateAfter`, `scheduledDays`, `elapsedDays`,
                    `groupName`, `notes`, `imagePaths`, `opponentId`,
                    `fightDifficulty`, `stabilityMultiplier`
                ) VALUES
                    (3, 1, NULL, 1705500000000, 3, 'FSRS', 'REVIEW', 'REVIEW', 9, 2,
                     NULL, '', '', 1, 5, 1.95)
                """
            )

            assertEquals(
                5L,
                db.queryLong("SELECT `fightDifficulty` FROM `review_logs` WHERE `id` = 3")
            )
            // 1.3 opponent x 1.5 for a rating of 5.
            assertEquals(
                1.95,
                db.queryDouble("SELECT `stabilityMultiplier` FROM `review_logs` WHERE `id` = 3")
            )
        }
    }
}
