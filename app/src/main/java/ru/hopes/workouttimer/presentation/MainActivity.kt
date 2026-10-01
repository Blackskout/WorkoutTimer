package ru.hopes.workouttimer.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.hopes.workouttimer.presentation.navigation.NavGraph
import ru.hopes.workouttimer.presentation.utils.ActiveWorkoutTracker
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var activeWorkoutTracker: ActiveWorkoutTracker

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Пользователь принял или отклонил разрешение на уведомления
    }

    private val newIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Запрашиваем разрешение на уведомления (Android 13+)
        requestNotificationPermission()

        setContent {
            WorkoutTimerTheme {
                NavGraph(
                    newIntent = newIntent.value,
                    onIntentHandled = { newIntent.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Тренировка идёт — тап по виджету просто возвращает в неё: разбор диплинка
        // перестроил бы стек и сбросил экран выполнения вместе с таймером.
        if (activeWorkoutTracker.isActive) return
        setIntent(intent)
        newIntent.value = intent
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
