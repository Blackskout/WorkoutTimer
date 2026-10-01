package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.hopes.workouttimer.domain.model.ExerciseHistory
import ru.hopes.workouttimer.domain.model.groupProgressSessions
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

/** Запись справочника и все её сессии для экрана прогресса; null — записи нет. */
class ObserveExerciseHistoryUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    operator fun invoke(catalogId: Long): Flow<ExerciseHistory?> =
        combine(repo.observeExercise(catalogId), repo.observeSetsForExercise(catalogId)) { exercise, sets ->
            exercise?.let { ExerciseHistory(it, groupProgressSessions(sets)) }
        }
}
