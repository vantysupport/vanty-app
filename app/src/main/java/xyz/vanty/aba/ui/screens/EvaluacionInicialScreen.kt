package xyz.vanty.aba.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import xyz.vanty.aba.data.Backend
import xyz.vanty.aba.data.Evaluacion
import xyz.vanty.aba.data.EvaluacionInicial
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Terapia
import xyz.vanty.aba.data.comoFormulario
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.Pestana
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.CabeceraSub
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.monedaFmt

private val MAPA = MapSerializer(String.serializer(), JsonElement.serializer())

/**
 * Evaluación inicial (EvaluacionInicialView): cada fase con su pantalla.
 * pendiente_intake → ficha · analizando → espera · recomendado → aceptar · confirmado → anamnesis ·
 * anamnesis_completa → elegir terapias · terapia_seleccionada / revisado / completado → listo.
 */
@Composable
fun EvaluacionInicialScreen(e: Estado, vm: AppViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val hijo = e.hijo
    val perfil = e.perfil
    if (hijo == null || perfil == null) { SinHijo(); return }
    val alcance = rememberCoroutineScope()
    var ev by remember { mutableStateOf<Evaluacion?>(null) }
    var cargado by remember { mutableStateOf(false) }
    var leccion by remember { mutableStateOf<String?>(null) } // "intake" | "anamnesis"
    var trabajando by remember { mutableStateOf(false) }
    var terapias by remember { mutableStateOf<List<Terapia>>(emptyList()) }
    var elegidas by remember { mutableStateOf(setOf<String>()) }
    var mensaje by remember { mutableStateOf("") }

    suspend fun cargar() { ev = runCatching { EvaluacionInicial.obtener(hijo.id, perfil.id) }.getOrNull(); cargado = true }
    LaunchedEffect(hijo.id) { cargar() }
    val estado = ev?.estado ?: "pendiente_intake"
    // Mientras la IA analiza, se vuelve a consultar cada pocos segundos
    LaunchedEffect(estado) {
        if (estado == "analizando") while (true) { delay(5000); cargar(); if (ev?.estado != "analizando") break }
        if (estado == "anamnesis_completa") {
            terapias = EvaluacionInicial.terapias()
            if (ev?.terapiasRecomendadas.isNullOrEmpty()) { trabajando = true; ev?.id?.let { EvaluacionInicial.recomendarTerapias(it) }; cargar(); trabajando = false }
            elegidas = ev?.terapiasRecomendadas.orEmpty().toSet()
        }
    }
    val secciones = remember(xyz.vanty.aba.util.Idioma.actual) { EvaluacionInicial.secciones(ctx) }
    val claveFicha = "intake_${hijo.id}"
    val claveAnam = "anamnesis_${hijo.id}"
    fun leerBorrador(c: String) = prefs.borrador(c)?.let { runCatching { Backend.json.decodeFromString(MAPA, it) }.getOrNull() }.orEmpty()

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        item { CabeceraSub(L("Evaluación inicial", "Initial evaluation")) { vm.irA(Pestana.Mas) } }
        item { Pasos(estado) }
        if (!cargado) item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = T.acento, trackColor = T.relleno) }
        else item {
            when (estado) {
                "pendiente_intake" -> Fase(Aria.SALUDO, L("Cuéntanos sobre ${hijo.primerNombre}", "Tell us about ${hijo.primerNombre}"),
                    L("Una ficha corta para conocer a tu familia. Toma unos 10 minutos y puedes pausar cuando quieras.", "A short form to get to know your family. About 10 minutes, and you can pause anytime."),
                    L("EMPEZAR", "START")) { leccion = "intake" }
                "analizando" -> Fase(Aria.POSE_8, L("Estamos revisando tu información", "We're reviewing your information"),
                    L("ARIA y el equipo están analizando la ficha. Esto toma un momento. 💙", "ARIA and the team are analyzing the form. This takes a moment. 💙"), null) {}
                "recomendado" -> Tarjeta {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { AriaFlotando(Aria.CELEBRA, 120.dp) }
                    Text(L("Nuestra recomendación", "Our recommendation"), style = MaterialTheme.typography.titleLarge, color = T.texto)
                    Spacer(Modifier.height(6.dp))
                    Text(xyz.vanty.aba.ui.chat.conFormato(ev?.mensaje ?: ev?.resumen ?: L("Revisamos tu ficha y te recomendamos continuar con una evaluación.", "We reviewed your form and recommend continuing with an evaluation.")),
                        style = MaterialTheme.typography.bodyMedium, color = T.secundario)
                    Spacer(Modifier.height(16.dp))
                    BotonGrande(L("ESTOY DE ACUERDO, CONTINUAR", "I AGREE, CONTINUE"), {
                        trabajando = true
                        alcance.launch { ev?.id?.let { EvaluacionInicial.confirmar(it, true, null) }; cargar(); trabajando = false }
                    }, Modifier.fillMaxWidth(), color = T.exito, labio = Color(0xFF0B5C9E), icono = Icons.Rounded.ThumbUp, cargando = trabajando)
                    TextButton({
                        alcance.launch { ev?.id?.let { EvaluacionInicial.confirmar(it, false, L("Prefiero hablar con el centro", "I'd rather talk to the center")) }; cargar() }
                    }, Modifier.align(Alignment.CenterHorizontally)) { Text(L("Prefiero hablarlo con el centro", "I'd rather talk to the center"), color = T.secundario) }
                }
                "confirmado" -> Fase(Aria.SALUDO, L("Algunas preguntas más", "A few more questions"),
                    L("Para preparar la evaluación de ${hijo.primerNombre}, completa la historia del desarrollo. Se guarda tu avance.", "To prepare ${hijo.primerNombre}'s evaluation, fill in the developmental history. Your progress is saved."),
                    L("CONTINUAR", "CONTINUE")) { leccion = "anamnesis" }
                "anamnesis_completa" -> Tarjeta {
                    Text(L("Elige las terapias", "Choose the therapies"), style = MaterialTheme.typography.titleLarge, color = T.texto)
                    Text(L("ARIA marcó las recomendadas para ${hijo.primerNombre}. Puedes ajustar.", "ARIA marked the recommended ones for ${hijo.primerNombre}. You can adjust."),
                        style = MaterialTheme.typography.bodySmall, color = T.secundario)
                    Spacer(Modifier.height(10.dp))
                    if (trabajando && terapias.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = T.acento, trackColor = T.relleno)
                    val rec = ev?.terapiasRecomendadas.orEmpty().toSet()
                    terapias.sortedByDescending { it.id in rec }.forEach { t ->
                        val sel = t.id in elegidas
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp).presionable { elegidas = if (sel) elegidas - t.id else elegidas + t.id }
                                .background(if (sel) T.acentoSuave else T.relleno, RoundedCornerShape(16.dp))
                                .border(2.dp, if (sel) T.acento else Color.Transparent, RoundedCornerShape(16.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.nombre ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto)
                                    if (t.id in rec) Text("  ⭐ " + L("Recomendada", "Recommended"), style = MaterialTheme.typography.labelSmall, color = T.aviso)
                                }
                                t.descripcion?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 3) }
                                Text(listOfNotNull(t.duracion, t.modalidad, t.precio?.let { p -> monedaFmt(p, t.moneda ?: "PEN") }).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = T.terciario)
                            }
                            if (sel) Icon(Icons.Rounded.Check, null, tint = T.acento, modifier = Modifier.size(24.dp))
                        }
                    }
                    OutlinedTextField(mensaje, { mensaje = it }, label = { Text(L("Mensaje para el especialista (opcional)", "Message for the specialist (optional)")) },
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    Spacer(Modifier.height(12.dp))
                    BotonGrande(L("ENVIAR MI ELECCIÓN", "SEND MY CHOICE"), {
                        trabajando = true
                        alcance.launch {
                            val ok = ev?.id?.let { EvaluacionInicial.seleccionar(it, elegidas.toList(), mensaje) } ?: false
                            if (!ok) vm.mostrarAviso(L("No se pudo enviar.", "Couldn't send."))
                            cargar(); trabajando = false
                        }
                    }, Modifier.fillMaxWidth(), habilitado = elegidas.isNotEmpty(), cargando = trabajando)
                }
                "terapia_seleccionada" -> Fase(Aria.CELEBRA, L("¡Listo! 🎉", "All set! 🎉"),
                    L("El equipo de ${e.centro?.name ?: "tu centro"} revisará tu elección y te contactará para coordinar.", "${e.centro?.name ?: "Your center"}'s team will review your choice and contact you."), null) {}
                "rechazado" -> Fase(Aria.SENTADA, L("Lo conversamos con el centro", "We'll talk it over with the center"),
                    L("El centro te contactará para ver juntos los siguientes pasos.", "The center will contact you to go over next steps together."), null) {}
                else -> Fase(Aria.CELEBRA, L("Evaluación completa ✓", "Evaluation complete ✓"),
                    L("Gracias por completar todo. El equipo ya tiene la información de ${hijo.primerNombre}.", "Thanks for completing everything. The team has ${hijo.primerNombre}'s information."), null) {}
            }
        }
    }

    when (leccion) {
        "intake" -> Leccion(
            secciones.intake.comoFormulario("intake", L("Ficha inicial", "Intake form")), { leccion = null },
            inicial = leerBorrador(claveFicha), onBorrador = { prefs.guardarBorrador(claveFicha, Backend.json.encodeToString(MAPA, it)) },
        ) { r ->
            val ok = EvaluacionInicial.enviarFicha(hijo.id, perfil.id, r)
            if (ok) { prefs.guardarBorrador(claveFicha, null); leccion = null; cargar() } else vm.mostrarAviso(L("No se pudo enviar. Revisa tu conexión.", "Couldn't send. Check your connection."))
            ok
        }
        "anamnesis" -> {
            val neuro = ev?.recomendacion == "neuropsicologica"
            Leccion(
                (if (neuro) secciones.neuro else secciones.psico).comoFormulario("anamnesis", L("Historia del desarrollo", "Developmental history")), { leccion = null },
                inicial = leerBorrador(claveAnam), onBorrador = { prefs.guardarBorrador(claveAnam, Backend.json.encodeToString(MAPA, it)) },
            ) { r ->
                val ok = ev?.id?.let { EvaluacionInicial.enviarAnamnesis(it, r) } ?: false
                if (ok) { prefs.guardarBorrador(claveAnam, null); leccion = null; cargar() } else vm.mostrarAviso(L("No se pudo guardar.", "Couldn't save."))
                ok
            }
        }
    }
}

