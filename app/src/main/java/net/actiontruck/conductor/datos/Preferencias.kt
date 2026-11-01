package net.actiontruck.conductor.datos

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.actiontruck.conductor.BuildConfig
import java.util.UUID

/**
 * Datos que sobreviven al cierre de la app, cifrados con una clave del almacén de claves de Android: URL del ERP,
 * token de la sesión (nunca la contraseña), identificador de instalación y si la jornada está iniciada.
 */
class Preferencias(contexto: Context) {
    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            contexto, "conductor",
            MasterKey.Builder(contexto).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Almacén de claves dañado (p. ej. tras restaurar el móvil): se empieza de cero y el conductor vuelve a entrar
        contexto.deleteSharedPreferences("conductor")
        EncryptedSharedPreferences.create(
            contexto, "conductor",
            MasterKey.Builder(contexto).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var urlServidor: String
        get() = prefs.getString("url", null) ?: BuildConfig.ERP_URL
        set(v) = prefs.edit().putString("url", v.trim().trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    var usuario: String
        get() = prefs.getString("usuario", "") ?: ""
        set(v) = prefs.edit().putString("usuario", v).apply()

    var conductor: String
        get() = prefs.getString("conductor", "") ?: ""
        set(v) = prefs.edit().putString("conductor", v).apply()

    var jornadaIniciada: Boolean
        get() = prefs.getBoolean("jornada", false)
        set(v) = prefs.edit().putBoolean("jornada", v).apply()

    /** Último aviso del servidor que obliga a volver a entrar (401) o a avisar a tráfico (403). */
    var avisoSesion: String?
        get() = prefs.getString("aviso_sesion", null)
        set(v) = prefs.edit().putString("aviso_sesion", v).apply()

    /** Generado al instalar y conservado: identifica el dispositivo en el ERP (maestros.dispositivo). */
    val idInstalacion: String
        get() = prefs.getString("id_instalacion", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id_instalacion", it).apply()
        }

    fun cerrarSesion() {
        prefs.edit().remove("token").remove("conductor").putBoolean("jornada", false).apply()
    }
}
