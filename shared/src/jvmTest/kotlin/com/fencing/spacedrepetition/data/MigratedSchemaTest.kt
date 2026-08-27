// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a migrated database looks like when the migrations are done, and
 * whether the chain that produces it is well-formed.
 *
 * Room compares the schema it finds on disk against the one its entities
 * describe, and refuses to open a database that disagrees -- the app crashes
 * on launch, and since there is deliberately no
 * `fallbackToDestructiveMigration()`, it keeps crashing rather than wiping
 * itself. That is the right behaviour, and it is also a bad thing to discover
 * from a user. The column lists below are the entity definitions written out
 * a second time on purpose: if an entity changes and no migration follows,
 * these fail here instead of on someone's phone.
 */
class MigratedSchemaTest {

    @Test
    fun `migrating to 12 leaves cards with exactly the columns the entity declares`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11()
            db.migrate(from = 11)

            assertEquals(
                setOf(
                    "id", "question", "answer", "category", "tags", "imagePaths",
                    "fsrsStability", "fsrsDifficulty", "fsrsElapsedDays", "fsrsScheduledDays",
                    "fsrsReps", "fsrsLapses", "fsrsState",
                    "lastReview", "nextReview", "created", "modified", "isDisabled",
                ),
                db.columnsOf("cards")
            )
        }
    }

    @Test
    fun `migrating to 12 leaves groups with exactly the columns the entity declares`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11()
            db.migrate(from = 11)

            assertEquals(
                setOf(
                    "id", "name", "description", "independentLearning", "created",
                    "cardsPerSession", "autoShowAnswer", "randomizeDueCards",
                    "randomizeBucketHours", "practiceDays", "maximumInterval",
                    "fsrsRetention", "fsrsEnableFuzzing",
                ),
                db.columnsOf("groups")
            )
        }
    }

    @Test
    fun `migrating to 12 leaves the learning state with exactly the columns the entity declares`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11()
            db.migrate(from = 11)

            assertEquals(
                setOf(
                    "cardId", "groupId",
                    "fsrsStability", "fsrsDifficulty", "fsrsElapsedDays", "fsrsScheduledDays",
                    "fsrsReps", "fsrsLapses", "fsrsState",
                    "lastReview", "nextReview", "modified",
                ),
                db.columnsOf("card_group_learning_state")
            )
        }
    }

    @Test
    fun `migrating to 12 keeps every table the database is made of`() = runTest {
        MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11()
            db.migrate(from = 11)

            val tables = db.tableNames()
            listOf(
                "cards", "groups", "card_group_cross_ref", "card_group_learning_state",
                "practice_sessions", "review_logs", "opponents",
            ).forEach {
                assertTrue(it in tables, "table `$it` is missing after the migration: $tables")
            }
        }
    }

    @Test
    fun `the registered migrations form one unbroken chain`() {
        // A gap means a user on that version cannot upgrade; a duplicate means
        // Room picks one of two paths and the other is dead. Both are the kind
        // of thing that only shows up on a device that skipped a release.
        val steps = ALL_MIGRATIONS
            .map { it.startVersion to it.endVersion }
            .sortedBy { it.first }

        steps.forEach { (from, to) ->
            assertEquals(from + 1, to, "migration $from -> $to skips a version")
        }

        val starts = steps.map { it.first }
        assertEquals(starts.distinct(), starts, "two migrations start at the same version: $starts")
        assertEquals(1, steps.first().first, "the chain does not start at version 1")
        assertEquals(
            steps.size,
            steps.last().second - steps.first().first,
            "the chain has a hole in it: $steps"
        )
    }

    @Test
    fun `every version from 1 has a path to the newest`() = runTest {
        // Walking the whole chain, rather than only the newest step, is what
        // catches a migration that is fine on its own but not against what the
        // ones before it actually left behind.
        val newest = ALL_MIGRATIONS.maxOf { it.endVersion }
        MigrationHarness(foreignKeys = true).use { db ->
            db.createSchemaV11()
            db.migrateThrough(from = 11, to = newest)

            assertEquals("ok", db.integrityCheck())
            assertEquals(0L, db.foreignKeyViolations())
        }
    }
}
