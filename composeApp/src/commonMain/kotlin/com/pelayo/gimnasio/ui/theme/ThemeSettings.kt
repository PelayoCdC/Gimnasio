package com.pelayo.gimnasio.ui.theme

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val labelEs: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro"),
}

/**
 * Preferencias de apariencia, persistidas y observables. Viven fuera de un
 * ViewModel porque las lee `App()` (por encima de la navegación) y las escribe
 * Ajustes: un estado global pequeño y legítimo.
 */
class ThemeSettings(private val settings: Settings) {

    companion object {
        const val KEY_MODE = "themeMode"
        const val KEY_DYNAMIC = "dynamicColor"
    }

    private val _mode = MutableStateFlow(
        settings.getStringOrNull(KEY_MODE)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
            ?: ThemeMode.SYSTEM,
    )
    val mode: StateFlow<ThemeMode> = _mode

    private val _dynamicColor = MutableStateFlow(settings.getBoolean(KEY_DYNAMIC, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        settings.putString(KEY_MODE, mode.name)
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        settings.putBoolean(KEY_DYNAMIC, enabled)
    }
}
