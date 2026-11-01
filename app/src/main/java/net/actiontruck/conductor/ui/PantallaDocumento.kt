package net.actiontruck.conductor.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.actiontruck.conductor.marca.Marca
import java.io.File

/**
 * Documento de control o albarán en PDF, tal como lo genera el ERP. Se ve en la propia app (PdfRenderer, sin
 * depender de un lector instalado), se amplía con dos dedos y se puede compartir o imprimir con otra app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDocumento(m: Modelo, p: Pantalla.Documento) {
    val c = LocalContext.current
    var fichero by remember { mutableStateOf<File?>(null) }
    var paginas by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var escala by remember { mutableFloatStateOf(1f) }
    var desplazamiento by remember { mutableStateOf(Offset.Zero) }
    val ancho = c.resources.displayMetrics.widthPixels * 2

    LaunchedEffect(p.ruta) {
        val f = m.descargar(p.ruta, p.fichero)
        if (f == null) { error = m.mensaje ?: "No se pudo descargar"; return@LaunchedEffect }
        fichero = f
        paginas = withContext(Dispatchers.IO) {
            runCatching {
                PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
                    (0 until r.pageCount).map { i ->
                        r.openPage(i).use { pg ->
                            val bmp = Bitmap.createBitmap(ancho, ancho * pg.height / pg.width, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }.getOrElse { error = "El PDF no se puede abrir: ${it.message}"; emptyList() }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(p.titulo, color = Marca.textoSobreAzul) },
            navigationIcon = { IconButton(onClick = { m.atras() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Marca.textoSobreAzul) } },
            actions = {
                fichero?.let { f ->
                    IconButton(onClick = {
                        val uri = FileProvider.getUriForFile(c, "${c.packageName}.archivos", f)
                        c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), p.titulo))
                    }) { Icon(Icons.Filled.Share, "Compartir", tint = Marca.textoSobreAzul) }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Marca.brandAzul),
        )
    }) { relleno ->
        Box(Modifier.padding(relleno).fillMaxSize().background(Marca.fondoSuave)
            .pointerInput(Unit) {
                detectTransformGestures { _, mover, zoom, _ ->
                    escala = (escala * zoom).coerceIn(1f, 4f)
                    desplazamiento = if (escala == 1f) Offset.Zero else desplazamiento + mover
                }
            }) {
            when {
                error != null -> Text(error!!, Modifier.padding(16.dp), color = Marca.estadoError)
                paginas.isEmpty() -> Text("Descargando el documento…", Modifier.padding(16.dp))
                else -> LazyColumn(
                    Modifier.fillMaxSize().graphicsLayer(scaleX = escala, scaleY = escala, translationX = desplazamiento.x, translationY = desplazamiento.y),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(paginas) { i, b ->
                        Image(b.asImageBitmap(), "Página ${i + 1}", Modifier.fillMaxWidth().padding(8.dp), contentScale = ContentScale.FillWidth)
                    }
                }
            }
        }
    }
}
