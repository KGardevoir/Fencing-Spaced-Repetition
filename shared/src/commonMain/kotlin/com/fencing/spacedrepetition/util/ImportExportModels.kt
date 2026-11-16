// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.Card

// The data holders the import/export format is expressed in. They are plain
// records over the Room entities -- no streams, no Context, nothing a browser
// lacks -- and the repositories name them in their signatures, so they belong
// with the repositories in common code.
//
// CardImportExport itself stays in :app until its file and gzip I/O grows an
// expect/actual seam. The package name is unchanged, so every existing
// reference still resolves.

/**
 * Represents a parsed card with optional full state
 */
data class ParsedCard(
    val concept: String,
    val answer: String,
    val lineNumber: Int,
    val imagePaths: List<String> = emptyList(), // For export (file paths)
    val imageData: List<String> = emptyList(),  // For import (base64 encoded)
    // Full state (null if simple import)
    val stateContext: String? = null,  // "GLOBAL", a learning group's name, or (legacy) a group's name
    val nextReview: Long? = null,
    val lastReview: Long? = null,
    val fsrsStability: Double? = null,
    val fsrsDifficulty: Double? = null,
    val fsrsState: String? = null,
    val fsrsReps: Int? = null,
    val fsrsLapses: Int? = null,
    val fsrsScheduledDays: Int? = null,
    val fsrsElapsedDays: Int? = null,
    val groupNames: List<String> = emptyList(),
    /**
     * True when [stateContext] names a learning group. False for the states of
     * files from before learning groups, which name the group that learned
     * independently instead.
     */
    val learningGroupState: Boolean = false,
    /** The concepts the card is tagged with, by name. Carried on its own (Global) record. */
    val conceptNames: List<String> = emptyList(),
    /** The card's stable identifier, on its own record; null in files from before them. */
    val uid: String? = null,
    /** When this record's progress was last reset (0 never); null when the file does not say. */
    val lastReset: Long? = null
) {
    // Every full-format row carries a state context ("GLOBAL" or a group
    // name); the two-column simple format carries none.
    val hasFullState: Boolean get() = stateContext != null
    val isGlobalState: Boolean get() = stateContext == null || stateContext == "GLOBAL"
    val isGroupSpecificState: Boolean get() = !isGlobalState
}

/**
 * Data class for exporting a card with its groups
 */
data class CardWithGroupNames(
    val card: Card,
    val groupNames: List<String>
)

/**
 * Data class for exporting a card with its learning group states, keyed by
 * learning group name.
 */
data class CardWithGroupStates(
    val card: Card,
    val groupNames: List<String>,
    val learningStates: Map<String, com.fencing.spacedrepetition.data.model.CardLearningState> = emptyMap(),
    val conceptNames: List<String> = emptyList()
)

/**
 * A learning concept for export: the concept with its Global state, and its
 * state in each learning group, keyed by learning group name.
 */
data class ConceptWithStates(
    val concept: com.fencing.spacedrepetition.data.model.Concept,
    val learningStates: Map<String, com.fencing.spacedrepetition.data.model.ConceptLearningState> = emptyMap()
)
