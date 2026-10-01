package ru.hopes.workouttimer.presentation.screen.workoutExecution

import ru.hopes.workouttimer.presentation.session.WorkoutExecutionState
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.presentation.components.music.MusicSection
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
import ru.hopes.workouttimer.presentation.utils.formatLoad
import ru.hopes.workouttimer.presentation.utils.setFormat
import ru.hopes.workouttimer.presentation.utils.toCorrectNum

@Composable
fun WorkoutExecutionScreen(
    workoutId: Int,
    onMinimize: () -> Unit,
    onLeave: () -> Unit,
    viewModel: WorkoutExecutionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome by viewModel.chrome.collectAsState()

    // Диалог заметки и шторка веса запоминают позицию упражнения в сессии, а не сам
    // Exercise: значения полей берутся из текущего снимка. Открываются они только для
    // текущего упражнения (в Rest — упражнения следующего подхода).
    var noteIndex by remember { mutableStateOf<Int?>(null) }
    var weightSheetIndex by remember { mutableStateOf<Int?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var showSupersetPicker by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Идёт тренировка — «Назад» и стрелка сворачивают её без диалога. В Loading, Error и
    // Finished сворачивать нечего: уход закрывает сессию (onCleared → close).
    val isLive = uiState is WorkoutExecutionState.Active || uiState is WorkoutExecutionState.Rest

    // start() идемпотентен: при пересоздании активности и при возврате в свёрнутую
    // тренировку сессия не сбрасывается.
    LaunchedEffect(workoutId) {
        viewModel.start(workoutId)
    }

    // Пока пишется завершение, «Назад» поглощается и ничего не делает: свернуть посреди
    // записи значило бы получить Finished, которого никто не увидит.
    BackHandler(enabled = isLive) {
        if (!chrome.isFinishing) onMinimize()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val finishError by viewModel.finishError.collectAsState()
    val finishErrorText = stringResource(R.string.execution_finish_error)
    LaunchedEffect(finishError) {
        if (finishError) {
            snackbarHostState.showSnackbar(finishErrorText)
            viewModel.dismissFinishError()
        }
    }

    val finishedState = uiState as? WorkoutExecutionState.Finished
    if (finishedState != null) {
        FinishedContent(
            workoutName = chrome.workoutName,
            durationMillis = finishedState.durationMillis,
            onDone = onLeave
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    when {
                        !isLive -> onLeave()
                        !chrome.isFinishing -> onMinimize()
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(
                            if (isLive) R.string.execution_minimize else R.string.common_back
                        ),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (currentExercise != null) {
                        ExerciseChip(
                            name = currentExercise.name,
                            position = chrome.currentExerciseNumber,
                            total = chrome.totalExercises,
                            onClick = { showExercisePicker = true }
                        )
                    } else {
                        Text(
                            text = chrome.workoutName.ifEmpty { stringResource(R.string.execution_workout_fallback) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // «Назад» больше не выходит — выход без сохранения переехал в меню.
                if (isLive && !chrome.isFinishing) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.execution_more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (chrome.partnerOf(chrome.exerciseIndex) != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.execution_superset_unpair)) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.unpair()
                                    }
                                )
                            } else if (chrome.supersetCandidates.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.execution_superset_with)) },
                                    onClick = {
                                        menuExpanded = false
                                        showSupersetPicker = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.execution_exit_without_saving)) },
                                onClick = {
                                    menuExpanded = false
                                    showExitDialog = true
                                }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.size(48.dp))
                }
            }

            if (currentExercise != null && chrome.totalExercises > 0) {
                ProgressSegments(
                    done = chrome.exercises.map { it.isDone },
                    currentIndex = chrome.exerciseIndex,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentState) {
                    is WorkoutExecutionState.Loading -> LoadingContent()

                    is WorkoutExecutionState.Error -> EmptyState(
                        icon = Icons.Default.ErrorOutline,
                        title = stringResource(R.string.execution_load_error_title),
                        subtitle = stringResource(R.string.execution_load_error_subtitle),
                        actionText = stringResource(R.string.execution_retry),
                        onAction = { viewModel.retry(workoutId) }
                    )

                    is WorkoutExecutionState.Active -> ActiveContent(
                        state = currentState,
                        onEditNote = { noteIndex = chrome.exerciseIndex },
                        onEditWeightAndReps = { weightSheetIndex = chrome.exerciseIndex }
                    )

                    is WorkoutExecutionState.Rest -> RestContent(
                        state = currentState,
                        onEditNote = { noteIndex = chrome.exerciseIndex },
                        onEditWeightAndReps = { weightSheetIndex = chrome.exerciseIndex }
                    )

                    is WorkoutExecutionState.Finished -> Unit
                }
            }

            if (currentState is WorkoutExecutionState.Active ||
                currentState is WorkoutExecutionState.Rest
            ) {
                MusicSection(
                    isResting = currentState is WorkoutExecutionState.Rest,
                    modifier = Modifier.padding(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        bottom = 10.dp
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ScreenPadding, end = ScreenPadding, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentState is WorkoutExecutionState.Active) {
                        PrimaryButton(
                            text = stringResource(R.string.execution_finish_set),
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
                        val isTransition = (currentState as? WorkoutExecutionState.Rest)?.isTransition == true
                        PrimaryButton(
                            text = stringResource(
                                if (isTransition) R.string.execution_skip_transition else R.string.execution_skip_rest
                            ),
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
                text = stringResource(R.string.execution_exercises),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
            )
            chrome.exercises.forEachIndexed { index, row ->
                val isCurrent = index == chrome.exerciseIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.moveToExercise(index)
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
                    // Название с пометкой занимает всё место, счётчик — у правого края.
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                row.isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Пара суперсета: у обоих номер напарника.
                        chrome.partnerOf(index)?.let { partner ->
                            Text(
                                text = stringResource(R.string.execution_superset_label, partner + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        // Добавленные «+ в сегодняшнюю» стоят в конце списка с пометкой.
                        if (row.addedToday) {
                            Text(
                                text = stringResource(R.string.execution_added_today),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    // Начатое, но не доделанное: видно, сколько подходов уже есть.
                    if (row.doneSets > 0 && !row.isDone) {
                        Text(
                            text = "${row.doneSets}/${row.exercise.sets}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSupersetPicker) {
        val lead = chrome.exercises.getOrNull(chrome.exerciseIndex)
        AppBottomSheet(onDismiss = { showSupersetPicker = false }) {
            Text(
                text = stringResource(R.string.execution_superset_pick_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp)
            )
            if (lead != null) {
                Text(
                    text = stringResource(
                        R.string.execution_superset_pick_hint,
                        lead.exercise.name,
                        (WorkoutSessionManager.TRANSITION_MILLIS / 1000).toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp)
                )
            }
            chrome.supersetCandidates.forEach { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.pairWith(index)
                            showSupersetPicker = false
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
                        text = chrome.exercises[index].exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    val weightSheetExercise = weightSheetIndex?.let { chrome.exercises.getOrNull(it)?.exercise }
    if (weightSheetIndex != null && weightSheetExercise != null) {
        val index = weightSheetIndex ?: 0
        AppBottomSheet(onDismiss = { weightSheetIndex = null }) {
            WeightRepsSheetContent(
                exerciseName = weightSheetExercise.name,
                unit = weightSheetExercise.unit,
                weight = weightSheetExercise.weight,
                extraWeight = weightSheetExercise.extraWeight,
                reps = weightSheetExercise.reps,
                onApply = { weight, extraWeight, reps ->
                    viewModel.updateExerciseWeightAndReps(index, weight, extraWeight, reps)
                    weightSheetIndex = null
                }
            )
        }
    }

    val noteExercise = noteIndex?.let { chrome.exercises.getOrNull(it)?.exercise }
    if (noteIndex != null && noteExercise != null) {
        val index = noteIndex ?: 0
        NoteEditDialog(
            exercise = noteExercise,
            onDismiss = { noteIndex = null },
            onSave = { note ->
                viewModel.updateExerciseNote(index, note)
                noteIndex = null
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.execution_exit_title)) },
            text = { Text(stringResource(R.string.execution_exit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    // Сначала уходим, потом сбрасываем: уходящий экран держит последний кадр
                    // (ViewModel не отдаёт None), и onCleared застанет уже None — close() пустой.
                    onMinimize()
                    viewModel.abandon()
                }) { Text(stringResource(R.string.common_exit)) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showFinishDialog) {
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.execution_finish_title)) },
            text = { Text(stringResource(R.string.execution_finish_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showFinishDialog = false
                    viewModel.onExerciseFinished()
                }) { Text(stringResource(R.string.execution_finish_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text(stringResource(R.string.common_cancel)) }
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
internal fun ActiveContent(
    state: WorkoutExecutionState.Active,
    onEditNote: (Exercise) -> Unit,
    onEditWeightAndReps: (Exercise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EyebrowLabel(
            text = stringResource(R.string.execution_set_of, state.currentSet, state.totalSets),
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
            when (state.exercise.unit) {
                ExerciseUnit.KG -> StatTile(
                    value = state.weight.toCorrectNum(),
                    unit = stringResource(R.string.common_unit_kg),
                    modifier = Modifier.weight(1f),
                    onClick = { onEditWeightAndReps(state.exercise) }
                )

                ExerciseUnit.PLATE -> StatTile(
                    value = state.weight.toCorrectNum(),
                    unit = if (state.extraWeight > 0.0) {
                        stringResource(R.string.unit_plate_with_extra, state.extraWeight.toCorrectNum())
                    } else {
                        stringResource(R.string.unit_name_plate)
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { onEditWeightAndReps(state.exercise) }
                )

                // Без веса плитки нагрузки нет: повторы занимают всю ширину.
                ExerciseUnit.BODYWEIGHT -> Unit
            }
            StatTile(
                value = state.reps.toString(),
                unit = stringResource(R.string.common_unit_reps),
                modifier = Modifier.weight(1f),
                onClick = { onEditWeightAndReps(state.exercise) }
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
    onEditNote: (Exercise) -> Unit,
    onEditWeightAndReps: (Exercise) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EyebrowLabel(
            text = stringResource(
                if (state.isTransition) R.string.execution_transition_next_set else R.string.execution_rest_next_set,
                state.currentSet
            ),
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
        // Отдых — самый удобный момент, чтобы поправить вес на следующий подход,
        // поэтому строка ведёт в тот же лист, что и плитки в Active.
        val load = formatLoad(state.exercise.unit, state.exercise.weight, state.exercise.extraWeight, setFormat())
        Text(
            text = if (load != null) {
                pluralStringResource(
                    R.plurals.execution_rest_weight_reps, state.exercise.reps, load, state.exercise.reps
                )
            } else {
                pluralStringResource(R.plurals.execution_rest_reps, state.exercise.reps, state.exercise.reps)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { onEditWeightAndReps(state.exercise) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
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
            SectionHeader(text = stringResource(R.string.common_note), modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.execution_edit_note),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = note.ifBlank { stringResource(R.string.execution_no_notes) },
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
        EyebrowLabel(text = stringResource(R.string.common_done))
        Text(
            text = DateFormatter.formatDurationCompact(durationMillis),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = workoutName.ifEmpty { stringResource(R.string.execution_workout_fallback) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Box(modifier = Modifier.height(36.dp))
        PrimaryButton(text = stringResource(R.string.execution_to_home), onClick = onDone)
    }
}

@Composable
private fun LoadingContent() {
    // Спека Часть 3 требует Loading/Error через EmptyState — раньше здесь
    // был голый спиннер, из состояний соответствовал только Error.
    EmptyState(
        icon = Icons.Default.HourglassEmpty,
        title = stringResource(R.string.execution_loading_title),
        subtitle = stringResource(R.string.execution_loading_subtitle)
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
        title = { Text(stringResource(R.string.execution_note_dialog_title)) },
        text = {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.execution_note_placeholder)) },
                minLines = 3,
                maxLines = 6
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(noteText) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
            onEditNote = {},
            onEditWeightAndReps = {}
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
            onEditNote = {},
            onEditWeightAndReps = {}
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
