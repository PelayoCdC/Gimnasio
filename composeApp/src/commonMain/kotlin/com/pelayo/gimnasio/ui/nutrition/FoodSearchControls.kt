package com.pelayo.gimnasio.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Campo de búsqueda, botón de "Buscar online" con su cuenta atrás de rate limit,
 * "Crear alimento" y los mensajes del resultado. Lo comparten el buscador del
 * diario y el catálogo de alimentos: eran ~90 líneas duplicadas y los textos ya
 * se habían desincronizado entre las dos copias.
 */
@Composable
fun FoodSearchControls(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onlineState: OnlineSearchState,
    rateLimitLeft: Int,
    onSearchOnline: () -> Unit,
    onCreateFood: () -> Unit,
    /** Mensaje al terminar la búsqueda online con éxito, según cuántos llegaron. */
    onlineDoneMessage: (Int) -> String,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(placeholder) },
        singleLine = true,
    )

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onSearchOnline,
            enabled = query.trim().length >= 2 &&
                onlineState !is OnlineSearchState.Loading &&
                rateLimitLeft == 0,
        ) {
            if (onlineState is OnlineSearchState.Loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(if (rateLimitLeft > 0) "Espera ${rateLimitLeft}s" else "Buscar online")
        }
        OutlinedButton(onClick = onCreateFood) { Text("Crear alimento") }
    }

    // Durante el corte el aviso sustituye al error: dicen lo mismo.
    if (rateLimitLeft > 0) {
        RateLimitWarning(rateLimitLeft)
    } else when (onlineState) {
        is OnlineSearchState.Failed -> Text(
            onlineState.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        is OnlineSearchState.Done -> Text(
            if (onlineState.count == 0) {
                "Open Food Facts no encontró nada con ese nombre."
            } else {
                onlineDoneMessage(onlineState.count)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        else -> Unit
    }
}
