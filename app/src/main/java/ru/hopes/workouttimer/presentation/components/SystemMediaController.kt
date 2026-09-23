package ru.hopes.workouttimer.presentation.components

import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.presentation.ui.theme.PrimaryButtonHeight

/**
 * Кнопка запуска плеера. Управление воспроизведением отсюда убрано:
 * AudioManager.dispatchMediaKeyEvent() — односторонняя отправка, поэтому
 * кнопки срабатывали с задержкой, а иконка play/pause никогда не меняла вид.
 */
@Composable
fun YandexMusicButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            val packageName = "ru.yandex.music"
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
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
        // Совпадает с высотой PrimaryButton рядом в том же Row (WorkoutExecutionScreen).
        modifier = modifier.size(PrimaryButtonHeight)
    ) {
        Icon(
            modifier = Modifier.size(44.dp),
            painter = painterResource(R.drawable.yandex_icon_pain),
            contentDescription = "Открыть плеер",
            tint = Color.Unspecified
        )
    }
}
