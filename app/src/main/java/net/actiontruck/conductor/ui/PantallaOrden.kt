package net.actiontruck.conductor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.actiontruck.conductor.datos.numero
import net.actiontruck.conductor.datos.objetos
import net.actiontruck.conductor.datos.texto
import net.actiontruck.conductor.marca.Marca
import org.json.JSONObject

/** Nombre para el conductor de cada tipo de evento (los de orden_eventos). */
val NOMBRE_EVENTO = mapOf(
    "llegada_carga" to "Llegada a carga", "cargada" to "Mercancía cargada", "llegada_descarga" to "Llegada a descarga",
    "entregada" to "Entregada", "entrega_parcial" to "Entrega parcial", "rechazada" to "Rechazada en destino", "ausente" to "Destinatario ausente",
    "incidencia" to "Incidencia", "nota" to "Nota", "creada" to "Creada", "planificada" to "Planificada", "asignada" to "Asignada",
    "en_transito" to "En tránsito", "cancelada" to "Cancelada", "facturada" to "Facturada", "subcontratada" to "Subcontratada",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaOrden(m: Modelo, p: Pantalla.Orden) {
    var datos by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(p.id, m.viaje) { datos = m.orden(p.id) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(p.codigo, color = Marca.textoSobreAzul) },
            navigationIcon = { IconButton(onClick = { m.atras() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Marca.textoSobreAzul) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Marca.brandAzul),
        )
    }) { relleno ->
        val d = datos
        if (d == null) {
            Text(m.mensaje ?: "Cargando…", Modifier.padding(relleno).padding(16.dp))
            return@Scaffold
        }
        val o = d.getJSONObject("orden")
        val estado = o.getString("estado")
        val c = LocalContext.current
        Column(Modifier.padding(relleno).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(o.texto("cliente") ?: "", style = MaterialTheme.typography.titleLarge)
            Text(listOfNotNull(o.texto("servicio"), o.texto("referencia_cliente")?.let { "ref. $it" }, "estado: ${estado.replace('_', ' ')}").joinToString(" · "),
                color = Marca.textoCuerpoSecundario)
            m.mensaje?.let { Text(it, color = Marca.brandAzul) }

            Lugar("Carga", o.getJSONObject("origen"), o.texto("recogida_desde"), o.texto("recogida_hasta"))
            Lugar("Entrega", o.getJSONObject("destino"), o.texto("entrega_desde"), o.texto("entrega_hasta"))

            Text("Mercancía", style = MaterialTheme.typography.titleMedium)
            d.getJSONArray("mercancia").objetos().forEach { l ->
                val desc = l.texto("descripcion")?.takeIf { it != l.texto("unidad") }
                Text("${l.numero("cantidad")?.let { fmt(it) } ?: ""} ${l.texto("unidad") ?: ""}${desc?.let { " · $it" } ?: ""}" +
                    (l.numero("peso_kg")?.let { " · ${fmt(it)} kg" } ?: "") + (l.texto("lote")?.let { " · lote $it" } ?: ""))
            }
            o.texto("observaciones")?.let { Text("Observaciones: $it", color = Marca.textoCuerpoSecundario) }

            HorizontalDivider(color = Marca.lineaSeparador, thickness = 2.dp)
            Text("Registrar", style = MaterialTheme.typography.titleMedium)
            val abierta = estado !in listOf("entregada", "facturada", "cancelada")
            if (abierta) {
                val acciones = listOf("llegada_carga", "cargada", "llegada_descarga", "entregada")
                acciones.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fila.forEach { t ->
                            Button(onClick = { m.ir(Pantalla.Evento(p.id, p.codigo, t)) }, modifier = Modifier.weight(1f).height(56.dp),
                                colors = if (t == "entregada") ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = Marca.brandAzul)) {
                                Text(NOMBRE_EVENTO.getValue(t))
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { m.ir(Pantalla.Evento(p.id, p.codigo, "incidencia")) }, modifier = Modifier.weight(1f)) { Text("Incidencia") }
                    OutlinedButton(onClick = { m.ir(Pantalla.Evento(p.id, p.codigo, "nota")) }, modifier = Modifier.weight(1f)) { Text("Nota") }
                }
            } else {
                Text("Orden ${estado.replace('_', ' ')}: no admite más registros de carga o entrega.", color = Marca.textoCuerpoSecundario)
                OutlinedButton(onClick = { m.ir(Pantalla.Evento(p.id, p.codigo, "nota")) }) { Text("Añadir nota") }
            }

            HorizontalDivider(color = Marca.lineaSeparador, thickness = 2.dp)
            Text("Documentos", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { m.ir(Pantalla.Documento("Documento de control ${p.codigo}", "/ordenes/${p.id}/documento", "${p.codigo}.pdf")) },
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Description, null); Spacer(Modifier.width(6.dp)); Text("Documento de control")
            }
            d.getJSONArray("albaranes").objetos().forEach { a ->
                val codigo = a.texto("codigo") ?: "Albarán"
                OutlinedButton(onClick = { m.ir(Pantalla.Documento("Albarán $codigo", "/albaranes/${a.getString("id")}/documento", "$codigo.pdf")) },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Description, null); Spacer(Modifier.width(6.dp)); Text("Albarán $codigo (${a.texto("estado")})")
                }
            }

            val eventos = d.getJSONArray("eventos").objetos()
            if (eventos.isNotEmpty()) {
                HorizontalDivider(color = Marca.lineaSeparador, thickness = 2.dp)
                Text("Historial", style = MaterialTheme.typography.titleMedium)
                eventos.forEach { e ->
                    Column {
                        Text("${hora(e.texto("fecha_evento")) ?: ""}  ${NOMBRE_EVENTO[e.getString("tipo")] ?: e.getString("tipo")}", fontWeight = FontWeight.Medium)
                        val detalle = listOfNotNull(e.texto("incidencia"), e.texto("receptor_nombre")?.let { "recibe $it" }, e.texto("descripcion"),
                            e.optInt("adjuntos").takeIf { it > 0 }?.let { "$it adjuntos" }).joinToString(" · ")
                        if (detalle.isNotEmpty()) Text(detalle, style = MaterialTheme.typography.bodySmall, color = Marca.textoCuerpoSecundario)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun fmt(n: Double) = if (n % 1.0 == 0.0) n.toLong().toString() else "%.2f".format(n)

@Composable
private fun Lugar(titulo: String, d: JSONObject, desde: String?, hasta: String?) {
    val c = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = Marca.fondoSuave)) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(titulo.uppercase(), color = Marca.brandRojo, fontWeight = FontWeight.Bold)
            Text(d.texto("nombre") ?: "", style = MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(d.texto("linea1"), d.texto("linea2")).joinToString(", "))
            Text("${d.texto("codigo_postal") ?: ""} ${d.texto("poblacion") ?: ""}")
            d.texto("horario")?.let { Text("Horario: $it") }
            if (desde != null) Text("Cita: ${hora(desde)}${hasta?.let { " – ${hora(it)}" } ?: ""}")
            d.texto("instrucciones")?.let { Text(it, color = Marca.textoCuerpoSecundario) }
            d.texto("contacto_nombre")?.let { Text("Contacto: $it") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val lat = d.numero("lat"); val lon = d.numero("lon")
                if (lat != null && lon != null) Button(onClick = { Navegacion.navegarA(c, lat, lon, d.texto("nombre") ?: titulo) }) {
                    Icon(Icons.Filled.Navigation, null); Spacer(Modifier.width(4.dp)); Text("Navegar")
                }
                d.texto("contacto_telefono")?.let { tel ->
                    OutlinedButton(onClick = { Navegacion.llamar(c, tel) }) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(4.dp)); Text("Llamar") }
                }
            }
        }
    }
}

