package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TechBluePrimary,
    onPrimary = Color.White,
    primaryContainer = TechBlueDark,
    onPrimaryContainer = TechBlueLight,
    secondary = TechBlueLight,
    onSecondary = TechBlueDark,
    background = TechBackgroundDark,
    onBackground = TechTextPrimaryDark,
    surface = TechSurfaceDark,
    onSurface = TechTextPrimaryDark,
    surfaceVariant = TechSurfaceVariantDark,
    onSurfaceVariant = TechTextSecondaryDark,
    outline = TechDividerDark
)

private val LightColorScheme = lightColorScheme(
    primary = TechBluePrimary,
    onPrimary = Color.White,
    primaryContainer = TechBlueContainer,
    onPrimaryContainer = TechBlueDark,
    secondary = TechBlueLight,
    onSecondary = TechBluePrimary,
    background = TechBackgroundLight,
    onBackground = TechTextPrimaryLight,
    surface = TechSurfaceLight,
    onSurface = TechTextPrimaryLight,
    surfaceVariant = TechSurfaceVariantLight,
    onSurfaceVariant = TechTextSecondaryLight,
    outline = TechDividerLight
)

@Composable
fun TechManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use intentional Tech Manager blue theme by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward compatibility alias for tests
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = TechManagerTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
