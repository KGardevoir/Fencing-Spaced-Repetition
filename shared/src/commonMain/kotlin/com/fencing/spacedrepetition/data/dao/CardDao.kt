// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.dao

import androidx.room3.*
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardWithGroups
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY nextReview ASC")
    fun getAllCards(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE isDisabled = 0 ORDER BY nextReview ASC")
    suspend fun getAllCardsSync(): List<Card>

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCardById(cardId: Long): Card?

    @Query("SELECT * FROM cards WHERE nextReview <= :now AND isDisabled = 0 ORDER BY nextReview ASC LIMIT :limit")
    suspend fun getDueCards(now: Long = Time.now(), limit: Int = 100): List<Card>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: Card): Long

    @Update
    suspend fun updateCard(card: Card)

    @Update
    suspend fun updateCards(cards: List<Card>)

    @Query("DELETE FROM cards WHERE id = :cardId")
    suspend fun deleteCardById(cardId: Long)

    @Query("SELECT COUNT(*) FROM cards")
    fun getCardCount(): Flow<Int>

    // Group-related queries
    @Transaction
    @Query("SELECT * FROM cards ORDER BY nextReview ASC")
    fun getAllCardsWithGroups(): Flow<List<CardWithGroups>>

    @Query("""
        SELECT DISTINCT c.* FROM cards c
        INNER JOIN card_group_cross_ref cgc ON c.id = cgc.cardId
        INNER JOIN groups g ON cgc.groupId = g.id
        LEFT JOIN card_learning_state cls ON cls.cardId = c.id AND cls.learningGroupId = g.learningGroupId
        WHERE cgc.groupId = :groupId AND c.isDisabled = 0
        ORDER BY CASE
            WHEN g.learningGroupId IS NOT NULL THEN COALESCE(cls.nextReview, c.nextReview)
            ELSE c.nextReview
          END ASC
    """)
    suspend fun getCardsByGroupSync(groupId: Long): List<Card>

    @Query("""
        SELECT DISTINCT c.* FROM cards c
        INNER JOIN card_group_cross_ref cgc ON c.id = cgc.cardId
        INNER JOIN groups g ON cgc.groupId = g.id
        LEFT JOIN card_learning_state cls ON cls.cardId = c.id AND cls.learningGroupId = g.learningGroupId
        WHERE cgc.groupId = :groupId
          AND c.isDisabled = 0
          AND CASE
            WHEN g.learningGroupId IS NOT NULL THEN COALESCE(cls.nextReview, c.nextReview)
            ELSE c.nextReview
          END <= :now
        ORDER BY CASE
            WHEN g.learningGroupId IS NOT NULL THEN COALESCE(cls.nextReview, c.nextReview)
            ELSE c.nextReview
          END ASC
        LIMIT :limit
    """)
    suspend fun getDueCardsByGroup(groupId: Long, now: Long = Time.now(), limit: Int = 100): List<Card>

    @Query("""
        SELECT COUNT(DISTINCT c.id) FROM cards c
        INNER JOIN card_group_cross_ref cgc ON c.id = cgc.cardId
        WHERE cgc.groupId = :groupId
    """)
    fun getCardCountByGroup(groupId: Long): Flow<Int>

    @Query("SELECT * FROM cards WHERE uid = :uid LIMIT 1")
    suspend fun findCardByUid(uid: String): Card?

    @Query("SELECT * FROM cards WHERE question = :question LIMIT 1")
    suspend fun findCardByQuestion(question: String): Card?

    @Query("SELECT * FROM cards")
    suspend fun getAllCardsAsList(): List<Card>
}
