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

private val DeepNightColorScheme = darkColorScheme(
    primary = AccentIndigo,
    secondary = AccentPurple,
    tertiary = GlowingStar,
    background = NightDeepBg,
    surface = NightDeepBg,
    onPrimary = Color(0xFF0D0B14),
    onSecondary = Color.White,
    onTertiary = Color(0xFF0D0B14),
    onBackground = Color(0xFFE1E5F2),
    onSurface = Color(0xFFE1E5F2)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to true for the healing night experience
    dynamicColor: Boolean = false, // Disable dynamic colors by default to preserve our custom starry night design
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DeepNightColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
