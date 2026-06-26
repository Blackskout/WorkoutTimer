package ru.hopes.workouttimer.presentation.components

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent

/**
 * Простой менеджер для отправки медиа-команд через AudioManager.
 * Не отслеживает состояние воспроизведения, так как это требует системных разрешений.
 */
class MediaButtonManager(private val context: Context) {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Отправляет медиа-событие (play/pause, next, previous).
     */
    fun sendMediaCommand(keyCode: Int) {
        if (audioManager == null) return

        try {
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)

            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
