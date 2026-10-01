package ru.hopes.workouttimer.presentation.service

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Снимает «Отдых завершён» и «Вы всё ещё тренируетесь?», когда на них уже не нужно реагировать:
 * пользователь вернулся к тренировке или она закончилась. Без запуска сервиса — снимать
 * уведомления можно и из фона.
 */
class WorkoutAlerts @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun dismiss() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(TimerNotificationService.FINISHED_NOTIFICATION_ID)
        manager.cancel(TimerNotificationService.IDLE_REMINDER_NOTIFICATION_ID)
    }
}
