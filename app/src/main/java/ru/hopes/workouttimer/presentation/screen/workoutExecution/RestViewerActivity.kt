package ru.hopes.workouttimer.presentation.screen.workoutExecution

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.session.WorkoutExecutionState
import ru.hopes.workouttimer.presentation.session.WorkoutSession
import ru.hopes.workouttimer.presentation.session.WorkoutSessionManager
import ru.hopes.workouttimer.presentation.ui.theme.WorkoutTimerTheme
import ru.hopes.workouttimer.presentation.utils.DateFormatter
import javax.inject.Inject

@AndroidEntryPoint
class RestViewerActivity : ComponentActivity() {
    @Inject
    lateinit var sessionManager: WorkoutSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)
        val initialMillis = sessionManager.session.value.restViewerMillis(sessionId)
        if (initialMillis <= 0L) {
            finish()
            return
        }

        showViewerWhenLocked()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())

        var restTimeMillis by mutableLongStateOf(initialMillis)
        setContent {
            WorkoutTimerTheme {
                RestViewerContent(DateFormatter.formatDurationCompact(restTimeMillis))
            }
        }
        // Закрываемся и в STOPPED, чтобы по возврате не показать уже закончившийся отдых.
        lifecycleScope.launch {
            sessionManager.session
                .map { it.restViewerMillis(sessionId) }
                .takeWhile { it > 0L }
                .collect { restTimeMillis = it }
            finish()
        }
    }

    // Только это окно может быть поверх блокировки. Под ним остаётся обычный экран приложения.
    @Suppress("DEPRECATION")
    private fun showViewerWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"

        internal fun intent(context: Context, sessionId: Long): Intent =
            Intent(context, RestViewerActivity::class.java).putExtra(EXTRA_SESSION_ID, sessionId)
    }
}

internal val WorkoutExecutionState.Rest.canOpenRestViewer: Boolean
    get() = !isTransition && restTimeMillis > 0L

internal fun WorkoutSession.restViewerMillis(sessionId: Long): Long {
    val session = this as? WorkoutSession.Present ?: return 0L
    if (session.sessionId != sessionId) return 0L
    val rest = session.phase as? WorkoutExecutionState.Rest ?: return 0L
    return if (rest.canOpenRestViewer) rest.restTimeMillis else 0L
}

@Composable
private fun RestViewerContent(time: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.execution_rest_viewer_title),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center
            )
            Text(
                text = time,
                modifier = Modifier.fillMaxWidth(0.85f),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayLarge.copy(
                    lineHeight = TextUnit.Unspecified,
                    fontWeight = FontWeight(650),
                    letterSpacing = (-0.04).em,
                    textDirection = TextDirection.Ltr
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 180.sp)
            )
        }
    }
}

@Preview(widthDp = 320, heightDp = 600)
@Preview(widthDp = 640, heightDp = 300, fontScale = 2f)
@Composable
private fun RestViewerPreview() {
    WorkoutTimerTheme {
        RestViewerContent("00:12")
    }
}
