// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Migration 19 -> 20: how much of each concept a card contains. */
class Migration19To20Test {

    @Test
    fun `every existing tag contains all of its concept`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11()
            db.migrateThrough(from = 11, to = 19)
            db.exec("INSERT INTO `cards` (`id`, `question`, `answer`) VALUES (1, 'Lunge', '')")
            db.exec(
                """
                INSERT INTO `concepts` (`id`, `name`, `description`, `fsrsStability`, `fsrsDifficulty`,
                    `fsrsElapsedDays`, `fsrsScheduledDays`, `fsrsReps`, `fsrsLapses`, `fsrsState`,
                    `lastReview`, `nextReview`, `created`, `modified`, `uid`)
                VALUES (7, 'Distance', '', 0, 0, 0, 0, 0, 0, 'NEW', 0, 0, 0, 0, 'c-7')
                """
            )
            db.exec("INSERT INTO `card_concept_cross_ref` (`cardId`, `conceptId`) VALUES (1, 7)")
            db.migrate(from = 19)

            assertEquals(setOf("cardId", "conceptId", "weight"), db.columnsOf("card_concept_cross_ref"))
            assertEquals(1.0, db.queryDouble("SELECT `weight` FROM `card_concept_cross_ref`"))
            assertEquals("ok", db.integrityCheck())
        }
    }

    /**
     * Released versions never change: Room checks the schema's identity hash
     * on open, and a phone already on a version whose schema changed under
     * the same number crashes on launch. Change the schema in a new version.
     */
    @Test
    fun `released schemas never change`() {
        fun identity(path: String) =
            Regex(""""identityHash"\s*:\s*"([0-9a-f]+)"""").find(File(path).readText())!!.groupValues[1]
        assertEquals(
            identity("src/jvmTest/resources/released/19-b490cfe.json"),
            identity("schemas/com.fencing.spacedrepetition.data.AppDatabase/19.json")
        )
        assertTrue(File("schemas/com.fencing.spacedrepetition.data.AppDatabase/20.json").exists())
    }
}
