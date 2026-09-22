package ru.hopes.workouttimer.presentation.widget

import androidx.compose.material3.darkColorScheme
import androidx.glance.material3.ColorProviders
import ru.hopes.workouttimer.presentation.ui.theme.Accent
import ru.hopes.workouttimer.presentation.ui.theme.Background
import ru.hopes.workouttimer.presentation.ui.theme.OnAccent
import ru.hopes.workouttimer.presentation.ui.theme.OutlineDark
import ru.hopes.workouttimer.presentation.ui.theme.SurfaceDark
import ru.hopes.workouttimer.presentation.ui.theme.TextPrimary
import ru.hopes.workouttimer.presentation.ui.theme.TextSecondary

private val WidgetScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = SurfaceDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = Background,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark
)

/** Виджет не следует системной теме: приложение всегда тёмное. */
val WidgetColorScheme = ColorProviders(light = WidgetScheme, dark = WidgetScheme)
