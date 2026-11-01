package net.actiontruck.conductor.datos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.IOException

/**
 * Vacía la cola local hacia el ERP: posiciones en lotes y eventos uno a uno. Lo usan el servicio del GPS (cada 30 s
 * en marcha, cada 5 min parado) y la app al volver a primer plano. Un 401/403 cierra la jornada y pide volver a entrar.
 */
class Sincronizador(private val api: Api, private val cola: ColaLocal, private val prefs: Preferencias) {
    data class Estado(val posicionesPendientes: Int = 0, val eventosPendientes: Int = 0, val ultimoEnvio: Long? = null,
                      val ultimoError: String? = null)

    private val mutex = Mutex()
    private val _estado = MutableStateFlow(Estado())
    val estado = _estado.asStateFlow()

    fun refrescarContadores() {
        _estado.value = _estado.value.copy(posicionesPendientes = cola.posicionesPendientes(), eventosPendientes = cola.eventos().size)
    }

    /** Devuelve false si la sesión ya no vale (hay que volver a entrar). */
    suspend fun sincronizar(): Boolean = mutex.withLock {
        if (prefs.token == null) return false
        try {
            // Posiciones: lotes de 100 (el servidor admite 500) hasta vaciar la cola
            while (true) {
                val lote = cola.posiciones(LOTE)
                if (lote.isEmpty()) break
                api.enviarPosiciones(JSONArray(lote))
                // Aceptadas, duplicadas y rechazadas (formato malo: no se arreglan reenviando) salen de la cola
                cola.borrarPosiciones(lote.map { it.getString("id") })
                if (lote.size < LOTE) break
            }
            for (e in cola.eventos()) {
                try {
                    api.enviarEvento(e.ordenId, e.cuerpo)
                    cola.borrarEvento(e.id)
                } catch (x: ErrorApi) {
                    if (x.estado == 401 || x.estado == 429 || x.estado >= 500) throw x
                    // 404/409/422: el ERP no lo admite; queda a la vista del conductor para que lo revise o lo descarte
                    cola.marcarErrorEvento(e.id, x.message ?: "Error ${x.estado}")
                }
            }
            _estado.value = Estado(cola.posicionesPendientes(), cola.eventos().size, System.currentTimeMillis(), null)
            true
        } catch (x: ErrorApi) {
            if (x.estado == 401 || x.estado == 403) {
                prefs.avisoSesion = x.message
                prefs.cerrarSesion()
                _estado.value = _estado.value.copy(ultimoError = x.message)
                return false
            }
            _estado.value = _estado.value.copy(ultimoError = x.message, posicionesPendientes = cola.posicionesPendientes(),
                eventosPendientes = cola.eventos().size)
            true
        } catch (x: IOException) {
            _estado.value = _estado.value.copy(ultimoError = "Sin conexión con el ERP: se enviará al recuperarla",
                posicionesPendientes = cola.posicionesPendientes(), eventosPendientes = cola.eventos().size)
            true
        }
    }

    companion object {
        const val LOTE = 100
    }
}
