package net.actiontruck.conductor.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

/**
 * Envía la parada a la app de navegación del móvil. El ERP no usa servicios de rutas de terceros (las rutas son de
 * pgRouting): aquí solo se abre la app de navegación que el conductor ya tiene, con el destino como coordenadas.
 * Preferencia: Google Maps en modo conducción; si no está, cualquier app que entienda «geo:» (Waze, OsmAnd…).
 */
object Navegacion {
    private const val PAQUETE_GOOGLE_MAPS = "com.google.android.apps.maps"

    private fun coord(lat: Double, lon: Double) = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)

    fun navegarA(c: Context, lat: Double, lon: Double, etiqueta: String) {
        val navegacion = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${coord(lat, lon)}&mode=d")).setPackage(PAQUETE_GOOGLE_MAPS)
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${coord(lat, lon)}?q=${coord(lat, lon)}(${Uri.encode(etiqueta)})"))
        abrir(c, navegacion, geo)
    }

    /** Ruta con las paradas pendientes en orden: destino la última y hasta 9 intermedias (lo que admite Google Maps). */
    fun rutaCompleta(c: Context, paradas: List<Parada>) {
        val puntos = paradas.filter { it.lat != null && it.lon != null }.take(10)
        if (puntos.isEmpty()) { Toast.makeText(c, "Las paradas pendientes no tienen posición", Toast.LENGTH_LONG).show(); return }
        val destino = puntos.last()
        val intermedias = puntos.dropLast(1).joinToString("|") { coord(it.lat!!, it.lon!!) }
        val url = Uri.Builder().scheme("https").authority("www.google.com").path("/maps/dir/")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", coord(destino.lat!!, destino.lon!!))
            .appendQueryParameter("travelmode", "driving")
            .apply { if (intermedias.isNotEmpty()) appendQueryParameter("waypoints", intermedias) }
            .build()
        abrir(c, Intent(Intent.ACTION_VIEW, url).setPackage(PAQUETE_GOOGLE_MAPS), Intent(Intent.ACTION_VIEW, url))
    }

    fun llamar(c: Context, telefono: String) = abrir(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${telefono.filter { it.isDigit() || it == '+' }}")))

    private fun abrir(c: Context, vararg intentos: Intent) {
        for (i in intentos) {
            try {
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(c, "No hay ninguna app instalada para abrirlo", Toast.LENGTH_LONG).show()
    }
}
