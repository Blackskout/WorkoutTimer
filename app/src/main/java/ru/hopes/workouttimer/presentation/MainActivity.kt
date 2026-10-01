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
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: WorkoutSessionManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Пользователь принял или отклонил разрешение на уведомления
    }

    private val newIntent = mutableStateOf<Intent?>(null)

    // Просьба открыть идущую тренировку; NavGraph гасит её после перехода.
    private val returnToSession = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Процесс жив, сессия идёт, а активность создана заново: тренировку свернули и
        // закрыли приложение «Назад» со списка, потом тапнули виджет или уведомление.
        // NavController сам разобрал бы диплинк стартового интента и открыл выполнение
        // другой тренировки — поэтому данные снимаются, а вместо них — возврат в идущую.
        // При восстановлении (savedInstanceState != null) стек восстанавливается сам,
        // диплинк повторно не разбирается, а возврат выдернул бы пользователя с его экрана.
        if (savedInstanceState == null &&
            resolveIntent(intent.action, intent.dataString, sessionManager.isRunning) == IntentRoute.ReturnToSession
        ) {
            intent = Intent(intent).setData(null)
            returnToSession.value = true
        }

        // Запрашиваем разрешение на уведомления (Android 13+)
        requestNotificationPermission()

        setContent {
            WorkoutTimerTheme {
                NavGraph(
                    newIntent = newIntent.value,
                    onIntentHandled = { newIntent.value = null },
                    returnToSessionRequested = returnToSession.value,
                    onReturnHandled = { returnToSession.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (resolveIntent(intent.action, intent.dataString, sessionManager.isRunning)) {
            IntentRoute.OpenDeepLink -> {
                setIntent(intent)
                newIntent.value = intent
            }
            // Диплинк не разбирается: handleDeepLink перестроил бы стек. Пользователь может
            // стоять на любом экране — выполнение откроется поверх него.
            IntentRoute.ReturnToSession -> returnToSession.value = true
            IntentRoute.Ignore -> Unit
        }
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

    companion object {
        /** Тап по уведомлению таймера: вернуть в идущую тренировку, если она есть. */
        const val ACTION_OPEN_ACTIVE_WORKOUT = "ru.hopes.workouttimer.action.OPEN_ACTIVE_WORKOUT"
    }
}
