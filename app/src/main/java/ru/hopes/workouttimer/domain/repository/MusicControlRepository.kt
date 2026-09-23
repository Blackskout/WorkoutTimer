package ru.hopes.workouttimer.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.hopes.workouttimer.domain.model.MusicState

interface MusicControlRepository {
    val state: Flow<MusicState>

    /**
     * Перепроверить доступ к уведомлениям и пересобрать подписку.
     * Нужен после возврата из системных настроек: Activity при этом не
     * умирает, подписчик не отваливается, и без внешнего толчка состояние
     * PermissionRequired осталось бы навсегда.
     */
    fun refresh()

    fun play()
    fun pause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setLiked(liked: Boolean)
    fun openPlayer()
    fun openPermissionSettings()
}
