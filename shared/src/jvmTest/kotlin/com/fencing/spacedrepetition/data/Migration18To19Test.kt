// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration 18 -> 19, which adds where each concept is available. Upgrades
 * from every installed build of version 18 are in ConceptSchedulingDbTest,
 * against the schemas those builds exported.
 */
class Migration18To19Test {

    private suspend fun MigrationHarness.seedV18() {
        createSchemaV11()
        migrateThrough(from = 11, to = 18)
        exec("INSERT INTO `learning_groups` (`id`, `name`, `description`, `created`) VALUES (5, 'Bouting', '', 0)")
        exec(
            """
            INSERT INTO `concepts` (`id`, `name`, `description`, `fsrsStability`, `fsrsDifficulty`,
                `fsrsElapsedDays`, `fsrsScheduledDays`, `fsrsReps`, `fsrsLapses`, `fsrsState`,
                `lastReview`, `nextReview`, `created`, `modified`, `uid`)
            VALUES (7, 'Distance', '', 0, 0, 0, 0, 0, 0, 'NEW', 0, 0, 0, 0, 'c-7')
            """
        )
    }

    @Test
    fun `adds availability, empty, and nothing else changes`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.seedV18()
            val conceptColumns = db.columnsOf("concepts")
            db.migrate(from = 18)

            assertEquals(conceptColumns + "inGlobal", db.columnsOf("concepts"))
            assertEquals(0L, db.queryLong("SELECT `inGlobal` FROM `concepts` WHERE `id` = 7"))
            assertEquals("c-7", db.queryText("SELECT `uid` FROM `concepts` WHERE `id` = 7"))
            assertEquals(setOf("conceptId", "learningGroupId"), db.columnsOf("concept_learning_group"))
            assertTrue("index_concept_learning_group_learningGroupId" in db.indexNames())
            assertEquals(0L, db.count("concept_learning_group"))

            // Its links go with the concept or the learning group.
            db.exec("INSERT INTO `concept_learning_group` VALUES (7, 5)")
            db.exec("DELETE FROM `learning_groups` WHERE `id` = 5")
            assertEquals(0L, db.count("concept_learning_group"))
            assertEquals("ok", db.integrityCheck())
        }
    }

    /**
     * Version 18 has been installed, so its schema is fixed for good: a change
     * to it would change the identity hash Room checks on open, and every
     * phone already on 18 would crash on launch (which is what happened when
     * it was amended in place). Change the schema in a new version instead.
     */
    @Test
    fun `the released version 18 schema never changes`() {
        fun identity(path: String) =
            Regex(""""identityHash"\s*:\s*"([0-9a-f]+)"""").find(java.io.File(path).readText())!!.groupValues[1]
        assertEquals(
            identity("src/jvmTest/resources/released-v18/18-7141ca0.json"),
            identity("schemas/com.fencing.spacedrepetition.data.AppDatabase/18.json")
        )
    }
}
