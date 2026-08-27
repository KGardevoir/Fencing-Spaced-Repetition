// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

/**
 * Schema version 11 -- the last one that had SM-2 -- as SQL.
 *
 * This is what is actually on an existing user's disk before
 * [MIGRATION_11_12] runs, so the migration tests build it and migrate it
 * rather than starting from a Room-created database. Writing it out by hand
 * is the point: a fixture generated from today's entities would follow the
 * entities when they change and could never catch a migration that disagreed
 * with what shipped.
 *
 * Transcribed from the migrations that built it -- 1_2 through 10_11 -- so it
 * carries their DEFAULT clauses and index names exactly.
 */
internal object SchemaV11 {

    val tables: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `cards` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `question` TEXT NOT NULL,
            `answer` TEXT NOT NULL,
            `category` TEXT NOT NULL DEFAULT '',
            `tags` TEXT NOT NULL DEFAULT '',
            `imagePaths` TEXT NOT NULL DEFAULT '',
            `algorithm` TEXT NOT NULL DEFAULT 'FSRS',
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
            `created` INTEGER NOT NULL DEFAULT 0,
            `modified` INTEGER NOT NULL DEFAULT 0,
            `isDisabled` INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `groups` (
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
            `sm2IntervalModifier` INTEGER DEFAULT NULL,
            `fsrsEnableFuzzing` INTEGER DEFAULT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `card_group_cross_ref` (
            `cardId` INTEGER NOT NULL,
            `groupId` INTEGER NOT NULL,
            PRIMARY KEY(`cardId`, `groupId`),
            FOREIGN KEY(`cardId`) REFERENCES `cards`(`id`) ON DELETE CASCADE,
            FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON DELETE CASCADE
        )
        """,
        """
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
        """,
        """
        CREATE TABLE IF NOT EXISTS `practice_sessions` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `startTime` INTEGER NOT NULL DEFAULT 0,
            `endTime` INTEGER DEFAULT NULL,
            `cardsReviewed` INTEGER NOT NULL DEFAULT 0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `review_logs` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `cardId` INTEGER NOT NULL,
            `sessionId` INTEGER DEFAULT NULL,
            `reviewTime` INTEGER NOT NULL DEFAULT 0,
            `grade` INTEGER NOT NULL DEFAULT 0,
            `algorithm` TEXT NOT NULL DEFAULT 'FSRS',
            `stateBefore` TEXT NOT NULL DEFAULT '',
            `stateAfter` TEXT NOT NULL DEFAULT '',
            `scheduledDays` INTEGER NOT NULL DEFAULT 0,
            `elapsedDays` INTEGER NOT NULL DEFAULT 0,
            `groupName` TEXT DEFAULT NULL,
            `notes` TEXT NOT NULL DEFAULT '',
            `imagePaths` TEXT NOT NULL DEFAULT '',
            `opponentId` INTEGER DEFAULT NULL,
            `stabilityMultiplier` REAL NOT NULL DEFAULT 1.0
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS `opponents` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `skillMultiplier` REAL NOT NULL DEFAULT 1.0,
            `notes` TEXT NOT NULL DEFAULT '',
            `created` INTEGER NOT NULL DEFAULT 0,
            `modified` INTEGER NOT NULL DEFAULT 0
        )
        """,
    )

    val indices: List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS `index_card_group_cross_ref_groupId` ON `card_group_cross_ref`(`groupId`)",
        "CREATE INDEX IF NOT EXISTS `index_card_group_learning_state_groupId` ON `card_group_learning_state`(`groupId`)",
        "CREATE INDEX IF NOT EXISTS `index_card_group_learning_state_cardId` ON `card_group_learning_state`(`cardId`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_opponents_name` ON `opponents`(`name`)",
    )
}
