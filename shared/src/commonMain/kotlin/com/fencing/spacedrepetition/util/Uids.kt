// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A new stable identifier: a random (version 4) UUID in its canonical
 * lower-case text form.
 *
 * Every record a person creates and might rename, move or carry to another
 * device -- cards, card groups, learning groups, opponents, concepts,
 * sessions, review logs -- gets one when it is made and keeps it for life.
 * Row ids are this database's own and names are labels; the uid is what
 * "the same thing" means. See docs/DESIGN.md, "Identity".
 */
@OptIn(ExperimentalUuidApi::class)
fun newUid(): String = Uuid.random().toString()
