package com.pelayo.gimnasio.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** true donde el sistema ofrece paleta dinámica (Android 12+); iOS no la tiene. */
expect fun isDynamicColorSupported(): Boolean

/**
 * Esquema de color del sistema (Material You) si está disponible y [dark] indica
 * el modo; null donde no exista, y entonces se usa la paleta propia de la app.
 */
@Composable
expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?
