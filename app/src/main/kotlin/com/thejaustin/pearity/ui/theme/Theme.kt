package com.thejaustin.pearity.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary         = PearGreen40,
    onPrimary       = Color.White,
    primaryContainer = PearContainer,
    onPrimaryContainer = Color(0xFF102200),
    secondary       = Color(0xFF55624C),
    tertiary        = Color(0xFF006874),
)

private val DarkScheme = darkColorScheme(
    primary         = PearGreen80,
    onPrimary       = Color(0xFF1D3700),
    primaryContainer = PearGreen40,
    onPrimaryContainer = PearGreenLight,
    secondary       = Color(0xFFBACAA9),
    tertiary        = Color(0xFF4FD8E8),
)

@Composable
fun PearityTheme(
    darkTheme: Boolean    = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,       // Material You — respects wallpaper color
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else      -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),     // M3 default type scale
        content     = content,
    )
}
