package ru.hopes.workouttimer.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme

@Composable
fun ProgressSegments(
    total: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    if (total <= 0) return
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(total) { index ->
            val color = when {
                index < currentIndex -> accent.copy(alpha = 0.5f)
                index == currentIndex -> accent
                else -> track
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            ) {}
        }
    }
}

@Preview(backgroundColor = 0xFF0B0B0F, showBackground = true)
@Composable
private fun ProgressSegmentsPreview() {
    WorkoutTimerTheme {
        ProgressSegments(
            total = 6,
            currentIndex = 1,
            modifier = Modifier.padding(18.dp)
        )
    }
}
