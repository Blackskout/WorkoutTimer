package ru.hopes.workouttimer.presentation.screen.workoutPreview

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.Exercise
import ru.hopes.workouttimer.presentation.session.AddResult
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.PrimaryButton
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.presentation.utils.SetFormat
import ru.hopes.workouttimer.presentation.utils.daysAgoText
import ru.hopes.workouttimer.presentation.utils.formatPlannedSet
import ru.hopes.workouttimer.presentation.utils.formatSetList
import ru.hopes.workouttimer.presentation.utils.setFormat

// Разделитель частей плана — знак препинания, а не текст: в ресурсы не выносится (как в SetFormat).
private const val PLAN_SEPARATOR = " · "

@Composable
fun WorkoutPreviewScreen(
    workoutId: Int,
    onNavigateBack: () -> Unit,
    onStart: () -> Unit,
    onReturn: () -> Unit,
    onOpenProgress: (Long) -> Unit,
    viewModel: WorkoutPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    LaunchedEffect(workoutId) {
        viewModel.load(workoutId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            PreviewHeader(name = if (state.loadFailed) "" else state.workout?.name.orEmpty(), onNavigateBack = onNavigateBack)
            WorkoutPreviewContent(
                state = state,
                onStart = onStart,
                onReturn = onReturn,
                onOpenProgress = onOpenProgress,
                onAddToday = { exercise ->
                    if (viewModel.addToday(exercise) == AddResult.ADDED) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                resources.getString(R.string.preview_added_snackbar, exercise.name)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PreviewHeader(name: String, onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Тело экрана под шапкой: сводка, упражнения, нижняя зона. Вынесено из
 * [WorkoutPreviewScreen], чтобы тестировать и превьюшить без hiltViewModel().
 */
@Composable
internal fun WorkoutPreviewContent(
    state: WorkoutPreviewState,
    onStart: () -> Unit,
    onReturn: () -> Unit,
    onOpenProgress: (Long) -> Unit,
    onAddToday: (Exercise) -> Unit
) {
    // До первого ответа базы пустое состояние не показываем — оно мигнуло бы.
    if (!state.isLoaded) return
    if (state.loadFailed) {
        EmptyState(icon = Icons.Default.ErrorOutline, title = stringResource(R.string.preview_error_load))
        return
    }
    val workout = state.workout
    if (workout == null) {
        EmptyState(icon = Icons.Default.FitnessCenter, title = stringResource(R.string.preview_not_found))
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (workout.exercises.isEmpty()) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(icon = Icons.Default.FitnessCenter, title = stringResource(R.string.preview_empty))
            }
        } else {
            val format = setFormat()
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(CardSpacing)
            ) {
                item(key = "summary") {
                    Text(
                        text = summaryLine(workout.exercises.size, state.lastDurationMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(workout.exercises, key = { it.id }) { exercise ->
                    PreviewExerciseRow(
                        exercise = exercise,
                        lastTime = state.lastTime[exercise.catalogId],
                        format = format,
                        canAdd = state.canAddToday,
                        added = exercise.id in state.addedExerciseIds,
                        onOpen = { onOpenProgress(exercise.catalogId) },
                        onAdd = { onAddToday(exercise) }
                    )
                }
            }
        }
        PreviewBottomZone(bottom = state.bottom, onStart = onStart, onReturn = onReturn)
    }
}

/** «6 упр · 52:10» — те же строки, что метаданные списка тренировок. */
@Composable
private fun summaryLine(count: Int, durationMillis: Long?): String =
    if (durationMillis != null) {
        stringResource(R.string.list_meta_with_duration, count, DateFormatter.formatDurationCompact(durationMillis))
    } else {
        stringResource(R.string.list_meta, count)
    }

@Composable
private fun PreviewExerciseRow(
    exercise: Exercise,
    lastTime: LastTime?,
    format: SetFormat,
    canAdd: Boolean,
    added: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Вся строка, кроме кнопки добавления, — тап на прогресс упражнения. Метка нажатия,
        // а не описание: описание заменило бы для TalkBack план и «прошлый раз».
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    onClickLabel = stringResource(R.string.preview_open_progress, exercise.name),
                    role = Role.Button,
                    onClick = onOpen
                )
                .padding(12.dp)
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = planLine(exercise, format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = lastTimeLine(lastTime, format),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (exercise.note.isNotBlank()) {
                Text(
                    text = exercise.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (canAdd) {
            AddTodayButton(name = exercise.name, added = added, onAdd = onAdd)
        }
    }
}

/** «4 подхода · 60 кг × 8 · отдых 02:00»; без веса — «3 подхода · 12 · отдых 01:00». */
@Composable
private fun planLine(exercise: Exercise, format: SetFormat): String = listOf(
    pluralStringResource(R.plurals.preview_sets, exercise.sets, exercise.sets),
    formatPlannedSet(exercise.unit, exercise.weight, exercise.extraWeight, exercise.reps, format),
    stringResource(R.string.preview_rest, DateFormatter.formatDurationCompact(exercise.timeMillis))
).joinToString(PLAN_SEPARATOR)

/** Последняя сессия упражнения из любой тренировки — тот же источник, что у «Упражнений». */
@Composable
private fun lastTimeLine(lastTime: LastTime?, format: SetFormat): String =
    if (lastTime == null || lastTime.sets.isEmpty()) {
        stringResource(R.string.preview_never)
    } else {
        stringResource(
            R.string.preview_last_time,
            daysAgoText(lastTime.finishedAt),
            formatSetList(lastTime.sets, format)
        )
    }

@Composable
private fun AddTodayButton(name: String, added: Boolean, onAdd: () -> Unit) {
    val description = stringResource(R.string.preview_add_today_description, name)
    TextButton(
        onClick = onAdd,
        enabled = !added,
        modifier = Modifier
            .padding(end = 4.dp)
            .heightIn(min = 48.dp)
            .then(if (added) Modifier else Modifier.semantics { contentDescription = description })
    ) {
        if (!added) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = stringResource(if (added) R.string.preview_added else R.string.preview_add_today),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** Нижняя зона над плашкой: «Начать», «Вернуться к тренировке» или подсказка. */
@Composable
private fun PreviewBottomZone(bottom: PreviewBottom, onStart: () -> Unit, onReturn: () -> Unit) {
    when (bottom) {
        PreviewBottom.None -> Unit
        PreviewBottom.Start -> PrimaryButton(
            text = stringResource(R.string.preview_start),
            onClick = onStart,
            modifier = Modifier.padding(ScreenPadding)
        )
        PreviewBottom.Return -> PrimaryButton(
            text = stringResource(R.string.preview_return),
            onClick = onReturn,
            modifier = Modifier.padding(ScreenPadding)
        )
        is PreviewBottom.RunningOther -> Text(
            text = stringResource(R.string.preview_running_other, bottom.workoutName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 16.dp)
        )
    }
}
