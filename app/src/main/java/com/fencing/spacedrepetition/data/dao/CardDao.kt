package com.fencing.spacedrepetition.data.dao

import androidx.room.*
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardWithGroups
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY nextReview ASC")
    fun getAllCards(): Flow<List<Card>>

    @Query("SELECT * FROM cards ORDER BY nextReview ASC")
    suspend fun getAllCardsSync(): List<Card>

    @Query("SELECT * FROM cards WHERE id = :cardId")
    suspend fun getCardById(cardId: Long): Card?

    @Query("SELECT * FROM cards WHERE id = :cardId")
    fun getCardByIdFlow(cardId: Long): Flow<Card?>

    @Query("SELECT * FROM cards WHERE nextReview <= :now ORDER BY nextReview ASC LIMIT :limit")
    suspend fun getDueCards(now: Long = System.currentTimeMillis(), limit: Int = 100): List<Card>

    @Query("SELECT * FROM cards WHERE nextReview <= :now ORDER BY nextReview ASC LIMIT :limit")
    fun getDueCardsFlow(now: Long = System.currentTimeMillis(), limit: Int = 100): Flow<List<Card>>

    @Query("SELECT COUNT(*) FROM cards WHERE nextReview <= :now")
    fun getDueCardCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT * FROM cards WHERE category = :category ORDER BY nextReview ASC")
    fun getCardsByCategory(category: String): Flow<List<Card>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: Card): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<Card>)

    @Update
    suspend fun updateCard(card: Card)

    @Update
    suspend fun updateCards(cards: List<Card>)

    @Delete
    suspend fun deleteCard(card: Card)

    @Query("DELETE FROM cards WHERE id = :cardId")
    suspend fun deleteCardById(cardId: Long)

    @Query("DELETE FROM cards")
    suspend fun deleteAllCards()

    @Query("SELECT COUNT(*) FROM cards")
    fun getCardCount(): Flow<Int>

    @Query("SELECT DISTINCT category FROM cards WHERE category != '' ORDER BY category")
    fun getAllCategories(): Flow<List<String>>

    // Group-related queries
    @Transaction
    @Query("SELECT * FROM cards WHERE id = :cardId")
    fun getCardWithGroups(cardId: Long): Flow<CardWithGroups?>

    @Transaction
    @Query("SELECT * FROM cards ORDER BY nextReview ASC")
    fun getAllCardsWithGroups(): Flow<List<CardWithGroups>>

    @Query("""
        SELECT c.* FROM cards c
        INNER JOIN card_group_cross_ref cgc ON c.id = cgc.cardId
        INNER JOIN groups g ON cgc.groupId = g.id
        LEFT JOIN card_group_learning_state cgls ON cgls.cardId = c.id AND cgls.groupId = :groupId
        WHERE cgc.groupId = :groupId
        ORDER BY CASE
            WHEN g.independentLearning = 1 THEN COALESCE(cgls.nextReview, c.nextReview)
            ELSE c.nextReview
          END ASC
    """)
    fun getCardsByGroup(groupId: Long): Flow<List<Card>>

    @Query("""
        SELECT c.* FROM cards c
        INNER JOIN card_group_cross_ref cgc ON c.id = cgc.cardId
        INNER JOIN groups g ON cgc.groupId = g.id
        LEFT JOIN card_group_learning_state cgls ON cgls.cardId = c.id AND cgls.groupId = :groupId
        WHERE cgc.groupId = :groupId
        ORDER BY CASE
            WHEN g.independentLearning = 1 THEN COALESCE(cgls.nextReview, c.nextReview)
            ELSE c.nextReview
          END ASC
    """)
    suspend fun getCardsByGroupSync(groupId: Long): List<Card>

    @Query("""
        SELECT c.* FROM cards c
        INNER JOIN card_group_cross_ref cgc ON c.id = cgc.cardId
        INNER JOIN groups g ON cgc.groupId = g.id
        LEFT JOIN card_group_learning_state cgls ON cgls.cardId = c.id AND cgls.groupId = :groupId
        WHERE cgc.groupId = :groupId
          AND CASE
            WHEN g.independentLearning = 1 THEN COALESCE(cgls.nextReview, c.nextReview)
            ELSE c.nextReview
          END <= :now
        ORDER BY CASE
            WHEN g.independentLearning = 1 THEN COALESCE(cgls.nextReview, c.nextReview)
            ELSE c.nextReview
          END ASC
        LIMIT :limit
    """)
    suspend fun getDueCardsByGroup(groupId: Long, now: Long = System.currentTimeMillis(), limit: Int = 100): List<Card>

    @Query("""
        SELECT COUNT(*) FROM cards c
        INNER JOIN card_group_cross_ref cgc ON c.id = cgc.cardId
        INNER JOIN groups g ON cgc.groupId = g.id
        LEFT JOIN card_group_learning_state cgls ON cgls.cardId = c.id AND cgls.groupId = :groupId
        WHERE cgc.groupId = :groupId
          AND CASE
            WHEN g.independentLearning = 1 THEN COALESCE(cgls.nextReview, c.nextReview)
            ELSE c.nextReview
          END <= :now
    """)
    fun getDueCardCountByGroup(groupId: Long, now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM cards c
        INNER JOIN card_group_cross_ref cgc ON c.id = cgc.cardId
        WHERE cgc.groupId = :groupId
    """)
    fun getCardCountByGroup(groupId: Long): Flow<Int>

    @Query("SELECT * FROM cards WHERE question = :question LIMIT 1")
    suspend fun findCardByQuestion(question: String): Card?

    @Query("SELECT * FROM cards")
    suspend fun getAllCardsAsList(): List<Card>
}
