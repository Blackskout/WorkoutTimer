package ru.hopes.workouttimer.presentation.screen.creation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: CreateWorkoutViewModel = hiltViewModel(),
    onFinished: () -> Unit,
    workoutId: Int? = null
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(workoutId) {
        workoutId?.let { id ->
            viewModel.loadWorkout(id)
        }
    }

    if (state.isFinished) {
        onFinished()
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (workoutId == null) "Новая тренировка" else "Редактирование",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    Icon(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 8.dp)
                            .clickable {
                                viewModel.processCommand(CreateWorkoutCommand.Back)
                            },
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.processCommand(CreateWorkoutCommand.AddExercise())
                },
                contentColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить упражнение"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                value = state.workoutName,
                onValueChange = {
                    viewModel.processCommand(CreateWorkoutCommand.ChangeWorkoutName(it))
                },
                label = { Text("Название тренировки") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = "Упражнения",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.exercises, key = { it.id }) { exercise ->
                    ExerciseItem(
                        exercise = exercise,
                        onNameChange = {
                            viewModel.processCommand(
                                CreateWorkoutCommand.UpdateExercise(
                                    exercise.id,
                                    exercise.copy(name = it)
                                )
                            )
                        },
                        onWeightChange = {
                            viewModel.processCommand(
                                CreateWorkoutCommand.UpdateExercise(
                                    exercise.id,
                                    exercise.copy(weight = it)
                                )
                            )
                        },
                        onSetsChange = {
                            viewModel.processCommand(
                                CreateWorkoutCommand.UpdateExercise(
                                    exercise.id,
                                    exercise.copy(sets = it)
                                )
                            )
                        },
                        onRepsChange = {
                            viewModel.processCommand(
                                CreateWorkoutCommand.UpdateExercise(
                                    exercise.id,
                                    exercise.copy(reps = it)
                                )
                            )
                        },
                        onRestTimeChange = {
                            viewModel.processCommand(
                                CreateWorkoutCommand.UpdateExercise(
                                    exercise.id,
                                    exercise.copy(restTimeSeconds = it)
                                )
                            )
                        },
                        onNoteChange = {
                            viewModel.processCommand(
                                CreateWorkoutCommand.UpdateExerciseNote(
                                    exercise.id,
                                    it
                                )
                            )
                        },
                        onRemoveClick = {
                            viewModel.processCommand(CreateWorkoutCommand.RemoveExercise(exercise.id))
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                onClick = {
                    viewModel.processCommand(CreateWorkoutCommand.Save)
                },
                shape = RoundedCornerShape(12.dp),
                enabled = state.isSaveEnabled,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Сохранить тренировку",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ExerciseItem(
    exercise: ExerciseItem,
    onNameChange: (String) -> Unit,
    onWeightChange: (Double) -> Unit,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRestTimeChange: (Int) -> Unit,
    onNoteChange: (String) -> Unit,
    onRemoveClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = exercise.name,
                onValueChange = onNameChange,
                label = { Text("Название") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                modifier = Modifier
                    .clickable { onRemoveClick() }
                    .padding(8.dp),
                imageVector = Icons.Default.Close,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.width(100.dp),
                value = if (exercise.weight == 0.0) "" else exercise.weight.toString(),
                onValueChange = {
                    onWeightChange(it.toDoubleOrNull() ?: 0.0)
                },
                label = { Text("Вес (кг)") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                modifier = Modifier.width(80.dp),
                value = exercise.sets.toString(),
                onValueChange = {
                    onSetsChange(it.toIntOrNull() ?: 1)
                },
                label = { Text("Подходы") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                modifier = Modifier.width(80.dp),
                value = exercise.reps.toString(),
                onValueChange = {
                    onRepsChange(it.toIntOrNull() ?: 1)
                },
                label = { Text("Повторы") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                modifier = Modifier.width(100.dp),
                value = (exercise.restTimeSeconds / 60).toString(),
                onValueChange = {
                    val minutes = it.toIntOrNull() ?: 1
                    onRestTimeChange(minutes.coerceAtLeast(1) * 60)
                },
                label = { Text("Отдых (мин)") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = exercise.note,
            onValueChange = onNoteChange,
            label = { Text("Заметка") },
            placeholder = { Text("Добавить заметку к упражнению") },
            minLines = 2,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Preview
@Composable
fun CreateWorkoutScreenPreview() {
    WorkoutTimerTheme {
        CreateWorkoutScreen(onFinished = {})
    }
}
