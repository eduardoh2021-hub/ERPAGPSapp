package net.actiontruck.conductor.datos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error con respuesta del servidor ({ "error": "…" }); los fallos de red llegan como IOException. */
class ErrorApi(val estado: Int, mensaje: String) : Exception(mensaje)

/**
 * Cliente de /api/movil/v1 (contrato en docs/api-movil.md). Todas las llamadas son suspend y van al hilo de E/S.
 * Sin cookies: cada petición lleva «Authorization: Bearer <token>».
 */
class Api(private val prefs: Preferencias) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun url(ruta: String) = "${prefs.urlServidor}/api/movil/v1$ruta"

    private suspend fun llamar(metodo: String, ruta: String, cuerpo: Any? = null, conToken: Boolean = true): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url(ruta))
        if (conToken) b.header("Authorization", "Bearer ${prefs.token ?: throw ErrorApi(401, "Sesión no iniciada")}")
        b.method(metodo, cuerpo?.toString()?.toRequestBody(json))
        http.newCall(b.build()).execute().use { r ->
            val texto = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val mensaje = runCatching { JSONObject(texto).getString("error") }.getOrElse { "Error ${r.code} del servidor" }
                throw ErrorApi(r.code, mensaje)
            }
            texto
        }
    }

    suspend fun iniciarSesion(usuario: String, contrasena: String, modelo: String, version: String): JSONObject {
        val cuerpo = JSONObject()
            .put("usuario", usuario).put("contrasena", contrasena)
            .put("dispositivo", JSONObject().put("id_instalacion", prefs.idInstalacion).put("plataforma", "android")
                .put("modelo", modelo).put("version_app", version))
        return JSONObject(llamar("POST", "/sesion", cuerpo, conToken = false))
    }

    suspend fun cerrarSesion() { llamar("DELETE", "/sesion") }
    suspend fun yo() = JSONObject(llamar("GET", "/yo"))
    suspend fun viaje(id: String? = null) = JSONObject(llamar("GET", if (id == null) "/viaje" else "/viaje?id=$id"))
    suspend fun viajes() = JSONArray(llamar("GET", "/viajes"))
    suspend fun config() = JSONObject(llamar("GET", "/config"))
    suspend fun tiposIncidencia() = JSONArray(llamar("GET", "/tipos-incidencia"))
    suspend fun orden(id: String) = JSONObject(llamar("GET", "/ordenes/$id"))
    suspend fun declararVehiculo(matricula: String) = JSONObject(llamar("POST", "/jornada", JSONObject().put("matricula", matricula)))
    suspend fun enviarPosiciones(lote: JSONArray) = JSONObject(llamar("POST", "/posiciones", JSONObject().put("posiciones", lote)))
    suspend fun enviarEvento(ordenId: String, evento: JSONObject) = JSONObject(llamar("POST", "/ordenes/$ordenId/eventos", evento))

    /** Descarga un PDF (documento de control o albarán) a un fichero. */
    suspend fun descargar(ruta: String, destino: File): File = withContext(Dispatchers.IO) {
        val r = http.newCall(Request.Builder().url(url(ruta)).header("Authorization", "Bearer ${prefs.token}").build()).execute()
        r.use { resp ->
            if (!resp.isSuccessful) {
                val mensaje = runCatching { JSONObject(resp.body!!.string()).getString("error") }.getOrElse { "Error ${resp.code} del servidor" }
                throw ErrorApi(resp.code, mensaje)
            }
            destino.parentFile?.mkdirs()
            val temporal = File(destino.path + ".parcial")
            temporal.outputStream().use { s -> resp.body!!.byteStream().copyTo(s) }
            destino.delete()
            if (!temporal.renameTo(destino)) throw IOException("No se pudo guardar ${destino.name}")
        }
        destino
    }
}

/** org.json devuelve "null" como texto con optString: esto devuelve null de verdad. */
fun JSONObject.texto(clave: String): String? = if (isNull(clave)) null else optString(clave).takeIf { it.isNotEmpty() }
fun JSONObject.numero(clave: String): Double? = if (isNull(clave)) null else optDouble(clave).takeUnless { it.isNaN() }
fun JSONObject.objeto(clave: String): JSONObject? = if (isNull(clave)) null else optJSONObject(clave)
fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
