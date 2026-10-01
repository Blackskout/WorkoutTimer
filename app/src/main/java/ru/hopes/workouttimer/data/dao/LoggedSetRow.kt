package ru.hopes.workouttimer.data.dao

/** Подход с текущим названием записи справочника и временем завершения сессии. */
data class LoggedSetRow(
    val id: Long,
    val sessionId: Long,
    val catalogId: Long,
    val exerciseName: String,
    val weight: Double,
    val extraWeight: Double,
    val reps: Int,
    val unit: String,
    val finishedAt: Long
)
