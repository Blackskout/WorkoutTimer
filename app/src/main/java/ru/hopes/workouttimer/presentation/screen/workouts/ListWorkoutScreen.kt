package ru.hopes.workouttimer.presentation.screen.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.data.dao.WorkoutWithExercises
import ru.hopes.workouttimer.data.entity.ExerciseEntity
import ru.hopes.workouttimer.data.entity.WorkoutEntity
import ru.hopes.workouttimer.presentation.ui.components.ActionSheet
import ru.hopes.workouttimer.presentation.ui.components.ActionSheetItem
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.SectionHeader
import ru.hopes.workouttimer.presentation.ui.theme.Accent
import ru.hopes.workouttimer.presentation.ui.theme.AccentDark
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.OnAccent
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.SectionSpacing
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListWorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: ListWorkoutViewModel = hiltViewModel(),
    onAddWorkoutClick: () -> Unit,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onEditClick: (WorkoutEntity) -> Unit = {},
    onExportImportClick: () -> Unit = {},
    onHistoryClick: (WorkoutEntity) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var workoutToDelete by rememberSaveable { mutableStateOf<WorkoutEntity?>(null) }
    var menuFor by remember { mutableStateOf<WorkoutEntity?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Снекбар отмены пропуска. dismissSkipUndo() гасит состояние и по таймауту,
    // иначе «Отменить» осталась бы доступной после того, как снекбар исчез.
    LaunchedEffect(state.skippedWorkout) {
        val skipped = state.skippedWorkout ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "${skipped.name} пропущена",
            actionLabel = "Отменить"
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoSkip()
        } else {
            viewModel.dismissSkipUndo()
        }
    }

    workoutToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { workoutToDelete = null },
            title = { Text("Удалить тренировку?") },
            text = { Text("«${target.name}» и её история будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkout(target)
                    workoutToDelete = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { workoutToDelete = null }) { Text("Отмена") }
            }
        )
    }

    menuFor?.let { target ->
        val items = buildList {
            add(
                ActionSheetItem("Начать", Icons.Default.PlayArrow, {
                    menuFor = null
                    onWorkoutClick(target)
                })
            )
            // В режиме поиска очередь не видна целиком, поэтому пропуск скрыт:
            // он переставил бы порядок, которого пользователь сейчас не наблюдает.
            if (!state.isSearching) {
                add(
                    ActionSheetItem("Пропустить", Icons.Default.SkipNext, {
                        menuFor = null
                        viewModel.skipWorkout(target)
                    }, subtitle = "в конец очереди")
                )
            }
            add(
                ActionSheetItem("Редактировать", Icons.Default.Edit, {
                    menuFor = null
                    onEditClick(target)
                })
            )
            add(
                ActionSheetItem("История", Icons.Default.History, {
                    menuFor = null
                    onHistoryClick(target)
                })
            )
            add(
                ActionSheetItem("Удалить", Icons.Default.Delete, {
                    menuFor = null
                    workoutToDelete = target
                }, destructive = true)
            )
        }
        ActionSheet(
            title = target.name,
            subtitle = subtitleFor(target.lastUseAt),
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
                Text("Новая", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
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
                    text = "Тренировки",
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
                        contentDescription = "Поиск",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onExportImportClick) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Экспорт и импорт",
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
                    placeholder = { Text("Название тренировки или упражнения") },
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
                onWorkoutClick = onWorkoutClick,
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
private fun QueueContent(
    state: ListWorkoutState,
    onWorkoutClick: (WorkoutEntity) -> Unit,
    onMenuClick: (WorkoutEntity) -> Unit,
    onAddWorkoutClick: () -> Unit
) {
    if (state.workouts.isEmpty()) {
        EmptyState(
            icon = Icons.Default.FitnessCenter,
            title = if (state.isSearching) "Ничего не найдено" else "Нет тренировок",
            subtitle = if (state.isSearching) {
                "Попробуйте другой запрос"
            } else {
                "Создайте первую — она сразу встанет первой в очереди"
            },
            actionText = if (state.isSearching) null else "Создать тренировку",
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
                        onStart = { onWorkoutClick(next.workout) },
                        onMenu = { onMenuClick(next.workout) }
                    )
                }
            }
            if (state.restOfQueue.isNotEmpty()) {
                item(key = "queue-header") {
                    SectionHeader(
                        text = "Дальше по очереди",
                        modifier = Modifier.padding(top = SectionSpacing, bottom = 2.dp)
                    )
                }
            }
        }

        val rows = if (state.isSearching) state.workouts else state.restOfQueue
        items(rows, key = { it.workout.id }) { item ->
            val position = state.workouts.indexOf(item) + 1
            WorkoutRow(
                item = item,
                position = if (state.isSearching) null else position,
                durationMillis = state.lastSessionDurations[item.workout.id],
                onClick = { onWorkoutClick(item.workout) },
                onMenu = { onMenuClick(item.workout) }
            )
        }
    }
}

/** Подпись давности. `lastUseAt == 0` означает «ещё не делали» (см. Task 12). */
private fun subtitleFor(lastUseAt: Long): String {
    if (lastUseAt == 0L) return "ещё не делали"
    val days = ((System.currentTimeMillis() - lastUseAt) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "сегодня"
        days == 1 -> "вчера"
        else -> "$days дн. назад"
    }
}

@Composable
private fun NextWorkoutCard(
    item: WorkoutWithExercises,
    durationMillis: Long?,
    onStart: () -> Unit,
    onMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Accent, AccentDark)))
            .padding(16.dp)
    ) {
        IconButton(
            onClick = onMenu,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Действия", tint = OnAccent)
        }
        Column {
            Text(
                text = "СЛЕДУЮЩАЯ",
                style = MaterialTheme.typography.labelSmall,
                color = OnAccent.copy(alpha = 0.7f)
            )
            Text(
                text = item.workout.name,
                style = MaterialTheme.typography.headlineMedium,
                color = OnAccent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = metaLine(item, durationMillis),
                style = MaterialTheme.typography.bodySmall,
                color = OnAccent.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 6.dp)
            )
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.background)
                    .combinedClickable(onClick = onStart),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "НАЧАТЬ",
                    color = Accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun WorkoutRow(
    item: WorkoutWithExercises,
    position: Int?,
    durationMillis: Long?,
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
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
            text = subtitleFor(item.workout.lastUseAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onMenu, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Действия",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun metaLine(item: WorkoutWithExercises, durationMillis: Long?): String {
    val exercises = "${item.exercises.size} упр"
    return if (durationMillis != null) {
        "$exercises · ${DateFormatter.formatDurationCompact(durationMillis)}"
    } else {
        exercises
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
        ExerciseEntity(
            id = index,
            workoutId = id.toLong(),
            name = "Упражнение ${index + 1}",
            weight = 20.0,
            sets = 3,
            reps = 10,
            restTimeMillis = 60_000,
            orderInWorkout = index
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
                    previewWorkout(1, "Грудь и трицепс", 0L, 6),
                    previewWorkout(2, "Спина и бицепс", now - dayMillis, 5),
                    previewWorkout(3, "Ноги", now - 2 * dayMillis, 7),
                    previewWorkout(4, "Плечи", now - 5 * dayMillis, 4),
                    previewWorkout(5, "Кардио", now - 10 * dayMillis, 3),
                    previewWorkout(6, "Пресс", now - 20 * dayMillis, 5)
                ),
                lastSessionDurations = mapOf(1 to 3_125_000L)
            ),
            onWorkoutClick = {},
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
            onWorkoutClick = {},
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
            onWorkoutClick = {},
            onMenuClick = {},
            onAddWorkoutClick = {}
        )
    }
}
