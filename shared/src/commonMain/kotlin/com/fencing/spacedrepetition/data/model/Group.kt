// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.model

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Junction
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import com.fencing.spacedrepetition.util.Time

/**
 * A set of learning progress that one or more card groups share.
 *
 * Every card keeps one learning state of its own -- the Global one, held on
 * the card itself. A learning group is a second, separate state per card:
 * every card group pointed at it practises and grades against that state
 * instead, so two groups can share progress with each other without sharing
 * it with the rest of the collection. A group pointed at no learning group
 * learns Global, which is the default.
 */
@Entity(tableName = "learning_groups")
data class LearningGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val created: Long = Time.now()
)

@Entity(
    tableName = "groups",
    foreignKeys = [
        ForeignKey(
            entity = LearningGroup::class,
            parentColumns = ["id"],
            childColumns = ["learningGroupId"],
            // Deleting a learning group sends its card groups back to Global.
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("learningGroupId")]
)
data class Group(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    /** The learning group whose state this group practises against; null is Global. */
    val learningGroupId: Long? = null,
    val created: Long = Time.now(),
    // Per-group settings overrides (null = use global default)
    val cardsPerSession: Int? = null,
    val autoShowAnswer: Boolean? = null,
    val randomizeDueCards: Boolean? = null,
    val randomizeBucketHours: Int? = null,
    val practiceDays: String? = null,  // comma-separated ISO-8601 day ints (1=Mon..7=Sun)
    val maximumInterval: Int? = null,
    // Retention overrides (null = use global default)
    val fsrsRetention: Int? = null,         // integer percent 70–97, e.g. 90 for 90 %
    val fsrsEnableFuzzing: Boolean? = null  // override FSRS interval fuzzing setting
) {
    /** Returns true if any per-group setting override is set */
    fun hasCustomSettings(): Boolean =
        cardsPerSession != null || autoShowAnswer != null || randomizeDueCards != null ||
        randomizeBucketHours != null || practiceDays != null || maximumInterval != null ||
        fsrsRetention != null || fsrsEnableFuzzing != null

    /** Parse the practiceDays string to a Set<Int>, or null if not overridden */
    fun parsePracticeDays(): Set<Int>? {
        val raw = practiceDays ?: return null
        return raw.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
    }
}

@Entity(
    tableName = "card_group_cross_ref",
    primaryKeys = ["cardId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class CardGroupCrossRef(
    val cardId: Long,
    val groupId: Long
)

/** One card's learning progress in one [LearningGroup]. */
@Entity(
    tableName = "card_learning_state",
    primaryKeys = ["cardId", "learningGroupId"],
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LearningGroup::class,
            parentColumns = ["id"],
            childColumns = ["learningGroupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("learningGroupId"), Index("cardId")]
)
data class CardLearningState(
    val cardId: Long,
    val learningGroupId: Long,

    // FSRS parameters
    val fsrsStability: Double = 0.0,
    val fsrsDifficulty: Double = 0.0,
    val fsrsElapsedDays: Int = 0,
    val fsrsScheduledDays: Int = 0,
    val fsrsReps: Int = 0,
    val fsrsLapses: Int = 0,
    val fsrsState: String = "NEW", // NEW, LEARNING, REVIEW, RELEARNING

    // Common scheduling fields
    val lastReview: Long = 0L,
    val nextReview: Long = 0L,
    val modified: Long = Time.now()
)

data class CardWithGroups(
    @Embedded val card: Card,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        associateBy = Junction(
            CardGroupCrossRef::class,
            parentColumns = ["cardId"],
            entityColumns = ["groupId"]
        )
    )
    val groups: List<Group>
)

data class GroupWithCards(
    @Embedded val group: Group,
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["id"],
        associateBy = Junction(
            CardGroupCrossRef::class,
            parentColumns = ["groupId"],
            entityColumns = ["cardId"]
        )
    )
    val cards: List<Card>
)
