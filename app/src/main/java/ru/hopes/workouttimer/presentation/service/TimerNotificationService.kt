package ru.hopes.workouttimer.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.MainActivity

class TimerNotificationService : Service() {

    private val channelId = "timer_notification_channel"
    private val notificationId = 1

    private val finishedChannelId = "rest_finished_notification_channel"
    private val finishedNotificationId = 2

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createFinishedNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Упражнение"
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                val timeLeft = intent.getLongExtra(EXTRA_TIME_LEFT, 0L)
                startForeground(notificationId, createNotification(exerciseName, currentSet, totalSets, timeLeft))
            }
            ACTION_UPDATE -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Упражнение"
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                val timeLeft = intent.getLongExtra(EXTRA_TIME_LEFT, 0L)
                notificationManager.notify(notificationId, createNotification(exerciseName, currentSet, totalSets, timeLeft))
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SHOW_FINISHED -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Упражнение"
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                notificationManager.notify(finishedNotificationId, createFinishedNotification(exerciseName, currentSet, totalSets))
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Таймер отдыха",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Отображение обратного отсчета таймера во время отдыха между подходами"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createFinishedNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                finishedChannelId,
                "Завершение отдыха",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомление о завершении отдыха между подходами"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        exerciseName: String,
        currentSet: Int,
        totalSets: Int,
        timeLeftMillis: Long
    ): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = formatTime(timeLeftMillis)
        val title = "Отдых: $timeText"
        val content = "$exerciseName — Подход $currentSet из $totalSets"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createFinishedNotification(
        exerciseName: String,
        currentSet: Int,
        totalSets: Int
    ): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Отдых завершён!"
        val content = "$exerciseName — Подход $currentSet из $totalSets"

        return NotificationCompat.Builder(this, finishedChannelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setShowWhen(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "START"
        const val ACTION_UPDATE = "UPDATE"
        const val ACTION_STOP = "STOP"
        const val ACTION_SHOW_FINISHED = "SHOW_FINISHED"

        const val EXTRA_EXERCISE_NAME = "exercise_name"
        const val EXTRA_CURRENT_SET = "current_set"
        const val EXTRA_TOTAL_SETS = "total_sets"
        const val EXTRA_TIME_LEFT = "time_left"
    }
}
