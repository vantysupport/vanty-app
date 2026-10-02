package xyz.vanty.aba.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.data.Actividad
import xyz.vanty.aba.data.Programa
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.IconoTono
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.Vacio
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.MarcaDegradado
import xyz.vanty.aba.ui.theme.Naranja
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.hoyIso
import xyz.vanty.aba.util.letraDia
import xyz.vanty.aba.util.semanaActual

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticaScreen(e: Estado, vm: AppViewModel, pad: PaddingValues) {
    var seccion by rememberSaveable { mutableStateOf(0) }
    PullToRefreshBox(e.refrescando, vm::refrescar, Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item { xyz.vanty.aba.ui.comp.CabeceraSub(L("Practicar en casa", "Practice at home")) { vm.irA(xyz.vanty.aba.ui.Pestana.Mas) } }
            item { Segmentos(seccion) { seccion = it } }
            if (seccion == 0) planSemanal(e, vm) else programas(e, vm)
        }
    }
}

@Composable
private fun Segmentos(sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(T.relleno, RoundedCornerShape(16.dp)).padding(4.dp)) {
        listOf(L("Plan de la semana", "Weekly plan"), L("Programas ABA", "ABA programs")).forEachIndexed { i, t ->
            val activo = i == sel
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (activo) T.tarjeta else Color.Transparent)
                    .presionable { onSel(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t, style = MaterialTheme.typography.titleSmall, color = if (activo) T.acento else T.secundario)
            }
        }
    }
}

// ── Plan semanal (engagement) ───────────────────────────────────────────────
private fun androidx.compose.foundation.lazy.LazyListScope.planSemanal(e: Estado, vm: AppViewModel) {
    val plan = e.plan
    when {
        e.generandoPlan -> item {
            Tarjeta {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AriaFlotando(Aria.POSE_8, 130.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(L("ARIA está preparando el plan…", "ARIA is preparing the plan…"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(0.7f).clip(CircleShape), color = T.acento, trackColor = T.relleno)
                }
            }
        }
        plan == null || plan.actividades.isEmpty() -> item {
            Tarjeta {
                Vacio(
                    Aria.SENTADA,
                    if (e.cargandoDatos) L("Cargando…", "Loading…") else L("Aún no hay plan esta semana", "No plan this week yet"),
                    L("ARIA crea actividades cortas para casa según lo que ${e.hijo?.primerNombre ?: "tu peque"} trabaja en terapia.",
                        "ARIA creates short home activities based on what ${e.hijo?.primerNombre ?: "your child"} works on in therapy."),
                    if (e.cargandoDatos) null else L("CREAR PLAN CON ARIA", "CREATE PLAN WITH ARIA"),
                    vm::generarPlan,
                )
            }
        }
        else -> {
            val hechas = plan.actividades.count { it.completada }
            item { CabeceraPlan(plan.mensaje, hechas, plan.actividades.size) }
            itemsIndexed(plan.actividades, key = { i, a -> "$i-${a.titulo}" }) { i, a ->
                TarjetaActividad(a, Modifier.aparecer(i)) { vm.alternarActividad(i) }
            }
        }
    }
}

@Composable
private fun CabeceraPlan(mensaje: String?, hechas: Int, total: Int) {
    val p by animateFloatAsState(if (total == 0) 0f else hechas / total.toFloat(), tween(700, easing = FastOutSlowInEasing), label = "plan")
    Box(Modifier.fillMaxWidth().background(MarcaDegradado, RoundedCornerShape(24.dp)).padding(18.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("Tu plan de la semana", "Your weekly plan"), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    if (!mensaje.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(mensaje, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), maxLines = 3)
                    }
                }
                AriaFlotando(if (hechas == total && total > 0) Aria.CELEBRA else Aria.POSE_2, 86.dp)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { p }, modifier = Modifier.weight(1f).height(12.dp).clip(CircleShape),
                    color = Color(0xFFFFC53D), trackColor = Color.White.copy(alpha = 0.25f), strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(10.dp))
                Text("$hechas/$total", style = MaterialTheme.typography.titleSmall, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaActividad(a: Actividad, modifier: Modifier, onToggle: () -> Unit) {
    var abierta by rememberSaveable(a.titulo) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val (icono, tono) = areaVisual(a.area)
    Tarjeta(modifier, onClick = { abierta = !abierta }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Check(a.completada) {
                haptic.performHapticFeedback(if (a.completada) HapticFeedbackType.ContextClick else HapticFeedbackType.Confirm)
                onToggle()
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.titulo ?: L("Actividad", "Activity"), style = MaterialTheme.typography.titleMedium,
                    color = if (a.completada) T.terciario else T.texto,
                    textDecoration = if (a.completada) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    a.area?.let { Etiqueta(nombreArea(it), tono.copy(alpha = 0.14f), tono) }
                    a.minutos?.let { Etiqueta("⏱ $it min", T.relleno, T.secundario) }
                    a.dificultad?.let { Etiqueta(dificultad(it), T.relleno, T.secundario) }
                }
            }
            Spacer(Modifier.width(6.dp))
            IconoTono(icono, tono.copy(alpha = 0.14f), tono, 36.dp)
        }
        AnimatedVisibility(abierta, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                a.descripcion?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = T.secundario) }
                a.materiales?.let { Bloque(L("Materiales", "Materials"), it) }
                a.porQue?.let { Bloque(L("Por qué importa", "Why it matters"), it) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            val giro by animateFloatAsState(if (abierta) 180f else 0f, label = "giro")
            Icon(Icons.Rounded.ExpandMore, null, tint = T.terciario, modifier = Modifier.graphicsLayer { rotationZ = giro })
        }
    }
}

