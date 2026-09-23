package ru.hopes.workouttimer.data

import android.graphics.Bitmap

/**
 * Плоский снимок медиа-сессии. Существует затем, чтобы логика разбора
 * (откаты названий, флаги доступных действий) считалась чистой функцией и
 * проверялась обычными unit-тестами: сами MediaMetadata и PlaybackState —
 * final-классы платформы, на JVM их не создать.
 */
data class MediaSnapshot(
    val playbackState: Int,
    val actions: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val playbackSpeed: Float,
    val title: String?,
    val displayTitle: String?,
    val artist: String?,
    val displaySubtitle: String?,
    val albumArt: Bitmap?,
    val art: Bitmap?,
    val displayIcon: Bitmap?,
    val durationMs: Long,
    val ratingType: Int,
    val isLiked: Boolean?
)
