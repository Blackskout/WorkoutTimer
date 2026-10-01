package ru.hopes.workouttimer.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R

/**
 * Давность по полным суткам от «сейчас»: «сегодня», «вчера», «N дн. назад».
 * 0 — «ещё не делали»: так lastUseAt помечает новую тренировку.
 * Общая для списка тренировок и экрана «Упражнения».
 */
@Composable
fun daysAgoText(timestamp: Long): String {
    if (timestamp == 0L) return stringResource(R.string.common_never_done)
    val days = ((System.currentTimeMillis() - timestamp) / 86_400_000L).toInt()
    return when {
        days <= 0 -> stringResource(R.string.list_today)
        days == 1 -> stringResource(R.string.list_yesterday)
        else -> stringResource(R.string.d_ago, days)
    }
}
