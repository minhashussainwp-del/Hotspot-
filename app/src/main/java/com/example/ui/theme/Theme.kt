package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkNocColorScheme = darkColorScheme(
    primary = NocCyanPrimary,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF003B44),
    onPrimaryContainer = Color(0xFFB3F7FF),
    secondary = NocEmeraldSecondary,
    onSecondary = Color(0xFF002912),
    secondaryContainer = Color(0xFF004521),
    onSecondaryContainer = Color(0xFFB8FFD8),
    tertiary = NocAmberWarning,
    onTertiary = Color(0xFF2E1E00),
    tertiaryContainer = Color(0xFF4D3400),
    onTertiaryContainer = Color(0xFFFFE099),
    error = NocCrimsonDanger,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF4D0A16),
    onErrorContainer = Color(0xFFFFB3BC),
    background = NocObsidianBg,
    onBackground = NocTextPrimary,
    surface = NocSlateSurface,
    onSurface = NocTextPrimary,
    surfaceVariant = NocElevatedCard,
    onSurfaceVariant = NocTextSecondary,
    outline = NocBorderSubtle,
    outlineVariant = Color(0xFF1A273D)
)

private val LightTechColorScheme = lightColorScheme(
    primary = TechLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F4FA),
    onPrimaryContainer = Color(0xFF00282E),
    secondary = TechLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F7DC),
    onSecondaryContainer = Color(0xFF002911),
    tertiary = Color(0xFFC77700),
    onTertiary = Color.White,
    error = Color(0xFFD32F2F),
    onError = Color.White,
    background = TechLightBg,
    onBackground = TechLightTextPrimary,
    surface = TechLightSurface,
    onSurface = TechLightTextPrimary,
    surfaceVariant = TechLightElevated,
    onSurfaceVariant = TechLightTextSecondary,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun HotspotControlProTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkNocColorScheme else LightTechColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
