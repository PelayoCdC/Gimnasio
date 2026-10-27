package com.pelayo.gimnasio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.pelayo.gimnasio.platform.platformDynamicColorScheme

private val LightColors = lightColorScheme(
    primary = LimePrimary,
    onPrimary = LimeOnPrimary,
    primaryContainer = LimeContainer,
    onPrimaryContainer = LimeOnContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealContainer,
    onSecondaryContainer = TealOnContainer,
    tertiary = OrangeTertiary,
    onTertiary = OrangeOnTertiary,
    tertiaryContainer = OrangeContainer,
    onTertiaryContainer = OrangeOnContainer,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkTealSecondary,
    onSecondary = DarkOnTealSecondary,
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = DarkOnTealContainer,
    tertiary = DarkOrangeTertiary,
    onTertiary = DarkOnOrangeTertiary,
    tertiaryContainer = DarkOrangeContainer,
    onTertiaryContainer = DarkOnOrangeContainer,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkErrorRed,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

@Composable
fun GimnasioTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You en Android 12+; donde no exista se ignora y manda la paleta lima. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = (if (dynamicColor) platformDynamicColorScheme(darkTheme) else null)
        ?: if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
