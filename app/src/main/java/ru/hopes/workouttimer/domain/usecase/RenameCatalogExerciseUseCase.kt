package ru.hopes.workouttimer.domain.usecase

import ru.hopes.workouttimer.domain.model.RenameResult
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

class RenameCatalogExerciseUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    suspend operator fun invoke(id: Long, name: String): RenameResult = repo.rename(id, name)
}
