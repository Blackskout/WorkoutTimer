package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.CatalogExercise
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressDelta
import ru.hopes.workouttimer.domain.model.ProgressPeriod
import ru.hopes.workouttimer.domain.model.ProgressPoint
import ru.hopes.workouttimer.domain.model.ProgressSession
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.domain.model.summarizeProgress
import ru.hopes.workouttimer.presentation.ui.components.EmptyState
import ru.hopes.workouttimer.presentation.ui.components.SectionHeader
import ru.hopes.workouttimer.presentation.ui.theme.CardSpacing
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding
import ru.hopes.workouttimer.presentation.ui.theme.SectionSpacing
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import ru.hopes.workouttimer.presentation.utils.SetFormat
import ru.hopes.workouttimer.presentation.utils.formatSetList
import ru.hopes.workouttimer.presentation.utils.setFormat
import ru.hopes.workouttimer.presentation.utils.unitName
import java.util.TimeZone

const val PROGRESS_HERO_TAG = "progress_hero"
const val PROGRESS_LIST_TAG = "progress_list"

@Composable
fun ExerciseProgressScreen(
    catalogId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ExerciseProgressViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(catalogId) {
        viewModel.load(catalogId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            ProgressHeader(exercise = state.exercise, onNavigateBack = onNavigateBack)
            ProgressContent(
                state = state,
                onPeriodChange = viewModel::selectPeriod,
                onSelectPoint = viewModel::selectPoint
            )
        }
    }
}

