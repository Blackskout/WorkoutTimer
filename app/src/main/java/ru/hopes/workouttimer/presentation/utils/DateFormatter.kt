package ru.hopes.workouttimer.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.hopes.workouttimer.R
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class RelativeTime {
    data object JustNow : RelativeTime()
    data class HoursAgo(val hours: Long) : RelativeTime()
    data class DaysAgo(val days: Long) : RelativeTime()
    data class Absolute(val timestamp: Long) : RelativeTime()
}

private val milesInHour = TimeUnit.HOURS.toMillis(1)
private val milesInDay = TimeUnit.DAYS.toMillis(1)
private val milesIn13Days = TimeUnit.DAYS.toMillis(13)
private val milesIn14Days = TimeUnit.DAYS.toMillis(14)

fun relativeTimeOf(timestamp: Long, now: Long = System.currentTimeMillis()): RelativeTime {
    val diff = now - timestamp

    return when {
        diff < milesInHour -> RelativeTime.JustNow
        diff < milesInDay -> RelativeTime.HoursAgo(TimeUnit.MILLISECONDS.toHours(diff))
        diff < milesIn14Days -> RelativeTime.DaysAgo(TimeUnit.MILLISECONDS.toDays(diff))
        else -> RelativeTime.Absolute(timestamp)
    }
}

fun isStaleWorkout(timestamp: Long, now: Long = System.currentTimeMillis()): Boolean {
    return now - timestamp >= milesIn13Days
}

object DateFormatter {

    private val formatter = SimpleDateFormat.getDateInstance(DateFormat.SHORT)
    private val sessionDateTimeFormatter = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))
    private val relativeDateTimeFormatter =
        SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)


    fun formatCurrentDate(): String {
        return formatter.format(System.currentTimeMillis())
    }

    @Composable
    fun formatDateToString(timestamp: Long): String {
        return when (val relativeTime = relativeTimeOf(timestamp)) {
            is RelativeTime.JustNow -> stringResource(R.string.just_now)
            is RelativeTime.HoursAgo -> stringResource(R.string.h_ago, relativeTime.hours)
            is RelativeTime.DaysAgo -> stringResource(R.string.d_ago, relativeTime.days)
            is RelativeTime.Absolute -> relativeDateTimeFormatter.format(relativeTime.timestamp)
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