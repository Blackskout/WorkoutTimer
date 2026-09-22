package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import java.util.Locale

@Composable
fun RestRing(
    timeLeftMillis: Long,
    totalTimeMillis: Long,
    modifier: Modifier = Modifier
) {
    val target = if (totalTimeMillis > 0L) {
        (timeLeftMillis.toFloat() / totalTimeMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 200 мс совпадает с частотой тика во ViewModel: при большем значении
    // кольцо отстаёт от цифр, при меньшем — дёргается.
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "RestRingProgress"
    )

    val track = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val stroke = 10.dp

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round)
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatClock(timeLeftMillis),
                style = MaterialTheme.typography.displayLarge,
                fontSize = 52.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "из ${formatClock(totalTimeMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun RestRingPreview() {
    WorkoutTimerTheme {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RestRing(timeLeftMillis = 60_000L, totalTimeMillis = 60_000L)
            RestRing(timeLeftMillis = 30_000L, totalTimeMillis = 60_000L)
            RestRing(timeLeftMillis = 0L, totalTimeMillis = 60_000L)
        }
    }
}
