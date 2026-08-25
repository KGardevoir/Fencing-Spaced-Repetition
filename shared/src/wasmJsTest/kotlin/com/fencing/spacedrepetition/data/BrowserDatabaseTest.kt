// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import com.fencing.spacedrepetition.data.model.Group
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * Opens the real browser database and puts a row through it.
 *
 * The narrowest test of the whole browser stack: the vendored worker,
 * SQLite's WebAssembly build, the driver's message protocol, Room's generated
 * wasm implementation and the schema Room creates from the entities. Every one of those is a place where
 * "it compiles" and "it works" come apart, which is why it runs against a
 * real browser engine rather than a fake.
 *
 * It uses the same getDatabase() the app will use, rather than a test-only
 * builder, so that a mistake in the builder is a failing test rather than a
 * mistake only the browser finds. That means it writes to the app's own OPFS
 * database, so it cleans up after itself: the row it inserts is named for
 * this test and deleted before it returns.
 *
 * It does not close the database. getDatabase() hands out one shared instance
 * per page, and closing it here would pull the storage out from under
 * anything that ran afterwards.
 */
class BrowserDatabaseTest {

    @Test
    fun insertsAndReadsBackAGroup() = runTest {
        val dao = AppDatabase.getDatabase().groupDao()

        val id = dao.insertGroup(
            Group(name = "browser-database-test", description = "round trip")
        )

        val stored = dao.getGroupById(id)
        assertNotNull(stored, "the row Room said it inserted was not there")
        assertEquals("browser-database-test", stored.name)
        assertEquals("round trip", stored.description)

        dao.deleteGroupById(id)
        assertNull(dao.getGroupById(id), "the row outlived its deletion")
    }
}
