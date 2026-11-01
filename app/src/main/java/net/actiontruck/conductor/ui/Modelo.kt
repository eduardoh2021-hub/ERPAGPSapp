package net.actiontruck.conductor.ui

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.actiontruck.conductor.AppConductor
import net.actiontruck.conductor.BuildConfig
import net.actiontruck.conductor.datos.ErrorApi
import net.actiontruck.conductor.datos.numero
import net.actiontruck.conductor.datos.objeto
import net.actiontruck.conductor.datos.objetos
import net.actiontruck.conductor.datos.texto
import net.actiontruck.conductor.gps.ServicioGps
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Parada del viaje (GET /viaje). */
data class Parada(
    val numero: Int, val tipo: String, val ordenId: String, val orden: String, val cliente: String, val hecha: Boolean,
    val nombre: String, val direccion: String, val poblacion: String, val lat: Double?, val lon: Double?,
    val contacto: String?, val telefono: String?, val horario: String?, val instrucciones: String?, val eta: String?, val retrasoMin: Int?,
) {
    val esRecogida get() = tipo == "recogida"
}

data class Viaje(val codigo: String, val estado: String, val vehiculo: String?, val remolque: String?, val paradas: List<Parada>,
                 val geojson: JSONObject?) {
    val siguiente get() = paradas.firstOrNull { !it.hecha }
}

sealed interface Pantalla {
    data object Acceso : Pantalla
    data object Principal : Pantalla
    data class Orden(val id: String, val codigo: String) : Pantalla
    data class Evento(val ordenId: String, val orden: String, val tipo: String) : Pantalla
    data class Documento(val titulo: String, val ruta: String, val fichero: String) : Pantalla
}

/** Estado de la app y llamadas al ERP. Una sola instancia para toda la actividad. */
class Modelo(aplicacion: Application) : AndroidViewModel(aplicacion) {
    val app = aplicacion as AppConductor
    val prefs = app.preferencias
    val pila = mutableStateListOf<Pantalla>(if (prefs.token == null) Pantalla.Acceso else Pantalla.Principal)
    var pestana by mutableStateOf(0)
    var viaje by mutableStateOf<Viaje?>(null)
    var cargando by mutableStateOf(false)
    var mensaje by mutableStateOf<String?>(null)
    var estiloMapa by mutableStateOf<String?>(null)
    var tiposIncidencia by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var vehiculoJornada by mutableStateOf<String?>(null)

    val actual get() = pila.last()
    fun ir(p: Pantalla) { pila.add(p) }
    fun atras(): Boolean = if (pila.size > 1) { pila.removeAt(pila.lastIndex); true } else false

    private fun error(e: Exception): String = when (e) {
        is ErrorApi -> {
            if (e.estado == 401 || e.estado == 403) sesionPerdida(e.message ?: "Vuelva a iniciar sesión")
            e.message ?: "Error del servidor"
        }
        is IOException -> "Sin conexión con el ERP (${prefs.urlServidor})"
        else -> e.message ?: e.toString()
    }

    private fun sesionPerdida(aviso: String) {
        prefs.avisoSesion = aviso
        prefs.cerrarSesion()
        ServicioGps.detener(app)
        pila.clear(); pila.add(Pantalla.Acceso)
    }

    // --- Sesión -----------------------------------------------------------------------------
    fun entrar(url: String, usuario: String, contrasena: String, hecho: () -> Unit) = viewModelScope.launch {
        cargando = true; mensaje = null
        try {
            prefs.urlServidor = url
            val r = app.api.iniciarSesion(usuario.trim(), contrasena, "${Build.MANUFACTURER} ${Build.MODEL}", BuildConfig.VERSION_NAME)
            prefs.token = r.getString("token")
            prefs.usuario = usuario.trim()
            prefs.conductor = r.objeto("conductor")?.texto("nombre") ?: usuario
            prefs.avisoSesion = null
            pila.clear(); pila.add(Pantalla.Principal)
            hecho()
            cargar()
        } catch (e: Exception) {
            // Un 401 aquí son credenciales malas, no una sesión perdida
            mensaje = if (e is ErrorApi) e.message else error(e)
        } finally {
            cargando = false
        }
    }

    fun salir() = viewModelScope.launch {
        ServicioGps.detener(app)
        app.sincronizador.sincronizar()
        runCatching { app.api.cerrarSesion() }
        prefs.cerrarSesion()
        viaje = null
        pila.clear(); pila.add(Pantalla.Acceso)
    }

