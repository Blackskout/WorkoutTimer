package ru.hopes.workouttimer.presentation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.MainActivity
import java.time.Instant

class TimerNotificationService : Service() {

    private val channelId = "timer_notification_channel"
    private val notificationId = 1

    private val finishedChannelId = "rest_finished_notification_channel"
    private val finishedNotificationId = FINISHED_NOTIFICATION_ID

    private val idleReminderChannelId = "idle_reminder_notification_channel"
    private val idleReminderNotificationId = IDLE_REMINDER_NOTIFICATION_ID

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createFinishedNotificationChannel()
        createIdleReminderNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: getString(R.string.create_fab_exercise)
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                val finishAt = intent.getLongExtra(EXTRA_FINISH_AT, 0L)
                startForeground(
                    notificationId,
                    createNotification(exerciseName, currentSet, totalSets, finishAt)
                )
            }
            ACTION_UPDATE -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: getString(R.string.create_fab_exercise)
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                val finishAt = intent.getLongExtra(EXTRA_FINISH_AT, 0L)
                notificationManager.notify(
                    notificationId,
                    createNotification(exerciseName, currentSet, totalSets, finishAt)
                )
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SHOW_FINISHED -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: getString(R.string.create_fab_exercise)
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                val isTransition = intent.getBooleanExtra(EXTRA_TRANSITION, false)
                notificationManager.notify(
                    finishedNotificationId,
                    createFinishedNotification(exerciseName, currentSet, totalSets, isTransition)
                )
            }
            ACTION_SHOW_IDLE_REMINDER -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: getString(R.string.create_fab_exercise)
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                notificationManager.notify(idleReminderNotificationId, createIdleReminderNotification(exerciseName, currentSet, totalSets))
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_timer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_timer_description)
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
                getString(R.string.notification_channel_rest_finished),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_rest_finished_description)
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
        finishAtMillis: Long
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Свой action: при идущей сессии тап возвращает в неё с любого экрана.
            action = MainActivity.ACTION_OPEN_ACTIVE_WORKOUT
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progress = getString(
            R.string.notification_remaining_sets,
            remainingRestSets(currentSet, totalSets),
            totalSets
        )
        val remainingMillis = (finishAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)

        // Обратный отсчёт ведёт сама система (хронометр до finishAt): уведомление не
        // перевыкладывается каждую секунду.
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(exerciseName)
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setAllowSystemGeneratedContextualActions(false)
            .setWhen(finishAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            // Если менеджер не успел снять уведомление, отрицательного отсчёта не будет.
            .setTimeoutAfter(remainingMillis + 1_000L)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            withTimerMetric(
                notification,
                metricLabel = progress,
                finishAtMillis = finishAtMillis
            )
        } else {
            notification
        }
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun withTimerMetric(
        notification: Notification,
        metricLabel: String,
        finishAtMillis: Long
    ): Notification {
        val countdown = Notification.Metric.TimeDifference.forTimer(
            Instant.ofEpochMilli(finishAtMillis),
            Notification.Metric.TimeDifference.FORMAT_CHRONOMETER
        )
        return Notification.Builder.recoverBuilder(this, notification)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setStyle(
                Notification.MetricStyle()
                    .addMetric(Notification.Metric(countdown, metricLabel))
                    .setCriticalMetric(0)
            )
            .build()
    }

    private fun createFinishedNotification(
        exerciseName: String,
        currentSet: Int,
        totalSets: Int,
        isTransition: Boolean
    ): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Свой action: при идущей сессии тап возвращает в неё с любого экрана.
            action = MainActivity.ACTION_OPEN_ACTIVE_WORKOUT
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(
            if (isTransition) R.string.notification_transition_finished_title else R.string.notification_rest_finished_title
        )
        val content = getString(R.string.notification_set_content, exerciseName, currentSet, totalSets)

        return NotificationCompat.Builder(this, finishedChannelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setShowWhen(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createIdleReminderNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                idleReminderChannelId,
                getString(R.string.notification_channel_idle),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_idle_description)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createIdleReminderNotification(
        exerciseName: String,
        currentSet: Int,
        totalSets: Int
    ): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Свой action: при идущей сессии тап возвращает в неё с любого экрана.
            action = MainActivity.ACTION_OPEN_ACTIVE_WORKOUT
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(R.string.notification_idle_title)
        val content = getString(R.string.notification_idle_content, exerciseName, currentSet, totalSets)

        return NotificationCompat.Builder(this, idleReminderChannelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setShowWhen(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "START"
        const val ACTION_UPDATE = "UPDATE"
        const val ACTION_STOP = "STOP"
        const val ACTION_SHOW_FINISHED = "SHOW_FINISHED"
        const val ACTION_SHOW_IDLE_REMINDER = "SHOW_IDLE_REMINDER"

        const val EXTRA_EXERCISE_NAME = "exercise_name"
        const val EXTRA_CURRENT_SET = "current_set"
        const val EXTRA_TOTAL_SETS = "total_sets"
        const val EXTRA_FINISH_AT = "finish_at"
        const val EXTRA_TRANSITION = "transition"

        // Снимаются вне сервиса (WorkoutAlerts), поэтому видны снаружи.
        const val FINISHED_NOTIFICATION_ID = 2
        const val IDLE_REMINDER_NOTIFICATION_ID = 3
    }
}

// The rest phase already points at the next set, which is still outstanding.
internal fun remainingRestSets(nextSet: Int, totalSets: Int): Int =
    (totalSets.coerceAtLeast(0) - (nextSet.coerceAtLeast(1) - 1)).coerceAtLeast(0)
