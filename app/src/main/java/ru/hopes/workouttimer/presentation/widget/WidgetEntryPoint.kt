package ru.hopes.workouttimer.presentation.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.hopes.workouttimer.domain.usecase.GetWidgetWorkoutsUseCase

// GlanceAppWidget создаётся системой и не имеет своего DI-скоупа,
// поэтому зависимости достаём из SingletonComponent вручную
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun getWidgetWorkoutsUseCase(): GetWidgetWorkoutsUseCase
}
