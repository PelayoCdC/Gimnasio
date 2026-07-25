package com.pelayo.gimnasio.platform

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación sobre el Storage Access Framework. La Activity registra los
 * lanzadores y los cede aquí mediante [FileTransferHost]; así el `expect` se
 * mantiene libre de tipos de Android.
 */
actual class FileTransfer(private val context: Context) {

    actual suspend fun saveText(suggestedName: String, mimeType: String, content: String): Boolean {
        val host = FileTransferHost.current ?: return false
        val uri = host.requestCreateDocument(suggestedName, mimeType) ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(content.toByteArray())
                } ?: return@runCatching false
                true
            }.getOrDefault(false)
        }
    }

    actual suspend fun readText(mimeType: String): String? {
        val host = FileTransferHost.current ?: return null
        val uri = host.requestOpenDocument(mimeType) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
        }
    }
}

/**
 * Puente con la Activity viva. Solo puede haber una a la vez, y se limpia al
 * destruirse para no retener el contexto.
 */
object FileTransferHost {
    var current: Delegate? = null

    interface Delegate {
        suspend fun requestCreateDocument(suggestedName: String, mimeType: String): Uri?
        suspend fun requestOpenDocument(mimeType: String): Uri?
    }
}

/** Utilidad para convertir un callback de ActivityResult en una llamada suspend. */
class UriRequest {
    private var pending: CompletableDeferred<Uri?>? = null

    suspend fun await(launch: () -> Unit): Uri? {
        val deferred = CompletableDeferred<Uri?>()
        pending = deferred
        launch()
        return deferred.await()
    }

    fun complete(uri: Uri?) {
        pending?.complete(uri)
        pending = null
    }
}
