package com.pelayo.gimnasio.platform

/** Sin implementar en iOS: haría falta AVFoundation con AVCaptureMetadataOutput. */
actual class BarcodeScanner {
    actual fun isAvailable(): Boolean = false
    actual suspend fun scan(): ScanResult = ScanResult.NotAvailable
}
