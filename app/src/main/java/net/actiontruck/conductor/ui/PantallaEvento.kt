package net.actiontruck.conductor.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.actiontruck.conductor.marca.Marca
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import androidx.compose.foundation.Canvas as LienzoCompose
import androidx.compose.ui.graphics.Path as TrazoCompose

/**
 * Registrar un evento de la orden. En la entrega (POD) pide quién recibe, su DNI, la firma en pantalla y fotos;
 * en la carga, la firma es opcional; en una incidencia, el tipo y fotos. Se guarda primero en el móvil y se envía
 * con un UUID propio: si no hay cobertura, sale solo al recuperarla y nunca se duplica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaEvento(m: Modelo, p: Pantalla.Evento) {
    val c = LocalContext.current
    val foco = LocalFocusManager.current
    val alcance = rememberCoroutineScope()
    val entrega = p.tipo == "entregada" || p.tipo == "entrega_parcial"
    val conFirma = entrega || p.tipo == "cargada"
    val conIncidencia = p.tipo == "incidencia"
    var tipoEntrega by remember { mutableStateOf(p.tipo) }
    var receptor by remember { mutableStateOf("") }
    var documento by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var incidencia by remember { mutableStateOf<Pair<String, String>?>(null) }
    var tipoIncidenciaEvento by remember { mutableStateOf("incidencia") }
    val trazos = remember { mutableStateListOf<List<Offset>>() }
    var trazoActual by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var firmando by remember { mutableStateOf(false) }
    var tamanoFirma by remember { mutableStateOf(IntSize.Zero) }
    val fotos = remember { mutableStateListOf<Bitmap>() }
    var fotoPendiente by remember { mutableStateOf<Uri?>(null) }
    var enviando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<String?>(null) }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = fotoPendiente
        if (ok && uri != null) alcance.launch { reducirFoto(c, uri)?.let { fotos.add(it) } }
    }
    fun hacerFoto() {
        val f = File(c.cacheDir, "fotos/${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.archivos", f)
        fotoPendiente = uri
        camara.launch(uri)
    }

    val faltan = buildList {
        if (entrega && receptor.isBlank()) add("quién recibe")
        if (entrega && trazos.isEmpty()) add("la firma")
        if (conIncidencia && incidencia == null) add("el tipo de incidencia")
        if (p.tipo == "nota" && descripcion.isBlank()) add("el texto")
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("${NOMBRE_EVENTO[p.tipo] ?: p.tipo} · ${p.orden}", color = Marca.textoSobreAzul) },
            navigationIcon = { IconButton(onClick = { m.atras() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Marca.textoSobreAzul) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Marca.brandAzul),
        )
    }) { relleno ->
        resultado?.let { r ->
            Column(Modifier.padding(relleno).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(r, style = MaterialTheme.typography.titleMedium)
                Button(onClick = { m.atras() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Volver a la orden") }
            }
            return@Scaffold
        }
        // Mientras se firma no se desplaza la pantalla: el lienzo consume los gestos
        Column(Modifier.padding(relleno).fillMaxSize().imePadding().verticalScroll(rememberScrollState(), enabled = !firmando).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (entrega) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("entregada" to "Completa", "entrega_parcial" to "Parcial").forEach { (t, n) ->
                        if (tipoEntrega == t) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(n) }
                        else OutlinedButton(onClick = { tipoEntrega = t }, modifier = Modifier.weight(1f)) { Text(n) }
                    }
                }
            }
            if (conFirma) {
                OutlinedTextField(receptor, { receptor = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (entrega) "Nombre de quien recibe" else "Nombre de quien entrega la carga (opcional)") })
                OutlinedTextField(documento, { documento = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("DNI / NIE (opcional)") })
            }
            if (conIncidencia) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("incidencia" to "Incidencia", "ausente" to "Ausente", "rechazada" to "Rechazada").forEach { (t, n) ->
                        if (tipoIncidenciaEvento == t) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(n) }
                        else OutlinedButton(onClick = { tipoIncidenciaEvento = t }, modifier = Modifier.weight(1f)) { Text(n) }
                    }
                }
                var abierto by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
                    OutlinedTextField(incidencia?.second ?: "", {}, readOnly = true, label = { Text("Tipo de incidencia") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                        m.tiposIncidencia.forEach { t -> DropdownMenuItem(text = { Text(t.second) }, onClick = { incidencia = t; abierto = false }) }
                    }
                }
            }
            OutlinedTextField(descripcion, { descripcion = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                label = { Text(if (p.tipo == "nota") "Nota" else "Observaciones (opcional)") })

            if (conFirma) {
                Row {
                    Text(if (entrega) "Firma de quien recibe" else "Firma (opcional)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { trazos.clear() }) { Text("Borrar") }
                }
                LienzoCompose(
                    // clip: nada de la firma se pinta fuera del recuadro
                    Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)).background(Marca.fondoPrincipal, RoundedCornerShape(8.dp))
                        .border(2.dp, Marca.lineaBorde, RoundedCornerShape(8.dp))
                        .onSizeChanged { tamanoFirma = it }
                        .pointerInput(Unit) {
                            // El trazo empieza al apoyar el dedo y se consume todo: la página no se desplaza mientras se firma.
                            // Solo se guardan los puntos dentro del recuadro: si el dedo sale, el trazo se corta en el borde y,
                            // si vuelve a entrar, empieza otro (no se dibujan líneas por fuera ni a lo largo del borde).
                            awaitEachGesture {
                                val ancho = size.width.toFloat(); val alto = size.height.toFloat()
                                fun dentro(p: Offset) = p.x in MARGEN_FIRMA..(ancho - MARGEN_FIRMA) && p.y in MARGEN_FIRMA..(alto - MARGEN_FIRMA)
                                fun alBorde(p: Offset) = Offset(p.x.coerceIn(MARGEN_FIRMA, ancho - MARGEN_FIRMA), p.y.coerceIn(MARGEN_FIRMA, alto - MARGEN_FIRMA))
                                val inicio = awaitFirstDown()
                                inicio.consume()
                                foco.clearFocus()   // se cierra el teclado: no tapa el recuadro de la firma
                                firmando = true
                                try {
                                    trazoActual = if (dentro(inicio.position)) listOf(inicio.position) else emptyList()
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val dedo = ev.changes.firstOrNull { it.id == inicio.id } ?: break
                                        if (!dedo.pressed) break
                                        dedo.consume()
                                        val punto = dedo.position
                                        if (dentro(punto)) {
                                            trazoActual = trazoActual + punto
                                        } else if (trazoActual.isNotEmpty()) {
                                            val cerrado = trazoActual + alBorde(punto)
                                            if (cerrado.size > 1) trazos.add(cerrado)
                                            trazoActual = emptyList()
                                        }
                                    }
                                    if (trazoActual.size > 1) trazos.add(trazoActual)
                                } finally {
                                    trazoActual = emptyList()
                                    firmando = false
                                }
                            }
                        },
                ) {
                    (trazos + listOf(trazoActual)).filter { it.size > 1 }.forEach { t ->
                        val trazo = TrazoCompose().apply { moveTo(t[0].x, t[0].y); t.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(trazo, Marca.textoCuerpo, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fotos.forEachIndexed { i, f ->
                    Image(f.asImageBitmap(), "Foto ${i + 1}", Modifier.size(72.dp).clickable { fotos.removeAt(i) }, contentScale = ContentScale.Crop)
                }
            }
            if (p.tipo != "nota" && fotos.size < 4) OutlinedButton(onClick = { hacerFoto() }) {
                Icon(Icons.Filled.PhotoCamera, null); Text("  Foto${if (fotos.isNotEmpty()) " (pulse una para quitarla)" else ""}")
            }

            if (faltan.isNotEmpty()) Text("Falta: ${faltan.joinToString(", ")}", color = Marca.textoCuerpoSecundario)
            Button(
                enabled = faltan.isEmpty() && !enviando,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    enviando = true
                    alcance.launch {
                        val tipo = when { entrega -> tipoEntrega; conIncidencia -> tipoIncidenciaEvento; else -> p.tipo }
                        val cuerpo = JSONObject().put("id", UUID.randomUUID().toString()).put("tipo", tipo).put("fecha", Instant.now().toString())
                        if (receptor.isNotBlank()) cuerpo.put("receptor_nombre", receptor.trim())
                        if (documento.isNotBlank()) cuerpo.put("receptor_documento", documento.trim().uppercase())
                        if (descripcion.isNotBlank()) cuerpo.put("descripcion", descripcion.trim())
                        incidencia?.let { if (conIncidencia) cuerpo.put("tipo_incidencia_id", it.first) }
                        withContext(Dispatchers.Default) {
                            if (conFirma && trazos.isNotEmpty()) cuerpo.put("firma", firmaPng(trazos, tamanoFirma, Marca.textoCuerpo.toArgb()))
                            if (fotos.isNotEmpty()) cuerpo.put("fotos", JSONArray(fotos.map { base64(it, Bitmap.CompressFormat.JPEG, 75) }))
                        }
                        resultado = m.registrarEvento(p.ordenId, "${NOMBRE_EVENTO[tipo] ?: tipo} · ${p.orden}", cuerpo)
                        enviando = false
                    }
                },
            ) {
                if (enviando) Box(Modifier.size(24.dp)) { CircularProgressIndicator(color = Marca.textoSobreRojo) } else Text("Registrar")
            }
        }
    }
}

/** Distancia mínima de la firma al borde del recuadro (la mitad del grosor del trazo): ningún punto se sale. */
private const val MARGEN_FIRMA = 3f

