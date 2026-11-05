package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MeowDarkColorScheme = darkColorScheme(
    primary = MeowAmber,
    onPrimary = Color.Black,
    primaryContainer = MeowAmberContainer,
    onPrimaryContainer = MeowAmberGlow,
    secondary = MeowEmerald,
    onSecondary = Color.Black,
    secondaryContainer = MeowEmeraldContainer,
    onSecondaryContainer = MeowEmeraldGlow,
    tertiary = MeowCyan,
    onTertiary = Color.Black,
    error = MeowRuby,
    onError = Color.White,
    errorContainer = MeowRubyContainer,
    onErrorContainer = Color(0xFFFCA5A5),
    background = MeowBlack,
    onBackground = MeowTextPrimary,
    surface = MeowGraphite,
    onSurface = MeowTextPrimary,
    surfaceVariant = MeowCardBg,
    onSurfaceVariant = MeowTextSecondary,
    outline = MeowCardBorder,
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun MeowSaveTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MeowDarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MeowSaveTheme(content = content)
}
