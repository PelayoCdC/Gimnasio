package com.pelayo.gimnasio.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelayo.gimnasio.ui.components.HubCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onOpenCatalog: () -> Unit,
    onOpenRoutines: () -> Unit,
) {
    // Scaffold + TopAppBar como las demás pestañas: el título no debe saltar de
    // sitio ni de estilo al cambiar de pestaña.
    Scaffold(
        topBar = { TopAppBar(title = { Text("Entrenamiento") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HubCard(
                icon = Icons.Filled.FitnessCenter,
                title = "Catálogo de ejercicios",
                subtitle = "Busca ejercicios con imágenes e instrucciones",
                onClick = onOpenCatalog,
            )
            HubCard(
                icon = Icons.AutoMirrored.Filled.List,
                title = "Mis rutinas",
                subtitle = "Crea tablas y comprueba el equilibrio muscular",
                onClick = onOpenRoutines,
            )
        }
    }
}
