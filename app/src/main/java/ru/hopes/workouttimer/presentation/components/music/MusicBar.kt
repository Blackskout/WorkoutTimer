package ru.hopes.workouttimer.presentation.components.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo

private val BarHeight = 56.dp

/**
 * Полоска без состояния — чтобы инструментальные тесты обходились без Hilt,
 * которого в androidTest этого проекта нет. Hilt-обёртка над ней появится
 * как MusicSection, вместе со шторкой.
 */
@Composable
fun MusicBarContent(
    state: MusicState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenPlayer: () -> Unit,
    onGrantPermission: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        MusicState.PermissionRequired -> HintRow(
            text = "Разрешить управление музыкой",
            onClick = onGrantPermission,
            modifier = modifier,
            icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) }
        )

        MusicState.NoSession -> HintRow(
            text = "Включить музыку",
            onClick = onOpenPlayer,
            modifier = modifier,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.yandex_icon_pain),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }
        )

        is MusicState.Playing ->
            PlayerRow(state.track, true, onPlayPause, onNext, onPrevious, onExpand, modifier)

        is MusicState.Paused ->
            PlayerRow(state.track, false, onPlayPause, onNext, onPrevious, onExpand, modifier)
    }
}

@Composable
private fun HintRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerRow(
    track: TrackInfo,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onExpand)
                .semantics { contentDescription = "Открыть плеер" }
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Artwork(track)
            Text(
                text = "${track.artist} — ${track.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )
        }
        IconButton(onClick = onPrevious, enabled = track.canSkipPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий трек")
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Играть"
            )
        }
        IconButton(onClick = onNext, enabled = track.canSkipNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Следующий трек")
        }
    }
}

@Composable
private fun Artwork(track: TrackInfo, size: Dp = 40.dp) {
    val bitmap = track.artwork
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
