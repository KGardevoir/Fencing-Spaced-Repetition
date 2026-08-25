// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver

@Volatile
private var INSTANCE: AppDatabase? = null

private val LOCK = Any()

/**
 * The Android database. Declared as an extension on AppDatabase's companion
 * rather than a member, because the class itself is common code and a member
 * would drag Context into it.
 *
 * Behaviour is unchanged from when this lived in :app -- same file name, same
 * migrations, same double-checked singleton.
 */
fun AppDatabase.Companion.getDatabase(context: Context): AppDatabase {
    return INSTANCE ?: synchronized(LOCK) {
        val instance = Room.databaseBuilder<AppDatabase>(
            context.applicationContext,
            DATABASE_NAME
        )
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(*ALL_MIGRATIONS)
            // Intentionally no fallbackToDestructiveMigration(): a migration that
            // doesn't match Room's expected schema should crash loudly (data stays
            // on disk, recoverable) rather than silently drop and recreate every
            // table, which wipes all cards/groups/history/opponents.
            .build()
        INSTANCE = instance
        instance
    }
}
