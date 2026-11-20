// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.fencing.spacedrepetition.data.AppDatabase
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.CardConceptCrossRef
import com.fencing.spacedrepetition.data.model.CardFilter
import com.fencing.spacedrepetition.data.model.CardGroupCrossRef
import com.fencing.spacedrepetition.data.model.Concept
import com.fencing.spacedrepetition.data.model.Group
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The card list's filter, over what the database really returns. */
class CardFilterDbTest {
    private lateinit var db: AppDatabase

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).build()
    }

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun card(q: String, disabled: Boolean = false, answer: String = "") =
        db.cardDao().insertCard(Card(question = q, answer = answer, isDisabled = disabled))

    private suspend fun shown(filter: CardFilter): List<String> =
        filter.apply(db.cardDao().getAllCardsWithGroups().first(), db.conceptDao().getAllCardConceptCrossRefsSync())
            .map { it.question }.sorted()

    @Test
    fun `each kind of filter, and all of them together`() = runTest {
        val footwork = db.groupDao().insertGroup(Group(name = "Footwork"))
        val blade = db.groupDao().insertGroup(Group(name = "Blade"))
        val distance = db.conceptDao().insertConcept(Concept(name = "Distance"))
        val tempo = db.conceptDao().insertConcept(Concept(name = "Tempo"))

        val lunge = card("Lunge", answer = "long step")
        val advance = card("Advance")
        val parry = card("Parry four")
        val beat = card("Beat", disabled = true)
        val loose = card("Loose card")
        listOf(lunge to footwork, advance to footwork, parry to blade, beat to blade, lunge to blade)
            .forEach { (c, g) -> db.groupDao().insertCardGroupCrossRef(CardGroupCrossRef(c, g)) }
        db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(lunge, distance))
        db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(parry, tempo))
        db.conceptDao().insertCardConceptCrossRef(CardConceptCrossRef(beat, distance))

        assertEquals(listOf("Advance", "Loose card", "Lunge", "Parry four"), shown(CardFilter()))
        // A card in two groups shows once, under either.
        assertEquals(listOf("Advance", "Lunge"), shown(CardFilter(groupIds = setOf(footwork))))
        assertEquals(listOf("Lunge", "Parry four"), shown(CardFilter(groupIds = setOf(blade))))
        assertEquals(listOf("Advance", "Lunge", "Parry four"), shown(CardFilter(groupIds = setOf(footwork, blade))))
        // Concepts: any of them.
        assertEquals(listOf("Lunge"), shown(CardFilter(conceptIds = setOf(distance))))
        assertEquals(listOf("Lunge", "Parry four"), shown(CardFilter(conceptIds = setOf(distance, tempo))))
        // Disabled: only those.
        assertEquals(listOf("Beat"), shown(CardFilter(showDisabled = true)))
        assertEquals(listOf("Beat"), shown(CardFilter(showDisabled = true, conceptIds = setOf(distance))))
        // Groups and concepts together: both.
        assertEquals(listOf("Lunge"), shown(CardFilter(groupIds = setOf(blade), conceptIds = setOf(distance))))
        assertEquals(emptyList(), shown(CardFilter(groupIds = setOf(footwork), conceptIds = setOf(tempo))))
        // Search: question or description, any case, surrounding spaces ignored.
        assertEquals(listOf("Lunge"), shown(CardFilter(query = " LONG ")))
        assertEquals(listOf("Parry four"), shown(CardFilter(query = "parry", groupIds = setOf(blade))))
    }
}
