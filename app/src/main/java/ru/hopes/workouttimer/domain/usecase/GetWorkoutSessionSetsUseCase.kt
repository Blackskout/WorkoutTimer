package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.hopes.workouttimer.domain.model.SessionExerciseSets
import ru.hopes.workouttimer.domain.model.groupSetsByExercise
import ru.hopes.workouttimer.domain.repository.ExerciseCatalogRepository
import javax.inject.Inject

/** Подходы сессий тренировки: ключ — id сессии, внутри — упражнения в порядке первого появления. */
class GetWorkoutSessionSetsUseCase @Inject constructor(
    private val repo: ExerciseCatalogRepository
) {
    operator fun invoke(workoutId: Int): Flow<Map<Long, List<SessionExerciseSets>>> =
        repo.observeSetsForWorkout(workoutId).map { sets ->
            sets.groupBy { it.set.sessionId }.mapValues { (_, inSession) -> groupSetsByExercise(inSession) }
        }
}
