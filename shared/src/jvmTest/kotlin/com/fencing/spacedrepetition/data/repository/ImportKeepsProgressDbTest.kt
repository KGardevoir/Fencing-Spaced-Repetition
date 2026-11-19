// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardConceptCrossRef
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.CardLearningState
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.ConceptLearningGroupCrossRef
import com.fencing.spacedrepetition.data.model.ConceptLearningState
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.PracticeSession
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.preferences.SchedulingPreferences
import com.fencing.spacedrepetition.util.CardImportExport
import com.fencing.spacedrepetition.util.ParsedCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An import updates everything but progress: what cards, concepts, groups,
 * opponents and sessions are and how they are arranged comes from the file,
 * while every card's and concept's learning state stays as it is here. So a
 * program can edit an export -- rename, rewrite, regroup, retag -- and the
 * result can be imported without resetting anyone's learning.
 */
class ImportKeepsProgressDbTest {

    private val prefs = object : SchedulingPreferences {
        override val randomizeDueCards: Flow<Boolean> = flowOf(false)
        override val randomizeBucketHours: Flow<Int> = flowOf(4)
        override val maximumInterval: Flow<Int> = flowOf(36500)
        override val practiceDays: Flow<Set<Int>> = flowOf((1..7).toSet())
        override val fsrsRetention: Flow<Int> = flowOf(90)
        override val fsrsEnableFuzzing: Flow<Boolean> = flowOf(false)
        override val globalConceptScheduling: Flow<Boolean> = flowOf(true)
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: CardRepository
    private lateinit var concepts: ConceptRepository

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
        repo = CardRepository(
            db.cardDao(), db.practiceSessionDao(), db.reviewLogDao(), db.groupDao(),
            db.opponentDao(), prefs, db.conceptDao()
        )
        concepts = ConceptRepository(db.conceptDao())
    }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun import(vararg records: ParsedCard, groups: Map<String, Long> = emptyMap(), learningGroups: Map<String, Long> = emptyMap()) =
        repo.archive.importCards(records.toList(), groups, learningGroups) { CardImportExport.parsedCardToCard(it) }

    @Test
    fun `a card already here takes the file's content and keeps its progress`() = runTest {
        val bouting = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting"))
        val oldGroup = db.groupDao().insertGroup(Group(name = "Old"))
        val newGroup = db.groupDao().insertGroup(Group(name = "New", learningGroupId = bouting))
        val id = db.cardDao().insertCard(
            Card(question = "Lunge", answer = "old", fsrsStability = 9.0, fsrsReps = 4, fsrsState = "REVIEW",
                lastReview = 100L, nextReview = 200L, lastReset = 50L)
        )
        val uid = db.cardDao().getCardById(id)!!.uid
        db.groupDao().insertCardGroupCrossRef(CardGroupCrossRef(id, oldGroup))
        db.groupDao().insertLearningState(CardLearningState(id, bouting, fsrsReps = 7, fsrsState = "REVIEW", lastReset = 60L))

        // The file renames it, rewrites it, moves it, and carries other progress.
        import(
            ParsedCard(
                concept = "Lunge (advance)", answer = "new", lineNumber = 1, stateContext = "GLOBAL", uid = uid,
                fsrsStability = 1.0, fsrsReps = 1, fsrsState = "LEARNING", nextReview = 999L, lastReset = 0L,
                groupNames = listOf("New")
            ),
            ParsedCard(
                concept = "Lunge (advance)", answer = "new", lineNumber = 1, stateContext = "Bouting",
                learningGroupState = true, fsrsReps = 1, fsrsState = "LEARNING"
            ),
            groups = mapOf("New" to newGroup),
            learningGroups = mapOf("Bouting" to bouting)
        )

        val card = db.cardDao().getCardById(id)!!
        assertEquals("Lunge (advance)", card.question)
        assertEquals("new", card.answer)
        assertEquals(listOf(newGroup), db.groupDao().getGroupsForCardSync(id).map { it.id })
        // Progress untouched, Global and learning group alike.
        assertEquals(9.0, card.fsrsStability)
        assertEquals(4, card.fsrsReps)
        assertEquals("REVIEW", card.fsrsState)
        assertEquals(200L, card.nextReview)
        assertEquals(50L, card.lastReset)
        val state = db.groupDao().getLearningState(id, bouting)!!
        assertEquals(7, state.fsrsReps)
        assertEquals(60L, state.lastReset)
        assertEquals(1, db.cardDao().getAllCardsAsList().size)
    }

    @Test
    fun `a card new here takes the progress the file carries`() = runTest {
        val bouting = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting"))
        import(
            ParsedCard(concept = "Fleche", answer = "", lineNumber = 1, stateContext = "GLOBAL", fsrsReps = 3, fsrsState = "REVIEW", lastReset = 40L),
            ParsedCard(concept = "Fleche", answer = "", lineNumber = 1, stateContext = "Bouting", learningGroupState = true, fsrsReps = 2, fsrsState = "REVIEW"),
            learningGroups = mapOf("Bouting" to bouting)
        )
        val card = db.cardDao().getAllCardsAsList().single()
        assertEquals(3, card.fsrsReps)
        assertEquals(40L, card.lastReset)
        assertEquals(2, db.groupDao().getLearningState(card.id, bouting)!!.fsrsReps)
    }

