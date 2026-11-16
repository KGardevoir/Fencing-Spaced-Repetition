// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.fencing.spacedrepetition.data.model.CardConceptCrossRef
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.ConceptLearningState
import kotlinx.coroutines.flow.Flow

@Dao
interface ConceptDao {
    @Query("SELECT * FROM concepts ORDER BY name COLLATE NOCASE ASC")
    fun getAllConcepts(): Flow<List<Concept>>

    @Query("SELECT * FROM concepts ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllConceptsSync(): List<Concept>

    @Query("SELECT * FROM concepts WHERE id = :conceptId")
    suspend fun getConceptById(conceptId: Long): Concept?

    @Query("SELECT * FROM concepts WHERE id IN (:conceptIds)")
    suspend fun getConceptsByIds(conceptIds: List<Long>): List<Concept>

    @Query("SELECT * FROM concepts WHERE uid = :uid LIMIT 1")
    suspend fun getConceptByUid(uid: String): Concept?

    @Query("SELECT * FROM concepts WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getConceptByName(name: String): Concept?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConcept(concept: Concept): Long

    @Update
    suspend fun updateConcept(concept: Concept)

    @Update
    suspend fun updateConcepts(concepts: List<Concept>)

    @Delete
    suspend fun deleteConcept(concept: Concept)

    // Card <-> concept links
    @Query("SELECT * FROM card_concept_cross_ref")
    fun getAllCardConceptCrossRefs(): Flow<List<CardConceptCrossRef>>

    @Query("SELECT * FROM card_concept_cross_ref")
    suspend fun getAllCardConceptCrossRefsSync(): List<CardConceptCrossRef>

    @Query("""
        SELECT c.* FROM concepts c
        INNER JOIN card_concept_cross_ref ccc ON c.id = ccc.conceptId
        WHERE ccc.cardId = :cardId
        ORDER BY c.name COLLATE NOCASE ASC
    """)
    fun getConceptsForCard(cardId: Long): Flow<List<Concept>>

    @Query("""
        SELECT c.* FROM concepts c
        INNER JOIN card_concept_cross_ref ccc ON c.id = ccc.conceptId
        WHERE ccc.cardId = :cardId
        ORDER BY c.name COLLATE NOCASE ASC
    """)
    suspend fun getConceptsForCardSync(cardId: Long): List<Concept>

    @Query("SELECT cardId FROM card_concept_cross_ref WHERE conceptId = :conceptId")
    suspend fun getCardIdsForConcept(conceptId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCardConceptCrossRef(crossRef: CardConceptCrossRef)

    @Delete
    suspend fun deleteCardConceptCrossRef(crossRef: CardConceptCrossRef)

    @Query("DELETE FROM card_concept_cross_ref WHERE cardId = :cardId")
    suspend fun deleteAllConceptsForCard(cardId: Long)

    @Query("DELETE FROM card_concept_cross_ref WHERE conceptId = :conceptId")
    suspend fun deleteAllCardsForConcept(conceptId: Long)

    // Learning-group states
    @Query("SELECT * FROM concept_learning_state")
    fun getAllConceptLearningStates(): Flow<List<ConceptLearningState>>

    @Query("SELECT * FROM concept_learning_state")
    suspend fun getAllConceptLearningStatesSync(): List<ConceptLearningState>

    @Query("SELECT * FROM concept_learning_state WHERE learningGroupId = :learningGroupId")
    suspend fun getConceptLearningStatesForLearningGroup(learningGroupId: Long): List<ConceptLearningState>

    @Query("SELECT * FROM concept_learning_state WHERE conceptId = :conceptId AND learningGroupId = :learningGroupId")
    suspend fun getConceptLearningState(conceptId: Long, learningGroupId: Long): ConceptLearningState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConceptLearningState(state: ConceptLearningState)

    @Query("DELETE FROM concept_learning_state WHERE conceptId = :conceptId AND learningGroupId = :learningGroupId")
    suspend fun deleteConceptLearningState(conceptId: Long, learningGroupId: Long)

    @Query("DELETE FROM concept_learning_state WHERE conceptId = :conceptId")
    suspend fun deleteAllLearningStatesForConcept(conceptId: Long)
}
