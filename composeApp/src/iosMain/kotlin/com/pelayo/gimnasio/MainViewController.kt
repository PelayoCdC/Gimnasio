package com.pelayo.gimnasio

import androidx.compose.ui.window.ComposeUIViewController
import com.pelayo.gimnasio.di.initKoin

@Suppress("unused", "FunctionName") // Punto de entrada consumido desde el proyecto Xcode futuro.
fun MainViewController() = ComposeUIViewController(
    configure = { initKoin() },
) {
    App()
}
