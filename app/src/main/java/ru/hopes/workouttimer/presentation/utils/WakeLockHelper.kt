package ru.hopes.workouttimer.presentation.utils

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WakeLockHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire() {
        if (wakeLock == null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            // PARTIAL_WAKE_LOCK: Экран может погаснуть, но процессор (CPU) будет работать
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WorkoutTimer:RestTimer")
            wakeLock?.setReferenceCounted(false)
        }

        // Держим процессор включенным (максимум на 3 минуты для безопасности) (было 10)
        wakeLock?.acquire(3 * 60 * 1000L)
    }

    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // Игнорируем ошибки, если лок уже был снят
        }
        wakeLock = null
    }
}