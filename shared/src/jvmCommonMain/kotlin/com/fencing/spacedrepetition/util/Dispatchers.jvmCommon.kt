// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** In jvmCommonMain, like Sha256's actual: Dispatchers.IO serves both targets. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
