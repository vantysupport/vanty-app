package xyz.vanty.aba.ui.equipo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import xyz.vanty.aba.data.Backend
import xyz.vanty.aba.data.Objetivo
import xyz.vanty.aba.data.TextoFlexible
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.hoyIso

// Programas ABA del paciente para el equipo (ProgramasABAView de la web): estado, criterio,
// gráfica de progreso por sets y registro de sesiones (POST /api/programas-aba, action registrar_sesion).

@Serializable
data class SesionAba(
    val id: String,
    val fecha: String? = null,
    @SerialName("porcentaje_exito") val pct: Double? = null,
    val fase: String? = null,
    @Serializable(with = TextoFlexible::class) val set: String? = null,
)

@Serializable
data class ProgramaAba(
    val id: String,
    @Serializable(with = TextoFlexible::class) val titulo: String? = null,
    @Serializable(with = TextoFlexible::class) val area: String? = null,
    @Serializable(with = TextoFlexible::class) val descripcion: String? = null,
    @SerialName("fase_actual") val fase: String? = null,
    val estado: String? = null,
    @SerialName("criterio_dominio_pct") val criterio: Int? = null,
    @SerialName("criterio_sesiones_consecutivas") val criterioSesiones: Int? = null,
    @SerialName("objetivos_cp") val objetivos: List<Objetivo> = emptyList(),
    @SerialName("sesiones_datos_aba") val sesiones: List<SesionAba> = emptyList(),
) {
    val crit get() = criterio ?: 90
    val ordenadas get() = sesiones.sortedBy { it.fecha.orEmpty() }
    val archivado get() = estado == "archivado"

    /** Igual que la web: dominado a mano, o las últimas N sesiones (del set activo) ≥ criterio. */
    val criterioAlcanzado: Boolean get() {
        if (estado?.lowercase() in setOf("dominado", "logrado", "criterio_alcanzado")) return true
        val n = criterioSesiones ?: 2
        val s = ordenadas
        val ultimoSet = s.lastOrNull()?.set
        val delSet = if (ultimoSet != null) s.filter { it.set == ultimoSet } else s
        val ult = delSet.takeLast(n)
        return ult.size >= n && ult.all { (it.pct ?: 0.0) >= crit }
    }
}

@Serializable
private data class ProgramasAbaResp(val data: List<ProgramaAba> = emptyList())

object RepoAba {
    suspend fun programas(childId: String): List<ProgramaAba> =
        Backend.getOrNull<ProgramasAbaResp>("/api/programas-aba?child_id=${java.net.URLEncoder.encode(childId, "UTF-8")}")
            ?.data.orEmpty().filterNot { it.archivado }

    /** Registra una sesión; devuelve null si salió bien o el mensaje de error. */
    suspend fun registrarSesion(p: ProgramaAba, childId: String, fecha: String, fase: String, oportunidades: Int, correctas: Int, set: String?, notas: String): String? {
        val r = Backend.apiPost("/api/programas-aba", buildJsonObject {
            put("action", "registrar_sesion")
            putJsonObject("sesion") {
                put("programa_id", p.id); put("child_id", childId); put("fecha", fecha); put("fase", fase)
                put("oportunidades_totales", oportunidades); put("respuestas_correctas", correctas)
                put("respuestas_incorrectas", (oportunidades - correctas).coerceAtLeast(0))
                put("set", set?.trim()?.ifBlank { null }); put("notas", notas)
            }
        })
        if (r.status.isSuccess()) return null
        return runCatching { r.bodyAsText() }.getOrNull() ?: "error"
    }
}

fun nombreFase(f: String?) = when (f) {
    "linea_base" -> L("Línea base", "Baseline")
    "intervencion" -> L("Intervención", "Intervention")
    "mantenimiento" -> L("Mantenimiento", "Maintenance")
    "generalizacion" -> L("Generalización", "Generalization")
    "dominado" -> L("Dominado", "Mastered")
    else -> f?.replaceFirstChar { it.uppercase() } ?: L("Intervención", "Intervention")
}

