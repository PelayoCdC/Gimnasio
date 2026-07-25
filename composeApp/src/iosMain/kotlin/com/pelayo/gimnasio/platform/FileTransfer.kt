package com.pelayo.gimnasio.platform

/** Sin implementar en iOS: haría falta un UIDocumentPicker. */
actual class FileTransfer {
    actual suspend fun saveText(suggestedName: String, mimeType: String, content: String): Boolean = false
    actual suspend fun readText(mimeType: String): String? = null
}
