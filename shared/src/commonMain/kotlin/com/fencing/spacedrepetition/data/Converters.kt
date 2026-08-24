// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import androidx.room3.ColumnTypeConverter
import com.fencing.spacedrepetition.data.model.AlgorithmType

class Converters {
    @ColumnTypeConverter
    fun fromAlgorithmType(value: AlgorithmType): String {
        return value.name
    }

    @ColumnTypeConverter
    fun toAlgorithmType(value: String): AlgorithmType {
        return try {
            AlgorithmType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AlgorithmType.FSRS
        }
    }

    @ColumnTypeConverter
    fun fromImagePathsList(value: List<String>): String {
        return value.joinToString(separator = "||")
    }

    @ColumnTypeConverter
    fun toImagePathsList(value: String): List<String> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split("||").filter { it.isNotEmpty() }
        }
    }
}
