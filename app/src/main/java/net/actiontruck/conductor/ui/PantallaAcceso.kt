package net.actiontruck.conductor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.actiontruck.conductor.BuildConfig
import net.actiontruck.conductor.marca.Marca

@Composable
fun PantallaAcceso(m: Modelo) {
    var url by remember { mutableStateOf(m.prefs.urlServidor) }
    var usuario by remember { mutableStateOf(m.prefs.usuario) }
    var contrasena by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Action Truck", style = MaterialTheme.typography.headlineSmall)
        Text("App del conductor", style = MaterialTheme.typography.titleMedium, color = Marca.textoCuerpoSecundario)
        HorizontalDivider(thickness = 2.dp, color = Marca.lineaSeparador)
        m.prefs.avisoSesion?.let { Text(it, color = Marca.estadoError) }
        OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        OutlinedTextField(contrasena, { contrasena = it }, label = { Text("Contraseña") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
        OutlinedTextField(url, { url = it }, label = { Text("Servidor del ERP") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = { Text("Lo indica tráfico. Ejemplo: https://erp.actiontruck.net") })
        m.mensaje?.let { Text(it, color = Marca.estadoError) }
        Button(
            onClick = { m.entrar(url, usuario, contrasena) { contrasena = "" } },
            enabled = !m.cargando && usuario.isNotBlank() && contrasena.isNotEmpty() && url.startsWith("http"),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (m.cargando) CircularProgressIndicator(Modifier.height(24.dp), color = Marca.textoSobreRojo) else Text("Entrar")
        }
        Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Marca.textoCuerpoSecundario)
    }
}
