package ru.hopes.workouttimer.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import ru.hopes.workouttimer.presentation.navigation.NavGraph
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutTimerTheme {
                NavGraph()
            }
        }
    }
}