/** Camino de 4 pasos, como los niveles de Duolingo: hechos en verde, el actual resaltado. */
@Composable
private fun Pasos(estado: String) {
    val actual = when (estado) {
        "pendiente_intake" -> 0; "analizando", "recomendado" -> 1; "confirmado" -> 2; "anamnesis_completa" -> 3; else -> 4
    }
    val nombres = listOf(L("Ficha", "Intake"), L("Revisión", "Review"), L("Historia", "History"), L("Terapias", "Therapies"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        nombres.forEachIndexed { i, n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                val hecho = i < actual
                val ahora = i == actual
                androidx.compose.foundation.layout.Box(
                    Modifier.size(if (ahora) 44.dp else 36.dp).background(if (hecho) T.exito else if (ahora) T.acento else T.relleno, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hecho) Icon(Icons.Rounded.Check, null, tint = Color.White)
                    else Text("${i + 1}", color = if (ahora) Color.White else T.terciario, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(n, style = MaterialTheme.typography.labelSmall, color = if (ahora) T.acento else T.terciario, textAlign = TextAlign.Center)
            }
            if (i < nombres.lastIndex) Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun Fase(pose: Int, titulo: String, texto: String, boton: String?, onBoton: () -> Unit) {
    Tarjeta {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AriaFlotando(pose, 130.dp)
            Spacer(Modifier.height(10.dp))
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = T.texto, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center)
            if (boton != null) {
                Spacer(Modifier.height(16.dp))
                BotonGrande(boton, onBoton, Modifier.fillMaxWidth())
            }
        }
    }
}
