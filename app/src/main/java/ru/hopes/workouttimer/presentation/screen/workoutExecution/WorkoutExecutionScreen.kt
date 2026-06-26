package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.hopes.workouttimer.presentation.components.SystemMediaControllerCompat

// screens/WorkoutExecutionScreen.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExecutionScreen(
    viewModel: WorkoutExecutionViewModel = hiltViewModel(),
    onExerciseCompleted: () -> Unit, // для навигации назад или к следующему упражнению
    workoutId: Int
) {
    val uiState by viewModel.uiState.collectAsState()
    val workoutName = viewModel.workoutName

    // Загружаем тренировку при первом запуске
    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    // Обрабатываем завершение тренировки
    LaunchedEffect(uiState) {
        if (uiState is WorkoutExecutionState.Finished) {
            onExerciseCompleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = workoutName.ifEmpty { "Тренировка" }) },
                navigationIcon = {
                    IconButton(onClick = { onExerciseCompleted() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when (val currentState = uiState) {
                        is WorkoutExecutionState.Loading -> {
                            LoadingState()
                        }
                        is WorkoutExecutionState.Error -> {
                            ErrorState(
                                message = currentState.message,
                                onRetry = { viewModel.loadWorkout(workoutId) }
                            )
                        }
                        is WorkoutExecutionState.Rest -> {
                            RestTimerContent(
                                restState = currentState,
                                currentExerciseNumber = viewModel.currentExerciseNumber,
                                totalExercises = viewModel.totalExercises,
                                onSkipTimer = { viewModel.skipRest() },
                            )
                        }
                        is WorkoutExecutionState.Active -> {
                            ActiveExerciseContent(
                                activeState = currentState,
                                currentExerciseNumber = viewModel.currentExerciseNumber,
                                totalExercises = viewModel.totalExercises,
                                onExerciseFinished = { viewModel.onExerciseFinished() }
                            )
                        }
                        is WorkoutExecutionState.Finished -> {
                            // Это состояние обрабатывается в LaunchedEffect выше
                            LoadingState()
                        }
                    }
                }
                
                // MediaController для управления музыкой из других приложений
                SystemMediaControllerCompat()
            }
        }
    }
}


@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Загрузка тренировки, пожалуйста подождите...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}

@Composable
fun RestTimerContent(
    restState: WorkoutExecutionState.Rest,
    currentExerciseNumber: Int,
    totalExercises: Int,
    onSkipTimer: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Упражнение $currentExerciseNumber/$totalExercises",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Далее: подход ${restState.currentSet}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        CircularTimer(
            timeLeftMillis = restState.restTimeMillis,
            totalTimeMillis = restState.totalRestTimeMillis
        )

        Text(
            text = restState.exercise.name,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "${restState.exercise.weight} кг",
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = "${restState.exercise.reps} повторений",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSkipTimer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Закончить отдых досрочно")
        }
    }
}

@Composable
fun CircularTimer(
    timeLeftMillis: Long,
    totalTimeMillis: Long
) {
    val timeLeftSeconds = (timeLeftMillis / 1000).toInt()
    val formattedTime = String.format("%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60)

    // 1. Считаем "целевой" прогресс (куда полоска должна прийти сейчас)
    val targetProgress = if (totalTimeMillis > 0L) {
        (timeLeftMillis.toFloat() / totalTimeMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 2. Анимируем значение.
    // targetValue меняется рывками (как приходит из ViewModel),
    // а animatedProgress меняется плавно.
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = 200, // Ставим время, равное частоте обновления во ViewModel (delay)
            easing = LinearEasing // Важно: Линейная скорость (время течет равномерно)
        ),
        label = "TimerAnimation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress }, // 3. Передаем анимированное значение
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = formattedTime,
            style = MaterialTheme.typography.headlineLarge
        )
    }
}

@Composable
fun ActiveExerciseContent(
    activeState: WorkoutExecutionState.Active,
    currentExerciseNumber: Int,
    totalExercises: Int,
    onExerciseFinished: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Упражнение $currentExerciseNumber/$totalExercises",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Подход ${activeState.currentSet} / ${activeState.totalSets}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = activeState.exercise.name,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "${activeState.weight} кг",
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = "${activeState.reps} повторений",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onExerciseFinished,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Закончить упражнение")
        }
    }
}


@Preview
@Composable
fun PreviewWorkoutExecutionScreen() {
    WorkoutExecutionScreen(
        viewModel = TODO(),
        onExerciseCompleted = TODO(),
        workoutId = TODO()
    )
}