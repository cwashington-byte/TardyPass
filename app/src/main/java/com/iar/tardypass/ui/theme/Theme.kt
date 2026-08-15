package com.iar.tardypass.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Matches the PWA's Tailwind slate/blue look.
val Blue600 = Color(0xFF2563EB)
val Green600 = Color(0xFF16A34A)
val Red600 = Color(0xFFDC2626)
val Amber700 = Color(0xFFB45309)
val Slate900 = Color(0xFF111827)

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    surface = Color.White,
    background = Color(0xFFF8FAFC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Slate900,
    surface = Color(0xFF1F2937),
    background = Slate900
)

@Composable
fun TardyPassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
