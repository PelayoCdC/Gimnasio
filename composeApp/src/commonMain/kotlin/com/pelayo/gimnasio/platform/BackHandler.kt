package com.pelayo.gimnasio.platform

import androidx.compose.runtime.Composable

/**
 * Intercepta el gesto o botón de "atrás" del sistema mientras el composable
 * esté en pantalla. En Android envuelve el BackHandler de activity-compose;
 * en iOS no hay botón de atrás del sistema y es un no-op (la navegación de
 * Compose ya gestiona el gesto de borde con su propio back stack).
 */
@Composable
expect fun AppBackHandler(enabled: Boolean = true, onBack: () -> Unit)
