package ru.hopes.workouttimer.presentation.components

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Простой менеджер для отправки медиа-команд через AudioManager.
 * Не отслеживает состояние воспроизведения, так как это требует системных разрешений.
 */
class MediaButtonManager(private val context: Context) {

    private val audioManager: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Отправляет медиа-событие (play/pause, next, previous).
     */
    fun sendMediaCommand(keyCode: Int) {
        if (audioManager == null) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)

                audioManager.dispatchMediaKeyEvent(downEvent)
                audioManager.dispatchMediaKeyEvent(upEvent)
            } else {
                @Suppress("DEPRECATION")
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                @Suppress("DEPRECATION")
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
