package xyz.vanty.aba.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.vanty.aba.BuildConfig
import xyz.vanty.aba.data.Backend
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import java.time.LocalDate

/** Lo que devuelve vanty.xyz/api/app/version (se edita en /control → Plataforma → App de Android). */
private data class InfoVersion(val codigo: Int, val nombre: String, val notas: List<String>, val urlApk: String, val obligatoria: Boolean)

private suspend fun traerVersion(): InfoVersion? = runCatching {
    val o = Json.parseToJsonElement(Backend.http.get(BuildConfig.API_BASE_URL + "/api/app/version").bodyAsText()).jsonObject
    InfoVersion(
        codigo = o["version_code"]?.jsonPrimitive?.intOrNull ?: 0,
        nombre = o["version_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        notas = o["notas"]?.jsonPrimitive?.contentOrNull.orEmpty().lines().map { it.trim().removePrefix("•").removePrefix("-").trim() }.filter { it.isNotBlank() },
        urlApk = o["url_apk"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        obligatoria = o["obligatoria"]?.jsonPrimitive?.booleanOrNull ?: false,
    )
}.getOrNull()


private fun actualizar(ctx: Context, info: InfoVersion) {
    val destino = when {
        xyz.vanty.aba.util.instaladaDesdePlay(ctx) -> "market://details?id=${ctx.packageName}"
        info.urlApk.isNotBlank() -> info.urlApk
        else -> "https://play.google.com/store/apps/details?id=${ctx.packageName}"
    }
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destino)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

/**
 * Al abrir la app:
 *  • si hay una versión más nueva publicada → "¡Nueva versión!" con sus notas y el botón Actualizar,
 *  • si se acaba de actualizar → "Novedades" una sola vez.
 */
@Composable
fun AvisoVersion() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var info by remember { mutableStateOf<InfoVersion?>(null) }
    var modo by remember { mutableStateOf<String?>(null) } // "nueva" | "novedades"
    val actual = BuildConfig.VERSION_CODE

    LaunchedEffect(Unit) {
        val vista = prefs.versionVista
        if (vista == 0) prefs.versionVista = actual // instalación nueva: sin "Novedades"
        val i = traerVersion() ?: return@LaunchedEffect
        info = i
        val hoy = LocalDate.now().toString()
        modo = when {
            i.codigo > actual && (i.obligatoria || prefs.versionPospuesta != "${i.codigo}|$hoy") -> "nueva"
            vista in 1 until actual && i.codigo == actual && i.notas.isNotEmpty() -> "novedades"
            else -> null
        }
        if (modo != "nueva") prefs.versionVista = actual
    }

    val i = info ?: return
    val m = modo ?: return
    val nueva = m == "nueva"
    val cerrar = {
        if (nueva) prefs.versionPospuesta = "${i.codigo}|${LocalDate.now()}" else prefs.versionVista = actual
        modo = null
    }
    AlertDialog(
        onDismissRequest = { if (!(nueva && i.obligatoria)) cerrar() },
        properties = DialogProperties(dismissOnBackPress = !(nueva && i.obligatoria), dismissOnClickOutside = !(nueva && i.obligatoria)),
        icon = { AriaFlotando(if (nueva) Aria.CORRE else Aria.CELEBRA, 110.dp) },
        title = {
            Text(
                if (nueva) L("¡Nueva versión${i.nombre.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}!", "New version${i.nombre.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}!")
                else L("Novedades de la versión ${BuildConfig.VERSION_NAME}", "What's new in ${BuildConfig.VERSION_NAME}"),
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nueva) Text(
                    if (i.obligatoria) L("Para seguir usando Vanty necesitas actualizar la app.", "You need to update the app to keep using Vanty.")
                    else L("Actualiza para tener lo último de Vanty:", "Update to get the latest from Vanty:"),
                    color = T.secundario, style = MaterialTheme.typography.bodyMedium,
                )
                i.notas.take(8).forEach { n ->
                    Row {
                        Text("✦", color = T.acento, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(n, color = T.texto, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton({ if (nueva) { actualizar(ctx, i); if (!i.obligatoria) cerrar() } else cerrar() }) {
                Text(if (nueva) L("Actualizar", "Update") else L("¡Genial!", "Great!"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (nueva && !i.obligatoria) ({ TextButton(cerrar) { Text(L("Más tarde", "Later")) } }) else null,
    )
}
