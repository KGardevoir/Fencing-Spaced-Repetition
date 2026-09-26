// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration 13 -> 14, which moves notes from each card of a session onto the
 * session and merges them into one.
 *
 * What matters is that nothing is lost on the way: every note arrives, in the
 * order the cards were reviewed, still saying which card it was about; every
 * image arrives once; and the reviews that are not part of a session keep
 * their notes where they were.
 *
 * Run against a real SQLite engine on a database migrated up from schema v11,
 * so this is the SQL that ships and not a description of it.
 */
class Migration13To14Test {

    /**
     * Two sessions and a quick grade, as version 13 left them.
     *
     * Session 10 has three reviews, out of id order in time so the merge has to
     * sort by when they happened; one has no note, one names a card since
     * deleted, and two share an image. Session 20 has reviews but no notes at
     * all. The quick grade (no session) and the review naming session 99,
     * which does not exist, are the two that must be left alone.
     */
    private suspend fun MigrationHarness.seedV13() {
        createSchemaV11()
        migrateThrough(from = 11, to = 13)

        exec(
            """
            INSERT INTO `cards` (`id`, `question`, `answer`) VALUES
                (1, 'Parry four', ''),
                (2, 'Counter-parry
            six', ''),
                (3, 'Fleche', '')
            """
        )
        exec(
            """
            INSERT INTO `practice_sessions` (`id`, `startTime`, `endTime`) VALUES
                (10, 1000, 2000),
                (20, 3000, 4000)
            """
        )
        exec(
            """
            INSERT INTO `review_logs` (
                `id`, `cardId`, `sessionId`, `reviewTime`, `grade`, `notes`, `imagePaths`
            ) VALUES
                (1, 2, 10, 1200, 3, 'blade too wide', 'img/b.jpg,img/shared.jpg'),
                (2, 1, 10, 1100, 2, 'late on the parry', 'img/a.jpg'),
                (3, 3, 10, 1300, 4, '', ''),
                (4, 77, 10, 1400, 1, 'no idea which card', 'img/shared.jpg'),
                (5, 1, 20, 3100, 3, '', ''),
                (6, 1, NULL, 5000, 3, 'quick grade note', 'img/q.jpg'),
                (7, 1, 99, 6000, 3, 'orphaned note', 'img/o.jpg')
            """
        )
    }

    @Test
    fun `adds notes and images to practice sessions`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV13()
            db.migrate(from = 13)

            val columns = db.columnsOf("practice_sessions")
            assertTrue("notes" in columns, "practice_sessions has no notes column: $columns")
            assertTrue("imagePaths" in columns, "practice_sessions has no imagePaths column: $columns")
        }
    }

    @Test
    fun `merges a session's notes into one, in review order, each under its card`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV13()
            db.migrate(from = 13)

            assertEquals(
                "**Parry four**\nlate on the parry\n\n" +
                    "**Counter-parry six**\nblade too wide\n\n" +
                    "**Deleted Card**\nno idea which card",
                db.queryText("SELECT `notes` FROM `practice_sessions` WHERE `id` = 10")
            )
        }
    }

    @Test
    fun `moves a session's images onto it, each once, in review order`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV13()
            db.migrate(from = 13)

            assertEquals(
                "img/a.jpg,img/b.jpg,img/shared.jpg",
                db.queryText("SELECT `imagePaths` FROM `practice_sessions` WHERE `id` = 10")
            )
        }
    }

    @Test
    fun `clears the merged notes from the reviews they came from`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV13()
            db.migrate(from = 13)

            assertEquals(
                0L,
                db.queryLong(
                    "SELECT COUNT(*) FROM `review_logs` WHERE `sessionId` IN (10, 20) " +
                        "AND (`notes` != '' OR `imagePaths` != '')"
                ),
                "a merged note is still on its review as well -- there are two copies"
            )
        }
    }

    @Test
    fun `a session with nothing noted stays empty`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV13()
            db.migrate(from = 13)

            assertEquals("", db.queryText("SELECT `notes` FROM `practice_sessions` WHERE `id` = 20"))
            assertEquals("", db.queryText("SELECT `imagePaths` FROM `practice_sessions` WHERE `id` = 20"))
        }
    }

    @Test
    fun `a quick grade keeps its own note`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV13()
            db.migrate(from = 13)

            assertEquals("quick grade note", db.queryText("SELECT `notes` FROM `review_logs` WHERE `id` = 6"))
            assertEquals("img/q.jpg", db.queryText("SELECT `imagePaths` FROM `review_logs` WHERE `id` = 6"))
        }
    }

    @Test
    fun `a review naming a missing session keeps its note rather than losing it`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV13()
            db.migrate(from = 13)

            assertEquals("orphaned note", db.queryText("SELECT `notes` FROM `review_logs` WHERE `id` = 7"))
            assertEquals("img/o.jpg", db.queryText("SELECT `imagePaths` FROM `review_logs` WHERE `id` = 7"))
        }
    }

    @Test
    fun `keeps every review`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV13()
            db.migrate(from = 13)

            assertEquals(7L, db.count("review_logs"))
            assertEquals("ok", db.integrityCheck())
        }
    }
}
