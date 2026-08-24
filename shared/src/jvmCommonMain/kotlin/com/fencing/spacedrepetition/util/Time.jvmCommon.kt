// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.util

import java.util.TimeZone

internal actual fun platformUtcOffsetSeconds(atEpochMillis: Long): Int =
    TimeZone.getDefault().getOffset(atEpochMillis) / 1000
