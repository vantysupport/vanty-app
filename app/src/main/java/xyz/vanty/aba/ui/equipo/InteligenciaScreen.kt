package xyz.vanty.aba.ui.equipo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.vanty.aba.data.Backend
import xyz.vanty.aba.data.ErrorApi
import xyz.vanty.aba.data.Paciente
import xyz.vanty.aba.data.errorApi
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.chat.conFormato
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.CabeceraSub
import xyz.vanty.aba.ui.comp.Pintura
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L

private data class Analisis(val clave: String, val ruta: String, val titulo: String, val sub: String, val icono: ImageVector, val pintura: Brush, val extra: Map<String, String> = emptyMap())

/**
 * Hub de inteligencia (InteligenciaHubView): por paciente, predicción a 30 días, patrones y objetivos sugeridos.
 * Cada pestaña según el plan (intel_predicciones / intel_patrones / intel_objetivos). Usa los análisis IA del centro.
 */
@Composable
fun InteligenciaScreen(e: EstadoEquipo, app: Estado, vm: EquipoViewModel, pad: PaddingValues) {
    val alcance = rememberCoroutineScope()
    var paciente by remember { mutableStateOf<Paciente?>(null) }
    var cargando by remember { mutableStateOf<String?>(null) }
    var resultado by remember { mutableStateOf<Pair<String, JsonObject>?>(null) }
    val analisis = listOfNotNull(
        Analisis("pred", "/api/agente-prediccion", L("Predicción", "Prediction"), L("Avance a 30 días", "30-day outlook"), Icons.Rounded.Insights, Pintura.azul, mapOf("semanas" to "12"))
            .takeIf { app.on("intel_predicciones") },
        Analisis("pat", "/api/agente-patrones", L("Patrones", "Patterns"), L("Regresiones y estancamientos", "Regressions, plateaus"), Icons.Rounded.Psychology, Pintura.morado)
            .takeIf { app.on("intel_patrones") },
        Analisis("obj", "/api/agente-objetivos", L("Objetivos", "Goals"), L("Siguientes metas sugeridas", "Suggested next goals"), Icons.Rounded.TrackChanges, Pintura.naranja, mapOf("accion" to "generar"))
            .takeIf { app.on("intel_objetivos") },
    )

    fun correr(a: Analisis) {
        val p = paciente ?: return
        cargando = a.clave; resultado = null
        alcance.launch {
            try {
                val r = Backend.apiPost(a.ruta, buildJsonObject {
                    put("childId", p.id); put("childName", p.nombre); put("locale", Backend.idioma)
                    a.extra.forEach { (k, v) -> v.toIntOrNull()?.let { put(k, it) } ?: put(k, v) }
                })
                if (r.status.isSuccess()) resultado = a.titulo to r.body<JsonObject>()
                else when (val err = r.errorApi()) {
                    is ErrorApi.SinIA -> vm.aviso(L("La IA no está activada en el centro.", "AI isn't enabled for the center."))
                    is ErrorApi.Otro -> vm.aviso(if (err.codigo == 402) L("El centro ya usó los análisis de IA de este período.", "The center has used this period's AI analyses.") else err.texto ?: L("No se pudo analizar.", "Couldn't analyze."))
                    else -> vm.aviso(L("No se pudo analizar.", "Couldn't analyze."))
                }
            } catch (ex: Exception) {
                vm.aviso(L("Sin conexión.", "No connection."))
            } finally { cargando = null }
        }
    }

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { CabeceraSub(L("Inteligencia", "Intelligence")) { vm.irA(TabEquipo.Mas) } }
        item {
            Text(L("Elige un paciente", "Pick a patient"), style = MaterialTheme.typography.labelMedium, color = T.secundario)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                e.pacientes.forEach { p ->
                    val sel = p.id == paciente?.id
                    Text(p.nombre, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else T.secundario,
                        modifier = Modifier.presionable { paciente = p; resultado = null }.background(if (sel) T.acento else T.relleno, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                analisis.forEach { a ->
                    Column(
                        Modifier.weight(1f).height(118.dp).clip(RoundedCornerShape(22.dp)).background(if (paciente == null) Pintura.gris else a.pintura)
                            .presionable(habilitado = paciente != null && cargando == null) { correr(a) }.padding(12.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Icon(a.icono, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(a.titulo, style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(a.sub, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f), maxLines = 2)
                    }
                }
            }
        }
        when {
            cargando != null -> item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AriaFlotando(Aria.POSE_8, 130.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(L("ARIA está analizando las sesiones de ${paciente?.nombre}…", "ARIA is analyzing ${paciente?.nombre}'s sessions…"),
                        style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center)
                }
            }
            resultado != null -> item {
                Tarjeta(Modifier.aparecer(0)) {
                    Text(resultado!!.first, style = MaterialTheme.typography.titleLarge, color = T.texto)
                    Spacer(Modifier.height(8.dp))
                    VistaJson(resultado!!.second, 0)
                }
            }
            paciente == null -> item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AriaFlotando(Aria.SENTADA, 130.dp)
                    Text(L("Elige un paciente y un análisis.", "Pick a patient and an analysis."), style = MaterialTheme.typography.bodyMedium, color = T.secundario)
                }
            }
        }
    }
}

// Claves técnicas que no se muestran
private val OCULTAS = setOf("_debug", "tokens", "id", "child_id", "programa_id", "prog_ids_usados", "upsert_error", "timestamp", "criterio_nota", "accion")

private fun etiqueta(k: String) = k.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Muestra una respuesta JSON de la IA como texto legible: títulos, párrafos y tarjetas anidadas. */
@Composable
private fun VistaJson(el: JsonElement, nivel: Int) {
    when (el) {
        is JsonPrimitive -> if (el.content.isNotBlank() && el.content != "null") Text(conFormato(el.content), style = MaterialTheme.typography.bodyMedium, color = T.texto)
        is JsonArray -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            el.take(12).forEach { item ->
                if (item is JsonObject) Box(Modifier.fillMaxWidth().background(T.relleno, RoundedCornerShape(14.dp)).padding(12.dp)) { VistaJson(item, nivel + 1) }
                else Row { Text("• ", color = T.acento); VistaJson(item, nivel + 1) }
            }
        }
        is JsonObject -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            el.filter { (k, v) -> k !in OCULTAS && v !is JsonNull && !(v is JsonPrimitive && v.content.isBlank()) && nivel < 4 }.forEach { (k, v) ->
                if (v is JsonPrimitive) {
                    Column {
                        Text(etiqueta(k), style = MaterialTheme.typography.labelMedium, color = T.acento)
                        VistaJson(v, nivel + 1)
                    }
                } else {
                    Text(etiqueta(k), style = MaterialTheme.typography.titleSmall, color = T.texto, modifier = Modifier.padding(top = 4.dp))
                    VistaJson(v, nivel + 1)
                }
            }
        }
    }
}
