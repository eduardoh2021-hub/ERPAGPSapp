package net.actiontruck.conductor.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.actiontruck.conductor.marca.Marca
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

/**
 * Todas las paradas del viaje sobre el mapa (MapLibre + teselas OSM, el estilo lo da el ERP en /config) con el trazado
 * previsto que calculó pgRouting. Verde: hecha; rojo: la siguiente; blanco con borde rojo: pendiente.
 * Pulsar una parada abre su orden.
 */
@Composable
fun PantallaMapa(m: Modelo) {
    val c = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val estilo = m.estiloMapa
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    var estiloCargado by remember { mutableStateOf<Style?>(null) }
    val vista = remember { MapView(c).apply { onCreate(null) } }

    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> vista.onStart()
                Lifecycle.Event.ON_RESUME -> vista.onResume()
                Lifecycle.Event.ON_PAUSE -> vista.onPause()
                Lifecycle.Event.ON_STOP -> vista.onStop()
                else -> {}
            }
        }
        ciclo.addObserver(observador)
        if (ciclo.currentState.isAtLeast(Lifecycle.State.STARTED)) vista.onStart()
        if (ciclo.currentState.isAtLeast(Lifecycle.State.RESUMED)) vista.onResume()
        onDispose {
            ciclo.removeObserver(observador)
            vista.onPause(); vista.onStop(); vista.onDestroy()
        }
    }

    LaunchedEffect(estilo) {
        if (estilo == null) return@LaunchedEffect
        vista.getMapAsync { mp ->
            mapa = mp
            mp.uiSettings.isRotateGesturesEnabled = false
            mp.setStyle(Style.Builder().fromUri(estilo)) { st ->
                estiloCargado = st
                activarUbicacion(c, mp, st)
            }
            mp.addOnMapClickListener { punto ->
                val pantalla = mp.projection.toScreenLocation(punto)
                val f = mp.queryRenderedFeatures(pantalla, "paradas-circulo").firstOrNull()
                val ordenId = f?.getStringProperty("orden_id")
                if (ordenId != null) { m.ir(Pantalla.Orden(ordenId, f.getStringProperty("orden") ?: "")); true } else false
            }
        }
    }

    // Capas del viaje: se rehacen cuando cambia el viaje (una parada hecha cambia de color)
    LaunchedEffect(estiloCargado, m.viaje) {
        val st = estiloCargado ?: return@LaunchedEffect
        val mp = mapa ?: return@LaunchedEffect
        val v = m.viaje ?: return@LaunchedEffect
        val lineas = JSONArray()
        v.geojson?.optJSONArray("features")?.let { fs ->
            for (i in 0 until fs.length()) {
                val f = fs.getJSONObject(i)
                val tipo = f.optJSONObject("geometry")?.optString("type")
                if (tipo == "LineString" || tipo == "MultiLineString") lineas.put(f)
            }
        }
        val paradas = JSONArray()
        v.paradas.filter { it.lat != null && it.lon != null }.forEach { p ->
            paradas.put(JSONObject().put("type", "Feature")
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(p.lon).put(p.lat)))
                .put("properties", JSONObject().put("n", if (p.hecha) "" else "${p.numero}").put("orden_id", p.ordenId).put("orden", p.orden)
                    .put("estado", when { p.hecha -> "hecha"; p == v.siguiente -> "siguiente"; else -> "pendiente" })))
        }
        val fcLineas = FeatureCollection.fromJson(JSONObject().put("type", "FeatureCollection").put("features", lineas).toString())
        val fcParadas = FeatureCollection.fromJson(JSONObject().put("type", "FeatureCollection").put("features", paradas).toString())
        (st.getSourceAs<GeoJsonSource>("trazado") ?: GeoJsonSource("trazado").also { st.addSource(it) }).setGeoJson(fcLineas)
        (st.getSourceAs<GeoJsonSource>("paradas") ?: GeoJsonSource("paradas").also { st.addSource(it) }).setGeoJson(fcParadas)
        if (st.getLayer("trazado-linea") == null) {
            st.addLayer(LineLayer("trazado-linea", "trazado").withProperties(
                PropertyFactory.lineColor(Marca.brandAzul.toArgb()), PropertyFactory.lineWidth(4f), PropertyFactory.lineOpacity(0.8f)))
            val color = Expression.match(Expression.get("estado"), Expression.color(Marca.fondoPrincipal.toArgb()),
                Expression.stop("hecha", Expression.color(Marca.estadoExito.toArgb())),
                Expression.stop("siguiente", Expression.color(Marca.brandRojo.toArgb())))
            st.addLayer(CircleLayer("paradas-circulo", "paradas").withProperties(
                PropertyFactory.circleRadius(13f), PropertyFactory.circleColor(color),
                PropertyFactory.circleStrokeColor(Marca.brandRojo.toArgb()), PropertyFactory.circleStrokeWidth(2.5f)))
            // Los números, con una tipografía que el estilo tenga (si se pide otra, MapLibre no dibuja la fuente entera)
            val tipografia = elegirTipografia(st)
            if (tipografia != null) st.addLayer(SymbolLayer("paradas-numero", "paradas").withProperties(
                PropertyFactory.textField(Expression.get("n")), PropertyFactory.textSize(13f), PropertyFactory.textFont(tipografia),
                PropertyFactory.textColor(Expression.match(Expression.get("estado"), Expression.color(Marca.brandAzul.toArgb()),
                    Expression.stop("hecha", Expression.color(Marca.textoSobreRojo.toArgb())),
                    Expression.stop("siguiente", Expression.color(Marca.textoSobreRojo.toArgb())))),
                PropertyFactory.textAllowOverlap(true), PropertyFactory.textIgnorePlacement(true)))
        }
        val puntos = v.paradas.mapNotNull { p -> if (p.lat != null && p.lon != null) LatLng(p.lat, p.lon) else null }
        when {
            puntos.size >= 2 -> mp.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(puntos).build(), 120))
            puntos.size == 1 -> mp.moveCamera(CameraUpdateFactory.newLatLngZoom(puntos[0], 13.0))
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView({ vista }, Modifier.fillMaxSize())
        when {
            estilo == null -> Text("Cargando el mapa…", Modifier.padding(16.dp))
            m.viaje == null -> Text("Sin viaje asignado", Modifier.padding(16.dp))
        }
    }
}

/** Tipografía del estilo para los números: negrita si la hay, si no la regular (sin cursivas), como en la web (mapa.js). */
private fun elegirTipografia(st: Style): Array<String>? {
    val pilas = st.layers.filterIsInstance<SymbolLayer>().mapNotNull { it.textFont.value?.takeIf { p -> p.isNotEmpty() } }
    fun con(re: Regex) = pilas.firstOrNull { p -> p.any { re.containsMatchIn(it) } && p.none { Regex("italic|oblique", RegexOption.IGNORE_CASE).containsMatchIn(it) } }
    return con(Regex("bold", RegexOption.IGNORE_CASE)) ?: con(Regex("regular|medium", RegexOption.IGNORE_CASE))
}

@SuppressLint("MissingPermission")
private fun activarUbicacion(c: android.content.Context, mp: MapLibreMap, st: Style) {
    if (ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    runCatching {
        mp.locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(c, st).build())
        mp.locationComponent.isLocationComponentEnabled = true
    }
}
