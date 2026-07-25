package com.pelayo.gimnasio

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.pelayo.gimnasio.platform.FileTransferHost
import com.pelayo.gimnasio.platform.UriRequest

class MainActivity : ComponentActivity(), FileTransferHost.Delegate {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val createDocumentRequest = UriRequest()
    private val openDocumentRequest = UriRequest()

    // El tipo MIME se fija al registrar el contrato, así que hay uno por formato.
    private val createJsonDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            createDocumentRequest.complete(uri)
        }

    private val createCsvDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            createDocumentRequest.complete(uri)
        }

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            openDocumentRequest.complete(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FileTransferHost.current = this
        askForNotificationPermission()
        setContent {
            App()
        }
    }

    override fun onDestroy() {
        // Evita retener la Activity una vez destruida.
        if (FileTransferHost.current === this) FileTransferHost.current = null
        super.onDestroy()
    }

    override suspend fun requestCreateDocument(suggestedName: String, mimeType: String): Uri? {
        val launcher = if (mimeType == "text/csv") createCsvDocument else createJsonDocument
        return createDocumentRequest.await { launcher.launch(suggestedName) }
    }

    override suspend fun requestOpenDocument(mimeType: String): Uri? =
        openDocumentRequest.await { openDocument.launch(arrayOf(mimeType, "*/*")) }

    /** Necesario desde Android 13 para avisar del fin del descanso entre series. */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
