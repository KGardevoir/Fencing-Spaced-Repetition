// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.FsrsState
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.fsrs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardEditorStateTest {

    private val card = Card(id = 7, question = "Lunge", answer = "Front foot first", fsrsStability = 3.0, fsrsState = "REVIEW")

    private fun form(base: Card? = card) = CardEditorState(
        base,
        mutableStateOf(base?.question ?: ""),
        mutableStateOf(TextFieldValue(base?.answer ?: "")),
        mutableStateOf(emptyList()),
        mutableStateOf(emptySet()),
        mutableStateOf(false),
        mutableStateOf(emptySet()),
        mutableStateOf(emptyMap()),
        mutableStateOf(false),
        mutableStateOf(false)
    )

    @Test
    fun loadingDoesNotMarkTheFormChangedNorOverrideTheUser() {
        val form = form()
        form.loadGroups(setOf(1, 2))
        assertFalse(form.isDirty)
        assertNull(form.groupIdsToSave(), "untouched groups are left as they are")

        form.groupIds = setOf(1)
        form.loadGroups(setOf(1, 2, 3))
        assertTrue(form.isDirty)
        assertEquals(listOf(1L), form.groupIdsToSave())
    }

    @Test
    fun takingAConceptOffDropsItsWeight() {
        val form = form()
        form.loadConcepts(setOf(10, 11), mapOf(10L to 2.0, 11L to 0.5))
        assertNull(form.conceptsToSave())
        form.conceptIds = setOf(11)
        assertEquals(mapOf(11L to 0.5), form.conceptsToSave())
        form.setWeight(11, 1.0)
        assertEquals(emptyMap(), form.conceptWeights)
    }

    @Test
    fun progressEditsApplyOverTheStoredStateUntilAReset() {
        val form = form()
        form.question = "Lunge, recovered"
        form.editProgress(null, ProgressEdit(card.fsrs, stability = "4.5", reps = "not a number"))
        val edited = form.editedCard()!!
        assertEquals("Lunge, recovered", edited.question)
        assertEquals(4.5, edited.fsrsStability)
        assertEquals(card.fsrsReps, edited.fsrsReps, "a value that does not parse is left as it was")

        val reset = card.copy(fsrsStability = 0.0, fsrsState = "NEW", lastReset = 1234)
        form.resetDone(null, reset)
        assertNull(form.progress[null])
        assertEquals(1234, form.editedCard()!!.lastReset, "a save after a reset keeps the reset")
        assertEquals("Lunge, recovered", form.editedCard()!!.question)
    }

    @Test
    fun aStagedGradeIsKeptPerContext() {
        val form = form()
        val graded = FsrsState(stability = 2.3, state = "LEARNING", nextReview = 99)
        form.stageGrade(5L, graded, Grade.GOOD)
        assertEquals(Grade.GOOD, form.progress[5L]?.stagedGrade)
        assertEquals(graded, form.progress[5L]?.result())
        assertNull(form.progress[null], "Global is untouched")
        assertEquals(card.fsrs, form.editedCard()!!.fsrs)
    }

    @Test
    fun aNewCardHasNoCardToUpdate() {
        val form = form(base = null)
        form.question = "Parry four"
        assertNull(form.editedCard())
    }
}
