package ru.hopes.workouttimer.presentation.screen.workoutExecution

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal const val AUTO_REST_VIEWER_PREFS = "workout_display"
internal const val AUTO_REST_VIEWER_ENABLED = "auto_rest_viewer"

internal data class AutoRestViewerTarget(val sessionId: Long, val generation: Long)

@Composable
internal fun AutoRestViewerOnLock(enabled: Boolean, viewModel: WorkoutExecutionViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current

    DisposableEffect(enabled, context, lifecycle, view, viewModel) {
        val power = context.getSystemService(PowerManager::class.java)
        if (!enabled || power == null) {
            return@DisposableEffect onDispose {}
        }

        fun isWorkoutFocused(): Boolean =
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                view.hasWindowFocus() && power.isInteractive

        val keyguard = context.getSystemService(KeyguardManager::class.java)
        var workoutWasVisible = isWorkoutFocused()

        fun launchForSleep(checkLockedWake: Boolean = false) {
            if (!workoutWasVisible) return
            if (power.isInteractive && !(checkLockedWake && keyguard?.isKeyguardLocked == true)) return

            workoutWasVisible = false
            val target = viewModel.autoRestViewerTarget() ?: return
            // Try while this task is still visible to Android; keep the Live Update if it declines.
            try {
                context.startActivity(RestViewerActivity.intent(context, target.sessionId, target.generation))
            } catch (_: ActivityNotFoundException) {
                // Keep the ongoing notification.
            } catch (_: SecurityException) {
                // Keep the ongoing notification.
            }
        }

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                workoutWasVisible = isWorkoutFocused()
            } else if (power.isInteractive) {
                workoutWasVisible = false
            } else {
                launchForSleep()
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> workoutWasVisible = isWorkoutFocused()
                Lifecycle.Event.ON_PAUSE -> {
                    if (power.isInteractive) workoutWasVisible = false
                    else launchForSleep()
                }
                else -> Unit
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> launchForSleep(checkLockedWake = true)
                    Intent.ACTION_SCREEN_ON -> workoutWasVisible = isWorkoutFocused()
                }
            }
        }
        val actions = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
        ContextCompat.registerReceiver(context, receiver, actions, ContextCompat.RECEIVER_NOT_EXPORTED)
        val viewObserver = view.viewTreeObserver
        viewObserver.addOnWindowFocusChangeListener(focusListener)
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            if (viewObserver.isAlive) viewObserver.removeOnWindowFocusChangeListener(focusListener)
            context.unregisterReceiver(receiver)
        }
    }
}
