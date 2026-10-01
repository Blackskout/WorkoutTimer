package ru.hopes.workouttimer.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = Accent,
    onSecondary = OnAccent,
    tertiary = Accent,
    onTertiary = OnAccent,
    // Контейнеры читают стандартные компоненты (ползунок, чипы, индикаторы); не заданные,
    // они остались бы фиолетовыми из базовой тёмной схемы Material.
    primaryContainer = AccentContainer,
    onPrimaryContainer = Accent,
    secondaryContainer = AccentContainer,
    onSecondaryContainer = Accent,
    tertiaryContainer = AccentContainer,
    onTertiaryContainer = Accent,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceElevated,
    surfaceContainerLowest = Background,
    surfaceContainerLow = SurfaceDark,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceElevated,
    outline = OutlineStrong,
    outlineVariant = OutlineDark,
    error = Destructive,
    onError = TextPrimary,
    errorContainer = DestructiveContainer,
    onErrorContainer = Destructive,
    inverseSurface = SurfaceElevated,
    inverseOnSurface = TextPrimary,
    inversePrimary = Accent
)

@Composable
fun WorkoutTimerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}