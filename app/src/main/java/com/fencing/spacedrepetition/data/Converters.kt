// SPDX-FileCopyrightText: 2026 Enmar Abrams
// SPDX-License-Identifier: GPL-3.0-or-later

package com.fencing.spacedrepetition.data

import androidx.room.TypeConverter
import com.fencing.spacedrepetition.data.model.AlgorithmType

class Converters {
    @TypeConverter
    fun fromAlgorithmType(value: AlgorithmType): String {
        return value.name
    }

    @TypeConverter
    fun toAlgorithmType(value: String): AlgorithmType {
        return try {
            AlgorithmType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AlgorithmType.FSRS
        }
    }

    @TypeConverter
    fun fromImagePathsList(value: List<String>): String {
        return value.joinToString(separator = "||")
    }

    @TypeConverter
    fun toImagePathsList(value: String): List<String> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split("||").filter { it.isNotEmpty() }
        }
    }
}
