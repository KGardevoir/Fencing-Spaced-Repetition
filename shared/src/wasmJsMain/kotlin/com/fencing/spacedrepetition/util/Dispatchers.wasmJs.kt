// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Default, because a browser page has one thread and Dispatchers.IO is not
 * declared for this target at all. Nothing moves off the main thread here; the
 * work yields where it awaits and no further.
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
