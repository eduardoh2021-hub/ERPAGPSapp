package net.actiontruck.conductor.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.actiontruck.conductor.gps.ServicioGps
import net.actiontruck.conductor.marca.Marca
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
private val DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm")
fun diaHora(iso: String?): String? = iso?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).format(DIA_HORA) }.getOrNull() }
fun hora(iso: String?): String? = iso?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).format(HORA) }.getOrNull() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipal(m: Modelo) {
    LaunchedEffect(m.mensaje) { if (m.mensaje != null) { delay(6000); m.mensaje = null } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(m.viaje?.codigo ?: "Action Truck", style = MaterialTheme.typography.titleLarge, color = Marca.textoSobreAzul)
                        Text(m.prefs.conductor, style = MaterialTheme.typography.bodySmall, color = Marca.textoSobreAzul)
                    }
                },
                actions = { IconButton(onClick = { m.cargar() }) { Icon(Icons.Filled.Refresh, "Actualizar", tint = Marca.textoSobreAzul) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Marca.brandAzul),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Marca.fondoPrincipal) {
                val colores = NavigationBarItemDefaults.colors(selectedIconColor = Marca.brandRojo, selectedTextColor = Marca.brandRojo,
                    indicatorColor = Marca.fondoSuave)
                NavigationBarItem(m.pestana == 0, { m.pestana = 0 }, { Icon(Icons.Filled.Route, null) }, label = { Text("Paradas") }, colors = colores)
                NavigationBarItem(m.pestana == 1, { m.pestana = 1 }, { Icon(Icons.Filled.Map, null) }, label = { Text("Mapa") }, colors = colores)
                NavigationBarItem(m.pestana == 2, { m.pestana = 2 }, { Icon(Icons.Filled.Person, null) }, label = { Text("Jornada") }, colors = colores)
            }
        },
        snackbarHost = { m.mensaje?.let { Snackbar(Modifier.padding(12.dp)) { Text(it) } } },
    ) { relleno ->
        Box(Modifier.padding(relleno).fillMaxSize()) {
            when (m.pestana) {
                0 -> PestanaParadas(m)
                1 -> PantallaMapa(m)
                else -> PestanaJornada(m)
            }
            if (m.cargando) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Marca.brandRojo)
        }
    }
}