/** Casilla redonda grande que "rebota" al marcarse. */
@Composable
private fun Check(hecho: Boolean, onClick: () -> Unit) {
    val escala by animateFloatAsState(if (hecho) 1f else 0.9f, spring(0.35f, 500f), label = "chk")
    val fondo by animateColorAsState(if (hecho) T.exito else Color.Transparent, label = "chkc")
    Box(
        Modifier.size(38.dp).graphicsLayer { scaleX = escala; scaleY = escala }
            .background(fondo, CircleShape)
            .border(2.5.dp, if (hecho) T.exito else T.borde, CircleShape)
            .presionable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(hecho, enter = scaleIn(spring(0.4f, 600f)), exit = fadeOut()) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Bloque(titulo: String, texto: String) {
    Column(Modifier.fillMaxWidth().background(T.relleno, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = T.acento)
        Spacer(Modifier.height(4.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = T.texto)
    }
}

// ── Programas ABA con registro de práctica semanal ──────────────────────────
private fun androidx.compose.foundation.lazy.LazyListScope.programas(e: Estado, vm: AppViewModel) {
    val activos = e.programas.filter { !it.dominado && !it.archivado }
    val dominados = e.programas.count { it.dominado }
    item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(L("${activos.size} en curso", "${activos.size} in progress"), style = MaterialTheme.typography.titleSmall, color = T.texto)
            if (dominados > 0) {
                Spacer(Modifier.width(8.dp))
                Etiqueta(L("⭐ $dominados dominados", "⭐ $dominados mastered"), T.exito.copy(alpha = 0.14f), T.exito)
            }
        }
    }
    if (activos.isEmpty()) {
        item {
            Tarjeta {
                Vacio(Aria.NEUTRAL,
                    if (e.cargandoDatos) L("Cargando…", "Loading…") else L("Sin programas activos", "No active programs"),
                    L("Cuando el equipo asigne programas, aquí verás cómo practicarlos en casa.", "When the team assigns programs, you'll see how to practice them at home here."))
            }
        }
    }
    itemsIndexed(activos, key = { _, p -> p.id }) { i, p ->
        TarjetaPrograma(p, e.practicados, Modifier.aparecer(i)) { fecha -> vm.alternarPractica(p.id, fecha) }
    }
}

@Composable
private fun TarjetaPrograma(p: Programa, practicados: Set<Pair<String, String>>, modifier: Modifier, onDia: (String) -> Unit) {
    var abierta by rememberSaveable(p.id) { mutableStateOf(false) }
    val (icono, tono) = areaVisual(p.area)
    val hoy = hoyIso()
    val semana = semanaActual()
    val hechos = semana.count { (p.id to it.toString()) in practicados }
    val haptic = LocalHapticFeedback.current

    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.presionable { abierta = !abierta }) {
            IconoTono(icono, tono.copy(alpha = 0.14f), tono, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.titulo ?: "—", style = MaterialTheme.typography.titleMedium, color = T.texto)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    p.area?.let { Etiqueta(it, tono.copy(alpha = 0.14f), tono) }
                    fase(p.fase)?.let { Etiqueta(it, T.acentoSuave, T.acento) }
                }
            }
            val giro by animateFloatAsState(if (abierta) 180f else 0f, label = "g")
            Icon(Icons.Rounded.ExpandMore, null, tint = T.terciario, modifier = Modifier.graphicsLayer { rotationZ = giro })
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(L("Práctica de esta semana", "Practice this week"), style = MaterialTheme.typography.labelMedium, color = T.texto, modifier = Modifier.weight(1f))
            Etiqueta("$hechos/7", T.exito.copy(alpha = 0.14f), T.exito)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            semana.forEach { d ->
                val f = d.toString()
                val hecho = (p.id to f) in practicados
                val esHoy = f == hoy
                val pasado = f <= hoy
                val escala by animateFloatAsState(if (hecho) 1f else 0.88f, spring(0.35f, 500f), label = f)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (hecho) T.exito.copy(alpha = 0.14f) else if (esHoy) T.acentoSuave else T.relleno)
                        .graphicsLayer { alpha = if (pasado) 1f else 0.4f }
                        .presionable(habilitado = pasado) {
                            haptic.performHapticFeedback(if (hecho) HapticFeedbackType.ContextClick else HapticFeedbackType.Confirm)
                            onDia(f)
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(letraDia(d), style = MaterialTheme.typography.labelSmall, color = if (esHoy) T.acento else T.secundario)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(24.dp).graphicsLayer { scaleX = escala; scaleY = escala }
                            .background(if (hecho) T.exito else Color.Transparent, CircleShape)
                            .border(2.dp, if (hecho) T.exito else if (esHoy) T.acento else T.borde, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (hecho) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        AnimatedVisibility(abierta, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                p.descripcion?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = T.secundario) }
                p.instrucciones?.let { Bloque(L("Cómo practicar en casa", "How to practice at home"), it) }
                p.materiales?.let { Bloque(L("Materiales", "Materials"), it) }
                p.reforzadores?.let { Bloque(L("Premios que funcionan", "Rewards that work"), it) }
                val objetivos = p.objetivos.filter { it.estado != "archivado" }
                if (objetivos.isNotEmpty()) {
                    Text(L("Objetivos", "Goals"), style = MaterialTheme.typography.labelMedium, color = T.acento)
                    objetivos.forEach { o ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val listo = o.estado == "dominado"
                            Icon(if (listo) Icons.Rounded.Star else Icons.Rounded.TrackChanges, null, tint = if (listo) T.aviso else T.terciario, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(o.nombre ?: o.descripcion ?: "—", style = MaterialTheme.typography.bodyMedium, color = T.texto)
                        }
                    }
                }
            }
        }
    }
}

