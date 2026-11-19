// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.fencing.spacedrepetition.data.ALL_MIGRATIONS
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.ReleasedSchemas
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardSelection
import com.fencing.spacedrepetition.data.model.SelectionMode
import com.fencing.spacedrepetition.data.model.SelectionReason
import com.fencing.spacedrepetition.data.model.CardConceptCrossRef
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.conceptShares
import com.fencing.spacedrepetition.data.model.fsrs
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.model.Opponent
import com.fencing.spacedrepetition.data.model.ReviewLog
import com.fencing.spacedrepetition.data.preferences.SchedulingPreferences
import com.fencing.spacedrepetition.util.Time
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drawing cards by concept and grading concepts, against a real Room database.
 */
class ConceptSchedulingDbTest {

    /** Global's concept-scheduling setting, which each test may flip. */
    private var globalConcepts: Flow<Boolean> = flowOf(true)

    private val prefs = object : SchedulingPreferences {
        override val randomizeDueCards: Flow<Boolean> = flowOf(false)
        override val randomizeBucketHours: Flow<Int> = flowOf(4)
        override val maximumInterval: Flow<Int> = flowOf(36500)
        override val practiceDays: Flow<Set<Int>> = flowOf((1..7).toSet())
        override val fsrsRetention: Flow<Int> = flowOf(90)
        override val fsrsEnableFuzzing: Flow<Boolean> = flowOf(false)
        override val globalConceptScheduling: Flow<Boolean> get() = globalConcepts
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: CardRepository

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repo = CardRepository(
            db.cardDao(), db.practiceSessionDao(), db.reviewLogDao(), db.groupDao(),
            db.opponentDao(), prefs, db.conceptDao()
        )
    }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun card(question: String, nextReview: Long = 0L) =
        db.cardDao().insertCard(Card(question = question, answer = "", nextReview = nextReview))

    private suspend fun concept(name: String, nextReview: Long = 0L) =
        db.conceptDao().insertConcept(Concept(name = name, nextReview = nextReview))