    @Test
    fun `a concept already here takes the file's description and availability and keeps its progress`() = runTest {
        val bouting = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting", useConcepts = true))
        val drills = db.groupDao().insertLearningGroup(LearningGroup(name = "Drills"))
        val id = db.conceptDao().insertConcept(Concept(name = "Distance", description = "old", fsrsReps = 5, fsrsState = "REVIEW"))
        db.conceptDao().insertConceptLearningGroup(ConceptLearningGroupCrossRef(id, bouting))
        db.conceptDao().upsertConceptLearningState(ConceptLearningState(id, bouting, fsrsReps = 6, fsrsState = "REVIEW"))
        val learningGroupIds = mapOf("Bouting" to bouting, "Drills" to drills)

        concepts.importConcepts(
            listOf(
                CardImportExport.ParsedConcept(
                    name = "Distance", description = "new", inGlobal = true, availableIn = listOf("Drills"),
                    globalState = ConceptLearningState(0, 0, fsrsReps = 1, fsrsState = "LEARNING")
                )
            ),
            emptySet()
        ) { learningGroupIds.getValue(it) }

        val concept = db.conceptDao().getConceptById(id)!!
        assertEquals("new", concept.description)
        assertTrue(concept.inGlobal)
        assertEquals(listOf(drills), db.conceptDao().getLearningGroupIdsForConcept(id))
        assertEquals(5, concept.fsrsReps)
        assertEquals(6, db.conceptDao().getConceptLearningState(id, bouting)!!.fsrsReps)

        // A file that does not say where it is available leaves that alone;
        // one that says nowhere, clears it.
        concepts.importConcepts(listOf(CardImportExport.ParsedConcept(name = "Distance", availableIn = null)), emptySet()) { learningGroupIds.getValue(it) }
        assertEquals(listOf(drills), db.conceptDao().getLearningGroupIdsForConcept(id))
        concepts.importConcepts(listOf(CardImportExport.ParsedConcept(name = "Distance", availableIn = emptyList())), emptySet()) { learningGroupIds.getValue(it) }
        assertTrue(db.conceptDao().getLearningGroupIdsForConcept(id).isEmpty())
        assertTrue(!db.conceptDao().getConceptById(id)!!.inGlobal)
    }

    @Test
    fun `a card's concepts are the file's when it says, even none`() = runTest {
        val a = db.conceptDao().insertConcept(Concept(name = "A"))
        val b = db.conceptDao().insertConcept(Concept(name = "B"))
        val id = db.cardDao().insertCard(Card(question = "Q", answer = ""))
        db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(id, a))
        val ids = mapOf("A" to a, "B" to b)
        fun record(names: List<String>, stated: Boolean) =
            ParsedCard(concept = "Q", answer = "", lineNumber = 1, conceptNames = names, conceptsStated = stated)

        repo.archive.linkImportedConcepts(listOf(record(emptyList(), stated = false)), ids)
        assertEquals(listOf(a), db.conceptDao().getConceptsForCardSync(id).map { it.id })
        repo.archive.linkImportedConcepts(listOf(record(listOf("B"), stated = true)), ids)
        assertEquals(listOf(b), db.conceptDao().getConceptsForCardSync(id).map { it.id })
        repo.archive.linkImportedConcepts(listOf(record(emptyList(), stated = true)), ids)
        assertTrue(db.conceptDao().getConceptsForCardSync(id).isEmpty())
    }

    @Test
    fun `an opponent already here takes the file's name, notes and multiplier`() = runTest {
        val opponents = OpponentRepository(db.opponentDao())
        val id = db.opponentDao().insertOpponent(Opponent(name = "Alex", skillMultiplier = 1.0, notes = "old"))
        val uid = db.opponentDao().getOpponentById(id)!!.uid
        val ids = opponents.ensureOpponentsExist(listOf(Triple("Alexandra", 1.4, "new")), mapOf("Alexandra" to uid))
        assertEquals(id, ids["Alexandra"])
        val alex = db.opponentDao().getOpponentById(id)!!
        assertEquals("Alexandra", alex.name)
        assertEquals(1.4, alex.skillMultiplier)
        assertEquals("new", alex.notes)
    }

    @Test
    fun `a session and review already here take the file's notes and keep their history`() = runTest {
        val cardId = db.cardDao().insertCard(Card(question = "Q", answer = ""))
        val sessionId = db.practiceSessionDao().insertSession(PracticeSession(startTime = 10L, endTime = 20L, notes = "old"))
        val session = db.practiceSessionDao().getSessionById(sessionId)!!
        repo.archive.ensureSession(session.copy(id = 0, endTime = 99L, notes = "new"))
        val after = db.practiceSessionDao().getSessionById(sessionId)!!
        assertEquals("new", after.notes)
        assertEquals(20L, after.endTime)

        val log = ReviewLog(
            cardId = cardId, sessionId = sessionId, reviewTime = 15L, grade = 3, algorithm = "FSRS",
            stateBefore = "", stateAfter = "", scheduledDays = 1, elapsedDays = 0, notes = "old"
        )
        repo.archive.importReviewLogs(listOf(log))
        repo.archive.importReviewLogs(listOf(log.copy(notes = "new", stabilityMultiplier = 2.0)))
        val stored = db.reviewLogDao().getAllReviewLogsSync().single()
        assertEquals("new", stored.notes)
        assertEquals(1.0, stored.stabilityMultiplier)
        assertEquals(3, stored.grade)
    }
}
