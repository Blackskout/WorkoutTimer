package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

class ChangeCatalogUnitUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    suspend operator fun invoke(id: Long, unit: ExerciseUnit) = repo.changeUnit(id, unit)
}