    private suspend fun tag(cardId: Long, vararg conceptIds: Long) =
        conceptIds.forEach { db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(cardId, it)) }

    @Test
    fun `draws by dot product of each card's shares with the concepts' need`() = runTest {
        // Every concept here is new, so each has the full need of 1.
        val a = concept("A"); val b = concept("B"); val c = concept("C")
        val c1 = card("half A, half B"); tag(c1, a, b)
        val c2 = card("all A"); tag(c2, a)
        val c3 = card("all C"); tag(c3, c)
        val c4 = card("untagged")   // new: a need of 1 of its own
        val c5 = card("all B"); tag(c5, b)

        // Each card meets 1 to start with; the tie goes to the lowest id, c1.
        // c1 then leaves half of A and half of B, so c3 and c4, still meeting
        // a whole 1 each, come before c2 and c5, which now meet 0.5.
        assertEquals(listOf(c1, c3), repo.drawing.drawCards(2, null).map { it.id })
        assertEquals(listOf(c1, c3, c4), repo.drawing.drawCards(3, null).map { it.id })
        assertEquals(listOf(c1, c3, c4, c2, c5), repo.drawing.drawCards(5, null).map { it.id })
    }

    @Test
    fun `of two cards for the same concept, the one graded longer ago is drawn`() = runTest {
        val a = concept("A")
        val recent = db.cardDao().insertCard(Card(question = "graded yesterday", answer = "", lastReview = Time.now() - day))
        val rested = db.cardDao().insertCard(Card(question = "graded a month ago", answer = "", lastReview = Time.now() - 30 * day))
        tag(recent, a); tag(rested, a)
        assertEquals(listOf(rested), repo.drawing.drawCards(1, null).map { it.id })
    }

    @Test
    fun `a concept that is not due does not pull its cards in ahead of due ones`() = runTest {
        val future = Time.now() + 10L * 86_400_000L
        val notDue = concept("Later", nextReview = future)
        val due = concept("Now")
        val waiting = card("waiting"); tag(waiting, notDue)
        val ready = card("ready"); tag(ready, due)

        assertEquals(listOf(ready), repo.drawing.drawCards(1, null).map { it.id })
    }

    private val day = 86_400_000L

    /** A review in a card's history, written directly so its time can be chosen. */
    private suspend fun log(
        cardId: Long, grade: Grade, time: Long, group: String? = null, session: Long? = null,
        groupUid: String? = null, uid: String? = null
    ) = db.reviewLogDao().insertReviewLog(
        ReviewLog(
            cardId = cardId, sessionId = session, reviewTime = time, grade = grade.value,
            algorithm = "FSRS", stateBefore = "", stateAfter = "", scheduledDays = 0, elapsedDays = 0,
            groupName = group, groupUid = groupUid
        ).let { if (uid != null) it.copy(uid = uid) else it }
    )

    private suspend fun global(conceptId: Long) = db.conceptDao().getConceptById(conceptId)!!

    @Test
    fun `tagging a card derives the concept's state from that card's history`() = runTest {
        val a = concept("A")
        val practised = card("practised")
        val t0 = Time.now() - 20 * day
        log(practised, Grade.GOOD, t0)
        log(practised, Grade.GOOD, t0 + 3 * day)

        repo.conceptDerivation.setCardConcepts(practised, listOf(a))
        val derived = global(a)
        assertEquals(2, derived.fsrsReps)
        assertEquals(t0 + 3 * day, derived.lastReview)
        assertTrue(derived.fsrsStability > 0)

        // Untagging takes the history away again: no cards, no history, new.
        repo.conceptDerivation.setCardConcepts(practised, emptyList())
        assertEquals("NEW", global(a).fsrsState)
        assertEquals(0, global(a).fsrsReps)
        assertEquals(0L, global(a).nextReview)
    }

    @Test
    fun `a concept whose cards have no history stays uninitialised`() = runTest {
        val a = concept("A")
        val fresh = card("fresh")
        repo.conceptDerivation.setCardConcepts(fresh, listOf(a))
        assertEquals("NEW", global(a).fsrsState)
        assertEquals(0, global(a).fsrsReps)
    }

    @Test
    fun `history from several cards interleaves in time order`() = runTest {
        val a = concept("A"); val onlyOne = concept("only one")
        val one = card("one"); val two = card("two")
        val t0 = Time.now() - 30 * day
        log(one, Grade.GOOD, t0)
        log(two, Grade.AGAIN, t0 + 5 * day)
        log(one, Grade.GOOD, t0 + 10 * day)

        repo.conceptDerivation.setCardConcepts(one, listOf(a, onlyOne))
        repo.conceptDerivation.setCardConcepts(two, listOf(a))
        val derived = global(a)
        assertEquals(3, derived.fsrsReps)
        assertEquals(t0 + 10 * day, derived.lastReview)
        // The second card's AGAIN, in the middle, leaves the concept weaker
        // than the first card's history alone does.
        assertEquals(2, global(onlyOne).fsrsReps)
        assertTrue(derived.fsrsStability < global(onlyOne).fsrsStability)
    }

    @Test
    fun `a session is one concept review, graded with the mean of its cards`() = runTest {
        val learningGroupId = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting", useConcepts = true))
        val groupId = db.groupDao().insertGroup(Group(name = "Footwork", learningGroupId = learningGroupId))
        val a = concept("A"); val b = concept("B"); val skippedOnly = concept("C")
        val c1 = card("A and B"); tag(c1, a, b)
        val c2 = card("A"); tag(c2, a)
        val c3 = card("skipped C"); tag(c3, skippedOnly)
        listOf(c1, c2, c3).forEach { db.groupDao().insertCardGroupCrossRef(CardGroupCrossRef(it, groupId)) }

        // Graded the way a practice session grades them.
        val session = repo.createPracticeSession(listOf(c1, c2, c3))
        val cards = listOf(c1, c2, c3).map { db.cardDao().getCardById(it)!! }
        repo.grading.reviewCardWithGroup(cards[0], Grade.GOOD, groupId, session)
        repo.grading.reviewCardWithGroup(cards[1], Grade.AGAIN, groupId, session)
        repo.grading.reviewCardWithGroup(cards[2], Grade.SKIP, groupId, session)
        repo.conceptDerivation.rederiveConceptsForCards(listOf(c1, c2, c3))

        val stateA = db.conceptDao().getConceptLearningState(a, learningGroupId)!!
        val stateB = db.conceptDao().getConceptLearningState(b, learningGroupId)!!
        assertEquals(1, stateA.fsrsReps)
        assertEquals(1, stateB.fsrsReps)
        // A: mean of GOOD and AGAIN is HARD, which is weaker than B's GOOD.
        assertTrue(stateA.fsrsStability < stateB.fsrsStability)
        // Skips say nothing about their concepts.
        assertNull(db.conceptDao().getConceptLearningState(skippedOnly, learningGroupId))
        // Global is separate progress; the learning group's history does not reach it.
        assertEquals(0, global(a).fsrsReps)

        // A Global review (no group) moves Global only.
        repo.grading.reviewCard(cards[0], Grade.EASY)
        repo.conceptDerivation.rederiveConceptsForCards(listOf(c1))
        assertEquals(1, global(a).fsrsReps)
        assertEquals(1, db.conceptDao().getConceptLearningState(a, learningGroupId)!!.fsrsReps)

        // Untagging c2 rederives A from c1 alone: GOOD, the same as B.
        repo.conceptDerivation.setCardConcepts(c2, emptyList())
        assertEquals(
            stateB.fsrsStability,
            db.conceptDao().getConceptLearningState(a, learningGroupId)!!.fsrsStability
        )
    }

    @Test
    fun `rederiving is deterministic and follows card deletion`() = runTest {
        val a = concept("A")
        val keep = card("keep"); val drop = card("drop")
        val t0 = Time.now() - 40 * day
        log(keep, Grade.GOOD, t0)
        log(drop, Grade.HARD, t0 + day)
        log(keep, Grade.EASY, t0 + 15 * day)
        tag(keep, a); tag(drop, a)

        repo.conceptDerivation.rederiveConcepts()
        val first = global(a)
        repo.conceptDerivation.rederiveConcepts()
        assertEquals(first.globalState(), global(a).globalState())
        assertEquals(3, first.fsrsReps)

        repo.deleteCardById(drop)
        assertEquals(2, global(a).fsrsReps)
    }

    @Test
    fun `a review whose group no longer exists cannot be placed and is left out`() = runTest {
        val a = concept("A")
        val c = card("c")
        log(c, Grade.GOOD, Time.now() - day, group = "Deleted group")
        repo.conceptDerivation.setCardConcepts(c, listOf(a))
        assertEquals(0, global(a).fsrsReps)
    }

    @Test
    fun `an import keeps a concept's state when there is no history to derive it from`() = runTest {
        val a = db.conceptDao().insertConcept(Concept(name = "A", fsrsReps = 4, fsrsState = "REVIEW", fsrsStability = 9.0))
        val c = card("c"); tag(c, a)
        repo.conceptDerivation.rederiveConcepts(onlyWithHistory = true)
        assertEquals(4, global(a).fsrsReps)
        // A full rederive is the rule, though: no history, new.
        repo.conceptDerivation.rederiveConcepts()
        assertEquals(0, global(a).fsrsReps)
    }

    @Test
    fun `the group's learning group decides which concept state is due`() = runTest {
        val learningGroupId = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting", useConcepts = true))
        val groupId = db.groupDao().insertGroup(Group(name = "Footwork", learningGroupId = learningGroupId))
        // Not due in Global, but never practised (so due) in the learning group.
        val a = concept("A", nextReview = Time.now() + 30L * 86_400_000L)
        val tagged = card("tagged", nextReview = Time.now() + 30L * 86_400_000L); tag(tagged, a)
        db.groupDao().insertCardGroupCrossRef(CardGroupCrossRef(tagged, groupId))

        assertEquals(listOf(tagged), repo.drawing.drawCards(1, groupId).map { it.id })
    }

    @Test
    fun `with concept scheduling off Global draws card by card`() = runTest {
        globalConcepts = flowOf(false)
        val a = concept("A"); val b = concept("B")
        // Card order is due order; tags make no difference.
        val first = card("first", nextReview = 1L); tag(first, a)
        val second = card("second", nextReview = 2L)
        val third = card("third", nextReview = 3L); tag(third, a, b)

        assertEquals(listOf(first, second), repo.drawing.drawCards(2, null).map { it.id })
    }

    @Test
    fun `each learning group has its own switch, independent of Global`() = runTest {
        globalConcepts = flowOf(true)
        val off = db.groupDao().insertLearningGroup(LearningGroup(name = "Drills"))
        val on = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting", useConcepts = true))
        val offGroup = db.groupDao().insertGroup(Group(name = "Off", learningGroupId = off))
        val onGroup = db.groupDao().insertGroup(Group(name = "On", learningGroupId = on))
        val a = concept("A"); val b = concept("B")
        val ab = card("A and B", nextReview = 5L); tag(ab, a, b)
        val onlyA = card("only A", nextReview = 1L); tag(onlyA, a)
        listOf(ab, onlyA).forEach {
            db.groupDao().insertCardGroupCrossRef(CardGroupCrossRef(it, offGroup))
            db.groupDao().insertCardGroupCrossRef(CardGroupCrossRef(it, onGroup))
        }

        assertTrue(repo.scheduler.usesConcepts(on))
        assertTrue(!repo.scheduler.usesConcepts(off))
        // Off: soonest-due card. On: the card covering the most due concepts.
        assertEquals(listOf(onlyA), repo.drawing.drawCards(1, offGroup).map { it.id })
        assertEquals(listOf(ab), repo.drawing.drawCards(1, onGroup).map { it.id })

        // State is derived in both, whether or not they schedule by it.
        val card = db.cardDao().getCardById(ab)!!
        repo.grading.reviewCardWithGroup(card, Grade.GOOD, offGroup)
        repo.grading.reviewCardWithGroup(card, Grade.GOOD, onGroup)
        repo.conceptDerivation.rederiveConceptsForCards(listOf(ab))
        assertEquals(1, db.conceptDao().getConceptLearningState(a, off)!!.fsrsReps)
        assertEquals(1, db.conceptDao().getConceptLearningState(a, on)!!.fsrsReps)

        // Switching a learning group off takes effect at once.
        db.groupDao().setLearningGroupUseConcepts(on, false)
        assertEquals(listOf(onlyA), repo.drawing.drawCards(1, onGroup).map { it.id })
    }

    // ==================== stable identifiers ====================

    @Test
    fun `a renamed group's history still counts where the group learns`() = runTest {
        val learningGroupId = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting"))
        val groupId = db.groupDao().insertGroup(Group(name = "Footwork", learningGroupId = learningGroupId))
        val group = db.groupDao().getGroupById(groupId)!!
        val a = concept("A")
        val c = card("c"); tag(c, a)
        log(c, Grade.GOOD, Time.now() - 3 * day, group = "Footwork", groupUid = group.uid)

        db.groupDao().updateGroup(group.copy(name = "Footwork (renamed)"))
        repo.conceptDerivation.rederiveConcepts()
        assertEquals(1, db.conceptDao().getConceptLearningState(a, learningGroupId)!!.fsrsReps)
        assertEquals(0, global(a).fsrsReps)
    }

    @Test
    fun `new reviews record the group's stable identifier`() = runTest {
        val groupId = db.groupDao().insertGroup(Group(name = "Footwork"))
        val c = db.cardDao().getCardById(card("c"))!!
        repo.grading.reviewCard(c, Grade.GOOD, groupId = groupId)
        val log = db.reviewLogDao().getAllReviewLogsSync().single()
        assertEquals(db.groupDao().getGroupById(groupId)!!.uid, log.groupUid)
        assertTrue(log.uid.isNotBlank())
    }

    @Test
    fun `every new record is made with its own identifier`() = runTest {
        val uids = listOf(
            db.cardDao().getCardById(card("one"))!!.uid,
            db.cardDao().getCardById(card("two"))!!.uid,
            db.groupDao().getGroupById(db.groupDao().insertGroup(Group(name = "G")))!!.uid,
            db.conceptDao().getConceptById(concept("C"))!!.uid
        )
        assertEquals(uids.size, uids.toSet().size)
        assertTrue(uids.all { Regex("[0-9a-f-]{36}").matches(it) })
    }

    @Test
    fun `imported history is not counted twice`() = runTest {
        val c = card("c")
        val t = Time.now() - day
        val first = ReviewLog(cardId = c, sessionId = null, reviewTime = t, grade = 3, algorithm = "FSRS",
            stateBefore = "", stateAfter = "", scheduledDays = 0, elapsedDays = 0)
        assertEquals(1, repo.archive.importReviewLogs(listOf(first)))
        // The same review again: by identifier, and -- for a file from before
        // identifiers -- by card, moment and grade.
        assertEquals(0, repo.archive.importReviewLogs(listOf(first)))
        assertEquals(0, repo.archive.importReviewLogs(listOf(first.copy(uid = "fresh-from-an-old-file"))))
        assertEquals(1, repo.archive.importReviewLogs(listOf(first.copy(uid = "another", reviewTime = t + 1))))
        assertEquals(2, db.reviewLogDao().getAllReviewLogsSync().size)
    }

    @Test
    fun `an imported card is found by identifier even after its question changed`() = runTest {
        val id = card("Old question")
        val uid = db.cardDao().getCardById(id)!!.uid
        val parsed = com.fencing.spacedrepetition.util.ParsedCard(
            concept = "New question", answer = "", lineNumber = 1, stateContext = "GLOBAL", uid = uid
        )
        repo.archive.importCards(listOf(parsed), emptyMap()) {
            com.fencing.spacedrepetition.util.CardImportExport.parsedCardToCard(it)
        }
        val cards = db.cardDao().getAllCardsAsList()
        assertEquals(1, cards.size)
        assertEquals("New question", cards.single().question)
        assertEquals(uid, cards.single().uid)
    }

    @Test
    fun `an imported group renamed elsewhere is renamed here, not duplicated`() = runTest {
        val groups = GroupRepository(db.groupDao(), db.cardDao())
        val id = db.groupDao().insertGroup(Group(name = "Footwork"))
        val uid = db.groupDao().getGroupById(id)!!.uid
        groups.adoptGroupIdentities(mapOf("Footwork drills" to uid, "Brand new" to "uid-new"))
        assertEquals("Footwork drills", db.groupDao().getGroupById(id)!!.name)
        assertEquals("uid-new", db.groupDao().getGroupByName("Brand new")!!.uid)
        assertEquals(2, db.groupDao().getGroupCount().first())
    }

    @Test
    fun `reviews record who they were against, and find them again after a re-import`() = runTest {
        val opponentId = db.opponentDao().insertOpponent(Opponent(name = "Alex"))
        val alex = db.opponentDao().getOpponentById(opponentId)!!
        val c = db.cardDao().getCardById(card("c"))!!
        repo.grading.reviewCard(c, Grade.GOOD, opponentId = opponentId)
        val log = db.reviewLogDao().getAllReviewLogsSync().single()
        assertEquals(alex.uid, log.opponentUid)

        // Deleted: the review keeps who it was. Re-added by an import, under
        // a new local id and even a new name: the review is theirs again.
        db.opponentDao().deleteOpponent(alex)
        val readded = db.opponentDao().insertOpponent(Opponent(name = "Alexandra", uid = alex.uid))
        repo.reconcileOpponentReferences()
        assertEquals(readded, db.reviewLogDao().getAllReviewLogsSync().single().opponentId)
    }

    @Test
    fun `changing a review's opponent from history changes who it identifies`() = runTest {
        val alex = db.opponentDao().insertOpponent(Opponent(name = "Alex"))
        val sam = db.opponentDao().insertOpponent(Opponent(name = "Sam"))
        val session = repo.createPracticeSession(emptyList())
        val c = db.cardDao().getCardById(card("c"))!!
        repo.grading.reviewCard(c, Grade.GOOD, sessionId = session, opponentId = alex)

        repo.updateSessionOpponent(session, sam)
        val log = db.reviewLogDao().getAllReviewLogsSync().single()
        assertEquals(sam, log.opponentId)
        assertEquals(db.opponentDao().getOpponentById(sam)!!.uid, log.opponentUid)

        repo.updateReviewLogOpponent(log, null)
        val cleared = db.reviewLogDao().getAllReviewLogsSync().single()
        assertNull(cleared.opponentId)
        assertNull(cleared.opponentUid)
    }

    // ==================== availability ====================

    @Test
    fun `saving a concept sets where it is available and leaves its cards alone`() = runTest {
        val concepts = ConceptRepository(db.conceptDao())
        val bouting = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting", useConcepts = true))
        val a = concept("A")
        val c = card("c"); tag(c, a)

        concepts.saveConcept(db.conceptDao().getConceptById(a)!!.copy(inGlobal = true), setOf(bouting))
        assertTrue(db.conceptDao().getConceptById(a)!!.inGlobal)
        assertEquals(listOf(bouting), db.conceptDao().getLearningGroupIdsForConcept(a))
        assertEquals(listOf(c), db.conceptDao().getCardIdsForConcept(a))

        concepts.saveConcept(db.conceptDao().getConceptById(a)!!.copy(inGlobal = false), emptySet())
        assertTrue(db.conceptDao().getLearningGroupIdsForConcept(a).isEmpty())
        assertEquals(listOf(c), db.conceptDao().getCardIdsForConcept(a))
    }

    @Test
    fun `a concept created from the card editor is available where the card is learnt`() = runTest {
        val concepts = ConceptRepository(db.conceptDao())
        val bouting = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting", useConcepts = true))
        val id = concepts.getOrCreateConceptIn("Distance", setOf(bouting, null))
        assertTrue(db.conceptDao().getConceptById(id)!!.inGlobal)
        assertEquals(listOf(bouting), db.conceptDao().getLearningGroupIdsForConcept(id))
        // Asking again for the same name finds it, adding nothing twice.
        assertEquals(id, concepts.getOrCreateConceptIn("distance", setOf(bouting)))
        assertEquals(listOf(bouting), db.conceptDao().getLearningGroupIdsForConcept(id))
    }

    // ==================== resets ====================

    @Test
    fun `resetting a card sets aside the reviews before it`() = runTest {
        val a = concept("A")
        val c = card("c"); tag(c, a)
        val t0 = Time.now() - 30 * day
        log(c, Grade.GOOD, t0)
        log(c, Grade.GOOD, t0 + 5 * day)
        repo.conceptDerivation.rederiveConcepts()
        assertEquals(2, global(a).fsrsReps)

        repo.resetCardState(c)
        val reset = db.cardDao().getCardById(c)!!
        assertTrue(reset.lastReset > t0 + 5 * day)
        assertEquals(0, global(a).fsrsReps)

        // Progress after the reset counts again.
        log(c, Grade.EASY, Time.now() + 1)
        repo.conceptDerivation.rederiveConcepts()
        assertEquals(1, global(a).fsrsReps)
        // The history itself is all still there.
        assertEquals(3, db.reviewLogDao().getAllReviewLogsSync().size)
    }

    @Test
    fun `resetting in a learning group leaves Global's history counting`() = runTest {
        val learningGroupId = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting"))
        val groupId = db.groupDao().insertGroup(Group(name = "Footwork", learningGroupId = learningGroupId))
        val groupUid = db.groupDao().getGroupById(groupId)!!.uid
        val a = concept("A")
        val c = card("c"); tag(c, a)
        val t0 = Time.now() - 10 * day
        log(c, Grade.GOOD, t0, group = "Footwork", groupUid = groupUid)
        log(c, Grade.GOOD, t0 + day)
        repo.conceptDerivation.rederiveConcepts()
        assertEquals(1, db.conceptDao().getConceptLearningState(a, learningGroupId)!!.fsrsReps)

        repo.resetCardStateInLearningGroup(c, learningGroupId)
        assertTrue(db.groupDao().getLearningState(c, learningGroupId)!!.lastReset > t0)
        assertNull(db.conceptDao().getConceptLearningState(a, learningGroupId))
        assertEquals(1, global(a).fsrsReps)

        // A full reset stamps every learning group, held or not.
        val other = db.groupDao().insertLearningGroup(LearningGroup(name = "Drills"))
        repo.resetCardState(c, resetGroupStates = true)
        assertTrue(db.groupDao().getLearningState(c, other)!!.lastReset > 0)
        assertEquals(0, global(a).fsrsReps)
    }

    /**
     * A real database at [version], built from the schema Room exported for
     * it rather than from the migration tests' hand-written v11, which is
     * simplified in places Room would object to.
     */
    private fun createFromExportedSchema(schema: File, version: Int, path: String) {
        val json = schema.readText()
        val token = Regex(""""(tableName|createSql)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val setup = Regex(""""setupQueries"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        fun unescape(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")
        val connection = BundledSQLiteDriver().open(path)
        try {
            var table = ""
            token.findAll(json).forEach { match ->
                val value = unescape(match.groupValues[2])
                if (match.groupValues[1] == "tableName") table = value
                else connection.execSQL(value.replace("\${TABLE_NAME}", table))
            }
            setup.find(json)!!.groupValues[1].let { list ->
                Regex(""""((?:[^"\\]|\\.)*)"""").findAll(list).forEach { connection.execSQL(unescape(it.groupValues[1])) }
            }
            connection.execSQL("PRAGMA user_version = $version")
        } finally {
            connection.close()
        }
    }

    private fun openMigrated(path: String) = Room.databaseBuilder<AppDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*ALL_MIGRATIONS)
        .build()

    @Test
    fun `Room accepts a database migrated from version 17`() = runTest {
        val file = File.createTempFile("concepts-migration", ".db").apply { delete() }
        try {
            createFromExportedSchema(File("schemas/com.fencing.spacedrepetition.data.AppDatabase/17.json"), 17, file.absolutePath)
            val migrated = openMigrated(file.absolutePath)
            try {
                // Opening runs 17 -> 18 -> 19 and validates every table against the entities.
                assertEquals(0, migrated.conceptDao().getAllConceptsSync().size)
                assertEquals(0, migrated.cardDao().getAllCardsAsList().size)
            } finally {
                migrated.close()
            }
        } finally {
            file.delete()
        }
    }

    /**
     * Inserts a row with every NOT NULL column given a value -- [values] where
     * given, else a zero or empty string -- whatever the variant's columns are.
     */
    private fun insertRow(path: String, table: String, values: Map<String, Any?>) {
        val connection = BundledSQLiteDriver().open(path)
        try {
            val columns = mutableListOf<Pair<String, Boolean>>()
            val info = connection.prepare("PRAGMA table_info(`$table`)")
            try {
                while (info.step()) columns += info.getText(1) to (info.getLong(3) == 1L)
            } finally {
                info.close()
            }
            val chosen = columns.filter { (name, notNull) -> name in values || notNull }
            fun literal(v: Any?): String = when (v) {
                null -> "NULL"
                is String -> "'" + v.replace("'", "''") + "'"
                else -> v.toString()
            }
            val sql = "INSERT INTO `$table` (" + chosen.joinToString { "`${it.first}`" } + ") VALUES (" +
                chosen.joinToString { (name, _) -> if (name in values) literal(values[name]) else "0" } + ")"
            connection.execSQL(sql)
        } finally {
            connection.close()
        }
    }

    /**
     * Every build of version 18 that was installed can be opened at 19. The
     * schemas are the ones each build exported (src/jvmTest/resources/
     * released-v18), so this is what a phone running each of them has.
     */
    private fun upgradesFromReleasedV18(commit: String) = runTest {
        val file = File.createTempFile("v18-$commit", ".db").apply { delete() }
        try {
            createFromExportedSchema(if (commit == "7141ca0") ReleasedSchemas.schema(18) else ReleasedSchemas.v18Variant(commit), 18, file.absolutePath)
            val path = file.absolutePath
            insertRow(path, "cards", mapOf("id" to 1, "question" to "Lunge", "answer" to "", "imagePaths" to "", "fsrsState" to "REVIEW", "category" to "", "tags" to "", "uid" to "seed-card"))
            insertRow(path, "groups", mapOf("id" to 2, "name" to "Footwork", "description" to "", "uid" to "seed-group"))
            insertRow(path, "card_group_cross_ref", mapOf("cardId" to 1, "groupId" to 2))
            insertRow(path, "opponents", mapOf("id" to 3, "name" to "Alex", "notes" to "", "skillMultiplier" to 1.0, "uid" to "seed-opponent"))
            insertRow(path, "review_logs", mapOf(
                "id" to 4, "cardId" to 1, "reviewTime" to 1000, "grade" to 3, "algorithm" to "FSRS",
                "stateBefore" to "", "stateAfter" to "", "groupName" to "Footwork", "notes" to "",
                "imagePaths" to "", "opponentId" to 3, "stabilityMultiplier" to 1.0,
                // Only where the variant has these columns, as its reviews would.
                "uid" to "seed-review", "groupUid" to "seed-group", "opponentUid" to "seed-opponent"
            ))
            insertRow(path, "concepts", mapOf("id" to 5, "name" to "Distance", "description" to "", "fsrsState" to "NEW", "uid" to "seed-concept"))
            insertRow(path, "card_concept_cross_ref", mapOf("cardId" to 1, "conceptId" to 5))

            val migrated = openMigrated(path)
            try {
                val card = migrated.cardDao().getCardById(1)!!
                val group = migrated.groupDao().getGroupById(2)!!
                val alex = migrated.opponentDao().getOpponentById(3)!!
                val concept = migrated.conceptDao().getConceptById(5)!!
                val log = migrated.reviewLogDao().getAllReviewLogsSync().single()
                assertEquals("Lunge", card.question)
                // Everything kept, and every record identified (filled in on
                // the variant from before identifiers).
                listOf(card.uid, group.uid, alex.uid, concept.uid, log.uid).forEach {
                    assertTrue(it.isNotBlank(), "a record without an identifier")
                }
                assertEquals(listOf(5L), migrated.conceptDao().getConceptsForCardSync(1).map { it.id })
                // The review's group and opponent are identified on every variant.
                assertEquals(alex.uid, log.opponentUid)
                assertEquals(group.uid, log.groupUid)
                // New in 19: availability, empty to start with.
                assertTrue(!concept.inGlobal)
                assertTrue(migrated.conceptDao().getAllConceptLearningGroupsSync().isEmpty())
            } finally {
                migrated.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `upgrades the released version 19`() = runTest {
        val file = File.createTempFile("v19", ".db").apply { delete() }
        try {
            val path = file.absolutePath
            createFromExportedSchema(ReleasedSchemas.schema(19), 19, path)
            insertRow(path, "cards", mapOf("id" to 1, "question" to "Lunge", "answer" to "", "imagePaths" to "", "fsrsState" to "NEW", "category" to "", "tags" to "", "uid" to "seed-card"))
            insertRow(path, "concepts", mapOf("id" to 5, "name" to "Distance", "description" to "", "fsrsState" to "NEW", "uid" to "seed-concept"))
            insertRow(path, "card_concept_cross_ref", mapOf("cardId" to 1, "conceptId" to 5))
            val migrated = openMigrated(path)
            try {
                // Opening runs 19 -> 20 and validates every table against the entities.
                assertEquals(listOf(1.0), migrated.conceptDao().getCardConceptLinksSync(1).map { it.weight })
            } finally {
                migrated.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `upgrades the released version 20`() = runTest {
        val file = File.createTempFile("v20", ".db").apply { delete() }
        try {
            val path = file.absolutePath
            createFromExportedSchema(ReleasedSchemas.schema(20), 20, path)
            insertRow(path, "cards", mapOf("id" to 1, "question" to "Lunge", "answer" to "", "imagePaths" to "", "fsrsState" to "NEW", "category" to "", "tags" to "", "uid" to "seed-card"))
            insertRow(path, "review_logs", mapOf("id" to 1, "cardId" to 1, "reviewTime" to 5, "grade" to 3, "algorithm" to "FSRS", "stateBefore" to "", "stateAfter" to "", "scheduledDays" to 0, "elapsedDays" to 0, "notes" to "", "imagePaths" to "", "stabilityMultiplier" to 1.0, "uid" to "seed-log"))
            val migrated = openMigrated(path)
            try {
                // Opening runs 20 -> 21 and validates every table; older logs say nothing of why.
                val log = migrated.reviewLogDao().getAllReviewLogsSync().single()
                assertNull(log.selectionMode)
                assertNull(log.selectionReason)
                assertNull(log.opponentSkillMultiplier)
            } finally {
                migrated.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `upgrades the released version 21`() = runTest {
        val file = File.createTempFile("v21", ".db").apply { delete() }
        try {
            val path = file.absolutePath
            createFromExportedSchema(ReleasedSchemas.schema(21), 21, path)
            insertRow(path, "cards", mapOf("id" to 1, "question" to "Lunge", "answer" to "", "imagePaths" to "", "fsrsState" to "NEW", "category" to "", "tags" to "", "uid" to "seed-card"))
            insertRow(path, "concepts", mapOf("id" to 5, "name" to "Distance", "description" to "", "fsrsState" to "NEW", "uid" to "seed-concept"))
            insertRow(path, "card_concept_cross_ref", mapOf("cardId" to 1, "conceptId" to 5, "weight" to 2.0))
            val migrated = openMigrated(path)
            try {
                // Opening runs 21 -> 23 and validates every table; nothing is lost.
                assertEquals(listOf(2.0), migrated.conceptDao().getCardConceptLinksSync(1).map { it.weight })
            } finally {
                migrated.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `upgrades master's version 22, which has identifiers but no concepts`() = runTest {
        val file = File.createTempFile("v22", ".db").apply { delete() }
        try {
            val path = file.absolutePath
            createFromExportedSchema(ReleasedSchemas.schema(22), 22, path)
            insertRow(path, "cards", mapOf("id" to 1, "question" to "Lunge", "answer" to "", "imagePaths" to "", "fsrsState" to "NEW", "category" to "", "tags" to "", "uid" to "seed-card"))
            insertRow(path, "learning_groups", mapOf("id" to 3, "name" to "Bouting", "description" to "", "uid" to "seed-lg"))
            val migrated = openMigrated(path)
            try {
                // Opening runs 22 -> 23 and validates every table against the entities.
                assertEquals("seed-card", migrated.cardDao().getCardById(1)!!.uid)
                assertEquals(false, migrated.groupDao().getLearningGroupById(3)!!.useConcepts)
                assertEquals(0, migrated.conceptDao().getAllConceptsSync().size)
            } finally {
                migrated.close()
            }
        } finally {
            file.delete()
        }
    }

    // ==================== why a card was practised ====================

    @Test
    fun `the concept with the most need is the trigger`() = runTest {
        // Same stability, but one was last reviewed much longer ago: FSRS
        // gives it the lower recall now, so the higher need.
        val stale = db.conceptDao().insertConcept(Concept(
            name = "stale", fsrsStability = 2.0, fsrsState = "REVIEW", fsrsReps = 1,
            lastReview = Time.now() - 20 * day, nextReview = Time.now() - 18 * day
        ))
        val fresh = db.conceptDao().insertConcept(Concept(
            name = "fresh", fsrsStability = 2.0, fsrsState = "REVIEW", fsrsReps = 1,
            lastReview = Time.now() - 3 * day, nextReview = Time.now() - day
        ))
        val both = card("both"); tag(both, stale, fresh)
        val onlyFresh = card("only the fresh one"); tag(onlyFresh, fresh)
        val uids = db.conceptDao().getAllConceptsSync().associate { it.id to it.uid }

        // "both" meets half of each need, which beats all of the smaller one.
        val drawn = repo.drawing.drawSession(1, null).single()
        assertEquals(both, drawn.card.id)
        assertEquals(uids.getValue(stale), drawn.selection.triggerConceptUid)
        assertEquals(listOf(uids.getValue(stale), uids.getValue(fresh)).sorted(), drawn.selection.conceptUids)
    }

    @Test
    fun `a session drawn by concept records why each card was chosen`() = runTest {
        val a = concept("A"); val b = concept("B")
        val covering = card("covers A and B"); tag(covering, a, b)
        val untagged = card("untagged")
        val later = card("not due", nextReview = Time.now() + 10 * day)
        val drawn = repo.drawing.drawSession(10, null).associate { it.card.id to it.selection }

        val uids = db.conceptDao().getAllConceptsSync().associate { it.id to it.uid }
        assertEquals(SelectionMode.CONCEPT, drawn.getValue(covering).mode)
        assertEquals(SelectionReason.DUE, drawn.getValue(covering).reason)
        assertEquals(listOf(uids.getValue(a), uids.getValue(b)).sorted(), drawn.getValue(covering).conceptUids)
        // The due concept whose turn picked it is one of those it covers.
        assertTrue(drawn.getValue(covering).triggerConceptUid in drawn.getValue(covering).conceptUids)
        assertEquals(SelectionReason.DUE, drawn.getValue(untagged).reason)
        assertNull(drawn.getValue(untagged).triggerConceptUid)
        assertNull(drawn.getValue(later).triggerConceptUid)
        assertTrue(drawn.getValue(untagged).conceptUids.isEmpty())
        assertEquals(SelectionReason.EXTRA, drawn.getValue(later).reason)
    }

    @Test
    fun `card by card, a session records due and extra study`() = runTest {
        globalConcepts = flowOf(false)
        val due = card("due")
        val later = card("later", nextReview = Time.now() + 10 * day)
        val drawn = repo.drawing.drawSession(2, null).associate { it.card.id to it.selection }
        assertEquals(CardSelection(SelectionMode.CARD, SelectionReason.DUE), drawn.getValue(due))
        assertEquals(CardSelection(SelectionMode.CARD, SelectionReason.EXTRA), drawn.getValue(later))
    }

    @Test
    fun `a graded review records why, the opponent's skill and the fight`() = runTest {
        val a = concept("A")
        val c = card("c"); tag(c, a)
        val rival = db.opponentDao().insertOpponent(Opponent(name = "Rival", skillMultiplier = 1.25))
        val selection = repo.drawing.drawSession(1, null).single().selection
        val graded = repo.getCardById(c)!!
        repo.grading.reviewMultipleCards(listOf(graded to Grade.GOOD), null, rival, fightDifficulty = 4, selections = mapOf(c to selection))

        val log = db.reviewLogDao().getAllReviewLogsSync().single()
        assertEquals("CONCEPT", log.selectionMode)
        assertEquals("DUE", log.selectionReason)
        assertEquals(listOf(db.conceptDao().getAllConceptsSync().single().uid), log.selectionConceptUids)
        assertEquals(db.conceptDao().getAllConceptsSync().single().uid, log.selectionTrigger)
        assertEquals(1.25, log.opponentSkillMultiplier)
        assertEquals(4, log.fightDifficulty)
        // The multiplier applied is the opponent's skill times the fight's.
        assertEquals(
            com.fencing.spacedrepetition.data.model.FightDifficulty.combinedMultiplier(1.25, 4),
            log.stabilityMultiplier
        )

        // A grade from the card editor says so.
        repo.grading.logGradeFromEdit(graded.id, graded.fsrs, graded.fsrs, Grade.HARD)
        assertEquals("EDITOR", db.reviewLogDao().getAllReviewLogsSync().maxBy { it.id }.selectionReason)
    }

    // ==================== weights ====================

    @Test
    fun `a session grades a concept by each card's share of it`() {
        runTest {
            // "barely" is 1 part A to 4 parts B: a fifth A.
            val a = concept("A"); val b = concept("B"); val c = concept("C")
            val allA = card("all A"); val barelyA = card("barely A")
            db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(allA, a, weight = 3.0))
            db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(barelyA, a, weight = 1.0))
            db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(barelyA, b, weight = 4.0))
            val session = repo.createPracticeSession(listOf(allA, barelyA))
            log(allA, Grade.EASY, Time.now() - day, session = session)
            log(barelyA, Grade.AGAIN, Time.now() - day, session = session)

            // The same grades on two cards that are each all C: the plain mean.
            val one = card("one"); val two = card("two")
            tag(one, c); tag(two, c)
            val other = repo.createPracticeSession(listOf(one, two))
            log(one, Grade.EASY, Time.now() - day, session = other)
            log(two, Grade.AGAIN, Time.now() - day, session = other)
            repo.conceptDerivation.rederiveConcepts()

            // A: (4 * 1 + 1 * 0.2) / 1.2 = 3.5 -> EASY; C: 2.5 -> GOOD.
            assertTrue(global(a).fsrsStability > global(c).fsrsStability, "the AGAIN on a card that is barely A should count for less")
            // The raw weight 3 on a card with one concept is still all of it.
            assertEquals(1.0, conceptShares(db.conceptDao().getAllCardConceptCrossRefsSync()).getValue(allA to a))
        }
    }

    @Test
    fun `changing a tag's weight rederives the concept, and 0 means the card does not count`() = runTest {
        val a = concept("A")
        val c = card("c")
        log(c, Grade.GOOD, Time.now() - day)
        repo.conceptDerivation.setCardConcepts(c, listOf(a), mapOf(a to 1.0))
        assertEquals(1, global(a).fsrsReps)
        repo.conceptDerivation.setCardConcepts(c, listOf(a), mapOf(a to 0.0))
        assertEquals(0, global(a).fsrsReps)
        assertEquals(listOf(0.0), db.conceptDao().getCardConceptLinksSync(c).map { it.weight })
    }

    @Test
    fun `drawing prefers, of cards covering as many due concepts, the one most about them`() = runTest {
        val later = Time.now() + 30 * day
        val a = concept("A")
        val b = concept("B", nextReview = later); val c = concept("C", nextReview = later)
        // Both cover the due A once; one is a quarter A, the other half.
        val quarter = card("quarter A")
        db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(quarter, a, weight = 1.0))
        db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(quarter, b, weight = 3.0))
        val half = card("half A")
        db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(half, a, weight = 2.0))
        db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(half, c, weight = 2.0))
        assertEquals(listOf(half), repo.drawing.drawCards(1, null).map { it.id })
    }

    @Test
    fun `upgrades the first version 18 build, from before identifiers`() = upgradesFromReleasedV18("9a9b31d")

    @Test
    fun `upgrades the version 18 build from before review opponent identifiers`() = upgradesFromReleasedV18("2a77072")

    @Test
    fun `upgrades the last version 18 build`() = upgradesFromReleasedV18("7141ca0")
}
