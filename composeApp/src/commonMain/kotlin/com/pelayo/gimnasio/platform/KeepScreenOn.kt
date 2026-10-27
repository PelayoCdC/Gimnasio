package com.pelayo.gimnasio.platform

import androidx.compose.runtime.Composable

/**
 * Mantiene la pantalla encendida mientras el composable esté en pantalla, y lo
 * deshace al salir. En el gimnasio, con las manos ocupadas y descansos de
 * minutos, que se apague a mitad de serie es un fastidio real.
 */
@Composable
expect fun KeepScreenOn()