// =============================================================================
//  Paradas del viaje
// =============================================================================
@Composable
private fun PestanaParadas(m: Modelo) {
    val v = m.viaje
    val c = LocalContext.current
    if (v == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No tiene ningún viaje asignado para las próximas 12 horas.", style = MaterialTheme.typography.titleMedium)
            Text("Cuando tráfico le asigne uno, aparecerá aquí con sus paradas. Pulse actualizar para comprobarlo.",
                color = Marca.textoCuerpoSecundario)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Sus viajes por hacer: el vigente primero; se puede abrir cualquiera
        if (m.viajes.size > 1) item {
            Text("Sus viajes", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(m.viajes, key = { it.viajeId }) { r ->
                    val elegido = r.viajeId == v.id
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (elegido) Marca.brandAzul else Marca.fondoSuave)
                            .clickable { m.elegirViaje(r.viajeId) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        val color = if (elegido) Marca.textoSobreAzul else Marca.textoCuerpo
                        Text(r.codigo, color = color, fontWeight = FontWeight.Bold)
                        Text("${diaHora(r.desde) ?: ""} · ${r.paradasPendientes} por hacer", color = color, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Text("${v.vehiculo ?: ""}${v.remolque?.let { " + $it" } ?: ""} · ${v.paradas.count { it.hecha }} de ${v.paradas.size} paradas hechas",
                color = Marca.textoCuerpoSecundario)
            val previsto = listOfNotNull(diaHora(v.desde), hora(v.hasta)).joinToString(" – ")
            if (previsto.isNotEmpty()) Text("Previsto: $previsto", color = Marca.textoCuerpoSecundario)
            val pasado = v.hasta?.let { runCatching { OffsetDateTime.parse(it).isBefore(OffsetDateTime.now()) }.getOrNull() } == true
            if (pasado && v.siguiente != null) {
                Text("La hora prevista de este viaje ya ha pasado: si lo está haciendo, siga registrando sus paradas con normalidad.",
                    color = Marca.estadoError, style = MaterialTheme.typography.bodySmall)
            }
        }
        v.siguiente?.let { s ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Marca.fondoSuave), modifier = Modifier.border(2.dp, Marca.brandRojo, RoundedCornerShape(12.dp))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SIGUIENTE ${if (s.esRecogida) "RECOGIDA" else "ENTREGA"}", color = Marca.brandRojo, fontWeight = FontWeight.Bold)
                        Text(s.nombre, style = MaterialTheme.typography.titleLarge)
                        Text("${s.direccion} · ${s.poblacion}")
                        Text("${s.orden} · ${s.cliente}", color = Marca.textoCuerpoSecundario)
                        hora(s.eta)?.let { Text("Llegada prevista $it${s.retrasoMin?.takeIf { r -> r > 0 }?.let { r -> " · $r min de retraso" } ?: ""}",
                            color = if ((s.retrasoMin ?: 0) > 0) Marca.estadoError else Marca.textoCuerpo) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { Navegacion.navegarA(c, s.lat!!, s.lon!!, s.nombre) }, enabled = s.lat != null,
                                modifier = Modifier.weight(1f).height(52.dp),
                            ) { Icon(Icons.Filled.Navigation, null); Spacer(Modifier.width(6.dp)); Text("Navegar") }
                            OutlinedButton(onClick = { m.ir(Pantalla.Orden(s.ordenId, s.orden)) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Abrir orden") }
                        }
                    }
                }
            }
        }
        val pendientes = v.paradas.filter { !it.hecha }
        if (pendientes.size > 1) item {
            TextButton(onClick = { Navegacion.rutaCompleta(c, pendientes) }) {
                Icon(Icons.Filled.Route, null); Spacer(Modifier.width(6.dp)); Text("Ruta con todas las paradas pendientes en Google Maps")
            }
        }
        item { Text("Todas las paradas", style = MaterialTheme.typography.titleMedium) }
        items(v.paradas, key = { "${it.numero}" }) { p -> FilaParada(p, p == v.siguiente) { m.ir(Pantalla.Orden(p.ordenId, p.orden)) } }
    }
}

@Composable
fun NumeroParada(p: Parada, siguiente: Boolean) {
    val fondo = when { p.hecha -> Marca.estadoExito; siguiente -> Marca.brandRojo; else -> Marca.fondoPrincipal }
    val texto = if (p.hecha || siguiente) Marca.textoSobreRojo else Marca.brandAzul
    Box(Modifier.size(36.dp).background(fondo, CircleShape).border(2.dp, if (p.hecha) fondo else Marca.brandRojo, CircleShape),
        contentAlignment = Alignment.Center) {
        Text(if (p.hecha) "✓" else "${p.numero}", color = texto, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FilaParada(p: Parada, siguiente: Boolean, abrir: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = abrir).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        NumeroParada(p, siguiente)
        Column(Modifier.weight(1f)) {
            Text("${if (p.esRecogida) "Recogida" else "Entrega"} · ${p.nombre}", fontWeight = FontWeight.Medium,
                color = if (p.hecha) Marca.textoCuerpoSecundario else Marca.textoCuerpo)
            Text("${p.poblacion} · ${p.orden}", style = MaterialTheme.typography.bodySmall, color = Marca.textoCuerpoSecundario)
        }
        hora(p.eta)?.takeIf { !p.hecha }?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), tint = Marca.textoCuerpoSecundario); Text(" $it")
            }
        }
    }
    HorizontalDivider(color = Marca.lineaBorde)
}