/** Шапка: название упражнения, справа — его текущая единица. */
@Composable
private fun ProgressHeader(exercise: CatalogExercise?, onNavigateBack: () -> Unit) {
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
            text = exercise?.name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (exercise != null) {
            Text(
                text = unitName(exercise.unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Тело экрана под шапкой. Вынесено из [ExerciseProgressScreen], чтобы
 * превьюшить и тестировать без `hiltViewModel()`. Список сессий — табличная
 * замена графика для TalkBack: каждое значение графика есть и в нём.
 */
@Composable
internal fun ProgressContent(
    state: ExerciseProgressState,
    onPeriodChange: (ProgressPeriod) -> Unit,
    onSelectPoint: (Long?) -> Unit
) {
    // До первого ответа базы пустое состояние не показываем — оно мигнуло бы.
    if (!state.isLoaded) return
    if (state.loadFailed) {
        EmptyState(icon = Icons.Default.Timeline, title = stringResource(R.string.progress_error_load))
        return
    }
    if (state.exercise == null) {
        EmptyState(icon = Icons.Default.Timeline, title = stringResource(R.string.progress_missing_title))
        return
    }
    val summary = state.summary ?: return
    if (state.sessions.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Timeline,
            title = stringResource(R.string.progress_empty_title),
            subtitle = stringResource(R.string.progress_empty_subtitle)
        )
        return
    }
    val format = setFormat()
    val repsTemplate = stringResource(R.string.progress_reps)
    val newestFirst = remember(state.sessions) { state.sessions.asReversed() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PROGRESS_LIST_TAG),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(CardSpacing)
    ) {
        summary.best?.let { best ->
            item(key = "hero") {
                ProgressHero(best = best, delta = summary.delta, period = state.period, format = format, repsTemplate = repsTemplate)
            }
        }
        // Нет ни одной точки в текущей единице — нечего ни рисовать, ни фильтровать.
        if (summary.hasPoints) {
            item(key = "chart") {
                ChartCard(
                    points = summary.points,
                    selectedSessionId = state.selectedSessionId,
                    format = format,
                    repsTemplate = repsTemplate,
                    onSelectPoint = onSelectPoint
                )
            }
        }
        if (summary.hasOtherUnits) {
            item(key = "other_units") {
                Text(
                    text = stringResource(R.string.progress_other_units),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (summary.hasPoints) {
            item(key = "period") {
                PeriodSelector(current = state.period, onChange = onPeriodChange)
            }
        }
        item(key = "sessions_title") {
            SectionHeader(
                text = stringResource(R.string.progress_sessions_title),
                modifier = Modifier.padding(top = SectionSpacing - CardSpacing)
            )
        }
        items(newestFirst, key = { it.sessionId }) { session ->
            SessionRow(session = session, isSelected = session.sessionId == state.selectedSessionId, format = format)
        }
    }
}

/**
 * Главная цифра — лучший подход за всё время, под ней изменение за период.
 * Изменение — текстовым цветом: рост веса не всегда «хорошо», а падение не «плохо».
 */
@Composable
private fun ProgressHero(
    best: SessionSet,
    delta: ProgressDelta?,
    period: ProgressPeriod,
    format: SetFormat,
    repsTemplate: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        SectionHeader(text = stringResource(R.string.progress_best_label))
        Text(
            text = progressSetText(best, format, repsTemplate),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = 6.dp)
                .testTag(PROGRESS_HERO_TAG)
        )
        if (delta != null) {
            Text(
                text = formatDelta(delta, periodSpan(period), deltaFormat()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Карточка графика: подпись выбранной точки над линией; меньше двух точек — объяснение вместо линии. */
@Composable
private fun ChartCard(
    points: List<ProgressPoint>,
    selectedSessionId: Long?,
    format: SetFormat,
    repsTemplate: String,
    onSelectPoint: (Long?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        if (points.size < 2) {
            Text(
                text = stringResource(R.string.progress_chart_few),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val selected = points.firstOrNull { it.sessionId == selectedSessionId }
            val months = stringArrayResource(R.array.progress_months).toList()
            val dateTemplate = stringResource(R.string.progress_short_date)
            // Строка держит высоту и без выбора — график не прыгает при первом тапе.
            Text(
                text = if (selected != null) {
                    stringResource(
                        R.string.progress_point_label,
                        shortDate(selected.finishedAt, dateTemplate, months, TimeZone.getDefault()),
                        progressSetText(selected.best, format, repsTemplate)
                    )
                } else {
                    stringResource(R.string.progress_chart_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            ProgressChart(
                points = points,
                selectedSessionId = selectedSessionId,
                onSelect = onSelectPoint,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Период: одна строка, три равные кнопки; выбранная — подложкой, а не цветом линии. */
@Composable
private fun PeriodSelector(current: ProgressPeriod, onChange: (ProgressPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ProgressPeriod.entries.forEach { period ->
            val selected = period == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .selectable(selected = selected, onClick = { onChange(period) }, role = Role.RadioButton),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = periodName(period),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Строка сессии: дата и все подходы со своими подписями. Выбранная точка подсвечивает её рамкой. */
@Composable
private fun SessionRow(session: ProgressSession, isSelected: Boolean, format: SetFormat) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .semantics(mergeDescendants = true) { selected = isSelected }
            .padding(14.dp)
    ) {
        Text(
            text = DateFormatter.formatSessionDateTime(session.finishedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatSetList(session.sets, format),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true, heightDp = 900)
@Composable
private fun ProgressContentPreview() {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    fun session(id: Long, daysAgo: Int, weight: Double, reps: Int) = ProgressSession(
        id, now - daysAgo * day, listOf(SessionSet(id, id, 1L, weight, 0.0, reps, ExerciseUnit.KG))
    )
    val sessions = listOf(session(1, 80, 55.0, 8), session(2, 45, 57.5, 8), session(3, 20, 60.0, 8), session(4, 5, 62.5, 6))
    WorkoutTimerTheme {
        ProgressContent(
            state = ExerciseProgressState(
                isLoaded = true,
                exercise = CatalogExercise(1L, "Присед", ExerciseUnit.KG),
                sessions = sessions,
                summary = summarizeProgress(sessions, ExerciseUnit.KG, ProgressPeriod.QUARTER, now, TimeZone.getDefault()),
                selectedSessionId = 3L
            ),
            onPeriodChange = {},
            onSelectPoint = {}
        )
    }
}
