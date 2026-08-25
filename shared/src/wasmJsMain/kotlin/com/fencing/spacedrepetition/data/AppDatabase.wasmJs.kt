// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import androidx.room3.Room
import androidx.sqlite.driver.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

/**
 * The browser database.
 *
 * NOT USABLE YET, and deliberately not called from anywhere. Room generates a
 * wasm implementation and this builds against it, but opening a database needs
 * a Web Worker script that runs SQLite's WebAssembly build and speaks Room's
 * messaging protocol. androidx does not publish that worker as a consumable
 * artifact -- its sample ships one as a local NPM module -- so the asset this
 * points at does not exist in this repository yet. Vendoring one is a
 * licensing and supply-chain decision, not a coding one, so it is not made
 * here.
 *
 * What this does establish is that the API compiles for wasmJs: the generated
 * database, the driver and the builder all line up. The worker is the only
 * thing missing.
 *
 * Storage note for when it does land: OPFS is per-origin and browsers evict
 * it under pressure. navigator.storage.persist() has to be requested, and iOS
 * only honours it for home-screen web apps -- which is the difference between
 * a review history that survives and one that quietly disappears.
 */
fun AppDatabase.Companion.getDatabase(): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = DATABASE_NAME)
        .setDriver(WebWorkerSQLiteDriver(createSqliteWorker()))
        .addMigrations(*ALL_MIGRATIONS)
        .build()

/**
 * The worker described above. One function, so that supplying the asset is a
 * one-line change once it exists.
 */
private fun createSqliteWorker(): Worker =
    Worker(js("""new URL("sqlite-web-worker/worker.js", import.meta.url)"""))
