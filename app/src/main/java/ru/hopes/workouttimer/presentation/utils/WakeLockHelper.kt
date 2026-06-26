package ru.hopes.workouttimer.presentation.utils

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WakeLockHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(timeoutMillis: Long = 3 * 60 * 1000L) {
        if (wakeLock == null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WorkoutTimer:RestTimer")
            wakeLock?.setReferenceCounted(false)
        }
        // +5s buffer so the lock outlives the countdown by a small margin
        wakeLock?.acquire(timeoutMillis + 5_000L)
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