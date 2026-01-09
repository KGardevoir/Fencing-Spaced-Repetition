package com.fencing.spacedrepetition.data.dao

import androidx.room.*
import com.fencing.spacedrepetition.data.model.Card
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY nextReview ASC")
    fun getAllCards(): Flow<List<Card>>

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
}
