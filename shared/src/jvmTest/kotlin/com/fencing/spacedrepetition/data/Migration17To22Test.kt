// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration 17 -> 22: stable identifiers, the group and opponent a review
 * refers to by identifier, and reset times. Numbered 22 because 18 to 21
 * were used by the learning-concepts builds; see docs/DESIGN.md, "Migrations".
 */
class Migration17To22Test {

    private suspend fun MigrationHarness.seedV17() {
        createSchemaV11()
        migrateThrough(from = 11, to = 17)
        exec("INSERT INTO `cards` (`id`, `question`, `answer`) VALUES (1, 'Parry four', ''), (2, 'Fleche', '')")
        exec("INSERT INTO `learning_groups` (`id`, `name`, `description`, `created`) VALUES (5, 'Bouting', '', 0)")
    }

    @Test
    fun `every existing row gets its own stable identifier`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV17()
            db.exec("INSERT INTO `groups` (`id`, `name`, `description`, `created`) VALUES (10, 'Footwork', '', 0), (11, 'Footwork', '', 0), (12, 'Blade', '', 0)")
            db.exec("INSERT INTO `opponents` (`name`, `skillMultiplier`, `notes`, `created`, `modified`) VALUES ('Alex', 1.0, '', 0, 0)")
            db.migrate(from = 17, to = 22)

            val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
            listOf("cards", "groups", "learning_groups", "opponents").forEach { table ->
                val uids = db.queryTexts("SELECT `uid` FROM `$table`")
                assertTrue(uids.isNotEmpty(), table)
                assertTrue(uids.all { uuid.matches(it) }, "$table: $uids")
                assertEquals(uids.size, uids.toSet().size, "$table uids must be unique")
            }
            assertTrue("index_cards_uid" in db.indexNames())
            assertEquals("ok", db.integrityCheck())
        }
    }

    @Test
    fun `review logs learn which group they were made in, by identifier`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV17()
            // Two groups share a name; the oldest is the one a log meant.
            db.exec("INSERT INTO `groups` (`id`, `name`, `description`, `created`) VALUES (10, 'Footwork', '', 0), (11, 'Footwork', '', 0)")
            db.exec(
                """
                INSERT INTO `review_logs` (`id`, `cardId`, `reviewTime`, `grade`, `algorithm`, `stateBefore`,
                    `stateAfter`, `scheduledDays`, `elapsedDays`, `groupName`, `notes`, `imagePaths`,
                    `stabilityMultiplier`)
                VALUES (1, 1, 100, 3, 'FSRS', '', '', 1, 0, 'Footwork', '', '', 1.0),
                       (2, 1, 200, 3, 'FSRS', '', '', 1, 0, NULL, '', '', 1.0),
                       (3, 1, 300, 3, 'FSRS', '', '', 1, 0, 'Gone', '', '', 1.0)
                """
            )
            db.migrate(from = 17, to = 22)

            assertEquals(
                db.queryText("SELECT `uid` FROM `groups` WHERE `id` = 10"),
                db.queryText("SELECT `groupUid` FROM `review_logs` WHERE `id` = 1")
            )
            assertEquals(0L, db.queryLong("SELECT COUNT(*) FROM `review_logs` WHERE `id` IN (2, 3) AND `groupUid` IS NOT NULL"))
            // Nothing has been reset yet.
            assertEquals(0L, db.queryLong("SELECT `lastReset` FROM `cards` WHERE `id` = 1"))
            assertTrue("lastReset" in db.columnsOf("card_learning_state"))
        }
    }

    @Test
    fun `review logs learn who they were against, by identifier`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV17()
            db.exec("INSERT INTO `opponents` (`id`, `name`, `skillMultiplier`, `notes`, `created`, `modified`) VALUES (4, 'Alex', 1.0, '', 0, 0)")
            db.exec(
                """
                INSERT INTO `review_logs` (`id`, `cardId`, `reviewTime`, `grade`, `algorithm`, `stateBefore`,
                    `stateAfter`, `scheduledDays`, `elapsedDays`, `notes`, `imagePaths`, `stabilityMultiplier`, `opponentId`)
                VALUES (1, 1, 100, 3, 'FSRS', '', '', 1, 0, '', '', 1.0, 4),
                       (2, 1, 200, 3, 'FSRS', '', '', 1, 0, '', '', 1.0, NULL),
                       (3, 1, 300, 3, 'FSRS', '', '', 1, 0, '', '', 1.0, 99)
                """
            )
            db.migrate(from = 17, to = 22)

            assertEquals(
                db.queryText("SELECT `uid` FROM `opponents` WHERE `id` = 4"),
                db.queryText("SELECT `opponentUid` FROM `review_logs` WHERE `id` = 1")
            )
            // No opponent, or one already deleted: nothing to identify.
            assertEquals(0L, db.queryLong("SELECT COUNT(*) FROM `review_logs` WHERE `id` IN (2, 3) AND `opponentUid` IS NOT NULL"))
        }
    }
}
