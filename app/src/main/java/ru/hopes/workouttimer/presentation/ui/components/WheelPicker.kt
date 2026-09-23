package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import kotlin.math.abs

private val ItemHeight = 42.dp
private const val VISIBLE_ITEMS = 5
private const val EDGE_ITEMS = VISIBLE_ITEMS / 2

/**
 * Индекс значения, ближайшего к [target]. Нужен, чтобы вес, введённый когда-то
 * с клавиатуры и не попадающий в шаг барабана, не сбрасывал барабан в начало.
 */
fun wheelIndexOfNearest(values: List<Double>, target: Double): Int {
    if (values.isEmpty()) return 0
    var best = 0
    var bestDelta = abs(values[0] - target)
    for (i in values.indices) {
        val delta = abs(values[i] - target)
        if (delta < bestDelta) {
            best = i
            bestDelta = delta
        }
    }
    return best
}

@Composable
fun <T> WheelPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    format: (T) -> String
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    val centeredIndex by remember {
        derivedStateOf {
            val offset = state.firstVisibleItemScrollOffset
            val index = state.firstVisibleItemIndex
            if (offset > 0) index + 1 else index
        }
    }

    // rememberUpdatedState: ключ LaunchedEffect(state) не меняется между
    // рекомпозициями (state запоминается один раз), поэтому без этого эффект
    // навсегда захватил бы selectedIndex и onSelected из первой композиции и
    // сравнивал бы с устаревшим индексом — барабан сообщал бы об изменении
    // не больше одного раза за всё время жизни.
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnSelected by rememberUpdatedState(onSelected)

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val index = centeredIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    if (index != currentSelectedIndex) currentOnSelected(index)
                }
            }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            state = state,
            flingBehavior = fling,
            modifier = Modifier.height(ItemHeight * VISIBLE_ITEMS),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // count = ... обязателен: внутри LazyColumn имя items занято параметром-списком.
            items(count = EDGE_ITEMS) { Box(modifier = Modifier.height(ItemHeight)) }
            items(count = items.size) { index ->
                val isSelected = index == centeredIndex
                Box(
                    modifier = Modifier
                        .height(ItemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = format(items[index]),
                        fontSize = if (isSelected) 26.sp else 18.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(count = EDGE_ITEMS) { Box(modifier = Modifier.height(ItemHeight)) }
        }
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Несколько барабанов в ряд с общей полосой выделения по центру.
 *
 * Стандартный [Row] с `Arrangement.SpaceEvenly` тут не подходит: без веса на
 * детях каждый неweighted ребёнок получает на измерение всю оставшуюся
 * главную ось (`mainAxisMax - уже занятое`), поэтому первый [WheelPicker]
 * (его LazyColumn с fillMaxWidth() внутри) забирает всю ширину ряда, а
 * следующим достаётся 0 — что и превращало «подх»/«повт» в невидимые
 * барабаны, а «отдых» — в полоску у края экрана. Раскладка ниже сама делит
 * ширину строки поровну между детьми через собственный [Layout], поэтому
 * барабаны не нужно снабжать `Modifier.weight` на каждом месте вызова —
 * забыть об этом невозможно, гарантия внутри WheelRow.
 */
@Composable
fun WheelRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(ItemHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Layout(
            content = { EqualWidthRowScope.content() },
            modifier = Modifier.fillMaxWidth()
        ) { measurables, constraints ->
            if (measurables.isEmpty()) {
                return@Layout layout(constraints.minWidth, constraints.minHeight) {}
            }
            val childWidth = constraints.maxWidth / measurables.size
            val childConstraints = Constraints(
                minWidth = childWidth,
                maxWidth = childWidth,
                minHeight = constraints.minHeight,
                maxHeight = constraints.maxHeight
            )
            val placeables = measurables.map { it.measure(childConstraints) }
            val rowHeight = placeables.maxOf { it.height }
            layout(constraints.maxWidth, rowHeight) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, 0)
                    x += childWidth
                }
            }
        }
    }
}

/**
 * [RowScope], нужный только для сигнатуры `content: @Composable RowScope.() -> Unit`
 * у [WheelRow]. Реальное измерение делает наш [Layout] выше и всегда делит
 * ширину поровну, поэтому `weight`/`align` тут намеренно ничего не делают —
 * их некому обещать, никакого настоящего [Row] под капотом уже нет.
 */
private object EqualWidthRowScope : RowScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this

    override fun Modifier.align(alignment: Alignment.Vertical): Modifier = this

    override fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine): Modifier = this

    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this

    override fun Modifier.alignByBaseline(): Modifier = this
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun WheelPickerPreview() {
    WorkoutTimerTheme {
        WheelPicker(
            items = (1..12).toList(),
            selectedIndex = 5,
            onSelected = {},
            label = "Повторы",
            modifier = Modifier.padding(18.dp),
            format = { it.toString() }
        )
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun WheelRowPreview() {
    WorkoutTimerTheme {
        WheelRow(modifier = Modifier.padding(18.dp)) {
            WheelPicker(
                items = (0..9).toList(),
                selectedIndex = 3,
                onSelected = {},
                label = "Мин",
                format = { it.toString() }
            )
            WheelPicker(
                items = (0..59).toList(),
                selectedIndex = 30,
                onSelected = {},
                label = "Сек",
                format = { it.toString().padStart(2, '0') }
            )
            WheelPicker(
                items = (1..10).toList(),
                selectedIndex = 2,
                onSelected = {},
                label = "Подходы",
                format = { it.toString() }
            )
            WheelPicker(
                items = listOf(0.0, 2.5, 5.0, 7.5, 10.0),
                selectedIndex = 2,
                onSelected = {},
                label = "Кг",
                format = { it.toString() }
            )
        }
    }
}
