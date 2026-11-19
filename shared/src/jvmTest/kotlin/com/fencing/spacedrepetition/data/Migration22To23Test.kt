// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two lines meeting at 23: master's 22 (identifiers, no concepts) gains
 * everything the concepts line added; this line's 21 is already there. Room
 * validates both against the entities in ConceptSchedulingDbTest; here, the
 * data and the identities the versions were released with.
 */
class Migration22To23Test {

    @Test
    fun `master's 22 gains the concept tables, and its learning groups keep scheduling by card`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11()
            db.migrateThrough(from = 11, to = 22)
            db.exec("INSERT INTO `cards` (`id`, `question`, `answer`, `uid`) VALUES (1, 'Lunge', '', 'card-1')")
            db.exec("INSERT INTO `learning_groups` (`id`, `name`, `description`, `created`, `uid`) VALUES (5, 'Bouting', '', 0, 'lg-5')")
            db.migrate(from = 22, to = 23)

            assertTrue(setOf("concepts", "card_concept_cross_ref", "concept_learning_state", "concept_learning_group")
                .all { it in db.tableNames() })
            assertEquals(0L, db.queryLong("SELECT `useConcepts` FROM `learning_groups` WHERE `id` = 5"))
            assertTrue("selectionReason" in db.columnsOf("review_logs"))
            assertEquals("card-1", db.queryText("SELECT `uid` FROM `cards` WHERE `id` = 1"))
            assertEquals("ok", db.integrityCheck())
        }
    }

    @Test
    fun `this line's 21 and master's 22 meet at the same tables`() = runTest {
        val viaConcepts = MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11(); db.migrateThrough(from = 11, to = 21); db.migrate(from = 21, to = 23)
            db.tableNames().associateWith { db.columnsOf(it) }
        }
        val viaMaster = MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11(); db.migrateThrough(from = 11, to = 22); db.migrate(from = 22, to = 23)
            db.tableNames().associateWith { db.columnsOf(it) }
        }
        assertEquals(viaConcepts, viaMaster)
    }

    @Test
    fun `23 to 24 records which concept picked a card, and nothing before it says`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11()
            db.migrateThrough(from = 11, to = 23)
            db.exec("INSERT INTO `cards` (`id`, `question`, `answer`, `uid`) VALUES (1, 'Lunge', '', 'card-1')")
            db.exec(
                """
                INSERT INTO `review_logs` (`id`, `cardId`, `reviewTime`, `grade`, `algorithm`, `stateBefore`,
                    `stateAfter`, `scheduledDays`, `elapsedDays`, `notes`, `imagePaths`, `stabilityMultiplier`, `uid`)
                VALUES (1, 1, 100, 3, 'FSRS', '', '', 1, 0, '', '', 1.0, 'log-1')
                """
            )
            db.migrate(from = 23)
            assertTrue("selectionTrigger" in db.columnsOf("review_logs"))
            assertEquals(0L, db.queryLong("SELECT COUNT(*) FROM `review_logs` WHERE `selectionTrigger` IS NOT NULL"))
        }
    }
}
