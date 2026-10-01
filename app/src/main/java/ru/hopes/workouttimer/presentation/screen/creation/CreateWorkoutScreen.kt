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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.fitLoadToUnit
import ru.hopes.workouttimer.presentation.ui.components.AppBottomSheet
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.PlateExtraValues
import ru.hopes.workouttimer.presentation.ui.components.PlateValues
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
import ru.hopes.workouttimer.presentation.utils.formatLoad
import ru.hopes.workouttimer.presentation.utils.setFormat
import ru.hopes.workouttimer.presentation.utils.toCorrectNum
import ru.hopes.workouttimer.presentation.utils.unitName
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

    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorText = stringResource(R.string.create_save_error)
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(saveErrorText)
            viewModel.processCommand(CreateWorkoutCommand.DismissSaveError)
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    stringResource(R.string.create_fab_exercise),
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
                        contentDescription = stringResource(R.string.common_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(
                            if (workoutId == null) R.string.create_title_new else R.string.create_title_edit
                        ),
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
                        contentDescription = stringResource(R.string.common_save),
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
                        label = { Text(stringResource(R.string.create_workout_name)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small
                    )
                }
                item(key = "section") {
                    SectionHeader(
                        text = stringResource(R.string.create_exercises_header, state.exercises.size),
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                itemsIndexed(state.exercises, key = { _, item -> item.id }) { _, item ->
                    ReorderableItem(reorderState, key = item.id) { _ ->
                        ExerciseRow(
                            item = item,
                            unit = state.unitOf(item),
                            onClick = { editingId = item.id },
                            dragHandle = {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = stringResource(R.string.create_reorder),
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
                                title = stringResource(R.string.create_empty_title),
                                subtitle = stringResource(R.string.create_empty_subtitle)
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
                unit = state.unitOf(item),
                suggestions = state.suggestionsFor(item),
                onDismiss = {
                    viewModel.processCommand(CreateWorkoutCommand.CloseExercise(id))
                    editingId = null
                },
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
            title = { Text(stringResource(R.string.create_exit_title)) },
            text = { Text(stringResource(R.string.create_exit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.processCommand(CreateWorkoutCommand.Back)
                }) { Text(stringResource(R.string.common_exit)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

/** Поле имени + заголовок секции идут перед списком упражнений. */
private const val HEADER_ITEMS = 2

@Composable
private fun ExerciseRow(
    item: ExerciseItem,
    unit: ExerciseUnit,
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
                text = item.name.ifBlank { stringResource(R.string.create_untitled) },
                style = MaterialTheme.typography.titleMedium,
                color = if (item.name.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Нагрузка показывается приведённой к единице записи — так её и сохранит DAO.
            val load = fitLoadToUnit(item.weight, item.extraWeight, unit)
            val loadText = formatLoad(unit, load.weight, load.extraWeight, setFormat())
            val rest = formatRest(item.restTimeSeconds)
            Text(
                text = if (loadText != null) {
                    stringResource(R.string.create_exercise_summary, loadText, item.sets, item.reps, rest)
                } else {
                    stringResource(R.string.create_exercise_summary_no_weight, item.sets, item.reps, rest)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExerciseEditSheet(
    item: ExerciseItem,
    unit: ExerciseUnit,
    suggestions: List<CatalogExercise>,
    onDismiss: () -> Unit,
    onChange: (ExerciseItem) -> Unit,
    onDelete: () -> Unit
) {
    AppBottomSheet(onDismiss = onDismiss) {
        ExerciseEditSheetContent(
            item = item,
            unit = unit,
            suggestions = suggestions,
            onChange = onChange,
            onDelete = onDelete,
            onDone = onDismiss
        )
    }
}

// Извлечено из ExerciseEditSheet, чтобы тело листа можно было превьюшить и тестировать
// без ModalBottomSheet — он требует Window.
@Composable
internal fun ExerciseEditSheetContent(
    item: ExerciseItem,
    unit: ExerciseUnit,
    suggestions: List<CatalogExercise>,
    onChange: (ExerciseItem) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    // TextFieldValue, а не String: после выбора подсказки курсор встаёт в конец
    // названия, а не остаётся там, где кончался набранный кусок.
    var nameField by remember(item.id) {
        mutableStateOf(TextFieldValue(item.name, TextRange(item.name.length)))
    }
    // Ключ включает единицу: подсказка с другой единицей меняет набор барабанов,
    // и индексы прежнего набора к новому не относятся.
    val load = fitLoadToUnit(item.weight, item.extraWeight, unit)
    var weightIndex by remember(item.id, unit) {
        mutableIntStateOf(wheelIndexOfNearest(WeightValues, load.weight))
    }
    var plateIndex by remember(item.id, unit) {
        mutableIntStateOf(wheelIndexOfNearest(PlateValues, load.weight))
    }
    var extraIndex by remember(item.id, unit) {
        mutableIntStateOf(wheelIndexOfNearest(PlateExtraValues, load.extraWeight))
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
            value = nameField,
            onValueChange = {
                nameField = it
                if (it.text != item.name) onChange(item.copy(name = it.text))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.create_exercise_name)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )

        if (suggestions.isNotEmpty()) {
            SuggestionList(
                suggestions = suggestions,
                onPick = { entry ->
                    nameField = TextFieldValue(entry.name, TextRange(entry.name.length))
                    onChange(item.copy(name = entry.name))
                }
            )
        }

        // WheelPicker запоминает позицию при первом показе, поэтому смена единицы
        // пересоздаёт ряд барабанов целиком. Единицу редактор показывает, но не меняет.
        key(unit) {
            WheelRow(modifier = Modifier.padding(vertical = 12.dp)) {
                when (unit) {
                    ExerciseUnit.KG -> WheelPicker(
                        items = WeightValues,
                        selectedIndex = weightIndex,
                        onSelected = {
                            weightIndex = it
                            onChange(item.copy(weight = WeightValues[it]))
                        },
                        label = stringResource(R.string.common_unit_kg),
                        format = { it.toCorrectNum() }
                    )

                    ExerciseUnit.PLATE -> {
                        WheelPicker(
                            items = PlateValues,
                            selectedIndex = plateIndex,
                            onSelected = {
                                plateIndex = it
                                onChange(item.copy(weight = PlateValues[it], extraWeight = PlateExtraValues[extraIndex]))
                            },
                            label = stringResource(R.string.unit_name_plate),
                            format = { it.toCorrectNum() }
                        )
                        WheelPicker(
                            items = PlateExtraValues,
                            selectedIndex = extraIndex,
                            onSelected = {
                                extraIndex = it
                                onChange(item.copy(weight = PlateValues[plateIndex], extraWeight = PlateExtraValues[it]))
                            },
                            label = stringResource(R.string.unit_plate_extra),
                            format = { it.toCorrectNum() }
                        )
                    }

                    // Без веса барабана нагрузки нет: только подходы, повторы, отдых.
                    ExerciseUnit.BODYWEIGHT -> Unit
                }
                WheelPicker(
                    items = SetsValues,
                    selectedIndex = setsIndex,
                    onSelected = {
                        setsIndex = it
                        onChange(item.copy(sets = SetsValues[it]))
                    },
                    label = stringResource(R.string.create_unit_sets),
                    format = { it.toString() }
                )
                WheelPicker(
                    items = RepsValues,
                    selectedIndex = repsIndex,
                    onSelected = {
                        repsIndex = it
                        onChange(item.copy(reps = RepsValues[it]))
                    },
                    label = stringResource(R.string.common_unit_reps),
                    format = { it.toString() }
                )
                WheelPicker(
                    items = RestValues,
                    selectedIndex = restIndex,
                    onSelected = {
                        restIndex = it
                        onChange(item.copy(restTimeSeconds = RestValues[it]))
                    },
                    label = stringResource(R.string.create_unit_rest),
                    format = { formatRest(it) }
                )
            }
        }

        OutlinedTextField(
            value = item.note,
            onValueChange = { onChange(item.copy(note = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.common_note)) },
            minLines = 2,
            shape = MaterialTheme.shapes.small
        )

        PrimaryButton(
            text = stringResource(R.string.common_done),
            onClick = onDone,
            modifier = Modifier.padding(top = 14.dp)
        )
        TextButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text(stringResource(R.string.create_delete_exercise), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Подсказки из справочника под полем названия; справа — единица записи. */
@Composable
private fun SuggestionList(
    suggestions: List<CatalogExercise>,
    onPick: (CatalogExercise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        suggestions.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(entry) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = unitName(entry.unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ExerciseRowPreview() {
    WorkoutTimerTheme {
        Column(modifier = Modifier.padding(ScreenPadding)) {
            ExerciseRow(
                unit = ExerciseUnit.KG,
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
                unit = ExerciseUnit.KG,
                suggestions = emptyList(),
                onChange = {},
                onDelete = {},
                onDone = {}
            )
        }
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ExerciseEditSheetContentPlatePreview() {
    WorkoutTimerTheme {
        Column(modifier = Modifier.padding(vertical = ScreenPadding)) {
            ExerciseEditSheetContent(
                item = ExerciseItem(
                    id = 2,
                    name = "Тяга",
                    weight = 5.0,
                    sets = 3,
                    reps = 12,
                    restTimeSeconds = 90,
                    extraWeight = 2.0
                ),
                unit = ExerciseUnit.PLATE,
                suggestions = listOf(
                    CatalogExercise(1, "Тяга блока", ExerciseUnit.PLATE),
                    CatalogExercise(2, "Тяга штанги в наклоне", ExerciseUnit.KG)
                ),
                onChange = {},
                onDelete = {},
                onDone = {}
            )
        }
    }
}