// ── Etiquetas ───────────────────────────────────────────────────────────────
@Composable
private fun areaVisual(area: String?): Pair<ImageVector, Color> {
    val a = area.orEmpty().lowercase()
    return when {
        Regex("comunic|lenguaje|verbal|habla|mand").containsMatchIn(a) -> Icons.Rounded.RecordVoiceOver to T.acento
        Regex("conduct|cooperaci").containsMatchIn(a) -> Icons.Rounded.Bolt to T.aviso
        Regex("imitaci|motor").containsMatchIn(a) -> Icons.AutoMirrored.Rounded.DirectionsRun to T.exito
        Regex("social|juego").containsMatchIn(a) -> Icons.Rounded.Groups to T.exito
        Regex("autonom|vida diaria").containsMatchIn(a) -> Icons.Rounded.Star to T.aviso
        Regex("cognit|habilidad|visual|desempe").containsMatchIn(a) -> Icons.Rounded.Psychology to T.acento
        else -> Icons.Rounded.AutoAwesome to Naranja
    }
}

private fun nombreArea(a: String) = when (a.lowercase()) {
    "comunicacion" -> L("Comunicación", "Communication")
    "conducta" -> L("Conducta", "Behavior")
    "habilidades" -> L("Habilidades", "Skills")
    "socializacion" -> L("Socialización", "Social")
    "autonomia" -> L("Autonomía", "Independence")
    else -> a.replaceFirstChar { it.uppercase() }
}

private fun dificultad(d: String) = when (d) {
    "facil" -> L("Fácil", "Easy")
    "media" -> L("Media", "Medium")
    "alta" -> L("Difícil", "Hard")
    else -> d
}

private fun fase(f: String?) = when (f) {
    "linea_base" -> L("Línea base", "Baseline")
    "intervencion" -> L("En intervención", "In intervention")
    "mantenimiento" -> L("Mantenimiento", "Maintenance")
    "dominado" -> L("Dominado", "Mastered")
    else -> null
}
