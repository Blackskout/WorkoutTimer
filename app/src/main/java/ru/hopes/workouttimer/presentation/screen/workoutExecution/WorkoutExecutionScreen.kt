package ru.hopes.workouttimer.presentation.screen.workoutExecution

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

// screens/WorkoutExecutionScreen.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExecutionScreen(
    viewModel: WorkoutExecutionViewModel = hiltViewModel(),
    onExerciseCompleted: () -> Unit // для навигации назад или к следующему упражнению
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "День груди") },
                navigationIcon = {
                    IconButton(onClick = { /* Вернуться к списку тренировок */ }) {
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
                .padding(16.dp)
        ) {
            when (val currentState = uiState) {
                is WorkoutExecutionState.Rest -> {
                    RestTimerContent(
                        restState = currentState,
                        onSkipTimer = { viewModel.skipRest() },
                    )
                }
                is WorkoutExecutionState.Active -> {
                    ActiveExerciseContent(
                        activeState = currentState,
                        onExerciseFinished = { viewModel.onExerciseFinished() }
                    )
                }
            }
        }
    }
}

@Composable
fun RestTimerContent(
    restState: WorkoutExecutionState.Rest,
    onSkipTimer: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Упражнение 1/8",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Подход ${restState.currentSet}/${restState.totalSets}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        CircularTimer(
            timeLeftMillis = restState.restTimeMillis
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
            Text("Закончить подход")
        }
    }
}

@Composable
fun CircularTimer(
    timeLeftMillis: Long
) {
    val timeLeftSeconds = (timeLeftMillis / 1000).toInt()
    val formattedTime = String.format("%02d:%02d", timeLeftSeconds / 60, timeLeftSeconds % 60)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        CircularProgressIndicator(
            progress = (timeLeftMillis.toFloat() / timeLeftMillis).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxSize()
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
    onExerciseFinished: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Упражнение 1/8",
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

/**
package ru.hopes.workouttimer.presentation.screen.workoutExecution

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

// screens/WorkoutExecutionScreen.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExecutionScreen(
    viewModel: WorkoutExecutionViewModel = hiltViewModel(),
    onExerciseCompleted: () -> Unit // для навигации назад или к следующему упражнению
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState is WorkoutExecutionState.Finished) {
        // Навигация назад
        onExerciseCompleted()
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "День груди") },
                navigationIcon = {
                    IconButton(onClick = { /* Вернуться к списку тренировок */ }) {
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
                .padding(16.dp)
        ) {
            // Обработка состояния Finished


// ... внутри Scaffold ...
            when (val currentState = uiState) {
                is WorkoutExecutionState.Rest -> {
                    RestTimerContent(
                        restState = currentState,
                        onSkipTimer = { viewModel.skipRest() }
                    )
                }
                is WorkoutExecutionState.Active -> {
                    ActiveExerciseContent(
                        activeState = currentState,
                        onExerciseFinished = { viewModel.onActiveFinished() }
                    )
                }
                // ... обработка Loading если нужно
                WorkoutExecutionState.Finished -> TODO()
                WorkoutExecutionState.Loading -> TODO()
            }
        }
    }
}

@Composable
fun RestTimerContent(
    restState: WorkoutExecutionState.Rest,
    onSkipTimer: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Упражнение 1/8",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Подход ${restState.nextSet}/${restState.totalSets}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        CircularTimer(
            timeLeftMillis = restState.timeLeftMillis,
            totalTimeMillis = restState.totalRestTimeMillis // <-- Передаем total
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
            Text("Закончить подход")
        }
    }
}

@Composable
fun CircularTimer(
    timeLeftMillis: Long,
    totalTimeMillis: Long // <-- Добавляем параметр
) {
    // Защита от деления на ноль
    val progress = if (totalTimeMillis > 0) {
        (timeLeftMillis.toFloat() / totalTimeMillis.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // ... остальной код
    CircularProgressIndicator(
        progress = progress,
        // ...
    )
    // ...
}

@Composable
fun ActiveExerciseContent(
    activeState: WorkoutExecutionState.Active,
    onExerciseFinished: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Упражнение 1/8",
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

*/