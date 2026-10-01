package ru.hopes.workouttimer.presentation.screen.workoutHistory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.SessionExerciseSets
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.presentation.utils.formatSetList
import ru.hopes.workouttimer.presentation.utils.setFormat

@Composable
fun WorkoutHistoryScreen(
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
    workoutId: Int,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(workoutId) {
        viewModel.loadHistory(workoutId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
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
                    text = state.workoutName.ifEmpty { stringResource(R.string.list_action_history) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HistoryContent(sessions = state.sessions, setsBySession = state.setsBySession)
        }
    }
}

/**
 * Тело экрана под шапкой: пустое состояние либо список сессий карточками.
 * Вынесено из [WorkoutHistoryScreen], чтобы превьюшить и тестировать без `hiltViewModel()`.
 */
@Composable
internal fun HistoryContent(
    sessions: List<WorkoutSession>,
    setsBySession: Map<Long, List<SessionExerciseSets>>
) {
    if (sessions.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = stringResource(R.string.history_empty_title),
            subtitle = stringResource(R.string.history_empty_subtitle)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(CardSpacing)
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionCard(session, setsBySession[session.id.toLong()].orEmpty())
            }
        }
    }
}

/**
 * Карточка сессии. С подходами — разворачивается по тапу; сессия до обновления
 * подходов не имеет, не разворачивается и честно это подписывает.
 */
@Composable
private fun SessionCard(session: WorkoutSession, exercises: List<SessionExerciseSets>) {
    val hasSets = exercises.isNotEmpty()
    // rememberSaveable: развёрнутая карточка переживает прокрутку и поворот экрана.
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val format = setFormat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                enabled = hasSets,
                onClickLabel = stringResource(if (expanded) R.string.history_collapse else R.string.history_expand)
            ) { expanded = !expanded }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DateFormatter.formatSessionDateTime(session.finishedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = DateFormatter.formatDurationCompact(session.durationMillis),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (hasSets) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        if (!hasSets) {
            Text(
                text = stringResource(R.string.history_no_sets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else if (expanded) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Строка упражнения поведёт на его прогресс с E3; пока не нажимается.
                exercises.forEach { group ->
                    Text(
                        text = stringResource(
                            R.string.history_exercise_sets,
                            group.exerciseName,
                            formatSetList(group.sets, format)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun previewSession(id: Int, finishedAt: Long, durationMillis: Long): WorkoutSession =
    WorkoutSession(
        id = id,
        workoutId = 1,
        startedAt = finishedAt - durationMillis,
        finishedAt = finishedAt,
        durationMillis = durationMillis
    )

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun HistoryContentPopulatedPreview() {
    val now = System.currentTimeMillis()
    val dayMillis = 86_400_000L
    WorkoutTimerTheme {
        HistoryContent(
            sessions = listOf(
                previewSession(1, now, 2_730_000L),
                previewSession(2, now - dayMillis, 3_125_000L),
                previewSession(3, now - 3 * dayMillis, 4_010_000L)
            ),
            setsBySession = mapOf(
                1L to listOf(
                    SessionExerciseSets(
                        catalogId = 1L,
                        exerciseName = "Присед",
                        sets = listOf(
                            SessionSet(1L, 1L, 1L, 60.0, 0.0, 8, ExerciseUnit.KG),
                            SessionSet(2L, 1L, 1L, 62.5, 0.0, 6, ExerciseUnit.KG)
                        )
                    )
                )
            )
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun HistoryContentEmptyPreview() {
    WorkoutTimerTheme {
        HistoryContent(sessions = emptyList(), setsBySession = emptyMap())
    }
}
