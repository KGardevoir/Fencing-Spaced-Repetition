// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration 17 -> 18, which adds learning concepts. Purely additive: the
 * three new tables exist with exactly the entities' columns and indices,
 * existing data is untouched, and the cascades the entities declare work.
 */
class Migration17To18Test {

    private suspend fun MigrationHarness.seedV17() {
        createSchemaV11()
        migrateThrough(from = 11, to = 17)
        exec("INSERT INTO `cards` (`id`, `question`, `answer`) VALUES (1, 'Parry four', ''), (2, 'Fleche', '')")
        exec("INSERT INTO `learning_groups` (`id`, `name`, `description`, `created`) VALUES (5, 'Bouting', '', 0)")
    }

    @Test
    fun `adds the concept tables with exactly the columns the entities declare`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV17()
            db.migrate(from = 17)

            assertEquals(
                setOf(
                    "id", "name", "description",
                    "fsrsStability", "fsrsDifficulty", "fsrsElapsedDays", "fsrsScheduledDays",
                    "fsrsReps", "fsrsLapses", "fsrsState", "lastReview", "nextReview",
                    "created", "modified",
                ),
                db.columnsOf("concepts")
            )
            assertEquals(setOf("cardId", "conceptId"), db.columnsOf("card_concept_cross_ref"))
            assertEquals(
                setOf(
                    "conceptId", "learningGroupId",
                    "fsrsStability", "fsrsDifficulty", "fsrsElapsedDays", "fsrsScheduledDays",
                    "fsrsReps", "fsrsLapses", "fsrsState", "lastReview", "nextReview", "modified",
                ),
                db.columnsOf("concept_learning_state")
            )
            assertTrue(
                db.indexNames().containsAll(
                    setOf(
                        "index_concepts_name",
                        "index_card_concept_cross_ref_conceptId",
                        "index_concept_learning_state_learningGroupId",
                        "index_concept_learning_state_conceptId",
                    )
                )
            )
            assertTrue("useConcepts" in db.columnsOf("learning_groups"))
            // Existing learning groups keep card-by-card scheduling.
            assertEquals(0L, db.queryLong("SELECT `useConcepts` FROM `learning_groups` WHERE `id` = 5"))
            assertEquals(2L, db.count("cards"))
            assertEquals(1L, db.count("learning_groups"))
            assertEquals("ok", db.integrityCheck())
        }
    }

    @Test
    fun `deleting a card, concept or learning group cascades to the concept rows`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV17()
            db.migrate(from = 17)

            db.exec(
                """
                INSERT INTO `concepts` (`id`, `name`, `description`, `fsrsStability`, `fsrsDifficulty`,
                    `fsrsElapsedDays`, `fsrsScheduledDays`, `fsrsReps`, `fsrsLapses`, `fsrsState`,
                    `lastReview`, `nextReview`, `created`, `modified`)
                VALUES (7, 'Distance', '', 0, 0, 0, 0, 0, 0, 'NEW', 0, 0, 0, 0),
                       (8, 'Timing', '', 0, 0, 0, 0, 0, 0, 'NEW', 0, 0, 0, 0)
                """
            )
            db.exec("INSERT INTO `card_concept_cross_ref` VALUES (1, 7), (2, 7), (2, 8)")
            db.exec(
                """
                INSERT INTO `concept_learning_state` VALUES
                    (7, 5, 1.0, 5.0, 0, 1, 1, 0, 'LEARNING', 0, 0, 0),
                    (8, 5, 1.0, 5.0, 0, 1, 1, 0, 'LEARNING', 0, 0, 0)
                """
            )

            db.exec("DELETE FROM `cards` WHERE `id` = 1")
            assertEquals(listOf(2L, 2L), db.queryLongs("SELECT `cardId` FROM `card_concept_cross_ref` ORDER BY `conceptId`"))

            db.exec("DELETE FROM `concepts` WHERE `id` = 8")
            assertEquals(1L, db.count("card_concept_cross_ref"))
            assertEquals(1L, db.count("concept_learning_state"))

            db.exec("DELETE FROM `learning_groups` WHERE `id` = 5")
            assertEquals(0L, db.count("concept_learning_state"))
            assertEquals(0L, db.foreignKeyViolations())
        }
    }
}
