package ru.hopes.workouttimer.presentation.screen.workoutExecution

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.presentation.components.YandexMusicButton
import ru.hopes.workouttimer.presentation.ui.components.AppBottomSheet
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.EyebrowLabel
import ru.hopes.workouttimer.presentation.ui.components.PrimaryButton
import ru.hopes.workouttimer.presentation.ui.components.ProgressSegments
import ru.hopes.workouttimer.presentation.ui.components.RestRing
import ru.hopes.workouttimer.presentation.ui.components.SectionHeader
import ru.hopes.workouttimer.presentation.ui.components.StatTile
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.presentation.utils.toCorrectNum

@Composable
fun WorkoutExecutionScreen(
    viewModel: WorkoutExecutionViewModel = hiltViewModel(),
    onExerciseCompleted: () -> Unit,
    workoutId: Int
) {
    val uiState by viewModel.uiState.collectAsState()

    var showNoteDialog by remember { mutableStateOf(false) }
    var currentEditingExercise by remember { mutableStateOf<Exercise?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }

    // Уходить без подтверждения нечего терять только в Loading/Error/Finished:
    // в Finished сессия уже сохранена, в остальных двух её ещё нет.
    val hasUnsavedProgress =
        uiState is WorkoutExecutionState.Active || uiState is WorkoutExecutionState.Rest

    LaunchedEffect(workoutId) {
        viewModel.loadWorkout(workoutId)
    }

    BackHandler(enabled = hasUnsavedProgress) { showExitDialog = true }

    val finishedState = uiState as? WorkoutExecutionState.Finished
    if (finishedState != null) {
        FinishedContent(
            workoutName = viewModel.workoutName,
            durationMillis = finishedState.durationMillis,
            onDone = onExerciseCompleted
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val currentState = uiState
            val currentExercise = when (currentState) {
                is WorkoutExecutionState.Active -> currentState.exercise
                is WorkoutExecutionState.Rest -> currentState.exercise
                else -> null
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (hasUnsavedProgress) showExitDialog = true else onExerciseCompleted()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (currentExercise != null) {
                        ExerciseChip(
                            name = currentExercise.name,
                            position = viewModel.currentExerciseNumber,
                            total = viewModel.totalExercises,
                            onClick = { showExercisePicker = true }
                        )
                    } else {
                        Text(
                            text = viewModel.workoutName.ifEmpty { "Тренировка" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Box(modifier = Modifier.size(48.dp))
            }

            if (currentExercise != null && viewModel.totalExercises > 0) {
                ProgressSegments(
                    total = viewModel.totalExercises,
                    currentIndex = viewModel.currentExerciseNumber - 1,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentState) {
                    is WorkoutExecutionState.Loading -> LoadingContent()

                    is WorkoutExecutionState.Error -> EmptyState(
                        icon = Icons.Default.ErrorOutline,
                        title = "Не удалось загрузить",
                        subtitle = currentState.message,
                        actionText = "Повторить",
                        onAction = { viewModel.loadWorkout(workoutId) }
                    )

                    is WorkoutExecutionState.Active -> ActiveContent(
                        state = currentState,
                        onEditNote = {
                            currentEditingExercise = it
                            showNoteDialog = true
                        }
                    )

                    is WorkoutExecutionState.Rest -> RestContent(
                        state = currentState,
                        onEditNote = {
                            currentEditingExercise = it
                            showNoteDialog = true
                        }
                    )

                    is WorkoutExecutionState.Finished -> Unit
                }
            }

            if (currentState is WorkoutExecutionState.Active ||
                currentState is WorkoutExecutionState.Rest
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ScreenPadding, end = ScreenPadding, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    YandexMusicButton()
                    if (currentState is WorkoutExecutionState.Active) {
                        PrimaryButton(
                            text = "Закончить подход",
                            onClick = {
                                if (viewModel.isLastSetOfWorkout) {
                                    showFinishDialog = true
                                } else {
                                    viewModel.onExerciseFinished()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        PrimaryButton(
                            text = "Пропустить отдых",
                            onClick = { viewModel.skipRest() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showExercisePicker) {
        AppBottomSheet(onDismiss = { showExercisePicker = false }) {
            Text(
                text = "Упражнения",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
            )
            viewModel.exercises.forEachIndexed { index, exercise ->
                val isCurrent = index + 1 == viewModel.currentExerciseNumber
                val isDone = index + 1 < viewModel.currentExerciseNumber
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.moveToSelectedExercise(exercise)
                            showExercisePicker = false
                        }
                        .padding(horizontal = ScreenPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

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

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Выйти из тренировки?") },
            text = { Text("Прогресс не будет сохранён.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onExerciseCompleted()
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("Завершить тренировку?") },
            text = { Text("Это последний подход. Тренировка будет сохранена в историю.") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    viewModel.onExerciseFinished()
                }) { Text("Завершить") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ExerciseChip(
    name: String,
    position: Int,
    total: Int,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = " · $position/$total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ActiveContent(
    state: WorkoutExecutionState.Active,
    onEditNote: (Exercise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EyebrowLabel(
            text = "Подход ${state.currentSet} из ${state.totalSets}",
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            text = state.exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                value = state.weight.toCorrectNum(),
                unit = "кг",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = state.reps.toString(),
                unit = "повт",
                modifier = Modifier.weight(1f)
            )
        }
        NoteBlock(
            note = state.exercise.note,
            onEdit = { onEditNote(state.exercise) },
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun RestContent(
    state: WorkoutExecutionState.Rest,
    onEditNote: (Exercise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EyebrowLabel(
            text = "Отдых · далее подход ${state.currentSet}",
            modifier = Modifier.padding(top = 18.dp)
        )
        RestRing(
            timeLeftMillis = state.restTimeMillis,
            totalTimeMillis = state.totalRestTimeMillis,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = state.exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = "${state.exercise.weight.toCorrectNum()} кг · ${state.exercise.reps} повторений",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        NoteBlock(
            note = state.exercise.note,
            onEdit = { onEditNote(state.exercise) },
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun NoteBlock(
    note: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(text = "Заметка", modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Редактировать заметку",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = note.ifBlank { "Нет заметок" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (note.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun FinishedContent(
    workoutName: String,
    durationMillis: Long,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EyebrowLabel(text = "Готово")
        Text(
            text = DateFormatter.formatDurationCompact(durationMillis),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = workoutName.ifEmpty { "Тренировка" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Box(modifier = Modifier.height(36.dp))
        PrimaryButton(text = "На главную", onClick = onDone)
    }
}

@Composable
private fun LoadingContent() {
    // Спека Часть 3 требует Loading/Error через EmptyState — раньше здесь
    // был голый спиннер, из состояний соответствовал только Error.
    EmptyState(
        icon = Icons.Default.HourglassEmpty,
        title = "Загрузка тренировки",
        subtitle = "Секунду…"
    )
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
        title = { Text("Заметка к упражнению") },
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
            TextButton(onClick = { onSave(noteText) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

private val previewExercise = Exercise(
    id = 1,
    name = "Жим лёжа",
    weight = 80.0,
    sets = 4,
    reps = 8,
    timeMillis = 90_000L,
    order = 0,
    note = "Держать локти ближе к корпусу"
)

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ActiveContentPreview() {
    WorkoutTimerTheme {
        ActiveContent(
            state = WorkoutExecutionState.Active(
                exercise = previewExercise,
                currentSet = 2,
                totalSets = 4,
                weight = 80.0,
                reps = 8
            ),
            onEditNote = {}
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun RestContentPreview() {
    WorkoutTimerTheme {
        RestContent(
            state = WorkoutExecutionState.Rest(
                exercise = previewExercise,
                currentSet = 3,
                totalSets = 4,
                restTimeMillis = 45_000L,
                totalRestTimeMillis = 90_000L
            ),
            onEditNote = {}
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun FinishedContentPreview() {
    WorkoutTimerTheme {
        FinishedContent(
            workoutName = "Верх тела",
            durationMillis = 2_715_000L,
            onDone = {}
        )
    }
}
