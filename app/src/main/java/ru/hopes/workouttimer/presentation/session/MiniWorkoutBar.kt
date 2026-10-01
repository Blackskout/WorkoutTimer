package ru.hopes.workouttimer.presentation.session

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter

private val BarHeight = 64.dp
private val RingZone = 48.dp
private val RingSize = 28.dp
private val RingStroke = 3.dp
private val TopBorder = 1.dp

/** Плашка свёрнутой тренировки внизу всех экранов, кроме экрана выполнения. */
@Composable
fun MiniWorkoutBar(
    viewModel: MiniWorkoutBarViewModel,
    onOpen: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.barState.collectAsState()
    // На время анимации исчезновения сессия уже None — плашка дорисовывает последний
    // показанный снимок, иначе уезжала бы пустой.
    val lastShown = remember { LastShown<MiniBarState>() }
    state?.let { lastShown.value = it }
    val shown = lastShown.value ?: return
    MiniWorkoutBarContent(state = shown, onOpen = onOpen, onExit = onExit, modifier = modifier)
}

/** Держатель без снимкового состояния: перерисовку и так даёт смена barState. */
private class LastShown<T : Any> {
    var value: T? = null
}

/** Тело плашки без ViewModel — для тестов и превью. */
@Composable
fun MiniWorkoutBarContent(
    state: MiniBarState,
    onOpen: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outline = MaterialTheme.colorScheme.outline
    val status = state.status
    val statusText = when (status) {
        is MiniBarStatus.Resting ->
            stringResource(R.string.minibar_rest, DateFormatter.formatDurationCompact(status.timeLeftMillis))
        is MiniBarStatus.Working ->
            stringResource(R.string.minibar_set, status.currentSet, status.totalSets, status.exerciseName)
        is MiniBarStatus.RestOver -> stringResource(R.string.minibar_rest_over, status.nextSet)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .drawBehind {
                // Верхняя граница цвета outline — как рамка карточек темы.
                drawLine(outline, Offset.Zero, Offset(size.width, 0f), strokeWidth = TopBorder.toPx())
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Вся плашка, кроме крестика, — одна зона нажатия. clickable сам объединяет тексты
        // в один узел: TalkBack читает «Ноги, Отдых 01:18». liveRegion не ставится —
        // отсчёт зачитывался бы вслух каждую секунду.
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    onClickLabel = stringResource(R.string.minibar_open),
                    role = Role.Button,
                    onClick = onOpen
                )
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(RingZone), contentAlignment = Alignment.Center) {
                MiniRing(status = status, modifier = Modifier.size(RingSize))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = state.workoutName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (status is MiniBarStatus.RestOver) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onExit) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.minibar_exit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Маленькое кольцо отдыха: дорожка outline, дуга primary. Не мигает и не крутится. */
@Composable
private fun MiniRing(status: MiniBarStatus, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val fraction = ringFraction(status)
    Canvas(modifier = modifier) {
        val width = RingStroke.toPx()
        val inset = width / 2
        val arcSize = Size(size.width - width, size.height - width)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width)
        )
        if (fraction > 0f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round)
            )
        }
    }
}

@Preview(backgroundColor = 0xFF16161C, showBackground = true)
@Composable
private fun MiniWorkoutBarPreview() {
    WorkoutTimerTheme {
        Column {
            MiniWorkoutBarContent(MiniBarState("Ноги", MiniBarStatus.Resting(78_000L, 120_000L)), {}, {})
            MiniWorkoutBarContent(MiniBarState("Ноги", MiniBarStatus.Working(2, 4, "Жим лёжа")), {}, {})
            MiniWorkoutBarContent(MiniBarState("Ноги", MiniBarStatus.RestOver(2)), {}, {})
        }
    }
}
