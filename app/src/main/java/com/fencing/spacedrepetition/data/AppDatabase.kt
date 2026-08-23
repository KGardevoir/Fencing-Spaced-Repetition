// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fencing.spacedrepetition.data.dao.CardDao
import com.fencing.spacedrepetition.data.dao.GroupDao
import com.fencing.spacedrepetition.data.dao.OpponentDao
import com.fencing.spacedrepetition.data.dao.PracticeSessionDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.CardGroupLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog

@Database(
    entities = [Card::class, PracticeSession::class, ReviewLog::class, Group::class, CardGroupCrossRef::class, CardGroupLearningState::class, Opponent::class],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun groupDao(): GroupDao
    abstract fun opponentDao(): OpponentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create groups table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `created` INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Create junction table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `card_group_cross_ref` (
                        `cardId` INTEGER NOT NULL,
                        `groupId` INTEGER NOT NULL,
                        PRIMARY KEY(`cardId`, `groupId`),
                        FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON DELETE CASCADE
                    )
                """)

                // Create index for groupId
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_group_cross_ref_groupId` ON `card_group_cross_ref`(`groupId`)")

                // Migrate existing categories to groups
                val currentTime = System.currentTimeMillis()
                db.execSQL("""
                    INSERT INTO `groups` (`name`, `created`)
                    SELECT DISTINCT `category`, $currentTime
                    FROM `cards`
                    WHERE `category` != '' AND `category` IS NOT NULL
                """)

                // Create cross-references for existing cards
                db.execSQL("""
                    INSERT INTO `card_group_cross_ref` (`cardId`, `groupId`)
                    SELECT c.`id`, g.`id`
                    FROM `cards` c
                    INNER JOIN `groups` g ON c.`category` = g.`name`
                    WHERE c.`category` != '' AND c.`category` IS NOT NULL
                """)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add independentLearning column to groups table
                db.execSQL("""
                    ALTER TABLE `groups` ADD COLUMN `independentLearning` INTEGER NOT NULL DEFAULT 0
                """)

                // Create card_group_learning_state table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `card_group_learning_state` (
                        `cardId` INTEGER NOT NULL,
                        `groupId` INTEGER NOT NULL,
                        `fsrsStability` REAL NOT NULL DEFAULT 0.0,
                        `fsrsDifficulty` REAL NOT NULL DEFAULT 0.0,
                        `fsrsElapsedDays` INTEGER NOT NULL DEFAULT 0,
                        `fsrsScheduledDays` INTEGER NOT NULL DEFAULT 0,
                        `fsrsReps` INTEGER NOT NULL DEFAULT 0,
                        `fsrsLapses` INTEGER NOT NULL DEFAULT 0,
                        `fsrsState` TEXT NOT NULL DEFAULT 'NEW',
                        `sm2EaseFactor` REAL NOT NULL DEFAULT 2.5,
                        `sm2Interval` INTEGER NOT NULL DEFAULT 0,
                        `sm2Repetitions` INTEGER NOT NULL DEFAULT 0,
                        `lastReview` INTEGER NOT NULL DEFAULT 0,
                        `nextReview` INTEGER NOT NULL DEFAULT 0,
                        `modified` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`cardId`, `groupId`),
                        FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON DELETE CASCADE
                    )
                """)

                // Create indices for card_group_learning_state
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_group_learning_state_groupId` ON `card_group_learning_state`(`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_card_group_learning_state_cardId` ON `card_group_learning_state`(`cardId`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add imagePaths column to cards table
                db.execSQL("""
                    ALTER TABLE `cards` ADD COLUMN `imagePaths` TEXT NOT NULL DEFAULT ''
                """)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add per-group settings columns (nullable = use global default)
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `cardsPerSession` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `autoShowAnswer` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `randomizeDueCards` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `randomizeBucketHours` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `practiceDays` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `maximumInterval` INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add per-group retention override columns (nullable = use global default)
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `fsrsRetention` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `sm2IntervalModifier` INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add per-group FSRS fuzzing override (nullable = use global default)
                db.execSQL("ALTER TABLE `groups` ADD COLUMN `fsrsEnableFuzzing` INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add groupName column to review_logs
                // null = all-cards practice, group name = within-group practice, "card_edit" = from Add/Edit screen
                db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `groupName` TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add notes and imagePaths columns to review_logs for history annotations
                db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `imagePaths` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create opponents table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `opponents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `skillMultiplier` REAL NOT NULL DEFAULT 1.0,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `created` INTEGER NOT NULL DEFAULT 0,
                        `modified` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_opponents_name` ON `opponents`(`name`)")

                // Add opponent reference + applied stability multiplier to review_logs.
                // opponentId is a soft reference (no FK) so deleting an opponent
                // does not cascade into historical logs.
                db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `opponentId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `stabilityMultiplier` REAL NOT NULL DEFAULT 1.0")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cards` ADD COLUMN `isDisabled` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fencing_spaced_repetition_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    // Intentionally no fallbackToDestructiveMigration(): a migration that
                    // doesn't match Room's expected schema should crash loudly (data stays
                    // on disk, recoverable) rather than silently drop and recreate every
                    // table, which wipes all cards/groups/history/opponents.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
