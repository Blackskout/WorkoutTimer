package ru.hopes.workouttimer.domain.model

import android.graphics.Bitmap

/**
 * Состояние чужого плеера, каким его видит экран выполнения.
 * PermissionRequired и NoSession — не ошибки, а обычные рабочие состояния:
 * в первом случае не выдан доступ к уведомлениям, во втором плеер не запущен.
 */
sealed interface MusicState {
    data object PermissionRequired : MusicState
    data object NoSession : MusicState
    data class Playing(val track: TrackInfo) : MusicState
    data class Paused(val track: TrackInfo) : MusicState
}

/**
 * artwork приходит готовым Bitmap из метаданных сессии — это единственное
 * место, где android.graphics попадает в domain. Заворачивать его в обёртку
 * ради чистоты слоя не стоит того.
 *
 * isLiked = null означает «неизвестно»: сердце рисуется контуром, но нажимается.
 */
data class TrackInfo(
    val title: String,
    val artist: String,
    val artwork: Bitmap?,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val playbackSpeed: Float,
    val canSeek: Boolean,
    val canRate: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val isLiked: Boolean?
)
