package ru.hopes.workouttimer.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.hopes.workouttimer.domain.repository.WidgetUpdater
import javax.inject.Inject

class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetUpdater {

    override suspend fun requestUpdate() {
        QuickStartWidget().updateAll(context)
    }
}
