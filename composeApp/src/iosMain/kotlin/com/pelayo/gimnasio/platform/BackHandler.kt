package com.pelayo.gimnasio.platform

import androidx.compose.runtime.Composable

@Composable
actual fun AppBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Sin botón de atrás del sistema en iOS: no hay nada que interceptar.
}
