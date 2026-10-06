package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CutsZoomColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = VoidBlack,
    primaryContainer = CardSurfaceElevated,
    onPrimaryContainer = NeonPurple,
    secondary = NeonCyan,
    onSecondary = VoidBlack,
    secondaryContainer = CardSurfaceElevated,
    onSecondaryContainer = NeonCyan,
    tertiary = GoldAccent,
    onTertiary = VoidBlack,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderHighlight,
    error = CrimsonError,
    onError = VoidBlack
)

@Composable
fun CutsZoomTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DarkBackground.toArgb()
                window.navigationBarColor = DarkBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = CutsZoomColorScheme,
        typography = Typography,
        content = content
    )
}
