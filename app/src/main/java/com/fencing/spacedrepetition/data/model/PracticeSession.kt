package com.fencing.spacedrepetition.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_sessions")
data class PracticeSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val completed: Boolean = false,

    // Card IDs in this session (comma-separated)
    val cardIds: String = "",

    // Grades assigned (comma-separated, corresponding to cardIds)
    // Empty until session is completed
    val grades: String = ""
)

/**
 * Represents a card and its grade during a practice session
 */
data class SessionCard(
    val card: Card,
    val grade: Grade? = null,
    val notes: String = "",
    val noteImagePaths: List<String> = emptyList()
)

enum class Grade(val value: Int, val label: String) {
    SKIP(0, "Skip"),
    AGAIN(1, "Again"),
    HARD(2, "Hard"),
    GOOD(3, "Good"),
    EASY(4, "Easy");

    companion object {
        fun fromValue(value: Int): Grade? = values().find { it.value == value }
    }
}
