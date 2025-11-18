package ru.hopes.workouttimer.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun PerformWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: PerformWorkoutViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = state.workoutName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        state.currentExercise?.let { ex ->
            Text("Упражнение: ${ex.name}", style = MaterialTheme.typography.titleMedium)
            Text("Подход ${state.currentSet} из ${ex.sets}")
            Text("Повторы: ${ex.reps}")
        } ?: Text("Тренировка окончена")

        Spacer(Modifier.height(16.dp))

        if (state.isResting) {
            Text("Отдых: ${state.restMillisLeft} сек", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { viewModel.stopRest() }) {
                Text("Пропустить отдых")
            }
        } else {
            Button(onClick = { viewModel.onSetCompleted() }) {
                Text("Подход выполнен")
            }
        }

        Spacer(Modifier.height(24.dp))
        state.nextExercise?.let { next ->
            Text("Следующее: ${next.name} (${next.sets}x${next.reps})")
        }
    }
}

@Preview
@Composable
fun PreviewPerformWorkoutScreen() {
    PerformWorkoutScreen(
        viewModel = hiltViewModel(),
        onFinish = {}
    )
}
