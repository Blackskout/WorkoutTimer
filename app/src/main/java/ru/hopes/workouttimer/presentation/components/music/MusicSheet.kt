package ru.hopes.workouttimer.presentation.components.music

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.hopes.workouttimer.data.mapper.currentPositionMs
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo
import ru.hopes.workouttimer.presentation.ui.components.AppBottomSheet
import ru.hopes.workouttimer.presentation.ui.theme.ScreenPadding

/**
 * Видимость шторки. Закрывается сама в двух случаях: кончился отдых (иначе
 * после звонка на экране была бы обложка вместо «пора работать») и состояние
 * перестало быть Playing/Paused — показывать тогда нечего.
 *
 * isResting — именно Boolean, а не состояние экрана: WorkoutExecutionState.Rest
 * пересоздаётся copy() на каждом тике таймера, и эффект на нём перезапускался
 * бы раз в секунду, закрывая шторку сразу после открытия.
 */
@Composable
fun rememberMusicSheetVisibility(isResting: Boolean, state: MusicState): MutableState<Boolean> {
    val visible = remember { mutableStateOf(false) }

    LaunchedEffect(isResting) {
        if (!isResting) visible.value = false
    }

    val hasTrack = state is MusicState.Playing || state is MusicState.Paused
    LaunchedEffect(hasTrack) {
        if (!hasTrack) visible.value = false
    }

    return visible
}

@Composable
fun MusicSheet(
    track: TrackInfo,
    playing: Boolean,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLike: (Boolean) -> Unit,
    onOpenPlayer: () -> Unit
) {
    AppBottomSheet(onDismiss = onDismiss) {
        MusicSheetContent(
            track = track,
            playing = playing,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onLike = onLike,
            onOpenPlayer = onOpenPlayer
        )
    }
}

/**
 * Извлечено из MusicSheet по той же причине, что и ActionSheetContent:
 * ModalBottomSheet требует Window и не поднимается ни в @Preview, ни в тесте.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnScope.MusicSheetContent(
    track: TrackInfo,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLike: (Boolean) -> Unit,
    onOpenPlayer: () -> Unit
) {
    // Долгий тап, а не обычный: во время отдыха легко мазнуть по самой
    // большой мишени экрана, а промах уводил бы из приложения посреди
    // подхода. Пустой onClick оставлен ради ripple — единственного намёка,
    // что обложка живая, не считая подписи жеста для talkback.
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 8.dp)
            .size(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = {},
                onLongClick = onOpenPlayer,
                onLongClickLabel = "Открыть Яндекс Музыку"
            )
            .semantics { contentDescription = "Обложка трека" },
        contentAlignment = Alignment.Center
    ) {
        val bitmap = track.artwork
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(220.dp)
            )
        } else {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
        }
    }

    Text(
        text = track.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = ScreenPadding, end = ScreenPadding)
    )
    Text(
        text = track.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = ScreenPadding, end = ScreenPadding)
    )

    if (track.canSeek) {
        SeekRow(track = track, playing = playing, onSeek = onSeek)
    }

    // Раскладка как в самой Яндекс Музыке: тройка транспорта строго по центру
    // экрана, лайк отдельно у правого края. Если поставить его четвёртым в
    // ряд, центр группы уезжает влево от обложки.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = ScreenPadding, end = ScreenPadding)
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, enabled = track.canSkipPrevious) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Предыдущий трек",
                    modifier = Modifier.size(36.dp)
                )
            }
            // Главная кнопка — круг акцентного цвета, как PrimaryButton
            // на самом экране выполнения.
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Пауза" else "Играть",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
            IconButton(onClick = onNext, enabled = track.canSkipNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Следующий трек",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        if (track.canRate) {
            IconButton(
                onClick = { onLike(track.isLiked != true) },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = if (track.isLiked == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (track.isLiked == true) "Убрать лайк" else "Лайк",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun SeekRow(
    track: TrackInfo,
    playing: Boolean,
    onSeek: (Long) -> Unit
) {
    // Пока палец на слайдере, входящие обновления игнорируются: иначе
    // ползунок дёргался бы назад на каждом обновлении позиции.
    var dragging by remember { mutableStateOf(false) }
    var draggedMs by remember { mutableStateOf(0f) }
    // Место, куда пользователь перемотал. Держится, пока сессия не подтвердит
    // его новым positionUpdatedAt. Без этого после отпускания ползунок прыгал
    // бы назад, к доперемоточной позиции, — и перемотка читалась бы как
    // несработавшая, ровно как старые кнопки без обратной связи.
    var pendingSeekMs by remember(track.positionUpdatedAt) { mutableStateOf<Float?>(null) }
    var tickedMs by remember(track.positionMs, track.positionUpdatedAt) {
        mutableStateOf(track.positionMs.toFloat())
    }

    // Тик нужен только пока играет: на паузе скорость нулевая.
    LaunchedEffect(track.positionUpdatedAt, playing) {
        while (playing) {
            tickedMs = currentPositionMs(track, SystemClock.elapsedRealtime()).toFloat()
            delay(1_000)
        }
    }

    val shown = if (dragging) draggedMs else pendingSeekMs ?: tickedMs

    Slider(
        value = shown.coerceIn(0f, track.durationMs.toFloat()),
        onValueChange = {
            dragging = true
            draggedMs = it
        },
        onValueChangeFinished = {
            pendingSeekMs = draggedMs
            dragging = false
            onSeek(draggedMs.toLong())
        },
        valueRange = 0f..track.durationMs.toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = ScreenPadding, end = ScreenPadding)
            .semantics { contentDescription = "Перемотка" }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = ScreenPadding),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatMs(shown.toLong()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatMs(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