// =============================================================================
//  Jornada: GPS, envío al ERP, vehículo y sesión
// =============================================================================
@Composable
private fun PestanaJornada(m: Modelo) {
    val c = LocalContext.current
    val enMarcha by ServicioGps.enMarcha.collectAsState()
    val estadoGps by ServicioGps.estadoGps.collectAsState()
    val sinc by m.app.sincronizador.estado.collectAsState()
    var matricula by remember { mutableStateOf("") }
    var eventos by remember { mutableStateOf(m.app.cola.eventos()) }
    LaunchedEffect(sinc) { eventos = m.app.cola.eventos() }
    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            m.prefs.jornadaIniciada = true
            ServicioGps.iniciar(c)
        } else {
            m.mensaje = "Sin permiso de ubicación no se puede enviar la posición al ERP"
        }
    }
    LaunchedEffect(Unit) { m.app.sincronizador.refrescarContadores() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(m.prefs.conductor, style = MaterialTheme.typography.titleLarge)
        Text("Vehículo: ${m.vehiculoJornada ?: "sin asignar"}", color = Marca.textoCuerpoSecundario)

        Card(colors = CardDefaults.cardColors(containerColor = Marca.fondoSuave)) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Seguimiento GPS", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(if (enMarcha) Marca.estadoExito else Marca.textoCuerpoSecundario, CircleShape))
                    Text("  $estadoGps")
                }
                Text("Posiciones por enviar: ${sinc.posicionesPendientes} · eventos por enviar: ${sinc.eventosPendientes}")
                sinc.ultimoEnvio?.let { Text("Último envío al ERP: ${java.text.DateFormat.getTimeInstance().format(java.util.Date(it))}") }
                sinc.ultimoError?.let { Text(it, color = Marca.estadoError) }
                if (enMarcha) {
                    Button(onClick = { m.prefs.jornadaIniciada = false; ServicioGps.detener(c); m.sincronizar() },
                        colors = ButtonDefaults.buttonColors(containerColor = Marca.brandAzul), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Finalizar jornada")
                    }
                } else {
                    Button(onClick = {
                        val lista = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) lista += Manifest.permission.POST_NOTIFICATIONS
                        permisos.launch(lista.toTypedArray())
                    }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Iniciar jornada") }
                }
                OutlinedButton(onClick = { m.sincronizar() }, modifier = Modifier.fillMaxWidth()) { Text("Enviar ahora") }
                TextButton(onClick = { c.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
                    Text("Que el ahorro de batería no corte el GPS")
                }
            }
        }

        if (eventos.isNotEmpty()) {
            Text("Pendientes de enviar", style = MaterialTheme.typography.titleMedium)
            eventos.forEach { e ->
                Card(colors = CardDefaults.cardColors(containerColor = Marca.fondoSuave)) {
                    Column(Modifier.padding(12.dp).fillMaxWidth()) {
                        Text(e.descripcion, fontWeight = FontWeight.Medium)
                        Text(e.error ?: "Esperando conexión", color = if (e.error != null) Marca.estadoError else Marca.textoCuerpoSecundario)
                        if (e.error != null) TextButton(onClick = { m.descartarEvento(e.id); eventos = m.app.cola.eventos() }) { Text("Descartar") }
                    }
                }
            }
        }

        if (m.viaje == null) {
            Text("Sin viaje asignado: declare el vehículo que conduce", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(matricula, { matricula = it }, label = { Text("Matrícula") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { m.declararVehiculo(matricula) }, enabled = matricula.length >= 4) { Text("Declarar") }
            }
        }

        HorizontalDivider(color = Marca.lineaBorde)
        Text("Servidor: ${m.prefs.urlServidor}", style = MaterialTheme.typography.bodySmall, color = Marca.textoCuerpoSecundario)
        OutlinedButton(onClick = { m.salir() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Logout, null); Spacer(Modifier.width(6.dp)); Text("Cerrar sesión")
        }
        Spacer(Modifier.height(12.dp))
    }
}

