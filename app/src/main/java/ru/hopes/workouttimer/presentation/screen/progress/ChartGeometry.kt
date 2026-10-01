package ru.hopes.workouttimer.presentation.screen.progress

import ru.hopes.workouttimer.domain.model.ProgressPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Ось Y: границы и подписанные деления — всегда целые (спека: «Подписи оси — целые значения»). */
data class YAxis(val min: Int, val max: Int, val ticks: List<Int>)

/** Область линии внутри Canvas в пикселях: слева место под подписи оси, снизу — под даты. */
data class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Своя пара координат вместо Offset: геометрия считается и проверяется без Compose. */
data class ChartPosition(val x: Float, val y: Float)

private val NICE_BASES = listOf(1, 2, 5)

// Больше четырёх промежутков на 200dp — подписи слипаются и сетка шумит.
private const val MAX_INTERVALS = 4

/** Целая ось вокруг уровней точек с круглым шагом 1, 2, 5, 10, 20, 50… */
fun yAxisFor(levels: List<Double>): YAxis {
    require(levels.isNotEmpty()) { "Ось строится хотя бы по одной точке" }
    val rawLo = floor(levels.min()).toInt()
    val rawHi = ceil(levels.max()).toInt()
    // Ровная линия рисуется посередине, а не прижатой к краю.
    val flat = levels.min() == levels.max()
    val lo0 = if (flat) (rawLo - 1).coerceAtLeast(0) else rawLo
    val hi0 = if (flat) rawHi + 1 else rawHi
    val stride = niceStep(hi0 - lo0)
    val lo = Math.floorDiv(lo0, stride) * stride
    val hi = -Math.floorDiv(-hi0, stride) * stride
    return YAxis(lo, hi, (lo..hi step stride).toList())
}

private fun niceStep(span: Int): Int {
    var magnitude = 1
    while (true) {
        for (base in NICE_BASES) {
            val step = base * magnitude
            if ((span + step - 1) / step <= MAX_INTERVALS) return step
        }
        magnitude *= 10
    }
}

/** Доля оси X: реальное время, перерыв между тренировками виден. Одна дата — середина. */
fun timeFraction(time: Long, start: Long, end: Long): Float =
    if (end <= start) 0.5f else ((time - start).toDouble() / (end - start)).toFloat()

/** Доля оси Y снизу вверх. */
fun levelFraction(level: Double, axis: YAxis): Float =
    ((level - axis.min) / (axis.max - axis.min)).toFloat()

fun tickY(tick: Int, axis: YAxis, plot: PlotArea): Float =
    plot.bottom - levelFraction(tick.toDouble(), axis) * (plot.bottom - plot.top)

/** Пиксели точек: X — от первой до последней даты периода, Y — уровень по оси. */
fun pointPositions(points: List<ProgressPoint>, axis: YAxis, plot: PlotArea): List<ChartPosition> {
    if (points.isEmpty()) return emptyList()
    val start = points.minOf { it.finishedAt }
    val end = points.maxOf { it.finishedAt }
    return points.map { point ->
        ChartPosition(
            x = plot.left + timeFraction(point.finishedAt, start, end) * (plot.right - plot.left),
            y = plot.bottom - levelFraction(point.level, axis) * (plot.bottom - plot.top)
        )
    }
}

/**
 * Тап выбирает ближайшую по X точку: человек целится в дату, а не в кружок
 * 8dp. Дальше [maxDistance] — мимо, выбор снимается.
 */
fun nearestPointIndex(xs: List<Float>, tapX: Float, maxDistance: Float): Int? {
    val index = xs.indices.minByOrNull { abs(xs[it] - tapX) } ?: return null
    return index.takeIf { abs(xs[index] - tapX) <= maxDistance }
}
