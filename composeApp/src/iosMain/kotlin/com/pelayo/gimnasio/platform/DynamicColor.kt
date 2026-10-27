package com.pelayo.gimnasio.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual fun isDynamicColorSupported(): Boolean = false

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null
