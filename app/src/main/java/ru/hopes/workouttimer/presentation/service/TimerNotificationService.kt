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
import dagger.hilt.android.AndroidEntryPoint
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.MainActivity
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager
import javax.inject.Inject

@AndroidEntryPoint
class TimerNotificationService : Service() {

    @Inject
    lateinit var sessionManager: WorkoutSessionManager

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
                val isTransition = intent.getBooleanExtra(EXTRA_TRANSITION, false)
                startForeground(
                    notificationId,
                    createNotification(exerciseName, currentSet, totalSets, finishAt, isTransition)
                )
            }
            ACTION_UPDATE -> {
                val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: getString(R.string.create_fab_exercise)
                val currentSet = intent.getIntExtra(EXTRA_CURRENT_SET, 1)
                val totalSets = intent.getIntExtra(EXTRA_TOTAL_SETS, 1)
                val finishAt = intent.getLongExtra(EXTRA_FINISH_AT, 0L)
                val isTransition = intent.getBooleanExtra(EXTRA_TRANSITION, false)
                notificationManager.notify(
                    notificationId,
                    createNotification(exerciseName, currentSet, totalSets, finishAt, isTransition)
                )
            }
            // Кнопки уведомления: менеджер сам остановит или продлит отсчёт и уведомление.
            // Запоздалое нажатие (отдых уже кончился) сервис заново поднимает — тогда гасим его.
            ACTION_SKIP_REST, ACTION_ADD_REST -> {
                if (!sessionManager.isRestTimerActive) {
                    stopSelf()
                } else if (intent.action == ACTION_SKIP_REST) {
                    sessionManager.skipRest()
                } else {
                    sessionManager.addRestTime()
                }
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
        finishAtMillis: Long,
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
            if (isTransition) R.string.notification_transition_title else R.string.notification_rest_title
        )
        val content = getString(R.string.notification_set_content, exerciseName, currentSet, totalSets)

        // Обратный отсчёт ведёт сама система (хронометр до finishAt): уведомление не
        // перевыкладывается каждую секунду.
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setWhen(finishAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            // Если менеджер не успел снять уведомление, отрицательного отсчёта не будет.
            .setTimeoutAfter((finishAtMillis - System.currentTimeMillis()).coerceAtLeast(0L) + 1_000L)
            .addAction(
                0,
                getString(if (isTransition) R.string.execution_skip_transition else R.string.notification_action_skip),
                actionIntent(ACTION_SKIP_REST)
            )
            .apply {
                if (!isTransition) addAction(0, getString(R.string.notification_action_add_rest), actionIntent(ACTION_ADD_REST))
            }
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, TimerNotificationService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private val notificationManager: NotificationManager
        get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "START"
        const val ACTION_UPDATE = "UPDATE"
        const val ACTION_STOP = "STOP"
        const val ACTION_SHOW_FINISHED = "SHOW_FINISHED"
        const val ACTION_SHOW_IDLE_REMINDER = "SHOW_IDLE_REMINDER"
        const val ACTION_SKIP_REST = "SKIP_REST"
        const val ACTION_ADD_REST = "ADD_REST"

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
