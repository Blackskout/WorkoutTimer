package ru.hopes.workouttimer.presentation.screen.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.ExerciseUnit
import ru.hopes.workouttimer.domain.model.ProgressPoint
import ru.hopes.workouttimer.domain.model.SessionSet
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import java.util.TimeZone

const val PROGRESS_CHART_TAG = "progress_chart"

// Высота включает полосу дат под осью: подписи не обрезаются и не дают вложенной прокрутки.
private val ChartHeight = 200.dp
// Поля сверху и справа вмещают выбранную точку с кольцом (6 + 2 dp).
private val PlotTopPadding = 12.dp
private val PlotEndPadding = 12.dp
private val XAxisBand = 28.dp
private val DateLabelGap = 10.dp
private val AxisLabelGap = 8.dp
private val LineWidth = 2.dp
private val GridWidth = 1.dp
private val MarkerRadius = 4.dp
private val SelectedMarkerRadius = 6.dp
private val RingWidth = 2.dp
// Зона попадания тапа по X — с запасом шире кружка 8dp.
private val HitRadius = 24.dp

/**
 * Линия лучшего подхода по сессиям. Вся математика — в ChartGeometry.kt;
 * здесь только рисование и передача тапа. Подпись выбранной точки рисует
 * не Canvas, а карточка над ним — её текст читает TalkBack и находят тесты.
 */
@Composable
internal fun ProgressChart(
    points: List<ProgressPoint>,
    selectedSessionId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    // Кольцо цвета подложки: точка читается поверх линии и соседних точек.
    val ringColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outline
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Текст — цветом текста, никогда цветом линии.
    val labelStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()
    val months = stringArrayResource(R.array.progress_months).toList()
    val dateTemplate = stringResource(R.string.progress_short_date)
    val zone = remember { TimeZone.getDefault() }
    val description = pluralStringResource(R.plurals.progress_chart_description, points.size, points.size)
    val density = LocalDensity.current
    val currentOnSelect by rememberUpdatedState(onSelect)

    val axis = remember(points) { yAxisFor(points.map { it.level }) }
    val tickLabels = remember(axis, labelStyle) {
        axis.ticks.map { textMeasurer.measure(it.toString(), labelStyle) }
    }
    val firstDateText = shortDate(points.first().finishedAt, dateTemplate, months, zone)
    val lastDateText = shortDate(points.last().finishedAt, dateTemplate, months, zone)
    val firstDate = remember(firstDateText, labelStyle) { textMeasurer.measure(firstDateText, labelStyle) }
    val lastDate = remember(lastDateText, labelStyle) { textMeasurer.measure(lastDateText, labelStyle) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val plot = with(density) {
        PlotArea(
            left = tickLabels.maxOf { it.size.width } + AxisLabelGap.toPx(),
            top = PlotTopPadding.toPx(),
            right = canvasSize.width - PlotEndPadding.toPx(),
            bottom = canvasSize.height - XAxisBand.toPx()
        )
    }
    val positions = remember(points, axis, plot) { pointPositions(points, axis, plot) }
    val hitRadius = with(density) { HitRadius.toPx() }
    val selectedIndex = points.indexOfFirst { it.sessionId == selectedSessionId }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .onSizeChanged { canvasSize = it }
            .pointerInput(points, positions) {
                detectTapGestures { tap ->
                    val index = nearestPointIndex(positions.map { it.x }, tap.x, hitRadius)
                    currentOnSelect(index?.let { points[it].sessionId })
                }
            }
            .semantics { contentDescription = description }
            .testTag(PROGRESS_CHART_TAG)
    ) {
        if (canvasSize == IntSize.Zero) return@Canvas
        val hairline = GridWidth.toPx()

        axis.ticks.forEachIndexed { i, tick ->
            val y = tickY(tick, axis, plot)
            drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = hairline)
            val label = tickLabels[i]
            drawText(
                label,
                topLeft = Offset(plot.left - AxisLabelGap.toPx() - label.size.width, y - label.size.height / 2f)
            )
        }

        val dateTop = plot.bottom + DateLabelGap.toPx()
        drawText(firstDate, topLeft = Offset(plot.left, dateTop))
        // Все точки в один день — одна подпись, а не две наложенные.
        if (lastDateText != firstDateText) {
            drawText(lastDate, topLeft = Offset(plot.right - lastDate.size.width, dateTop))
        }

        if (selectedIndex >= 0) {
            val x = positions[selectedIndex].x
            drawLine(crosshairColor, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = hairline)
        }

        val line = Path().apply {
            positions.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(
            line,
            lineColor,
            style = Stroke(width = LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        positions.forEachIndexed { i, p ->
            val radius = (if (i == selectedIndex) SelectedMarkerRadius else MarkerRadius).toPx()
            val center = Offset(p.x, p.y)
            drawCircle(ringColor, radius = radius + RingWidth.toPx(), center = center)
            drawCircle(lineColor, radius = radius, center = center)
        }
    }
}

@Preview(backgroundColor = 0xFF16161C, showBackground = true)
@Composable
private fun ProgressChartPreview() {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    fun point(id: Long, daysAgo: Int, weight: Double) = ProgressPoint(
        id, now - daysAgo * day, SessionSet(id, id, 1L, weight, 0.0, 8, ExerciseUnit.KG), weight
    )
    WorkoutTimerTheme {
        ProgressChart(
            points = listOf(point(1, 80, 55.0), point(2, 45, 57.5), point(3, 20, 60.0), point(4, 5, 62.5)),
            selectedSessionId = 3L,
            onSelect = {},
            modifier = Modifier.padding(12.dp)
        )
    }
}
