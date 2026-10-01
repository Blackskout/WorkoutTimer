package ru.hopes.workouttimer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.RenameResult

/** Справочник упражнений и подходы для показа. Пишет только переименование и единицу. */
interface ExerciseCatalogRepository {
    fun observeCatalog(): Flow<List<CatalogExercise>>

    /** Подходы последней сессии каждой записи справочника, в порядке записи. */
    fun observeLastSessionSets(): Flow<List<LoggedSet>>

    /** Все подходы всех сессий тренировки, в порядке записи. */
    fun observeSetsForWorkout(workoutId: Int): Flow<List<LoggedSet>>

    suspend fun rename(id: Long, name: String): RenameResult

    suspend fun changeUnit(id: Long, unit: ExerciseUnit)
}
