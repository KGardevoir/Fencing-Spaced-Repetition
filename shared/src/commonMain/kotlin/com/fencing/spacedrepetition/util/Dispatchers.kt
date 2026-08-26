// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Where the slow half of import and export runs.
 *
 * Parsing a deck, formatting one, and base64-ing every image in it are all
 * work measured in seconds on a large collection, and all of it used to sit
 * behind Dispatchers.IO in the Android view models. Now that those methods are
 * shared, they need a name for that dispatcher which also resolves in a
 * browser -- where Dispatchers.IO does not exist, because there is only ever
 * the one thread.
 *
 * So this is not a promise of parallelism. On Android it is Dispatchers.IO and
 * keeps the work off the frame loop; in a browser it is Dispatchers.Default,
 * which is the same thread the UI draws on and merely lets the coroutine
 * suspend at the points it already suspends at.
 */
expect val ioDispatcher: CoroutineDispatcher
