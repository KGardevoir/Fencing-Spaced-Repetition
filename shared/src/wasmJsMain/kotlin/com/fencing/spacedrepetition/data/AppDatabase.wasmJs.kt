// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

/**
 * The browser database: the same schema, the same name and the same ten
 * migrations as the Android one, on SQLite compiled to WebAssembly.
 *
 * Nothing about SQLite runs on this thread. [WebWorkerSQLiteDriver] posts
 * messages to the worker in third_party/sqlite-web-worker, which owns the
 * SQLite instance and the database file. That is what keeps a long query from
 * freezing the page, and it is also why the worker has to exist as a real
 * asset -- see that directory's README for where it came from.
 *
 * The database file lives in the Origin Private File System. OPFS is
 * per-origin and browsers evict it under storage pressure, so a caller that
 * wants the review history to survive should ask for
 * navigator.storage.persist() -- and on iOS that is only honoured for a web
 * app added to the home screen.
 */
private var INSTANCE: AppDatabase? = null

/**
 * The one database, built on first use and kept.
 *
 * Not an optimisation. Each call to the builder creates its own Web Worker,
 * its own SQLite instance and its own attempt to take the OPFS SAH pool's
 * exclusive lock on the same directory -- so a second live database does not
 * merely waste a megabyte of WebAssembly, it competes with the first for
 * storage that only one holder can have. The Android twin has cached its
 * instance since before this port; this half simply had not, and the browser
 * test suite is what noticed, by opening three databases in one page and
 * taking the browser down with it.
 *
 * No lock around it, unlike Android's: this runs on the browser's single main
 * thread, so there is no second caller to race with. The worker is where the
 * concurrency lives, and it is behind the driver.
 */
fun AppDatabase.Companion.getDatabase(): AppDatabase =
    INSTANCE ?: buildDatabase().also { INSTANCE = it }

private fun buildDatabase(): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = DATABASE_NAME)
        .setDriver(WebWorkerSQLiteDriver(createSqliteWorker()))
        // Room defaults to write-ahead logging, which SQLite's OPFS backends
        // cannot provide: WAL needs shared memory between connections, and
        // there is exactly one connection, inside the worker. Asking for
        // TRUNCATE says what actually happens rather than leaving a default
        // that the storage layer silently declines.
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .addMigrations(*ALL_MIGRATIONS)
        .build()

/**
 * The worker, addressed by module specifier rather than by path.
 *
 * `fencing-sqlite-web-worker` is the local npm package in
 * third_party/sqlite-web-worker; yarn links it into node_modules and webpack
 * resolves this URL at build time, emitting the worker and everything it
 * imports -- SQLite's .wasm included -- as its own chunk. Writing a literal
 * path instead would produce a URL that only works if someone remembers to
 * copy the file into the output directory.
 *
 * The whole expression is inside js() because a Kotlin/Wasm js() body has to
 * be one expression, and because webpack recognises this exact shape --
 * new Worker(new URL(..., import.meta.url)) -- as a worker to bundle.
 */
private fun createSqliteWorker(): Worker =
    js(
        "new Worker(new URL('fencing-sqlite-web-worker/worker.js', import.meta.url), { type: 'module' })"
    )
