package net.actiontruck.conductor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import net.actiontruck.conductor.datos.Api
import net.actiontruck.conductor.datos.ColaLocal
import net.actiontruck.conductor.datos.Preferencias
import net.actiontruck.conductor.datos.Sincronizador
import org.maplibre.android.MapLibre

/** Punto de entrada: preferencias cifradas, cola local (posiciones y eventos sin enviar), API y canal de la notificación del GPS. */
class AppConductor : Application() {
    lateinit var preferencias: Preferencias
    lateinit var cola: ColaLocal
    lateinit var api: Api
    lateinit var sincronizador: Sincronizador

    override fun onCreate() {
        super.onCreate()
        preferencias = Preferencias(this)
        cola = ColaLocal(this)
        api = Api(preferencias)
        sincronizador = Sincronizador(api, cola, preferencias)
        MapLibre.getInstance(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL_GPS, getString(R.string.canal_gps), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.canal_gps_desc)
            },
        )
    }

    companion object {
        const val CANAL_GPS = "gps"
    }
}
