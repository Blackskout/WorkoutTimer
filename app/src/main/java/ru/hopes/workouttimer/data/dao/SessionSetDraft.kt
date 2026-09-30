package ru.hopes.workouttimer.data.dao

/** Подход до записи: catalogId может оказаться неверным — тогда ищем по названию. */
data class SessionSetDraft(
    val catalogId: Long,
    val exerciseName: String,
    val weight: Double,
    val extraWeight: Double,
    val reps: Int,
    val unit: String
)