/** La firma dibujada, recortada a lo que ocupa, en PNG transparente y base64. */
private fun firmaPng(trazos: List<List<Offset>>, tam: IntSize, color: Int): String {
    val puntos = trazos.flatten()
    val margen = 12f
    val minX = (puntos.minOf { it.x } - margen).coerceAtLeast(0f); val minY = (puntos.minOf { it.y } - margen).coerceAtLeast(0f)
    val maxX = (puntos.maxOf { it.x } + margen).coerceAtMost(tam.width.toFloat()); val maxY = (puntos.maxOf { it.y } + margen).coerceAtMost(tam.height.toFloat())
    val bmp = Bitmap.createBitmap((maxX - minX).toInt().coerceAtLeast(1), (maxY - minY).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val lienzo = android.graphics.Canvas(bmp)
    val pincel = android.graphics.Paint().apply {
        this.color = color; style = android.graphics.Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    trazos.forEach { t ->
        val trazo = android.graphics.Path().apply { moveTo(t[0].x - minX, t[0].y - minY); t.drop(1).forEach { lineTo(it.x - minX, it.y - minY) } }
        lienzo.drawPath(trazo, pincel)
    }
    return base64(bmp, Bitmap.CompressFormat.PNG, 100)
}

private fun base64(b: Bitmap, formato: Bitmap.CompressFormat, calidad: Int): String =
    ByteArrayOutputStream().use { s -> b.compress(formato, calidad, s); Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP) }

/** Foto de la cámara reducida a 1600 px de lado mayor (unos 300 KB en JPEG). */
private suspend fun reducirFoto(c: android.content.Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        var muestra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (muestra * 2) >= 1600) muestra *= 2
        val b = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra }) }
            ?: return@runCatching null
        val escala = 1600f / maxOf(b.width, b.height)
        if (escala < 1f) Bitmap.createScaledBitmap(b, (b.width * escala).toInt(), (b.height * escala).toInt(), true) else b
    }.getOrNull()
}
