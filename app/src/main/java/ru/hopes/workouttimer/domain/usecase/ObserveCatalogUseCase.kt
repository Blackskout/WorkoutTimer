package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

class ObserveCatalogUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    operator fun invoke(): Flow<List<CatalogExercise>> = repo.observeCatalog()
}
