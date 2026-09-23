package ru.hopes.workouttimer.presentation.screen.workoutHistory

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.domain.model.WorkoutSession
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter

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
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = state.workoutName.ifEmpty { "История" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HistoryContent(sessions = state.sessions)
        }
    }
}

/**
 * Тело экрана под шапкой: пустое состояние либо список сессий карточками.
 * Вынесено из [WorkoutHistoryScreen], чтобы превьюшить без `hiltViewModel()`.
 */
@Composable
private fun HistoryContent(sessions: List<WorkoutSession>) {
    if (sessions.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = "История пуста",
            subtitle = "Завершите тренировку — она появится здесь"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(CardSpacing)
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionCard(session)
            }
        }
    }
}

@Composable
private fun SessionCard(session: WorkoutSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            )
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun HistoryContentEmptyPreview() {
    WorkoutTimerTheme {
        HistoryContent(sessions = emptyList())
    }
}