    // --- Viaje y catálogos ----------------------------------------------------------------------
    fun cargar() = viewModelScope.launch {
        if (prefs.token == null) return@launch
        cargando = true; mensaje = null
        try {
            app.sincronizador.sincronizar()
            if (estiloMapa == null) {
                val c = app.api.config()
                estiloMapa = c.texto("mapa_estilo_url")?.let { if (it.startsWith("/")) prefs.urlServidor + it else it }
            }
            if (tiposIncidencia.isEmpty()) tiposIncidencia = app.api.tiposIncidencia().objetos().map { it.getString("id") to it.getString("descripcion") }
            val v = app.api.viaje()
            viaje = v.objeto("viaje")?.let { vj ->
                val a = v.objeto("asignacion")
                Viaje(vj.getString("codigo"), vj.getString("estado"), a?.texto("vehiculo"), a?.texto("remolque"),
                    v.getJSONArray("paradas").objetos().map(::parada), v.objeto("geojson"))
            }
            val yo = app.api.yo()
            vehiculoJornada = yo.objeto("asignacion")?.texto("vehiculo") ?: yo.objeto("vehiculo_declarado")?.texto("matricula")
        } catch (e: Exception) {
            mensaje = error(e)
        } finally {
            cargando = false
        }
    }

    private fun parada(p: JSONObject): Parada {
        val d = p.getJSONObject("direccion")
        return Parada(
            numero = p.getInt("numero"), tipo = p.getString("tipo"), ordenId = p.getString("orden_id"), orden = p.getString("orden"),
            cliente = p.texto("cliente") ?: "", hecha = p.getString("estado") == "hecha", nombre = d.texto("nombre") ?: "",
            direccion = listOfNotNull(d.texto("linea1"), d.texto("codigo_postal")).joinToString(", "), poblacion = d.texto("poblacion") ?: "",
            lat = d.numero("lat"), lon = d.numero("lon"), contacto = d.texto("contacto_nombre"), telefono = d.texto("contacto_telefono"),
            horario = d.texto("horario"), instrucciones = d.texto("instrucciones"), eta = p.texto("eta") ?: p.texto("llegada_prevista"),
            retrasoMin = p.numero("retraso_min")?.toInt(),
        )
    }

    fun declararVehiculo(matricula: String) = viewModelScope.launch {
        try {
            vehiculoJornada = app.api.declararVehiculo(matricula.trim().uppercase()).getJSONObject("vehiculo").getString("matricula")
            mensaje = "Vehículo $vehiculoJornada declarado para la jornada"
        } catch (e: Exception) {
            mensaje = error(e)
        }
    }

    suspend fun orden(id: String): JSONObject? = try { app.api.orden(id) } catch (e: Exception) { mensaje = error(e); null }

    suspend fun descargar(ruta: String, fichero: String): File? = try {
        app.api.descargar(ruta, File(app.cacheDir, "documentos/$fichero"))
    } catch (e: Exception) {
        mensaje = error(e); null
    }

    // --- Eventos (llegada, carga, entrega con firma, incidencias) --------------------------------------
    /**
     * Guarda el evento en la cola local (así no se pierde sin cobertura) e intenta enviarlo enseguida.
     * Devuelve el texto para el conductor.
     */
    suspend fun registrarEvento(ordenId: String, descripcion: String, cuerpo: JSONObject): String {
        ServicioGps.ultimaPosicion.value?.let { if (!cuerpo.has("lat")) cuerpo.put("lat", it.latitude).put("lon", it.longitude) }
        app.cola.guardarEvento(ordenId, descripcion, cuerpo)
        app.sincronizador.sincronizar()
        val pendiente = app.cola.eventos().firstOrNull { it.id == cuerpo.getString("id") }
        app.sincronizador.refrescarContadores()
        return when {
            pendiente == null -> { cargar(); "$descripcion: registrado en el ERP" }
            pendiente.error != null -> "$descripcion: el ERP no lo admite (${pendiente.error})"
            else -> "$descripcion: guardado en el móvil, se enviará al recuperar la conexión"
        }
    }

    fun descartarEvento(id: String) {
        app.cola.borrarEvento(id)
        app.sincronizador.refrescarContadores()
    }

    fun sincronizar() = viewModelScope.launch {
        if (!app.sincronizador.sincronizar() && prefs.token == null) sesionPerdida(prefs.avisoSesion ?: "Vuelva a iniciar sesión")
    }
}
