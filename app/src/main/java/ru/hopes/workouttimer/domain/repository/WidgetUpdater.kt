package ru.hopes.workouttimer.domain.repository

/**
 * Просит виджет перерисоваться. suspend — потому что GlanceAppWidget.updateAll()
 * приостанавливающая; все вызывающие в репозиториях и так suspend.
 */
interface WidgetUpdater {
    suspend fun requestUpdate()
}
