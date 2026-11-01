package net.actiontruck.conductor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.actiontruck.conductor.R
import net.actiontruck.conductor.marca.Marca

/** Tipografía corporativa (manual de marca): Yantramanav. */
val Yantramanav = FontFamily(
    Font(R.font.yantramanav_regular, FontWeight.Normal),
    Font(R.font.yantramanav_medium, FontWeight.Medium),
    Font(R.font.yantramanav_bold, FontWeight.Bold),
)

private val colores = lightColorScheme(
    primary = Marca.brandRojo, onPrimary = Marca.textoSobreRojo,
    secondary = Marca.brandAzul, onSecondary = Marca.textoSobreAzul,
    tertiary = Marca.estadoExito,
    background = Marca.fondoPrincipal, onBackground = Marca.textoCuerpo,
    surface = Marca.fondoPrincipal, onSurface = Marca.textoCuerpo,
    surfaceVariant = Marca.fondoSuave, onSurfaceVariant = Marca.textoCuerpoSecundario,
    surfaceContainer = Marca.fondoSuave, surfaceContainerLow = Marca.fondoPrincipal, surfaceContainerHigh = Marca.fondoSuave,
    outline = Marca.lineaBorde, outlineVariant = Marca.lineaBorde,
    error = Marca.estadoError, onError = Marca.textoSobreRojo,
)

private val base = Typography()
private fun TextStyle.marca(color: androidx.compose.ui.graphics.Color? = null) =
    copy(fontFamily = Yantramanav, color = color ?: this.color)

private val tipografia = Typography(
    headlineSmall = base.headlineSmall.marca(Marca.textoTitulo).copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.marca(Marca.textoTitulo).copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.marca(Marca.textoTitulo).copy(fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.marca(Marca.textoTitulo).copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.marca().copy(fontSize = 17.sp),
    bodyMedium = base.bodyMedium.marca().copy(fontSize = 15.sp),
    bodySmall = base.bodySmall.marca(),
    labelLarge = base.labelLarge.marca().copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    labelMedium = base.labelMedium.marca(),
    labelSmall = base.labelSmall.marca(),
)

@Composable
fun TemaConductor(contenido: @Composable () -> Unit) = MaterialTheme(colorScheme = colores, typography = tipografia, content = contenido)
