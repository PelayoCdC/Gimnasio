package com.pelayo.gimnasio.platform

sealed interface ScanResult {
    data class Success(val barcode: String) : ScanResult

    /** El usuario cerró el escáner sin leer nada. */
    data object Cancelled : ScanResult

    /** El dispositivo no puede escanear (sin Play Services, o iOS todavía). */
    data object NotAvailable : ScanResult

    data class Failed(val message: String) : ScanResult
}

expect class BarcodeScanner {
    fun isAvailable(): Boolean

    suspend fun scan(): ScanResult
}
