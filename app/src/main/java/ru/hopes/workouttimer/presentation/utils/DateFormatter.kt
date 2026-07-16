package ru.hopes.workouttimer.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateFormatter {

    private val milesInHour = TimeUnit.HOURS.toMillis(1)
    private val milesInDay = TimeUnit.DAYS.toMillis(1)
    private val formatter = SimpleDateFormat.getDateInstance(DateFormat.SHORT)
    private val sessionDateTimeFormatter = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))


    fun formatCurrentDate(): String {
        return formatter.format(System.currentTimeMillis())
    }

    @Composable
    fun formatDateToString(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < milesInHour -> stringResource(R.string.just_now)
            diff < milesInDay -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                stringResource(R.string.h_ago, hours)
            }

            else -> {
                formatter.format(timestamp)
            }
        }
    }

    fun formatDurationToString(millis: Long): String {
        val totalMinutes = millis / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            "$hours ч $minutes мин"
        } else {
            "$minutes мин"
        }
    }

    fun formatSessionDateTime(timestamp: Long): String {
        return sessionDateTimeFormatter.format(timestamp)
    }
}