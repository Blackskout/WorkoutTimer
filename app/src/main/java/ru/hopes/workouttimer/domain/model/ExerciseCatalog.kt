package ru.hopes.workouttimer.domain.model

/** Запись справочника упражнений. */
data class CatalogExercise(
    val id: Long,
    val name: String,
    val unit: ExerciseUnit
)

/** Фактический подход; unit — единица на момент подхода, после смены единицы не меняется. */
data class SessionSet(
    val id: Long,
    val sessionId: Long,
    val catalogId: Long,
    val weight: Double,
    val extraWeight: Double,
    val reps: Int,
    val unit: ExerciseUnit
)

/** Подход с тем, что нужно для показа: текущее название записи справочника и время сессии. */
data class LoggedSet(
    val set: SessionSet,
    val exerciseName: String,
    val finishedAt: Long
)

/** Подходы одного упражнения внутри сессии, в порядке записи. */
data class SessionExerciseSets(
    val catalogId: Long,
    val exerciseName: String,
    val sets: List<SessionSet>
)

/** Строка экрана «Упражнения»: запись и лучший подход её последней сессии. */
data class CatalogSummary(
    val exercise: CatalogExercise,
    val lastBest: SessionSet?,
    val lastDoneAt: Long?
)

/** Итог переименования записи справочника. */
enum class RenameResult { RENAMED, NAME_TAKEN, BLANK }
