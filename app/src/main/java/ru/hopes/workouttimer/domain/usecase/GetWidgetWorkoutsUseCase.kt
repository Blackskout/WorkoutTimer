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
                // Сверху — самая нужная: сначала те, которых вообще нет в истории сессий,
                // потом по возрастанию lastUseAt (чем дольше не делали, тем выше).
                // «Ещё не делали» определяется по отсутствию сессии, а не по lastUseAt:
                // при создании тренировки туда пишется now, и по нему новая тренировка
                // была бы неотличима от сделанной только что.
                .sortedWith(
                    compareBy(
                        { durations[it.workout.id] != null },
                        { it.workout.lastUseAt }
                    )
                )
                .map { it.toWidgetWorkout(durations[it.workout.id]) }
        }
            // Виджет с пустым списком лучше системной «ошибки загрузки» на всю плитку
            .catch { emit(emptyList()) }
}
