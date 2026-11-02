package net.actiontruck.conductor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.actiontruck.conductor.BuildConfig
import net.actiontruck.conductor.marca.Marca

/** «Buscar actualizaciones»: versión instalada, la última publicada en GitHub y el botón para instalarla. */
@Composable
fun TarjetaActualizaciones(m: Modelo) {
    Card(colors = CardDefaults.cardColors(containerColor = Marca.fondoSuave)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
            Text("Versión instalada: ${BuildConfig.VERSION_NAME}", color = Marca.textoCuerpoSecundario)
            when (val e = m.actualizacion) {
                EstadoActualizacion.SinBuscar -> {}
                EstadoActualizacion.Buscando -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Marca.brandRojo)
                EstadoActualizacion.AlDia -> Text("La app está al día.", color = Marca.estadoExito)
                is EstadoActualizacion.Error -> Text(e.mensaje, color = Marca.estadoError)
                is EstadoActualizacion.Disponible -> {
                    Text("Nueva versión ${e.version.etiqueta}", fontWeight = FontWeight.Bold, color = Marca.brandAzul)
                    hora(e.version.publicada)?.let { Text("Publicada ${diaHora(e.version.publicada)}", style = MaterialTheme.typography.bodySmall) }
                    if (e.version.notas.isNotBlank()) Text(e.version.notas.take(600), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { m.instalarActualizacion() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Filled.SystemUpdate, null); Spacer(Modifier.width(6.dp))
                        Text("Descargar e instalar (${"%.1f".format(e.version.bytes / 1_048_576f)} MB)")
                    }
                }
                is EstadoActualizacion.Descargando -> {
                    Text("Descargando ${e.version.etiqueta}… ${(e.progreso * 100).toInt()} %")
                    LinearProgressIndicator(progress = { e.progreso }, modifier = Modifier.fillMaxWidth(), color = Marca.brandRojo)
                }
            }
            if (m.actualizacion !is EstadoActualizacion.Descargando && m.actualizacion !is EstadoActualizacion.Buscando) {
                OutlinedButton(onClick = { m.buscarActualizaciones() }, modifier = Modifier.fillMaxWidth()) { Text("Buscar actualizaciones") }
            }
        }
    }
}
