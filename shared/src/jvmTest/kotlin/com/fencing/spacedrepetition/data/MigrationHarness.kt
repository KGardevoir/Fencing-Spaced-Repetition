// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL

/**
 * The plumbing the migration tests share: an in-memory database, a way to run
 * one of the shipped migrations against it, and enough read helpers to assert
 * on the result.
 *
 * A real SQLite engine rather than a fake, because the behaviour these tests
 * exist to pin down -- what DROP TABLE does to a child row when foreign keys
 * are enforced -- is SQLite's, and a fake would simply agree with whatever the
 * test author believed.
 */
internal class MigrationHarness(foreignKeys: Boolean) : AutoCloseable {

    private val connection: SQLiteConnection =
        BundledSQLiteDriver().open(":memory:")

    init {
        // Set before any transaction: PRAGMA foreign_keys is a no-op inside
        // one, which is exactly why a migration cannot turn it off itself.
        connection.execSQL("PRAGMA foreign_keys = ${if (foreignKeys) "ON" else "OFF"}")
    }

    /** Build a schema-v11 database, the state a migration to 12 starts from. */
    fun createSchemaV11() {
        SchemaV11.tables.forEach { connection.execSQL(it) }
        SchemaV11.indices.forEach { connection.execSQL(it) }
    }

    fun exec(sql: String) = connection.execSQL(sql)

    /**
     * Run the shipped migration for [from] -> [from] + 1.
     *
     * Looked up in [ALL_MIGRATIONS] rather than referenced directly, so the
     * tests exercise the array the database is actually built with. A
     * migration that was written but never registered fails here.
     */
    suspend fun migrate(from: Int) {
        val migration = ALL_MIGRATIONS.singleOrNull {
            it.startVersion == from && it.endVersion == from + 1
        } ?: error("No migration $from -> ${from + 1} in ALL_MIGRATIONS")
        migration.migrate(connection)
    }

    /** Run every registered migration from [from] up to [to], in order. */
    suspend fun migrateThrough(from: Int, to: Int) {
        for (version in from until to) migrate(version)
    }

    fun count(table: String): Long =
        queryLong("SELECT COUNT(*) FROM `$table`")

    fun queryLong(sql: String): Long {
        val statement = connection.prepare(sql)
        try {
            check(statement.step()) { "No row for: $sql" }
            return statement.getLong(0)
        } finally {
            statement.close()
        }
    }

    fun queryDouble(sql: String): Double {
        val statement = connection.prepare(sql)
        try {
            check(statement.step()) { "No row for: $sql" }
            return statement.getDouble(0)
        } finally {
            statement.close()
        }
    }

    fun queryText(sql: String): String {
        val statement = connection.prepare(sql)
        try {
            check(statement.step()) { "No row for: $sql" }
            return statement.getText(0)
        } finally {
            statement.close()
        }
    }

    /** Every value in the first column, in the order the query returns them. */
    fun queryLongs(sql: String): List<Long> {
        val statement = connection.prepare(sql)
        try {
            val out = mutableListOf<Long>()
            while (statement.step()) out += statement.getLong(0)
            return out
        } finally {
            statement.close()
        }
    }

    fun queryTexts(sql: String): List<String> {
        val statement = connection.prepare(sql)
        try {
            val out = mutableListOf<String>()
            while (statement.step()) out += statement.getText(0)
            return out
        } finally {
            statement.close()
        }
    }

    /** Column names of [table], as SQLite reports them. */
    fun columnsOf(table: String): Set<String> =
        queryTexts("SELECT name FROM pragma_table_info('$table')").toSet()

    fun tableNames(): Set<String> =
        queryTexts("SELECT name FROM sqlite_master WHERE type = 'table'").toSet()

    fun indexNames(): Set<String> =
        queryTexts("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%'").toSet()

    /** Rows that point at a parent that is not there. Empty means consistent. */
    fun foreignKeyViolations(): Long =
        queryLong("SELECT COUNT(*) FROM pragma_foreign_key_check")

    fun integrityCheck(): String =
        queryText("SELECT * FROM pragma_integrity_check")

    override fun close() {
        connection.close()
    }
}
