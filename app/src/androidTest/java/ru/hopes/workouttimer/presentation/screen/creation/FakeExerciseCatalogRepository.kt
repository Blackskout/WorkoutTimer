package ru.hopes.workouttimer.presentation.screen.creation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository

/** Справочник в памяти: тестам редактора нужен только поток записей. */
class FakeExerciseCatalogRepository(
    private val catalog: List<CatalogExercise> = emptyList()
) : ExerciseCatalogRepository {

    override fun observeCatalog(): Flow<List<CatalogExercise>> = flowOf(catalog)

    override fun observeLastSessionSets(): Flow<List<LoggedSet>> = flowOf(emptyList())

    override fun observeSetsForWorkout(workoutId: Int): Flow<List<LoggedSet>> = flowOf(emptyList())

    override fun observeExercise(id: Long): Flow<CatalogExercise?> = flowOf(catalog.firstOrNull { it.id == id })

    override fun observeSetsForExercise(id: Long): Flow<List<LoggedSet>> = flowOf(emptyList())

    override suspend fun rename(id: Long, name: String): RenameResult = RenameResult.RENAMED

    override suspend fun changeUnit(id: Long, unit: ExerciseUnit) = Unit
}
