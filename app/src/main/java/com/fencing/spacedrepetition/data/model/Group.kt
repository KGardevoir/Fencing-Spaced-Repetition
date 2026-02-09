package com.fencing.spacedrepetition.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val independentLearning: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    // Per-group settings overrides (null = use global default)
    val cardsPerSession: Int? = null,
    val autoShowAnswer: Boolean? = null,
    val randomizeDueCards: Boolean? = null,
    val randomizeBucketHours: Int? = null,
    val practiceDays: String? = null,  // comma-separated ISO-8601 day ints (1=Mon..7=Sun)
    val maximumInterval: Int? = null
) {
    /** Returns true if any per-group setting override is set */
    fun hasCustomSettings(): Boolean =
        cardsPerSession != null || autoShowAnswer != null || randomizeDueCards != null ||
        randomizeBucketHours != null || practiceDays != null || maximumInterval != null

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

@Entity(
    tableName = "card_group_learning_state",
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
    indices = [Index("groupId"), Index("cardId")]
)
data class CardGroupLearningState(
    val cardId: Long,
    val groupId: Long,

    // FSRS parameters
    val fsrsStability: Double = 0.0,
    val fsrsDifficulty: Double = 0.0,
    val fsrsElapsedDays: Int = 0,
    val fsrsScheduledDays: Int = 0,
    val fsrsReps: Int = 0,
    val fsrsLapses: Int = 0,
    val fsrsState: String = "NEW", // NEW, LEARNING, REVIEW, RELEARNING

    // SM-2 parameters
    val sm2EaseFactor: Double = 2.5,
    val sm2Interval: Int = 0,
    val sm2Repetitions: Int = 0,

    // Common scheduling fields
    val lastReview: Long = 0L,
    val nextReview: Long = 0L,
    val modified: Long = System.currentTimeMillis()
)

data class CardWithGroups(
    @Embedded val card: Card,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            CardGroupCrossRef::class,
            parentColumn = "cardId",
            entityColumn = "groupId"
        )
    )
    val groups: List<Group>
)

data class GroupWithCards(
    @Embedded val group: Group,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            CardGroupCrossRef::class,
            parentColumn = "groupId",
            entityColumn = "cardId"
        )
    )
    val cards: List<Card>
)
