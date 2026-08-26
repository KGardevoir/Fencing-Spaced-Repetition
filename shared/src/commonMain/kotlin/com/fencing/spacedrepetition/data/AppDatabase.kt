// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverters
import androidx.room3.migration.Migration
import androidx.sqlite.*
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
import com.fencing.spacedrepetition.util.Time

@Database(
    entities = [Card::class, PracticeSession::class, ReviewLog::class, Group::class, CardGroupCrossRef::class, CardGroupLearningState::class, Opponent::class],
    version = 12,
    exportSchema = true
)
@ColumnTypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun groupDao(): GroupDao
    abstract fun opponentDao(): OpponentDao

    // Empty, but not pointless: each platform's builder is an extension on
    // this companion -- getDatabase(context) in androidMain -- which is what
    // lets call sites stay unchanged while the builder itself, and the driver
    // it needs, stay off common code.
    companion object
}

/**
 * Room cannot find the generated implementation reflectively on every target,
 * so a multiplatform database names its constructor. The compiler writes the
 * actual object per target; there is no hand-written actual anywhere, which is
 * what the suppression says.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** The on-disk name of the database. Every platform's builder uses it. */
const val DATABASE_NAME = "fencing_spaced_repetition_database"

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Create groups table
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `groups` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `created` INTEGER NOT NULL DEFAULT 0
            )
        """)

        // Create junction table
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `card_group_cross_ref` (
                `cardId` INTEGER NOT NULL,
                `groupId` INTEGER NOT NULL,
                PRIMARY KEY(`cardId`, `groupId`),
                FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON DELETE CASCADE
            )
        """)

        // Create index for groupId
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_card_group_cross_ref_groupId` ON `card_group_cross_ref`(`groupId`)")

        // Migrate existing categories to groups
        val currentTime = Time.now()
        connection.execSQL("""
            INSERT INTO `groups` (`name`, `created`)
            SELECT DISTINCT `category`, $currentTime
            FROM `cards`
            WHERE `category` != '' AND `category` IS NOT NULL
        """)

        // Create cross-references for existing cards
        connection.execSQL("""
            INSERT INTO `card_group_cross_ref` (`cardId`, `groupId`)
            SELECT c.`id`, g.`id`
            FROM `cards` c
            INNER JOIN `groups` g ON c.`category` = g.`name`
            WHERE c.`category` != '' AND c.`category` IS NOT NULL
        """)
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Add independentLearning column to groups table
        connection.execSQL("""
            ALTER TABLE `groups` ADD COLUMN `independentLearning` INTEGER NOT NULL DEFAULT 0
        """)

        // Create card_group_learning_state table
        connection.execSQL("""
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
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_card_group_learning_state_groupId` ON `card_group_learning_state`(`groupId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_card_group_learning_state_cardId` ON `card_group_learning_state`(`cardId`)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Add imagePaths column to cards table
        connection.execSQL("""
            ALTER TABLE `cards` ADD COLUMN `imagePaths` TEXT NOT NULL DEFAULT ''
        """)
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Add per-group settings columns (nullable = use global default)
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN `cardsPerSession` INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN `autoShowAnswer` INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN `randomizeDueCards` INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN `randomizeBucketHours` INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN `practiceDays` TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN `maximumInterval` INTEGER DEFAULT NULL")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Add per-group retention override columns (nullable = use global default)
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN `fsrsRetention` INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN `sm2IntervalModifier` INTEGER DEFAULT NULL")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Add per-group FSRS fuzzing override (nullable = use global default)
        connection.execSQL("ALTER TABLE `groups` ADD COLUMN `fsrsEnableFuzzing` INTEGER DEFAULT NULL")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Add groupName column to review_logs
        // null = all-cards practice, group name = within-group practice, "card_edit" = from Add/Edit screen
        connection.execSQL("ALTER TABLE `review_logs` ADD COLUMN `groupName` TEXT DEFAULT NULL")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Add notes and imagePaths columns to review_logs for history annotations
        connection.execSQL("ALTER TABLE `review_logs` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `review_logs` ADD COLUMN `imagePaths` TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Create opponents table
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `opponents` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `skillMultiplier` REAL NOT NULL DEFAULT 1.0,
                `notes` TEXT NOT NULL DEFAULT '',
                `created` INTEGER NOT NULL DEFAULT 0,
                `modified` INTEGER NOT NULL DEFAULT 0
            )
        """)
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_opponents_name` ON `opponents`(`name`)")

        // Add opponent reference + applied stability multiplier to review_logs.
        // opponentId is a soft reference (no FK) so deleting an opponent
        // does not cascade into historical logs.
        connection.execSQL("ALTER TABLE `review_logs` ADD COLUMN `opponentId` INTEGER DEFAULT NULL")
        connection.execSQL("ALTER TABLE `review_logs` ADD COLUMN `stabilityMultiplier` REAL NOT NULL DEFAULT 1.0")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `cards` ADD COLUMN `isDisabled` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Drops SM-2. The algorithm is gone, so the columns that held its per-card
 * state go with it: `cards`.`algorithm` (FSRS is now the only scheduler),
 * `cards`.`sm2*` and `card_group_learning_state`.`sm2*`, plus the per-group
 * `sm2IntervalModifier` override.
 *
 * Recreate-and-copy rather than ALTER TABLE ... DROP COLUMN: minSdk is 24,
 * whose bundled SQLite predates DROP COLUMN by a long way.
 *
 * Cards that were on SM-2 keep their `lastReview`/`nextReview`, so nothing
 * comes due sooner than the user expects, but they carry no FSRS stability or
 * difficulty -- SM-2 never wrote any. FSRS treats them as new at their next
 * review and builds real state from there.
 */
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `cards_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `question` TEXT NOT NULL,
                `answer` TEXT NOT NULL,
                `category` TEXT NOT NULL DEFAULT '',
                `tags` TEXT NOT NULL DEFAULT '',
                `imagePaths` TEXT NOT NULL DEFAULT '',
                `fsrsStability` REAL NOT NULL DEFAULT 0.0,
                `fsrsDifficulty` REAL NOT NULL DEFAULT 0.0,
                `fsrsElapsedDays` INTEGER NOT NULL DEFAULT 0,
                `fsrsScheduledDays` INTEGER NOT NULL DEFAULT 0,
                `fsrsReps` INTEGER NOT NULL DEFAULT 0,
                `fsrsLapses` INTEGER NOT NULL DEFAULT 0,
                `fsrsState` TEXT NOT NULL DEFAULT 'NEW',
                `lastReview` INTEGER NOT NULL DEFAULT 0,
                `nextReview` INTEGER NOT NULL DEFAULT 0,
                `created` INTEGER NOT NULL DEFAULT 0,
                `modified` INTEGER NOT NULL DEFAULT 0,
                `isDisabled` INTEGER NOT NULL DEFAULT 0
            )
        """)
        connection.execSQL("""
            INSERT INTO `cards_new` (
                `id`, `question`, `answer`, `category`, `tags`, `imagePaths`,
                `fsrsStability`, `fsrsDifficulty`, `fsrsElapsedDays`, `fsrsScheduledDays`,
                `fsrsReps`, `fsrsLapses`, `fsrsState`,
                `lastReview`, `nextReview`, `created`, `modified`, `isDisabled`
            )
            SELECT
                `id`, `question`, `answer`, `category`, `tags`, `imagePaths`,
                `fsrsStability`, `fsrsDifficulty`, `fsrsElapsedDays`, `fsrsScheduledDays`,
                `fsrsReps`, `fsrsLapses`, `fsrsState`,
                `lastReview`, `nextReview`, `created`, `modified`, `isDisabled`
            FROM `cards`
        """)
        connection.execSQL("DROP TABLE `cards`")
        connection.execSQL("ALTER TABLE `cards_new` RENAME TO `cards`")

        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `card_group_learning_state_new` (
                `cardId` INTEGER NOT NULL,
                `groupId` INTEGER NOT NULL,
                `fsrsStability` REAL NOT NULL DEFAULT 0.0,
                `fsrsDifficulty` REAL NOT NULL DEFAULT 0.0,
                `fsrsElapsedDays` INTEGER NOT NULL DEFAULT 0,
                `fsrsScheduledDays` INTEGER NOT NULL DEFAULT 0,
                `fsrsReps` INTEGER NOT NULL DEFAULT 0,
                `fsrsLapses` INTEGER NOT NULL DEFAULT 0,
                `fsrsState` TEXT NOT NULL DEFAULT 'NEW',
                `lastReview` INTEGER NOT NULL DEFAULT 0,
                `nextReview` INTEGER NOT NULL DEFAULT 0,
                `modified` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`cardId`, `groupId`),
                FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON DELETE CASCADE
            )
        """)
        connection.execSQL("""
            INSERT INTO `card_group_learning_state_new` (
                `cardId`, `groupId`,
                `fsrsStability`, `fsrsDifficulty`, `fsrsElapsedDays`, `fsrsScheduledDays`,
                `fsrsReps`, `fsrsLapses`, `fsrsState`,
                `lastReview`, `nextReview`, `modified`
            )
            SELECT
                `cardId`, `groupId`,
                `fsrsStability`, `fsrsDifficulty`, `fsrsElapsedDays`, `fsrsScheduledDays`,
                `fsrsReps`, `fsrsLapses`, `fsrsState`,
                `lastReview`, `nextReview`, `modified`
            FROM `card_group_learning_state`
        """)
        connection.execSQL("DROP TABLE `card_group_learning_state`")
        connection.execSQL("ALTER TABLE `card_group_learning_state_new` RENAME TO `card_group_learning_state`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_card_group_learning_state_groupId` ON `card_group_learning_state`(`groupId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_card_group_learning_state_cardId` ON `card_group_learning_state`(`cardId`)")

        // groups: drop the per-group SM-2 interval modifier override.
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `groups_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `independentLearning` INTEGER NOT NULL DEFAULT 0,
                `created` INTEGER NOT NULL DEFAULT 0,
                `cardsPerSession` INTEGER DEFAULT NULL,
                `autoShowAnswer` INTEGER DEFAULT NULL,
                `randomizeDueCards` INTEGER DEFAULT NULL,
                `randomizeBucketHours` INTEGER DEFAULT NULL,
                `practiceDays` TEXT DEFAULT NULL,
                `maximumInterval` INTEGER DEFAULT NULL,
                `fsrsRetention` INTEGER DEFAULT NULL,
                `fsrsEnableFuzzing` INTEGER DEFAULT NULL
            )
        """)
        connection.execSQL("""
            INSERT INTO `groups_new` (
                `id`, `name`, `description`, `independentLearning`, `created`,
                `cardsPerSession`, `autoShowAnswer`, `randomizeDueCards`,
                `randomizeBucketHours`, `practiceDays`, `maximumInterval`,
                `fsrsRetention`, `fsrsEnableFuzzing`
            )
            SELECT
                `id`, `name`, `description`, `independentLearning`, `created`,
                `cardsPerSession`, `autoShowAnswer`, `randomizeDueCards`,
                `randomizeBucketHours`, `practiceDays`, `maximumInterval`,
                `fsrsRetention`, `fsrsEnableFuzzing`
            FROM `groups`
        """)
        connection.execSQL("DROP TABLE `groups`")
        connection.execSQL("ALTER TABLE `groups_new` RENAME TO `groups`")
    }
}

/**
 * Every migration, oldest first. The SQL is identical on every platform, so
 * each platform's builder passes this same array and there is nothing here
 * for a target to override.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
)
