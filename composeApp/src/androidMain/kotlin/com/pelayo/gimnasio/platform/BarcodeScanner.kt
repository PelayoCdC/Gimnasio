package com.pelayo.gimnasio.platform

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Usa el escáner a pantalla completa de Google Play Services. Tiene su propia
 * interfaz y gestiona la cámara por su cuenta, así que la app no necesita el
 * permiso CAMERA ni integrar CameraX.
 */
actual class BarcodeScanner(private val context: Context) {

    actual fun isAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
            ConnectionResult.SUCCESS

    actual suspend fun scan(): ScanResult {
        if (!isAvailable()) return ScanResult.NotAvailable

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .enableAutoZoom()
            .build()

        return suspendCancellableCoroutine { continuation ->
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode ->
                    val value = barcode.rawValue
                    continuation.resume(
                        if (value.isNullOrBlank()) {
                            ScanResult.Failed("No se pudo leer el código")
                        } else {
                            ScanResult.Success(value)
                        },
                    )
                }
                .addOnCanceledListener { continuation.resume(ScanResult.Cancelled) }
                .addOnFailureListener { error ->
                    continuation.resume(
                        ScanResult.Failed(error.message ?: "Error al abrir el escáner"),
                    )
                }
        }
    }
}
