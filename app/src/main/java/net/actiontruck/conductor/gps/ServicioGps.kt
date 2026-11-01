package net.actiontruck.conductor.gps

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.actiontruck.conductor.AppConductor
import net.actiontruck.conductor.R
import net.actiontruck.conductor.ui.MainActivity
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Servicio en primer plano (aviso fijo) mientras la jornada está iniciada: lee el GPS del móvil, guarda cada posición
 * en la cola local con su UUID y la envía al ERP (POST /posiciones) cada 30 s en marcha y cada 5 min parado,
 * como recomienda docs/api-movil.md. Sin cobertura acumula y envía al recuperarla.
 */
class ServicioGps : Service(), LocationListener {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var app: AppConductor
    private var bloqueo: PowerManager.WakeLock? = null
    private var ultimaGuardada: Location? = null
    private var ultimoEnvio = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = application as AppConductor
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACCION_PARAR || app.preferencias.token == null) {
            parar()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(this, NOTIFICACION, notificacion("Enviando la posición al ERP"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        if (!activo.value) {
            activo.value = true
            empezarGps()
            alcance.launch { bucleEnvio() }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun empezarGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            estadoGps.value = "Sin permiso de ubicación"
            return
        }
        bloqueo = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "conductor:gps").apply {
            setReferenceCounted(false); acquire()
        }
        val lm = getSystemService(LocationManager::class.java)
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVALO_GPS_MS, 0f, this, Looper.getMainLooper())
            estadoGps.value = "Buscando señal GPS…"
        } else {
            estadoGps.value = "El GPS del móvil está apagado: se usa la red (menos precisa)"
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, INTERVALO_GPS_MS * 2, 0f, this, Looper.getMainLooper())
        }
    }

    override fun onLocationChanged(l: Location) {
        // Con GPS reciente, las posiciones de la red (peores) se ignoran
        val previa = ultimaPosicion.value
        if (l.provider == LocationManager.NETWORK_PROVIDER && previa?.provider == LocationManager.GPS_PROVIDER &&
            l.time - previa.time < 60_000) return
        ultimaPosicion.value = l
        estadoGps.value = if (l.provider == LocationManager.GPS_PROVIDER) "GPS activo (±${l.accuracy.toInt()} m)" else "Posición por red (±${l.accuracy.toInt()} m)"
        val g = ultimaGuardada
        val parado = l.speed * 3.6f < 3f
        // En marcha se guarda cada lectura (15 s); parado, una cada 2 minutos o si se ha movido más de 50 m
        if (g != null && parado && l.time - g.time < 120_000 && l.distanceTo(g) < 50f) return
        ultimaGuardada = l
        val p = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("fecha", Instant.ofEpochMilli(l.time).toString())
            .put("lat", l.latitude).put("lon", l.longitude)
            .put("fuente", if (l.provider == LocationManager.GPS_PROVIDER) "gps" else "red")
        if (l.hasAccuracy()) p.put("precision_m", l.accuracy.toDouble())
        if (l.hasSpeed()) p.put("velocidad_kmh", (l.speed * 3.6).coerceIn(0.0, 500.0))
        if (l.hasBearing()) p.put("rumbo", l.bearing.toDouble().coerceIn(0.0, 360.0))
        if (l.hasAltitude()) p.put("altitud_m", l.altitude.coerceIn(-1000.0, 20000.0))
        bateria()?.let { p.put("bateria_pct", it) }
        alcance.launch {
            app.cola.guardarPosicion(p)
            app.sincronizador.refrescarContadores()
        }
    }

    private suspend fun bucleEnvio() {
        while (alcance.isActive && activo.value) {
            val enMarcha = (ultimaPosicion.value?.speed ?: 0f) * 3.6f >= 3f
            val espera = if (enMarcha) ENVIO_MARCHA_MS else ENVIO_PARADO_MS
            val pendientes = app.cola.posicionesPendientes()
            if (System.currentTimeMillis() - ultimoEnvio >= espera || pendientes >= 50 || app.cola.eventos().isNotEmpty()) {
                ultimoEnvio = System.currentTimeMillis()
                if (!app.sincronizador.sincronizar()) {
                    // Sesión caducada o dispositivo dado de baja: se para y se avisa al abrir la app
                    getSystemService(android.app.NotificationManager::class.java)
                        .notify(NOTIFICACION + 1, notificacion(app.preferencias.avisoSesion ?: "Vuelva a iniciar sesión", fija = false))
                    parar()
                    return
                }
            }
            delay(10_000)
        }
    }

    private fun bateria(): Int? =
        getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }

    private fun notificacion(texto: String, fija: Boolean = true): Notification {
        val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, AppConductor.CANAL_GPS)
            .setSmallIcon(R.drawable.ic_camion)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(texto)
            .setOngoing(fija)
            .setContentIntent(abrir)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun parar() {
        activo.value = false
        getSystemService(LocationManager::class.java).removeUpdates(this)
        bloqueo?.release()
        bloqueo = null
        estadoGps.value = "Jornada finalizada"
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        activo.value = false
        getSystemService(LocationManager::class.java).removeUpdates(this)
        bloqueo?.release()
        alcance.cancel()
        super.onDestroy()
    }

    @Deprecated("Solo para Android < 10")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) estadoGps.value = "El GPS del móvil se ha apagado"
    }

    companion object {
        private const val NOTIFICACION = 7
        private const val ACCION_PARAR = "parar"
        private const val INTERVALO_GPS_MS = 15_000L
        private const val ENVIO_MARCHA_MS = 30_000L
        private const val ENVIO_PARADO_MS = 300_000L

        private val activo = MutableStateFlow(false)
        val enMarcha = activo.asStateFlow()
        val ultimaPosicion = MutableStateFlow<Location?>(null)
        val estadoGps = MutableStateFlow("Jornada no iniciada")

        fun iniciar(c: Context) = ContextCompat.startForegroundService(c, Intent(c, ServicioGps::class.java))
        fun detener(c: Context) { c.startService(Intent(c, ServicioGps::class.java).setAction(ACCION_PARAR)) }
    }
}
