package ru.hopes.workouttimer.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import ru.hopes.workouttimer.data.mapper.toWidgetWorkout
import ru.hopes.workouttimer.domain.model.WidgetWorkout
import javax.inject.Inject

class GetWidgetWorkoutsUseCase @Inject constructor(
    private val getAllWorkoutsWithExercise: GetAllWorkoutsWithExerciseUseCase,
    private val getLastSessionDurations: GetLastSessionDurationsUseCase
) {
    operator fun invoke(): Flow<List<WidgetWorkout>> =
        combine(
            getAllWorkoutsWithExercise(),
            getLastSessionDurations()
        ) { workouts, durations ->
            workouts
                .sortedByDescending { it.workout.lastUseAt }
                .map { it.toWidgetWorkout(durations[it.workout.id]) }
        }
            // Виджет с пустым списком лучше системной «ошибки загрузки» на всю плитку
            .catch { emit(emptyList()) }
}
