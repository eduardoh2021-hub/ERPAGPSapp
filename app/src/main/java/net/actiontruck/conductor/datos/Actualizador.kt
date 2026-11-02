package net.actiontruck.conductor.datos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.actiontruck.conductor.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * «Buscar actualizaciones»: consulta la última release publicada en el repositorio de la app en GitHub
 * (BuildConfig.REPO_ACTUALIZACIONES, público: no hace falta ninguna clave), descarga el APK de la arquitectura del
 * móvil y abre el instalador de Android. Android solo instala la actualización si va firmada con la misma clave que
 * la versión instalada, así que un APK ajeno no puede sustituir a la app.
 */
class Actualizador(private val contexto: Context) {
    data class Version(val etiqueta: String, val nombre: String, val notas: String, val publicada: String?, val url: String, val fichero: String, val bytes: Long)

    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()

    /** La release más reciente si es más nueva que la instalada; null si ya está al día. */
    suspend fun buscar(): Version? = withContext(Dispatchers.IO) {
        val peticion = Request.Builder().url("https://api.github.com/repos/${BuildConfig.REPO_ACTUALIZACIONES}/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "ActionTruckConductor/${BuildConfig.VERSION_NAME}").build()
        val r = http.newCall(peticion).execute().use { resp ->
            if (resp.code == 404) return@withContext null          // todavía no hay ninguna release publicada
            if (!resp.isSuccessful) throw IOException("GitHub respondió ${resp.code}")
            JSONObject(resp.body!!.string())
        }
        val etiqueta = r.getString("tag_name")
        if (comparar(etiqueta, BuildConfig.VERSION_NAME) <= 0) return@withContext null
        val apks = r.getJSONArray("assets").objetos().filter { it.getString("name").endsWith(".apk") }
        // El de la arquitectura del móvil; si no está, el universal
        val elegido = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.getString("name").contains(abi) } }
            ?: apks.firstOrNull { it.getString("name").contains("universal") }
            ?: throw IOException("La versión $etiqueta no trae ningún APK para este móvil")
        Version(etiqueta, r.texto("name") ?: etiqueta, r.texto("body") ?: "", r.texto("published_at"),
            elegido.getString("browser_download_url"), elegido.getString("name"), elegido.optLong("size"))
    }

    /** Descarga el APK a la caché, informando del progreso (0..1). */
    suspend fun descargar(v: Version, progreso: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(contexto.cacheDir, "actualizaciones").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val destino = File(dir, v.fichero)
        http.newCall(Request.Builder().url(v.url).header("User-Agent", "ActionTruckConductor").build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("No se pudo descargar (${resp.code})")
            val cuerpo = resp.body!!
            val total = cuerpo.contentLength().takeIf { it > 0 } ?: v.bytes
            cuerpo.byteStream().use { entrada ->
                destino.outputStream().use { salida ->
                    val buf = ByteArray(64 * 1024)
                    var leidos = 0L
                    while (true) {
                        val n = entrada.read(buf)
                        if (n < 0) break
                        salida.write(buf, 0, n)
                        leidos += n
                        if (total > 0) progreso((leidos.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        destino
    }

    /** ¿Puede la app abrir el instalador? Si no, hay que permitir «Instalar apps desconocidas» para ella. */
    fun puedeInstalar() = contexto.packageManager.canRequestPackageInstalls()

    fun abrirPermisoInstalar() {
        contexto.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${contexto.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun instalar(apk: File) {
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", apk)
        contexto.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        /** Compara «v1.2.10» con «1.2.9» por números: >0 si a es más nueva. */
        fun comparar(a: String, b: String): Int {
            val na = a.trimStart('v', 'V').split('.', '-').mapNotNull { it.toIntOrNull() }
            val nb = b.trimStart('v', 'V').split('.', '-').mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(na.size, nb.size)) {
                val d = (na.getOrNull(i) ?: 0) - (nb.getOrNull(i) ?: 0)
                if (d != 0) return d
            }
            return 0
        }
    }
}
