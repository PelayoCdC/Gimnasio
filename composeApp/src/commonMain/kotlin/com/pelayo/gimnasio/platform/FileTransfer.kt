package com.pelayo.gimnasio.platform

/**
 * Guarda y lee ficheros a través del selector del sistema, para que las copias de
 * seguridad salgan del almacenamiento privado de la app (donde se borrarían con
 * ella) a donde el usuario decida.
 */
expect class FileTransfer {
    /** Abre el diálogo de guardar. Devuelve false si el usuario cancela o falla. */
    suspend fun saveText(suggestedName: String, mimeType: String, content: String): Boolean

    /** Abre el diálogo de abrir. Devuelve null si el usuario cancela. */
    suspend fun readText(mimeType: String): String?
}
