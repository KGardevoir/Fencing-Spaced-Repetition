// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.fencing.spacedrepetition.data.model.Card
import com.fencing.spacedrepetition.data.model.FsrsState
import com.fencing.spacedrepetition.data.model.Grade
import com.fencing.spacedrepetition.data.model.Group
import com.fencing.spacedrepetition.data.model.LearningGroup
import com.fencing.spacedrepetition.data.model.withFsrs

/**
 * One learning context's progress as the card editor holds it: the state
 * loaded, staged by Quick Grade, or reset ([fsrs]); the numbers as typed over
 * it; and the grade Quick Grade staged, logged when the card is saved.
 */
@Immutable
data class ProgressEdit(
    val fsrs: FsrsState = FsrsState(),
    val stability: String = fsrs.stability.toString(),
    val difficulty: String = fsrs.difficulty.toString(),
    val reps: String = fsrs.reps.toString(),
    val lapses: String = fsrs.lapses.toString(),
    val stagedGrade: Grade? = null
) {
    /** What to save: [fsrs] with the typed numbers over it; one that does not parse is left as it was. */
    fun result(): FsrsState = fsrs.copy(
        stability = stability.toDoubleOrNull() ?: fsrs.stability,
        difficulty = difficulty.toDoubleOrNull() ?: fsrs.difficulty,
        reps = reps.toIntOrNull() ?: fsrs.reps,
        lapses = lapses.toIntOrNull() ?: fsrs.lapses
    )
}

/**
 * A learning context the card is learnt in, as the editor shows it: Global,
 * or [learningGroup] reached through [group], one of the card's groups --
 * grading and resetting there go through that group.
 */
@Immutable
data class EditorContext(val learningGroup: LearningGroup?, val group: Group?) {
    /** The context's key: the learning group's id, null for Global. */
    val key: Long? get() = learningGroup?.id
    val name: String get() = learningGroup?.name ?: "Global"

    companion object {
        val GLOBAL = EditorContext(null, null)
    }
}

/**
 * Everything the card editor holds until Save. Setting a field marks the form
 * changed ([isDirty]); the `load` functions, which fill in what arrives from
 * the database after the editor opens, do not, and stop once the user has
 * changed that part themselves.
 *
 * The content, groups and concepts survive the process being stopped (while
 * picking an image, say); progress edits and staged grades do not.
 */
@Stable
class CardEditorState internal constructor(
    base: Card?,
    private val questionState: MutableState<String>,
    private val answerState: MutableState<TextFieldValue>,
    private val imagePathsState: MutableState<List<String>>,
    private val groupIdsState: MutableState<Set<Long>>,
    private val groupsTouchedState: MutableState<Boolean>,
    private val conceptIdsState: MutableState<Set<Long>>,
    private val conceptWeightsState: MutableState<Map<Long, Double>>,
    private val conceptsTouchedState: MutableState<Boolean>,
    private val dirtyState: MutableState<Boolean>
) {
    /** The card as saved, null while adding one. A reset replaces it with the card as reset. */
    var base by mutableStateOf(base)
        private set

    var isDirty by dirtyState

    var question: String
        get() = questionState.value
        set(value) { questionState.value = value; isDirty = true }

    var answer: TextFieldValue
        get() = answerState.value
        set(value) { answerState.value = value; isDirty = true }

    var imagePaths: List<String>
        get() = imagePathsState.value
        set(value) { imagePathsState.value = value; isDirty = true }

    var groupIds: Set<Long>
        get() = groupIdsState.value
        set(value) { groupIdsState.value = value; groupsTouchedState.value = true; isDirty = true }

    /** The concepts on the card; how much of each is [conceptWeights], 1 when absent. */
    var conceptIds: Set<Long>
        get() = conceptIdsState.value
        set(value) {
            conceptIdsState.value = value
            conceptWeightsState.value = conceptWeights.filterKeys { it in value }
            conceptsTouchedState.value = true
            isDirty = true
        }

    val conceptWeights: Map<Long, Double> get() = conceptWeightsState.value

    fun weightOf(conceptId: Long): Double = conceptWeights[conceptId] ?: 1.0

    fun setWeight(conceptId: Long, weight: Double) {
        conceptWeightsState.value = if (weight == 1.0) conceptWeights - conceptId else conceptWeights + (conceptId to weight)
        conceptsTouchedState.value = true
        isDirty = true
    }

    /** Each concept on the card with how much of it the card contains. */
    fun concepts(): Map<Long, Double> = conceptIds.associateWith(::weightOf)

    /** The groups to save, or null to leave them as they are: they were never touched. */
    fun groupIdsToSave(): List<Long>? = groupIds.toList().takeIf { groupsTouchedState.value }

    /** The concepts to save, or null to leave them as they are, as for [groupIdsToSave]. */
    fun conceptsToSave(): Map<Long, Double>? = concepts().takeIf { conceptsTouchedState.value }

    fun loadGroups(ids: Set<Long>) {
        if (!groupsTouchedState.value) groupIdsState.value = ids
    }

    fun loadConcepts(ids: Set<Long>, weights: Map<Long, Double>) {
        if (conceptsTouchedState.value) return
        conceptIdsState.value = ids
        conceptWeightsState.value = weights
    }

    /** Progress edited, graded or reset here, by context key (null is Global); other contexts show as stored. */
    var progress by mutableStateOf(emptyMap<Long?, ProgressEdit>())
        private set

    fun editProgress(context: Long?, edit: ProgressEdit) {
        progress = progress + (context to edit)
        isDirty = true
    }

    /** Quick Grade: [graded] is what grading the stored progress gives; saved, and the grade logged, on Save. */
    fun stageGrade(context: Long?, graded: FsrsState, grade: Grade) = editProgress(context, ProgressEdit(graded, stagedGrade = grade))

    /**
     * A reset, which is saved at once: drops what was edited or staged in
     * [context], so it shows as stored again -- [resetCard] being the card
     * as reset, for Global.
     */
    fun resetDone(context: Long?, resetCard: Card? = null) {
        progress = progress - context
        if (resetCard != null) base = resetCard
    }

    /** The card to save, [base] with the content and Global progress as edited; null while adding one. */
    fun editedCard(): Card? = base?.let { card ->
        val edited = card.copy(question = question, answer = answer.text, imagePaths = imagePaths)
        progress[null]?.let { edited.withFsrs(it.result()) } ?: edited
    }
}

@Composable
fun rememberCardEditorState(card: Card?, initialGroupId: Long?): CardEditorState {
    val question = rememberSaveable { mutableStateOf(card?.question ?: "") }
    val answer = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(card?.answer ?: "")) }
    val imagePaths = rememberSaveable { mutableStateOf(card?.imagePaths.orEmpty()) }
    // A new card starts in the group it was added from; an edited one's load.
    val groupIds = rememberSaveable { mutableStateOf(if (card == null) setOfNotNull(initialGroupId) else emptySet()) }
    val groupsTouched = rememberSaveable { mutableStateOf(false) }
    val conceptIds = rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val conceptWeights = rememberSaveable { mutableStateOf(emptyMap<Long, Double>()) }
    val conceptsTouched = rememberSaveable { mutableStateOf(false) }
    val dirty = rememberSaveable { mutableStateOf(false) }
    return remember {
        CardEditorState(
            card, question, answer, imagePaths, groupIds, groupsTouched,
            conceptIds, conceptWeights, conceptsTouched, dirty
        )
    }
}
