package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkGlassColorScheme = darkColorScheme(
    primary = WinPrimary,
    onPrimary = Color.White,
    primaryContainer = WinPrimaryContainer,
    onPrimaryContainer = Color.White,
    secondary = WinSecondary,
    onSecondary = Color.Black,
    background = WinBackground,
    onBackground = WinOnBackground,
    surface = WinSurface,
    onSurface = WinOnSurface,
    surfaceVariant = WinSurfaceVariant,
    onSurfaceVariant = WinOnSurface,
    error = WinError,
    onError = Color.White
)

private val LightGlassColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7), // Sky Blue 600
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD), // Sky 200
    onPrimaryContainer = Color(0xFF0369A1), // Sky 700
    secondary = Color(0xFF4F46E5), // Indigo 600
    onSecondary = Color.White,
    background = WinSkyBackground,
    onBackground = Color(0xFF0F172A), // Slate 900
    surface = Color(0xFFFFFFFF), // Limpio blanco plano
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFBAE6FD), // Sky 200
    onSurfaceVariant = Color(0xFF1E293B), // Slate 800
    outline = Color(0xFF7DD3FC), // Sky 300
    error = WinError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkGlassColorScheme else LightGlassColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
