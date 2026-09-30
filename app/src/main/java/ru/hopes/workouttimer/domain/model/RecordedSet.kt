package ru.hopes.workouttimer.domain.model

/**
 * Подход, закрытый кнопкой «Закончить подход». Копится в памяти и пишется
 * вместе с сессией. exerciseName нужен на случай, если catalogId не найдётся:
 * тогда запись справочника ищется по названию, и сессия не теряется.
 */
data class RecordedSet(
    val catalogId: Long,
    val exerciseName: String,
    val weight: Double,
    val extraWeight: Double,
    val reps: Int,
    val unit: ExerciseUnit
)
