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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo

// Минимум, а не точная высота: при крупном системном шрифте текст переносится, и строка растёт.
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
            text = stringResource(R.string.music_grant_access),
            onClick = onGrantPermission,
            modifier = modifier,
            icon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
        )

        MusicState.NoSession -> HintRow(
            text = stringResource(R.string.music_launch),
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
            .heightIn(min = BarHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
    val openPlayerDescription = stringResource(R.string.music_open_player)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BarHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onExpand)
                .semantics { contentDescription = openPlayerDescription }
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
            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.music_previous))
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.music_pause else R.string.music_play)
            )
        }
        IconButton(onClick = onNext, enabled = track.canSkipNext) {
            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.music_next))
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

/**
 * Полоска вместе со шторкой. Экран выполнения знает только про неё и
 * передаёт одну вещь — идёт ли сейчас отдых.
 */
@Composable
fun MusicSection(
    isResting: Boolean,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val expanded = rememberMusicSheetVisibility(isResting = isResting, state = state)
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAccessRationale by rememberSaveable { mutableStateOf(false) }

    // Разрешение выдаётся в системных настройках, Activity при этом не
    // умирает, поэтому проверяем его заново при каждом возврате на экран.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MusicBarContent(
        state = state,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onOpenPlayer = viewModel::openPlayer,
        onGrantPermission = { showAccessRationale = true },
        onExpand = { expanded.value = true },
        modifier = modifier
    )

    if (showAccessRationale) {
        MusicAccessRationaleDialog(
            onConfirm = {
                showAccessRationale = false
                viewModel.grantPermission()
            },
            onDismiss = { showAccessRationale = false }
        )
    }

    val current = state
    val track = when (current) {
        is MusicState.Playing -> current.track
        is MusicState.Paused -> current.track
        else -> null
    }
    if (expanded.value && track != null) {
        MusicSheet(
            track = track,
            playing = current is MusicState.Playing,
            onDismiss = { expanded.value = false },
            onPlayPause = viewModel::playPause,
            onNext = viewModel::next,
            onPrevious = viewModel::previous,
            onSeek = viewModel::seekTo,
            onLike = viewModel::setLiked,
            onOpenPlayer = viewModel::openPlayer
        )
    }
}

/**
 * Пояснение перед системным экраном «Доступ к уведомлениям». Android сопровождает
 * его предупреждением о чтении всех уведомлений, и без нашего объяснения просьба
 * выглядит подозрительно. Показывается при каждом тапе: отказ не запоминается,
 * хранилища настроек в проекте нет, а тап по полоске и так осознанный.
 */
@Composable
private fun MusicAccessRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.music_access_title)) },
        text = { Text(stringResource(R.string.music_access_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.music_access_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.music_access_dismiss))
            }
        }
    )
}
