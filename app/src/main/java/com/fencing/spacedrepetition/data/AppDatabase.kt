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
import com.fencing.spacedrepetition.data.dao.PracticeSessionDao
import com.fencing.spacedrepetition.data.dao.ReviewLogDao
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog

@Database(
    entities = [Card::class, PracticeSession::class, ReviewLog::class, Group::class, CardGroupCrossRef::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create groups table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `created` INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Create junction table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `card_group_cross_ref` (
                        `cardId` INTEGER NOT NULL,
                        `groupId` INTEGER NOT NULL,
                        PRIMARY KEY(`cardId`, `groupId`),
                        FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON DELETE CASCADE
                    )
                """)

                // Create index for groupId
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_card_group_cross_ref_groupId` ON `card_group_cross_ref`(`groupId`)")

                // Migrate existing categories to groups
                val currentTime = System.currentTimeMillis()
                database.execSQL("""
                    INSERT INTO `groups` (`name`, `created`)
                    SELECT DISTINCT `category`, $currentTime
                    FROM `cards`
                    WHERE `category` != '' AND `category` IS NOT NULL
                """)

                // Create cross-references for existing cards
                database.execSQL("""
                    INSERT INTO `card_group_cross_ref` (`cardId`, `groupId`)
                    SELECT c.`id`, g.`id`
                    FROM `cards` c
                    INNER JOIN `groups` g ON c.`category` = g.`name`
                    WHERE c.`category` != '' AND c.`category` IS NOT NULL
                """)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fencing_spaced_repetition_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
