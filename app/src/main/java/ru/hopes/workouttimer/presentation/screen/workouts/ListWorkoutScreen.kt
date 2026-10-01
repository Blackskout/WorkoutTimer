package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.data.dao.ExerciseWithCatalog
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.presentation.ui.components.ActionSheet
import ru.hopes.workouttimer.presentation.ui.components.ActionSheetItem
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.EyebrowLabel
import ru.hopes.workouttimer.presentation.ui.components.SectionHeader
import ru.hopes.workouttimer.presentation.ui.theme.Accent
import ru.hopes.workouttimer.presentation.ui.theme.AccentDark
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.OnAccent
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.SectionSpacing
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.presentation.utils.daysAgoText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: ListWorkoutViewModel = hiltViewModel(),
    onAddWorkoutClick: () -> Unit,
    onOpen: (WorkoutEntity) -> Unit,
    onStart: (WorkoutEntity) -> Unit,
    onReturn: () -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onExportImportClick: () -> Unit = {},
    onHistoryClick: (WorkoutEntity) -> Unit = {},
    onExercisesClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var workoutToDelete by rememberSaveable { mutableStateOf<WorkoutEntity?>(null) }
    var menuFor by remember { mutableStateOf<WorkoutEntity?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val undoLabel = stringResource(R.string.common_undo)

    // Снекбар отмены пропуска. dismissSkipUndo() гасит состояние и по таймауту,
    // иначе «Отменить» осталась бы доступной после того, как снекбар исчез.
    LaunchedEffect(state.skippedWorkout) {
        val skipped = state.skippedWorkout ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.list_skipped_snackbar, skipped.name),
            actionLabel = undoLabel
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoSkip()
        } else {
            viewModel.dismissSkipUndo()
        }
    }

    // Сбой операции с БД. Снекбар — единственный след ошибки: раньше исключение
    // из viewModelScope роняло приложение, теперь оно доезжает сюда.
    LaunchedEffect(state.errorMessage) {
        val messageRes = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(messageRes))
        viewModel.dismissError()
    }

    workoutToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text(stringResource(R.string.list_delete_title)) },
            text = { Text(stringResource(R.string.list_delete_message, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkout(target)
                    workoutToDelete = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    menuFor?.let { target ->
        val locked = stringResource(R.string.list_action_locked)
        val items = listMenuEntries(target.id, state.runningWorkoutId, state.isSearching).map { entry ->
            when (entry.action) {
                ListMenuAction.START ->
                    ActionSheetItem(stringResource(R.string.list_action_start), Icons.Default.PlayArrow, {
                        menuFor = null
                        onStart(target)
                    })
                ListMenuAction.RETURN ->
                    ActionSheetItem(stringResource(R.string.list_action_return), Icons.Default.PlayArrow, {
                        menuFor = null
                        onReturn()
                    })
                ListMenuAction.SKIP ->
                    ActionSheetItem(stringResource(R.string.list_action_skip), Icons.Default.SkipNext, {
                        menuFor = null
                        viewModel.skipWorkout(target)
                    }, subtitle = stringResource(R.string.list_action_skip_subtitle))
                ListMenuAction.EDIT ->
                    ActionSheetItem(
                        stringResource(R.string.list_action_edit), Icons.Default.Edit, {
                            menuFor = null
                            onEditClick(target)
                        },
                        subtitle = if (entry.enabled) null else locked,
                        enabled = entry.enabled
                    )
                ListMenuAction.HISTORY ->
                    ActionSheetItem(stringResource(R.string.list_action_history), Icons.Default.History, {
                        menuFor = null
                        onHistoryClick(target)
                    })
                ListMenuAction.DELETE ->
                    ActionSheetItem(
                        stringResource(R.string.common_delete), Icons.Default.Delete, {
                            menuFor = null
                            workoutToDelete = target
                        },
                        subtitle = if (entry.enabled) null else locked,
                        destructive = true,
                        enabled = entry.enabled
                    )
            }
        }
        ActionSheet(
            title = target.name,
            subtitle = daysAgoText(target.lastUseAt),
            items = items,
            onDismiss = { menuFor = null }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddWorkoutClick,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.list_fab_new), modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.list_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    searchVisible = !searchVisible
                    if (!searchVisible) viewModel.updateSearchQuery("")
                }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.list_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onExercisesClick) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = stringResource(R.string.catalog_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onExportImportClick) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.list_export_import),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (searchVisible) {
                TextField(
                    value = state.query,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding),
                    placeholder = { Text(stringResource(R.string.list_search_placeholder)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            QueueContent(
                state = state,
                onOpen = onOpen,
                onStart = onStart,
                onReturn = onReturn,
                onMenuClick = { menuFor = it },
                onAddWorkoutClick = onAddWorkoutClick
            )
        }
    }
}

/**
 * Содержимое экрана под шапкой: либо [EmptyState], либо очередь тренировок —
 * герой-карта следующей и список остальных. Вынесено из [ListWorkoutScreen], чтобы
 * каждое состояние экрана можно было превьюшить без `hiltViewModel()`.
 */
@Composable
internal fun QueueContent(
    state: ListWorkoutState,
    onOpen: (WorkoutEntity) -> Unit,
    onStart: (WorkoutEntity) -> Unit,
    onReturn: () -> Unit,
    onMenuClick: (WorkoutEntity) -> Unit,
    onAddWorkoutClick: () -> Unit
) {
    if (state.workouts.isEmpty()) {
        EmptyState(
            icon = Icons.Default.FitnessCenter,
            title = stringResource(
                if (state.isSearching) R.string.list_search_empty_title else R.string.list_empty_title
            ),
            subtitle = stringResource(
                if (state.isSearching) R.string.list_search_empty_subtitle else R.string.list_empty_subtitle
            ),
            actionText = if (state.isSearching) null else stringResource(R.string.list_empty_action),
            onAction = if (state.isSearching) null else onAddWorkoutClick
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenPadding,
            end = ScreenPadding,
            top = SectionSpacing,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(CardSpacing)
    ) {
        // В поиске очереди нет — только плоский список совпадений.
        if (!state.isSearching) {
            state.nextWorkout?.let { next ->
                item(key = "hero-${next.workout.id}") {
                    NextWorkoutCard(
                        item = next,
                        durationMillis = state.lastSessionDurations[next.workout.id],
                        action = heroActionOf(next.workout.id, state.runningWorkoutId),
                        onAction = {
                            if (state.runningWorkoutId == null) onStart(next.workout) else onReturn()
                        },
                        onMenu = { onMenuClick(next.workout) },
                        onOpen = { onOpen(next.workout) }
                    )
                }
            }
            if (state.restOfQueue.isNotEmpty()) {
                item(key = "queue-header") {
                    SectionHeader(
                        text = stringResource(R.string.list_queue_header),
                        modifier = Modifier.padding(top = SectionSpacing, bottom = 2.dp)
                    )
                }
            }
        }

        // rows — это state.restOfQueue (drop(1) от очереди), герой уже занял позицию 1,
        // поэтому первая строка тут — позиция 2. itemsIndexed вместо indexOf(item):
        // O(1) вместо O(n) на строку и без сравнения вложенных exercises по equals.
        val rows = if (state.isSearching) state.workouts else state.restOfQueue
        itemsIndexed(rows, key = { _, item -> item.workout.id }) { index, item ->
            WorkoutRow(
                item = item,
                position = if (state.isSearching) null else index + 2,
                durationMillis = state.lastSessionDurations[item.workout.id],
                isRunning = item.workout.id == state.runningWorkoutId,
                onClick = { onOpen(item.workout) },
                onMenu = { onMenuClick(item.workout) }
            )
        }
    }
}

@Composable
private fun NextWorkoutCard(
    item: WorkoutWithExercises,
    durationMillis: Long?,
    action: HeroAction?,
    onAction: () -> Unit,
    onMenu: () -> Unit,
    onOpen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Accent, AccentDark)))
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        IconButton(
            onClick = onMenu,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.list_actions),
                tint = OnAccent,
                modifier = Modifier.size(28.dp)
            )
        }
        Column {
            EyebrowLabel(text = stringResource(R.string.list_next), color = OnAccent.copy(alpha = 0.7f))
            Text(
                text = item.workout.name,
                style = MaterialTheme.typography.headlineMedium,
                color = OnAccent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "${metaLine(item, durationMillis)} · ${daysAgoText(item.workout.lastUseAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = OnAccent.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp)
            )
            // У другой тренировки при идущей сессии кнопки нет: вторую сессию не начать.
            if (action != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.background)
                        .combinedClickable(onClick = onAction),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(
                            when (action) {
                                HeroAction.START -> R.string.list_start_button
                                HeroAction.RETURN -> R.string.list_return_button
                            }
                        ),
                        color = Accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(
    item: WorkoutWithExercises,
    position: Int?,
    durationMillis: Long?,
    /** Эта тренировка сейчас идёт: акцентная рамка и «идёт» вместо давности. */
    isRunning: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (position != null) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.workout.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = metaLine(item, durationMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (isRunning) stringResource(R.string.list_running) else daysAgoText(item.workout.lastUseAt),
            style = MaterialTheme.typography.bodySmall,
            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isRunning) FontWeight.Medium else null
        )
        IconButton(onClick = onMenu) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.list_actions),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun metaLine(item: WorkoutWithExercises, durationMillis: Long?): String {
    val count = item.exercises.size
    return if (durationMillis != null) {
        stringResource(
            R.string.list_meta_with_duration,
            count,
            DateFormatter.formatDurationCompact(durationMillis)
        )
    } else {
        stringResource(R.string.list_meta, count)
    }
}

