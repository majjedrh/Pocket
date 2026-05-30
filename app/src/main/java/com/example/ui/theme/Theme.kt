package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = PrimaryAccent,
        onPrimary = PrimaryAccentDark,
        background = BgDark,
        onBackground = TextPrimary,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        outline = BorderColor,
        surfaceVariant = SecondaryButtonBg,
        onSurfaceVariant = TextPrimary,
        error = WarningText,
        onError = BgDark
    )

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
