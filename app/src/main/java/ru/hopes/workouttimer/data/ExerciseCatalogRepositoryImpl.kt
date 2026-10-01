package ru.hopes.workouttimer.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.hopes.workouttimer.data.dao.WorkoutDao
import ru.hopes.workouttimer.data.mapper.toDomain
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.LoggedSet
import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

// Виджет не обновляется: он показывает только число упражнений и длительность.
class ExerciseCatalogRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao
) : ExerciseCatalogRepository {

    override fun observeCatalog(): Flow<List<CatalogExercise>> =
        dao.observeCatalog().map { list -> list.map { it.toDomain() } }

    override fun observeLastSessionSets(): Flow<List<LoggedSet>> =
        dao.observeLastSessionSets().map { rows -> rows.map { it.toDomain() } }

    override fun observeSetsForWorkout(workoutId: Int): Flow<List<LoggedSet>> =
        dao.observeSetsForWorkout(workoutId.toLong()).map { rows -> rows.map { it.toDomain() } }

    override fun observeExercise(id: Long): Flow<CatalogExercise?> =
        dao.observeCatalogEntry(id).map { it?.toDomain() }

    override fun observeSetsForExercise(id: Long): Flow<List<LoggedSet>> =
        dao.observeSetsForCatalog(id).map { rows -> rows.map { it.toDomain() } }

    override suspend fun rename(id: Long, name: String): RenameResult = dao.renameCatalog(id, name)

    override suspend fun changeUnit(id: Long, unit: ExerciseUnit) = dao.changeCatalogUnit(id, unit)
}