/** Тестовая тренировка для превью — рантаймом не используется. */
private fun previewWorkout(
    id: Int,
    name: String,
    lastUseAt: Long,
    exerciseCount: Int
): WorkoutWithExercises = WorkoutWithExercises(
    workout = WorkoutEntity(id = id, name = name, lastUseAt = lastUseAt),
    exercises = List(exerciseCount) { index ->
        ExerciseWithCatalog(
            exercise = ExerciseEntity(
                id = index,
                workoutId = id.toLong(),
                name = "Упражнение ${index + 1}",
                weight = 20.0,
                sets = 3,
                reps = 10,
                restTimeMillis = 60_000,
                orderInWorkout = index,
                catalogId = index.toLong() + 1
            ),
            catalog = null
        )
    }
)

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun QueueContentSixWorkoutsPreview() {
    val now = System.currentTimeMillis()
    val dayMillis = 86_400_000L
    WorkoutTimerTheme {
        QueueContent(
            state = ListWorkoutState(
                workouts = listOf(
                    previewWorkout(4, "Плечи", now - 5 * dayMillis, 4),
                    previewWorkout(2, "Спина и бицепс", now - dayMillis, 5),
                    previewWorkout(3, "Ноги", now - 2 * dayMillis, 7),
                    previewWorkout(1, "Грудь и трицепс", 0L, 6),
                    previewWorkout(5, "Кардио", now - 10 * dayMillis, 3),
                    previewWorkout(6, "Пресс", now - 20 * dayMillis, 5)
                ),
                lastSessionDurations = mapOf(4 to 3_125_000L)
            ),
            onOpen = {}, onStart = {}, onReturn = {},
            onMenuClick = {},
            onAddWorkoutClick = {}
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun QueueContentEmptyPreview() {
    WorkoutTimerTheme {
        QueueContent(
            state = ListWorkoutState(),
            onOpen = {}, onStart = {}, onReturn = {},
            onMenuClick = {},
            onAddWorkoutClick = {}
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun QueueContentSearchPreview() {
    val now = System.currentTimeMillis()
    WorkoutTimerTheme {
        QueueContent(
            state = ListWorkoutState(
                query = "ноги",
                workouts = listOf(previewWorkout(3, "Ноги", now - 2 * 86_400_000L, 7))
            ),
            onOpen = {}, onStart = {}, onReturn = {},
            onMenuClick = {},
            onAddWorkoutClick = {}
        )
    }
}
