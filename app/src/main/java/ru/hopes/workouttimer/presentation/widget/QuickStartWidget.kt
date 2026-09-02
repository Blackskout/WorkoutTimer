package ru.hopes.workouttimer.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.android.EntryPointAccessors
import ru.hopes.workouttimer.R
import ru.hopes.workouttimer.domain.model.WidgetWorkout
import ru.hopes.workouttimer.presentation.MainActivity
import ru.hopes.workouttimer.presentation.navigation.Screen

class QuickStartWidget : GlanceAppWidget() {

    // Разметка от размера не зависит — список скроллится сам
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val getWidgetWorkouts = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .getWidgetWorkoutsUseCase()

        // Flow создаётся один раз, а не на каждую рекомпозицию
        val workoutsFlow = getWidgetWorkouts()

        provideContent {
            val workouts by workoutsFlow.collectAsState(initial = emptyList())
            GlanceTheme {
                WidgetContent(workouts)
            }
        }
    }
}

@Composable
private fun WidgetContent(workouts: List<WidgetWorkout>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .appWidgetBackground()
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        Text(
            text = context.getString(R.string.app_name),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable(actionStartActivity(openWorkoutsIntent(context)))
        )

        if (workouts.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp
                ),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openWorkoutsIntent(context)))
            )
        } else {
            LazyColumn {
                items(workouts, itemId = { it.id.toLong() }) { workout ->
                    WorkoutRow(workout)
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(workout: WidgetWorkout) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(actionStartActivity(startWorkoutIntent(context, workout.id)))
    ) {
        Text(
            text = workout.name,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Text(
            text = formatWidgetSubtitle(workout.exerciseCount, workout.lastDurationMillis),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp
            )
        )
    }
}

/**
 * Открывает приложение без deep link'а (шапка виджета, пустое состояние).
 * FLAG_ACTIVITY_NEW_TASK обязателен: клик по виджету запускает Activity не из
 * контекста другой Activity, без флага система выбросит исключение.
 */
private fun openWorkoutsIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

/**
 * Флаги обязательны: без NEW_TASK|CLEAR_TASK NavController обрезает стек до
 * одного экрана выполнения, и «Назад» закрывает приложение вместо возврата к списку.
 */
private fun startWorkoutIntent(context: Context, workoutId: Int): Intent =
    Intent(
        Intent.ACTION_VIEW,
        Screen.Execution.createDeepLink(workoutId).toUri(),
        context,
        MainActivity::class.java
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