@Composable
fun TarjetaProgramaAba(p: ProgramaAba, onRegistrar: () -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val s = p.ordenadas
    val ultima = s.lastOrNull()
    Tarjeta(Modifier.padding(bottom = 10.dp), onClick = { abierto = !abierto }) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(p.titulo ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.area?.let { Etiqueta(it, T.relleno, T.secundario) }
                    Etiqueta(nombreFase(p.fase), T.acentoSuave, T.acento)
                    if (p.criterioAlcanzado) Etiqueta("🏆 " + L("Criterio alcanzado", "Criterion met"), T.exito.copy(alpha = 0.14f), T.exito)
                }
            }
            Icon(Icons.Rounded.ExpandMore, null, tint = T.terciario, modifier = Modifier.size(24.dp))
        }
        p.descripcion?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = if (abierto) 12 else 2)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            listOfNotNull(
                L("${s.size} sesiones totales", "${s.size} total sessions"),
                ultima?.pct?.let { "${it.toInt()}%" + (ultima.set?.let { st -> L(" en $st", " in $st") } ?: "") },
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium, color = T.texto,
        )
        Text(L("Criterio: ${p.crit}%", "Criterion: ${p.crit}%"), style = MaterialTheme.typography.bodySmall, color = T.terciario)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.presionable(onClick = onRegistrar).background(T.acento, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(L("Sesión", "Session"), style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }
        }
        if (s.size >= 2) {
            Spacer(Modifier.height(12.dp))
            GraficaProgreso(s, p.crit, if (abierto) 200 else 90)
        }
        AnimatedVisibility(abierto) {
            Column(Modifier.padding(top = 12.dp)) {
                if (p.objetivos.isNotEmpty()) {
                    Text(L("Sets / objetivos", "Sets / targets"), style = MaterialTheme.typography.labelLarge, color = T.texto)
                    p.objetivos.sortedBy { it.set ?: 0 }.forEach { o ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(listOfNotNull(o.set?.let { "Set $it" }, o.nombre ?: o.descripcion).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = T.texto, modifier = Modifier.weight(1f))
                            if (o.estado == "dominado") Icon(Icons.Rounded.EmojiEvents, null, tint = T.exito, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (s.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(L("Últimas sesiones", "Latest sessions"), style = MaterialTheme.typography.labelLarge, color = T.texto)
                    s.takeLast(6).reversed().forEach { se ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(se.fecha?.take(10).orEmpty(), style = MaterialTheme.typography.bodySmall, color = T.secundario, modifier = Modifier.weight(1f))
                            Text(listOfNotNull(se.set, nombreFase(se.fase)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = T.terciario)
                            Spacer(Modifier.width(10.dp))
                            Text(se.pct?.let { "${it.toInt()}%" } ?: "—", style = MaterialTheme.typography.labelMedium,
                                color = if ((se.pct ?: 0.0) >= p.crit) T.exito else T.texto)
                        }
                    }
                }
            }
        }
    }
}

/** Gráfica de líneas 0–100 % con línea de criterio, separación por sets y puntos verdes al llegar al criterio. */
@Composable
fun GraficaProgreso(sesiones: List<SesionAba>, criterio: Int, alto: Int) {
    val linea = T.acento
    val ok = T.exito
    val guia = T.borde
    val critColor = T.exito.copy(alpha = 0.7f)
    Canvas(Modifier.fillMaxWidth().height(alto.dp).background(T.relleno.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(8.dp)) {
        val n = sesiones.size
        if (n < 2) return@Canvas
        val w = size.width; val h = size.height
        fun x(i: Int) = w * i / (n - 1).toFloat()
        fun y(p: Double) = h - (p.coerceIn(0.0, 100.0) / 100.0 * h).toFloat()
        // Guías al 0, 50 y 100 %
        listOf(0.0, 50.0, 100.0).forEach { g -> drawLine(guia, Offset(0f, y(g)), Offset(w, y(g)), 1f) }
        // Línea de criterio punteada
        drawLine(critColor, Offset(0f, y(criterio.toDouble())), Offset(w, y(criterio.toDouble())), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        // Separadores de set y un trazo por set (como la web)
        var inicio = 0
        for (i in 0..n) {
            val cambio = i == n || (i > 0 && sesiones[i].set != sesiones[i - 1].set)
            if (!cambio) continue
            if (i < n) drawLine(guia, Offset(x(i) - (x(1) - x(0)) / 2, 0f), Offset(x(i) - (x(1) - x(0)) / 2, h), 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val path = Path()
            for (j in inicio until i) {
                val px = x(j); val py = y(sesiones[j].pct ?: 0.0)
                if (j == inicio) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, linea, style = Stroke(width = 4f, cap = StrokeCap.Round))
            inicio = i
        }
        sesiones.forEachIndexed { i, s ->
            val p = s.pct ?: 0.0
            drawCircle(if (p >= criterio) ok else linea, radius = 6f, center = Offset(x(i), y(p)))
        }
    }
}

/** Registrar sesión (RegistrarSesionModal de la web). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaRegistrarSesion(p: ProgramaAba, childId: String, onCerrar: () -> Unit, onGuardado: () -> Unit) {
    val alcance = rememberCoroutineScope()
    var fecha by remember { mutableStateOf(hoyIso()) }
    var fase by remember { mutableStateOf(if (p.fase == "linea_base") "linea_base" else (p.fase ?: "intervencion")) }
    var oport by remember { mutableStateOf("") }
    var correctas by remember { mutableStateOf("") }
    var set by remember { mutableStateOf(p.ordenadas.lastOrNull()?.set.orEmpty()) }
    var notas by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val o = oport.toIntOrNull() ?: 0
    val c = correctas.toIntOrNull() ?: 0
    val pct = if (o > 0) c * 100.0 / o else null
    val sets = p.objetivos.mapNotNull { it.set }.distinct().sorted().map { "Set $it" }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(L("Registrar sesión", "Log session"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Text(p.titulo.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = T.secundario)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(fecha, { fecha = it }, label = { Text(L("Fecha (AAAA-MM-DD)", "Date (YYYY-MM-DD)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(L("Fase", "Phase"), style = MaterialTheme.typography.labelLarge, color = T.texto)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("linea_base", "intervencion", "mantenimiento", "generalizacion").forEach { f ->
                    Opcion(nombreFase(f), fase == f) { fase = f }
                }
            }
            if (sets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Set", style = MaterialTheme.typography.labelLarge, color = T.texto)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sets.forEach { st -> Opcion(st, set == st) { set = st } }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(oport, { oport = it.filter(Char::isDigit) }, label = { Text(L("Oportunidades", "Trials")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(correctas, { correctas = it.filter(Char::isDigit) }, label = { Text(L("Correctas", "Correct")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            pct?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${"%.1f".format(it)}%" + if (it >= p.crit) L(" · ¡alcanza el criterio de ${p.crit}%!", " · meets the ${p.crit}% criterion!") else "",
                    style = MaterialTheme.typography.titleMedium, color = if (it >= p.crit) T.exito else T.acento,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(notas, { notas = it }, label = { Text(L("Notas (opcional)", "Notes (optional)")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = T.peligro, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            BotonGrande(if (guardando) L("Guardando…", "Saving…") else L("GUARDAR SESIÓN", "SAVE SESSION"), {
                if (o <= 0) { error = L("Ingresa las oportunidades totales.", "Enter the total trials."); return@BotonGrande }
                if (c > o) { error = L("Las correctas no pueden ser más que las oportunidades.", "Correct cannot exceed trials."); return@BotonGrande }
                if (guardando) return@BotonGrande
                guardando = true; error = null
                alcance.launch {
                    val err = RepoAba.registrarSesion(p, childId, fecha.trim(), fase, o, c, set, notas)
                    guardando = false
                    if (err == null) onGuardado() else error = L("No se pudo guardar la sesión.", "Could not save the session.")
                }
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Opcion(texto: String, activa: Boolean, onClick: () -> Unit) {
    Text(
        texto, style = MaterialTheme.typography.labelLarge, color = if (activa) Color.White else T.texto,
        modifier = Modifier.presionable(onClick = onClick).background(if (activa) T.acento else T.relleno, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
