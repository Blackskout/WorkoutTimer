package ru.hopes.workouttimer.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import ru.hopes.workouttimer.domain.repository.WidgetUpdater
import javax.inject.Inject

class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetUpdater {

    override suspend fun requestUpdate() {
        // Обновление виджета — best-effort и второстепенно по отношению к операции
        // с данными, которая его вызывает. Исключение из композиции Glance (например,
        // из onCompositionError) не должно ронять поток, сохранивший данные в БД.
        try {
            QuickStartWidget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Намеренно проглатываем: логгера в проекте нет, а падать из-за
            // перерисовки виджета нельзя.
        }
    }
}
