package xyz.vanty.aba.ui.comp

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.vanty.aba.R
import xyz.vanty.aba.data.Backend
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L

/**
 * Vincular Google Calendar y Outlook, igual que la web (/api/google-calendar y /api/microsoft-calendar):
 *  • Agenda de jefe/admin/secretaría (con "sincronizar todo"), Agenda y Mi perfil del especialista,
 *    Mis citas y Perfil de la familia.
 * El permiso se da en el navegador; la web devuelve a la app con vantyaba://calendario?gcal=connected.
 */
object Calendarios {
    data class Proveedor(val api: String, val param: String, val nombre: String, val icono: Int)

    val GOOGLE = Proveedor("google-calendar", "gcal", "Google Calendar", R.drawable.ic_google)
    val OUTLOOK = Proveedor("microsoft-calendar", "mscal", "Outlook", R.drawable.ic_microsoft)

    private val _vuelta = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Mensaje para mostrar cuando el navegador vuelve a la app (conectado / error). */
    val vuelta = _vuelta.asSharedFlow()

    /** Lo llama MainActivity con vantyaba://calendario?... */
    fun volvio(uri: android.net.Uri) {
        val msg = listOf(GOOGLE, OUTLOOK).firstNotNullOfOrNull { p ->
            when (uri.getQueryParameter(p.param)) {
                "connected" -> L("${p.nombre} conectado ✓", "${p.nombre} connected ✓")
                "error" -> L("No se pudo conectar ${p.nombre}", "Couldn't connect ${p.nombre}")
                else -> null
            }
        } ?: return
        _vuelta.tryEmit(msg)
    }

    data class Estado(val conectado: Boolean, val email: String?)

    suspend fun estado(p: Proveedor, yo: String): Estado? =
        Backend.getOrNull<JsonObject>("/api/${p.api}?action=status&userId=$yo")?.let {
            Estado((it["connected"] as? JsonPrimitive)?.content == "true", (it["email"] as? JsonPrimitive)?.content?.takeIf { e -> e != "null" })
        }

    /** `rol` como en la web (padre, especialista, secretaria, admin) + "_app" para volver a la app. */
    suspend fun urlPermiso(p: Proveedor, yo: String, rol: String): String? =
        Backend.getOrNull<JsonObject>("/api/${p.api}?action=auth-url&userId=$yo&role=${rol}_app")
            ?.get("url")?.let { (it as? JsonPrimitive)?.content }

    suspend fun desconectar(p: Proveedor, yo: String) = runCatching { Backend.apiGet("/api/${p.api}?action=disconnect&userId=$yo") }

    /** "Sincronizar" de la agenda web: lleva todas las citas al calendario conectado. Devuelve cuántas. */
    suspend fun sincronizarTodo(p: Proveedor, yo: String): Int? = runCatching {
        val r = Backend.apiPost("/api/${p.api}", buildJsonObject { put("action", "sync-all"); put("userId", yo) })
        val j = Backend.json.parseToJsonElement(r.bodyAsText()) as JsonObject
        if ((j["ok"] as? JsonPrimitive)?.content == "true") (j["synced"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 else null
    }.getOrNull()
}

/**
 * Tarjeta "Recíbelas en tu calendario" / "Calendarios vinculados": una fila por proveedor con
 * conectar, quitar y (si [sincronizar]) el botón de sincronizar todas las citas.
 */
@Composable
fun VincularCalendarios(
    yo: String?, rol: String, titulo: String, sub: String?, aviso: (String) -> Unit,
    modifier: Modifier = Modifier, sincronizar: Boolean = false,
) {
    yo ?: return
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var estados by remember { mutableStateOf<Map<String, Calendarios.Estado?>>(emptyMap()) }
    var ocupado by remember { mutableStateOf<String?>(null) }
    var quitar by remember { mutableStateOf<Calendarios.Proveedor?>(null) }
    var refresco by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresco) {
        estados = listOf(Calendarios.GOOGLE, Calendarios.OUTLOOK).associate { it.api to Calendarios.estado(it, yo) }
    }
    LaunchedEffect(Unit) { Calendarios.vuelta.collect { aviso(it); refresco++ } }
    // Al volver del navegador (aunque no llegue el enlace) se vuelve a consultar
    val vida = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(vida) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { ocupado = null; refresco++ } }
        vida.lifecycle.addObserver(obs); onDispose { vida.lifecycle.removeObserver(obs) }
    }

    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoTono(Icons.Rounded.EventRepeat, T.acentoSuave, T.acento, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(titulo, style = MaterialTheme.typography.titleMedium, color = T.texto)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = T.secundario)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Calendarios.GOOGLE, Calendarios.OUTLOOK).forEach { p ->
                val st = estados[p.api]
                AnimatedContent(st?.conectado, label = p.api) { conectado ->
                    if (conectado == true) Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(T.acentoSuave).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(34.dp).background(T.tarjeta, CircleShape), contentAlignment = Alignment.Center) { Image(painterResource(p.icono), null, Modifier.size(18.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = T.acento, modifier = Modifier.size(14.dp))
                                Text(" " + L("Conectado", "Connected"), style = MaterialTheme.typography.labelLarge, color = T.acento)
                            }
                            Text(st?.email ?: p.nombre, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 1)
                        }
                        if (sincronizar) {
                            if (ocupado == p.api + "sync") CircularProgressIndicator(Modifier.size(20.dp), color = T.acento, strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Sync, L("Sincronizar", "Sync"), tint = T.acento, modifier = Modifier.size(34.dp).clip(CircleShape).presionable {
                                ocupado = p.api + "sync"
                                alcance.launch {
                                    val n = Calendarios.sincronizarTodo(p, yo)
                                    ocupado = null
                                    aviso(if (n != null) L("$n citas sincronizadas con ${p.nombre}", "$n appointments synced with ${p.nombre}") else L("Error al sincronizar", "Sync error"))
                                }
                            }.padding(7.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(L("Quitar", "Remove"), style = MaterialTheme.typography.labelLarge, color = T.peligro,
                            modifier = Modifier.clip(CircleShape).presionable { quitar = p }.padding(horizontal = 10.dp, vertical = 6.dp))
                    } else Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(T.tarjeta).border(1.5.dp, T.borde, CircleShape)
                            .presionable(st != null && ocupado == null) {
                                ocupado = p.api
                                alcance.launch {
                                    val url = Calendarios.urlPermiso(p, yo, rol)
                                    if (url != null) abrirEnlace(ctx, url)
                                    else { ocupado = null; aviso(L("No se pudo iniciar la conexión", "Couldn't start the connection")) }
                                }
                            },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (ocupado == p.api || st == null) CircularProgressIndicator(Modifier.size(18.dp), color = T.acento, strokeWidth = 2.dp)
                        else Image(painterResource(p.icono), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(L("Conectar ${p.nombre}", "Connect ${p.nombre}"), style = MaterialTheme.typography.titleSmall, color = T.texto)
                    }
                }
            }
        }
    }
    quitar?.let { p ->
        Confirmar(L("¿Desconectar ${p.nombre}?", "Disconnect ${p.nombre}?"), L("Las citas dejarán de llegar a ese calendario.", "Appointments will stop reaching that calendar."),
            L("Desconectar", "Disconnect"), onSi = {
                quitar = null
                alcance.launch { Calendarios.desconectar(p, yo); aviso(L("${p.nombre} desconectado", "${p.nombre} disconnected")); refresco++ }
            }, onNo = { quitar = null })
    }
}
