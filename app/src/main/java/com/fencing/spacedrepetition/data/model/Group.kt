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
    val created: Long = System.currentTimeMillis()
)

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
