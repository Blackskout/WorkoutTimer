package ru.hopes.workouttimer.presentation.service

import android.service.notification.NotificationListenerService

/**
 * Сервис намеренно пустой, и дописывать его не нужно.
 *
 * MediaSessionManager.getActiveSessions() пускает к чужим медиа-сессиям
 * только по ComponentName включённого NotificationListenerService. Этот класс
 * существует ровно ради такого ComponentName: уведомления он не читает и
 * никаких колбэков не переопределяет.
 */
class MediaAccessService : NotificationListenerService()
