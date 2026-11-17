// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.fencing.spacedrepetition.data.ALL_MIGRATIONS
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.model.Card
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
 * Stable identifiers, references by identifier and reset times, against a
 * real Room database. See docs/DESIGN.md, "Identity", "Resets" and "History".
 */
class IdentityDbTest {

    private val prefs = object : SchedulingPreferences {
        override val randomizeDueCards: Flow<Boolean> = flowOf(false)
        override val randomizeBucketHours: Flow<Int> = flowOf(4)
        override val maximumInterval: Flow<Int> = flowOf(36500)
        override val practiceDays: Flow<Set<Int>> = flowOf((1..7).toSet())
        override val fsrsRetention: Flow<Int> = flowOf(90)
        override val fsrsEnableFuzzing: Flow<Boolean> = flowOf(false)
        override val globalConceptScheduling: Flow<Boolean> = flowOf(false)
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: CardRepository

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        repo = CardRepository(
            db.cardDao(), db.practiceSessionDao(), db.reviewLogDao(), db.groupDao(), db.opponentDao(), prefs,
            db.conceptDao()
        )
    }

    @AfterTest
    fun tearDown() = db.close()

    private val day = 86_400_000L

    private suspend fun card(question: String) =
        db.cardDao().insertCard(Card(question = question, answer = ""))

    @Test
    fun `new reviews record the group's stable identifier`() = runTest {
        val groupId = db.groupDao().insertGroup(Group(name = "Footwork"))
        val c = db.cardDao().getCardById(card("c"))!!
        repo.reviewCard(c, Grade.GOOD, groupId = groupId)
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
            db.opponentDao().getOpponentById(db.opponentDao().insertOpponent(Opponent(name = "O")))!!.uid
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
        assertEquals(1, repo.importReviewLogs(listOf(first)))
        // The same review again: by identifier, and -- for a file from before
        // identifiers -- by card, moment and grade.
        assertEquals(0, repo.importReviewLogs(listOf(first)))
        assertEquals(0, repo.importReviewLogs(listOf(first.copy(uid = "fresh-from-an-old-file"))))
        assertEquals(1, repo.importReviewLogs(listOf(first.copy(uid = "another", reviewTime = t + 1))))
        assertEquals(2, db.reviewLogDao().getAllReviewLogsSync().size)
    }

    @Test
    fun `an imported card is found by identifier even after its question changed`() = runTest {
        val id = card("Old question")
        val uid = db.cardDao().getCardById(id)!!.uid
        val parsed = com.fencing.spacedrepetition.util.ParsedCard(
            concept = "New question", answer = "", lineNumber = 1, stateContext = "GLOBAL", uid = uid
        )
        repo.importCardsWithGroupStates(listOf(parsed), emptyMap()) {
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
        repo.reviewCard(c, Grade.GOOD, opponentId = opponentId)
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
        repo.reviewCard(c, Grade.GOOD, sessionId = session, opponentId = alex)

        repo.updateSessionOpponent(session, sam)
        val log = db.reviewLogDao().getAllReviewLogsSync().single()
        assertEquals(sam, log.opponentId)
        assertEquals(db.opponentDao().getOpponentById(sam)!!.uid, log.opponentUid)

        repo.updateReviewLogOpponent(log, null)
        val cleared = db.reviewLogDao().getAllReviewLogsSync().single()
        assertNull(cleared.opponentId)
        assertNull(cleared.opponentUid)
    }

    @Test
    fun `a reset records when, and keeps the history`() = runTest {
        val learningGroupId = db.groupDao().insertLearningGroup(LearningGroup(name = "Bouting"))
        val c = db.cardDao().getCardById(card("c"))!!
        val before = Time.now()
        repo.reviewCard(c, Grade.GOOD)

        repo.resetCardStateInLearningGroup(c.id, learningGroupId)
        assertTrue(db.groupDao().getLearningState(c.id, learningGroupId)!!.lastReset >= before)
        assertEquals(0L, db.cardDao().getCardById(c.id)!!.lastReset)

        // A full reset stamps the card and every learning group, held or not.
        val other = db.groupDao().insertLearningGroup(LearningGroup(name = "Drills"))
        repo.resetCardState(c.id, resetGroupStates = true)
        assertTrue(db.cardDao().getCardById(c.id)!!.lastReset >= before)
        assertTrue(db.groupDao().getLearningState(c.id, other)!!.lastReset >= before)
        assertEquals(1, db.reviewLogDao().getAllReviewLogsSync().size)
    }

    /**
     * The real version-17 database, built from the schema Room exported for
     * it rather than from the migration tests' hand-written v11, which is
     * simplified in places Room would object to.
     */
    private fun createSchemaV17(path: String) {
        val json = File("schemas/com.fencing.spacedrepetition.data.AppDatabase/17.json").readText()
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
            connection.execSQL("PRAGMA user_version = 17")
        } finally {
            connection.close()
        }
    }

    @Test
    fun `Room accepts a database migrated from version 17`() = runTest {
        val file = File.createTempFile("identity-migration", ".db").apply { delete() }
        try {
            createSchemaV17(file.absolutePath)
            val migrated = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .addMigrations(*ALL_MIGRATIONS)
                .build()
            try {
                // Opening runs 17 -> 22 -> 23 and validates every table against the
                // entities; the backfill itself is Migration17To22Test's.
                assertEquals(0, migrated.cardDao().getAllCardsAsList().size)
            } finally {
                migrated.close()
            }
        } finally {
            file.delete()
        }
    }
}
