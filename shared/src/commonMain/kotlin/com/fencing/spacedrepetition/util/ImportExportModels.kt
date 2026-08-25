// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import com.fencing.spacedrepetition.data.model.AlgorithmType
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
    val algorithm: AlgorithmType? = null,
    val stateContext: String? = null,  // "GLOBAL" or group name for group-specific state
    val nextReview: Long? = null,
    val lastReview: Long? = null,
    val fsrsStability: Double? = null,
    val fsrsDifficulty: Double? = null,
    val fsrsState: String? = null,
    val fsrsReps: Int? = null,
    val fsrsLapses: Int? = null,
    val fsrsScheduledDays: Int? = null,
    val fsrsElapsedDays: Int? = null,
    val sm2EaseFactor: Double? = null,
    val sm2Interval: Int? = null,
    val sm2Repetitions: Int? = null,
    val groupNames: List<String> = emptyList()
) {
    val hasFullState: Boolean get() = algorithm != null
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
 * Data class for exporting a card with group-specific learning states
 */
data class CardWithGroupStates(
    val card: Card,
    val groupNames: List<String>,
    val groupSpecificStates: Map<String, com.fencing.spacedrepetition.data.model.CardGroupLearningState> = emptyMap()
)
