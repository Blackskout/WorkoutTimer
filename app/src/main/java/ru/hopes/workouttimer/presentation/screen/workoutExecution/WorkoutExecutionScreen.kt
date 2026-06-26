@file:OptIn(ExperimentalMaterial3Api::class)

package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.hopes.workouttimer.presentation.components.SystemMediaControllerCompat
import ru.hopes.workouttimer.presentation.utils.toCorrectNum

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

    // Состояние для диалога редактирования заметки
    var showNoteDialog by remember { mutableStateOf(false) }
    var currentEditingExercise by remember { mutableStateOf<Exercise?>(null) }

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
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {

                val currentState = uiState
                if (currentState is WorkoutExecutionState.Rest || currentState is WorkoutExecutionState.Active) {
                    val currentEx = when (currentState) {
                        is WorkoutExecutionState.Rest -> currentState.exercise
                        is WorkoutExecutionState.Active -> currentState.exercise
                        else -> null
                    }

                    currentEx?.let {
                        ExercisesDropdown(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            exercises = viewModel.exercises,
                            selectedExercise = it,
                            onExerciseSelected = { viewModel.moveToSelectedExercise(it) }
                        )
                    }
                }

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
                                onEditNote = { exercise ->
                                    currentEditingExercise = exercise
                                    showNoteDialog = true
                                }
                            )

                        }

                        is WorkoutExecutionState.Active -> {
                            ActiveExerciseContent(
                                activeState = currentState,
                                currentExerciseNumber = viewModel.currentExerciseNumber,
                                totalExercises = viewModel.totalExercises,
                                onExerciseFinished = { viewModel.onExerciseFinished() },
                                onEditNote = { exercise ->
                                    currentEditingExercise = exercise
                                    showNoteDialog = true
                                }
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

    // Диалог редактирования заметки
    currentEditingExercise?.let { exercise ->
        if (showNoteDialog) {
            NoteEditDialog(
                exercise = exercise,
                onDismiss = { showNoteDialog = false },
                onSave = { note ->
                    viewModel.updateExerciseNote(exercise.id, note)
                    showNoteDialog = false
                }
            )
        }
    }
}

@Composable
private fun ExercisesDropdown(
    modifier: Modifier = Modifier,
    exercises: List<Exercise>,
    selectedExercise: Exercise,
    onExerciseSelected: (Exercise) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        modifier = modifier.fillMaxWidth(),
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            value = selectedExercise.name,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            exercises.forEach { exercise ->
                DropdownMenuItem(
                    text = {
                        Text(exercise.name)
                    },
                    onClick = {
                        onExerciseSelected(exercise)
                        expanded = false
                    }
                )
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
    onEditNote: (Exercise) -> Unit
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
            style = MaterialTheme.typography.headlineMedium,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Row {
            Text(
                text = "${restState.exercise.weight.toCorrectNum()} кг",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.width(16.dp))

            Text(
                text = "${restState.exercise.reps} повторений",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            thickness = 1.dp,      // Толщина
            color = Color.Gray     // Цвет
        )


        // Кнопка и текст заметки
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Заметка:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { onEditNote(restState.exercise) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(16.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Редактировать заметку",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (restState.exercise.note.isNotBlank()) {
                Text(
                    text = restState.exercise.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Нет заметок",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
    onExerciseFinished: () -> Unit,
    onEditNote: (Exercise) -> Unit
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
            fontSize = 20.sp,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Row {
            Text(
                text = "${activeState.weight.toCorrectNum()} кг",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "${activeState.reps} повторений",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            thickness = 1.dp,      // Толщина
            color = Color.Gray     // Цвет
        )

        // Кнопка и текст заметки
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Заметка:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { onEditNote(activeState.exercise) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(16.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Редактировать заметку",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (activeState.exercise.note.isNotBlank()) {
                Text(
                    text = activeState.exercise.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Нет заметок",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onExerciseFinished,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Закончить упражнение")
        }
    }
}


@Composable
private fun NoteEditDialog(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var noteText by remember { mutableStateOf(exercise.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Заметка для упражнения",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Введите заметку...") },
                minLines = 3,
                maxLines = 6
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(noteText) }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Отмена")
            }
        }
    )
}