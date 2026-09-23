package ru.hopes.workouttimer.presentation.screen.creation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.presentation.ui.components.AppBottomSheet
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.PrimaryButton
import ru.hopes.workouttimer.presentation.ui.components.RepsValues
import ru.hopes.workouttimer.presentation.ui.components.SectionHeader
import ru.hopes.workouttimer.presentation.ui.components.WeightValues
import ru.hopes.workouttimer.presentation.ui.components.WheelPicker
import ru.hopes.workouttimer.presentation.ui.components.WheelRow
import ru.hopes.workouttimer.presentation.ui.components.wheelIndexOfNearest
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.toCorrectNum
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

private val SetsValues = (1..10).toList()
private val RestValues = (15..1800 step 15).toList()

private fun formatRest(seconds: Int): String =
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

@Composable
fun CreateWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: CreateWorkoutViewModel = hiltViewModel(),
    onFinished: () -> Unit,
    workoutId: Int? = null
) {
    val state by viewModel.state.collectAsState()
    var editingId by remember { mutableStateOf<Int?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        workoutId?.let { viewModel.loadWorkout(it) }
    }

    // Навигация — эффект, а не часть композиции. Тело composable выполняется
    // заново на каждую перекомпоновку, и вызов onFinished() прямо здесь снимал
    // экран со стека по нескольку раз подряд, утаскивая за собой и предыдущий.
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onFinished()
    }

    if (state.isFinished) return

    // Перетаскивание можно потерять одним тапом «назад» — подтверждаем выход.
    BackHandler(enabled = state.hasUnsavedChanges) { showExitDialog = true }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.processCommand(
            CreateWorkoutCommand.MoveExercise(
                from = from.index - HEADER_ITEMS,
                to = to.index - HEADER_ITEMS
            )
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.processCommand(CreateWorkoutCommand.AddExercise())
                    // AddExercise кладёт новое упражнение в конец — открываем его лист.
                    editingId = viewModel.state.value.exercises.lastOrNull()?.id
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    "Упражнение",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (state.hasUnsavedChanges) {
                        showExitDialog = true
                    } else {
                        viewModel.processCommand(CreateWorkoutCommand.Back)
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (workoutId == null) "Новая тренировка" else "Редактирование",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.processCommand(CreateWorkoutCommand.Save) },
                    enabled = state.isSaveEnabled
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Сохранить",
                        tint = if (state.isSaveEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(CardSpacing)
            ) {
                item(key = "name") {
                    OutlinedTextField(
                        value = state.workoutName,
                        onValueChange = {
                            viewModel.processCommand(CreateWorkoutCommand.ChangeWorkoutName(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название тренировки") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                }
                item(key = "section") {
                    SectionHeader(
                        text = "Упражнения · ${state.exercises.size}",
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                itemsIndexed(state.exercises, key = { _, item -> item.id }) { _, item ->
                    ReorderableItem(reorderState, key = item.id) { _ ->
                        ExerciseRow(
                            item = item,
                            onClick = { editingId = item.id },
                            dragHandle = {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Переставить",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .draggableHandle()
                                )
                            }
                        )
                    }
                }
                if (state.exercises.isEmpty()) {
                    item(key = "empty") {
                        Box(modifier = Modifier.height(220.dp)) {
                            EmptyState(
                                icon = Icons.Default.FitnessCenter,
                                title = "Упражнений пока нет",
                                subtitle = "Добавьте первое кнопкой внизу"
                            )
                        }
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val item = state.exercises.firstOrNull { it.id == id }
        if (item == null) {
            editingId = null
        } else {
            ExerciseEditSheet(
                item = item,
                onDismiss = { editingId = null },
                onChange = { updated ->
                    viewModel.processCommand(CreateWorkoutCommand.UpdateExercise(id, updated))
                },
                onDelete = {
                    viewModel.processCommand(CreateWorkoutCommand.RemoveExercise(id))
                    editingId = null
                }
            )
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Выйти без сохранения?") },
            text = { Text("Изменения, включая порядок упражнений, будут потеряны.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.processCommand(CreateWorkoutCommand.Back)
                }) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Отмена") }
            }
        )
    }
}

/** Поле имени + заголовок секции идут перед списком упражнений. */
private const val HEADER_ITEMS = 2

@Composable
private fun ExerciseRow(
    item: ExerciseItem,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        dragHandle()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name.ifBlank { "Без названия" },
                style = MaterialTheme.typography.titleMedium,
                color = if (item.name.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.weight.toCorrectNum()} кг · ${item.sets}×${item.reps} · отдых ${formatRest(item.restTimeSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExerciseEditSheet(
    item: ExerciseItem,
    onDismiss: () -> Unit,
    onChange: (ExerciseItem) -> Unit,
    onDelete: () -> Unit
) {
    AppBottomSheet(onDismiss = onDismiss) {
        ExerciseEditSheetContent(
            item = item,
            onChange = onChange,
            onDelete = onDelete,
            onDone = onDismiss
        )
    }
}

// Извлечено из ExerciseEditSheet, чтобы тело листа можно было превьюшить без
// ModalBottomSheet — он требует Window и не рендерится в @Preview.
@Composable
private fun ExerciseEditSheetContent(
    item: ExerciseItem,
    onChange: (ExerciseItem) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    var weightIndex by remember(item.id) {
        mutableIntStateOf(wheelIndexOfNearest(WeightValues, item.weight))
    }
    var setsIndex by remember(item.id) {
        mutableIntStateOf(wheelIndexOfNearest(SetsValues.map { it.toDouble() }, item.sets.toDouble()))
    }
    var repsIndex by remember(item.id) {
        mutableIntStateOf(wheelIndexOfNearest(RepsValues.map { it.toDouble() }, item.reps.toDouble()))
    }
    var restIndex by remember(item.id) {
        mutableIntStateOf(
            wheelIndexOfNearest(RestValues.map { it.toDouble() }, item.restTimeSeconds.toDouble())
        )
    }

    Column(modifier = Modifier.padding(horizontal = ScreenPadding)) {
        OutlinedTextField(
            value = item.name,
            onValueChange = { onChange(item.copy(name = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Название упражнения") },
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )

        WheelRow(modifier = Modifier.padding(vertical = 12.dp)) {
            WheelPicker(
                items = WeightValues,
                selectedIndex = weightIndex,
                onSelected = {
                    weightIndex = it
                    onChange(item.copy(weight = WeightValues[it]))
                },
                label = "кг",
                format = { it.toCorrectNum() }
            )
            WheelPicker(
                items = SetsValues,
                selectedIndex = setsIndex,
                onSelected = {
                    setsIndex = it
                    onChange(item.copy(sets = SetsValues[it]))
                },
                label = "подх",
                format = { it.toString() }
            )
            WheelPicker(
                items = RepsValues,
                selectedIndex = repsIndex,
                onSelected = {
                    repsIndex = it
                    onChange(item.copy(reps = RepsValues[it]))
                },
                label = "повт",
                format = { it.toString() }
            )
            WheelPicker(
                items = RestValues,
                selectedIndex = restIndex,
                onSelected = {
                    restIndex = it
                    onChange(item.copy(restTimeSeconds = RestValues[it]))
                },
                label = "отдых",
                format = { formatRest(it) }
            )
        }

        OutlinedTextField(
            value = item.note,
            onValueChange = { onChange(item.copy(note = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Заметка") },
            minLines = 2,
            shape = MaterialTheme.shapes.small
        )

        PrimaryButton(
            text = "Готово",
            onClick = onDone,
            modifier = Modifier.padding(top = 14.dp)
        )
        TextButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text("Удалить упражнение", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ExerciseRowPreview() {
    WorkoutTimerTheme {
        Column(modifier = Modifier.padding(ScreenPadding)) {
            ExerciseRow(
                item = ExerciseItem(
                    id = 1,
                    name = "Жим лёжа",
                    weight = 60.0,
                    sets = 4,
                    reps = 10,
                    restTimeSeconds = 120,
                    note = ""
                ),
                onClick = {},
                dragHandle = {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            )
        }
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ExerciseEditSheetContentPreview() {
    WorkoutTimerTheme {
        Column(modifier = Modifier.padding(vertical = ScreenPadding)) {
            ExerciseEditSheetContent(
                item = ExerciseItem(
                    id = 1,
                    name = "Присед",
                    weight = 82.5,
                    sets = 5,
                    reps = 5,
                    restTimeSeconds = 180,
                    note = "Пояс с третьего подхода"
                ),
                onChange = {},
                onDelete = {},
                onDone = {}
            )
        }
    }
}
