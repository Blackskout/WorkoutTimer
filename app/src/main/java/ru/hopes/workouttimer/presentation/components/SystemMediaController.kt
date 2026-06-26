package ru.hopes.workouttimer.presentation.components

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ru.hopes.workouttimer.R

@Composable
fun SystemMediaControllerCompat(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager


    // Кнопки управления через AudioManager
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(
            onClick = {
                val packageName = "ru.yandex.music"
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)

                if (intent != null) {
                    // Если приложение установлено, запускаем его
                    context.startActivity(intent)
                } else {
                    // Если приложения нет, открываем его страницу в Google Play или браузере
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "market://details?id=$packageName".toUri()
                            )
                        )
                    } catch (e: Exception) {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://play.google.com/store/apps/details?id=$packageName".toUri()
                            )
                        )
                    }
                }
            },
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                modifier = Modifier.size(64.dp),
                painter = painterResource(R.drawable.yandex_icon_pain),
                contentDescription = "Открыть плеер",
                tint = Color.Unspecified
            )
        }

        IconButton(
            onClick = {
                sendMediaKeyEvent(context, audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            },
            enabled = audioManager != null,
            modifier = Modifier.size(90.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Предыдущий",
                modifier = Modifier.size(40.dp)
            )
        }

        IconButton(
            onClick = {
                sendMediaKeyEvent(context, audioManager, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            },
            enabled = audioManager != null,
            modifier = Modifier.size(90.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Пауза/Воспроизведение",
                modifier = Modifier.size(54.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(
            onClick = {
                sendMediaKeyEvent(context, audioManager, KeyEvent.KEYCODE_MEDIA_NEXT)
            },
            enabled = audioManager != null,
            modifier = Modifier.size(90.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Следующий",
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

private fun sendMediaKeyEvent(context: Context, audioManager: AudioManager?, keyCode: Int) {
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
