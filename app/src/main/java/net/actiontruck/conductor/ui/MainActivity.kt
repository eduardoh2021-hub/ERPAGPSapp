package net.actiontruck.conductor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val modelo: Modelo by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemaConductor {
                Surface(Modifier.fillMaxSize()) {
                    BackHandler(enabled = modelo.pila.size > 1) { modelo.atras() }
                    when (val p = modelo.actual) {
                        Pantalla.Acceso -> PantallaAcceso(modelo)
                        Pantalla.Principal -> PantallaPrincipal(modelo)
                        is Pantalla.Orden -> PantallaOrden(modelo, p)
                        is Pantalla.Evento -> PantallaEvento(modelo, p)
                        is Pantalla.Documento -> PantallaDocumento(modelo, p)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver a la app: lo que quedara en la cola sale ya y el viaje se refresca
        if (modelo.prefs.token != null) modelo.cargar()
    }
}
