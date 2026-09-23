package ru.hopes.workouttimer.data.mapper

import android.media.Rating
import android.media.session.PlaybackState
import ru.hopes.workouttimer.data.MediaSnapshot
import ru.hopes.workouttimer.domain.model.MusicState
import ru.hopes.workouttimer.domain.model.TrackInfo

private const val ПРОЧЕРК = "—"

/**
 * Снимок сессии → состояние для UI. Чистая функция: ни одного обращения к
 * платформе, только инлайнящиеся константы, поэтому тестируется на JVM.
 */
fun toMusicState(snapshot: MediaSnapshot?): MusicState {
    if (snapshot == null) return MusicState.NoSession
    if (snapshot.playbackState == PlaybackState.STATE_NONE ||
        snapshot.playbackState == PlaybackState.STATE_STOPPED
    ) {
        return MusicState.NoSession
    }

    val duration = snapshot.durationMs.coerceAtLeast(0L)
    val track = TrackInfo(
        title = snapshot.title.orNull() ?: snapshot.displayTitle.orNull() ?: ПРОЧЕРК,
        artist = snapshot.artist.orNull() ?: snapshot.displaySubtitle.orNull() ?: ПРОЧЕРК,
        artwork = snapshot.albumArt ?: snapshot.art ?: snapshot.displayIcon,
        durationMs = duration,
        positionMs = snapshot.positionMs.coerceAtLeast(0L),
        positionUpdatedAt = snapshot.positionUpdatedAt,
        playbackSpeed = snapshot.playbackSpeed,
        canSeek = snapshot.actions.has(PlaybackState.ACTION_SEEK_TO) && duration > 0L,
        canRate = snapshot.actions.has(PlaybackState.ACTION_SET_RATING) &&
            snapshot.ratingType == Rating.RATING_HEART,
        canSkipNext = snapshot.actions.has(PlaybackState.ACTION_SKIP_TO_NEXT),
        canSkipPrevious = snapshot.actions.has(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
        isLiked = snapshot.isLiked
    )

    // Буферизация считается воспроизведением: музыка вот-вот пойдёт.
    val playing = snapshot.playbackState == PlaybackState.STATE_PLAYING ||
        snapshot.playbackState == PlaybackState.STATE_BUFFERING
    return if (playing) MusicState.Playing(track) else MusicState.Paused(track)
}

/**
 * PlaybackState отдаёт снимок позиции, а не поток, поэтому между
 * обновлениями её досчитываем сами. Обрезка по длительности — только когда
 * длительность известна: у радиопотока она нулевая.
 */
fun currentPositionMs(track: TrackInfo, nowElapsedRealtime: Long): Long {
    val elapsed = (nowElapsedRealtime - track.positionUpdatedAt).coerceAtLeast(0L)
    val projected = (track.positionMs + elapsed * track.playbackSpeed).toLong().coerceAtLeast(0L)
    return if (track.durationMs > 0L) projected.coerceAtMost(track.durationMs) else projected
}

private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }

private fun Long.has(action: Long): Boolean = this and action != 0L
