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
}
