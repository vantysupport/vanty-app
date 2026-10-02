package xyz.vanty.aba.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import xyz.vanty.aba.data.DefFormulario
import xyz.vanty.aba.data.FormularioPadre
import xyz.vanty.aba.data.Formularios
import xyz.vanty.aba.data.Pregunta
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.Pestana
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.CabeceraSub
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.FestejoPantalla
import xyz.vanty.aba.ui.comp.IconoTono
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.Vacio
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.Naranja
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.diasHasta
import xyz.vanty.aba.util.fechaCorta
import xyz.vanty.aba.util.hoyIso

/** Formularios que el centro envía: pendientes, vencidos y enviados. Se llenan como una lección. */
@Composable
fun FormulariosScreen(e: Estado, vm: AppViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    val perfil = e.perfil ?: return
    var lista by remember { mutableStateOf<List<FormularioPadre>?>(null) }
    var abierto by remember { mutableStateOf<Pair<FormularioPadre, DefFormulario>?>(null) }
    var festejo by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()
    suspend fun cargar() { lista = runCatching { Formularios.deFamilia(perfil.id) }.getOrDefault(emptyList()) }
    LaunchedEffect(Unit) { cargar() }
    val hoy = hoyIso()
    val pendientes = lista.orEmpty().filter { it.status in setOf("pending", "assigned", "enviado") && (it.deadline == null || it.deadline >= hoy) }
    val enviados = lista.orEmpty().filter { it.status == "completed" }
    val vencidos = lista.orEmpty().filter { it.status != "completed" && it.deadline != null && it.deadline < hoy }

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item { CabeceraSub(L("Formularios", "Forms")) { vm.irA(Pestana.Mas) } }
        if (lista != null && pendientes.isEmpty()) item {
            Tarjeta { Vacio(Aria.CELEBRA, L("¡Estás al día!", "You're all caught up!"), L("No tienes formularios pendientes.", "You have no pending forms.")) }
        }
        itemsIndexed(pendientes, key = { _, f -> f.id }) { i, f ->
            val def = Formularios.catalogo(ctx)[f.tipo]
            Tarjeta(Modifier.aparecer(i), onClick = {
                if (def != null) abierto = f to def else vm.mostrarAviso(L("Este formulario se completa desde la web.", "This form is completed on the web."))
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoTono(Icons.AutoMirrored.Rounded.Assignment, Naranja.copy(alpha = 0.15f), Naranja)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.titulo ?: def?.title ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto)
                        f.mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 2) }
                        f.deadline?.let {
                            val n = diasHasta(it)
                            Etiqueta(if (n <= 0L) L("Vence hoy", "Due today") else if (n == 1L) L("Vence mañana", "Due tomorrow") else L("Vence en $n días", "Due in $n days"),
                                (if (n <= 1L) T.peligro else T.aviso).copy(alpha = 0.14f), if (n <= 1L) T.peligro else T.aviso, Modifier.padding(top = 6.dp))
                        }
                    }
                    def?.let { d -> Text("${d.sections.size}", style = MaterialTheme.typography.labelMedium, color = T.terciario) }
                }
            }
        }
        if (enviados.isNotEmpty()) {
            item { Text(L("Enviados", "Sent"), style = MaterialTheme.typography.titleMedium, color = T.texto, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(enviados, key = { _, f -> "c" + f.id }) { _, f ->
                Row(Modifier.fillMaxWidth().background(T.tarjeta, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = T.exito)
                    Spacer(Modifier.width(10.dp))
                    Text(f.titulo ?: "—", style = MaterialTheme.typography.bodyMedium, color = T.texto, modifier = Modifier.weight(1f))
                    f.completado?.let { val (d, m) = fechaCorta(it); Text("$d $m", style = MaterialTheme.typography.labelSmall, color = T.terciario) }
                }
            }
        }
        if (vencidos.isNotEmpty()) item {
            Text(L("${vencidos.size} formulario(s) vencido(s). Si aún lo necesitas, pídele al centro que lo reenvíe.", "${vencidos.size} expired form(s). Ask your center to resend it if needed."),
                style = MaterialTheme.typography.bodySmall, color = T.terciario, modifier = Modifier.padding(top = 6.dp))
        }
    }

    abierto?.let { (f, def) ->
        Leccion(def, onCerrar = { abierto = null }) { respuestas ->
            val ok = Formularios.enviar(f, respuestas, perfil.id)
            if (ok) { abierto = null; festejo = true; alcance.launch { cargar() } }
            else vm.mostrarAviso(L("No se pudo enviar. Revisa tu conexión.", "Couldn't send. Check your connection."))
            ok
        }
    }
    if (festejo) Dialog({ festejo = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        FestejoPantalla(L("¡Formulario enviado!", "Form sent!"), L("El equipo terapéutico lo revisará pronto. ¡Gracias! 💙", "The therapy team will review it soon. Thank you! 💙"),
            Aria.CELEBRA, { festejo = false })
    }
}

/** Llenar un formulario como una lección: una sección por paso, barra de progreso y opciones grandes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Leccion(
    def: DefFormulario, onCerrar: () -> Unit, inicial: Map<String, JsonElement> = emptyMap(),
    onBorrador: (Map<String, JsonElement>) -> Unit = {}, onEnviar: suspend (Map<String, JsonElement>) -> Boolean,
) {
    val respuestas = remember { mutableStateMapOf<String, JsonElement>().apply { putAll(inicial) } }
    var paso by remember { mutableStateOf(0) }
    var enviando by remember { mutableStateOf(false) }
    var faltan by remember { mutableStateOf(setOf<String>()) }
    val temblor = remember { Animatable(0f) }
    val alcance = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val total = def.sections.size.coerceAtLeast(1)
    val progreso by animateFloatAsState((paso + 1f) / total, tween(500, easing = FastOutSlowInEasing), label = "prog")

    Dialog(onCerrar, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(T.fondo).statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onBorrador(respuestas.toMap()); onCerrar() }) { Icon(Icons.Rounded.Close, L("Cerrar", "Close"), tint = T.terciario) }
                LinearProgressIndicator(progress = { progreso }, modifier = Modifier.weight(1f).height(14.dp).clip(CircleShape),
                    color = Color(0xFF01ABFC), trackColor = T.relleno, strokeCap = StrokeCap.Round, drawStopIndicator = {})
                Text("${paso + 1}/$total", style = MaterialTheme.typography.labelMedium, color = T.terciario, modifier = Modifier.padding(horizontal = 12.dp))
            }
            AnimatedContent(paso, transitionSpec = {
                (slideInHorizontally(spring(0.85f, 380f)) { it } + fadeIn()) togetherWith (slideOutHorizontally(spring(0.85f, 380f)) { -it } + fadeOut())
            }, modifier = Modifier.weight(1f), label = "paso") { p ->
                val sec = def.sections.getOrNull(p) ?: return@AnimatedContent
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).graphicsLayer { translationX = temblor.value }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AriaFlotando(if (p == total - 1) Aria.CELEBRA else Aria.SALUDO, 70.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(def.title, style = MaterialTheme.typography.labelMedium, color = T.terciario)
                            Text(sec.title, style = MaterialTheme.typography.headlineSmall, color = T.texto)
                        }
                    }
                    sec.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = T.secundario, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(14.dp))
                    sec.questions.forEach { q ->
                        PreguntaUI(q, respuestas[q.id], q.id in faltan) { v -> if (v == null) respuestas.remove(q.id) else respuestas[q.id] = v; faltan = faltan - q.id }
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
            val ultimo = paso >= total - 1
            BotonGrande(
                if (ultimo) L("ENVIAR", "SEND") else L("CONTINUAR", "CONTINUE"),
                {
                    val sec = def.sections.getOrNull(paso)
                    val vacias = sec?.questions.orEmpty().filter { it.required == true && respuestas[it.id].vacia() }.map { it.id }.toSet()
                    if (vacias.isNotEmpty()) {
                        faltan = vacias
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        alcance.launch { for (x in listOf(18f, -16f, 12f, -8f, 0f)) temblor.animateTo(x, tween(55)) }
                    } else if (!ultimo) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm); paso++
                        onBorrador(respuestas.toMap())
                    } else {
                        enviando = true
                        alcance.launch { onEnviar(respuestas.toMap()); enviando = false }
                    }
                },
                Modifier.fillMaxWidth().padding(16.dp), color = Color(0xFF01ABFC), labio = Color(0xFF0063D8), cargando = enviando,
            )
            if (paso > 0) Text(L("Atrás", "Back"), color = T.secundario, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally).presionable { paso-- }.padding(bottom = 10.dp))
        }
    }
}

private fun JsonElement?.vacia() = when (this) {
    null -> true
    is JsonPrimitive -> content.isBlank()
    is JsonArray -> isEmpty()
    else -> false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreguntaUI(q: Pregunta, valor: JsonElement?, falta: Boolean, onCambio: (JsonElement?) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Text(q.label + if (q.required == true) " *" else "", style = MaterialTheme.typography.titleSmall, color = if (falta) T.peligro else T.texto)
    q.helpText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = T.terciario) }
    Spacer(Modifier.height(8.dp))
    val actual = (valor as? JsonPrimitive)?.content
    when (q.type) {
        "select", "frequency", "radio" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            q.options.orEmpty().forEach { o -> Opcion(o, actual == o) { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); onCambio(JsonPrimitive(o)) } }
        }
        "boolean" -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Sí" to L("Sí", "Yes"), "No" to "No").forEach { (v, t) -> Box(Modifier.weight(1f)) { Opcion(t, actual == v) { onCambio(JsonPrimitive(v)) } } }
        }
        "multiselect" -> {
            val sel = (valor as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options.orEmpty().forEach { o ->
                    val activo = o in sel
                    Text(o, style = MaterialTheme.typography.labelLarge, color = if (activo) Color.White else T.texto,
                        modifier = Modifier.presionable { onCambio(JsonArray((if (activo) sel - o else sel + o).map { JsonPrimitive(it) })) }
                            .background(if (activo) T.acento else T.tarjeta, RoundedCornerShape(14.dp))
                            .border(2.dp, if (activo) T.acento else T.borde, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        "tabla" -> {
            // Filas que la familia agrega (p. ej. hermanos, terapias previas): cada fila es un objeto columna → valor
            val filas = (valor as? JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }.orEmpty()
            val cols = q.columns.orEmpty()
            fun guardar(nuevas: List<kotlinx.serialization.json.JsonObject>) = onCambio(if (nuevas.isEmpty()) null else JsonArray(nuevas))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filas.forEachIndexed { i, fila ->
                    Column(Modifier.fillMaxWidth().background(T.relleno, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${i + 1}", style = MaterialTheme.typography.labelMedium, color = T.acento, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.Close, L("Quitar", "Remove"), tint = T.terciario, modifier = Modifier.size(20.dp).presionable { guardar(filas.filterIndexed { k, _ -> k != i }) })
                        }
                        cols.forEach { c ->
                            OutlinedTextField(
                                (fila[c.id] as? JsonPrimitive)?.content.orEmpty(),
                                { v -> guardar(filas.mapIndexed { k, f -> if (k == i) kotlinx.serialization.json.JsonObject(f + (c.id to JsonPrimitive(v))) else f }) },
                                label = { Text(c.label) }, placeholder = c.placeholder?.let { { Text(it) } }, singleLine = true,
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = if (c.type == "number") KeyboardType.Number else KeyboardType.Text),
                            )
                        }
                    }
                }
                Text("+ " + (q.addLabel ?: L("Agregar", "Add")), style = MaterialTheme.typography.labelLarge, color = T.acento,
                    modifier = Modifier.presionable { guardar(filas + kotlinx.serialization.json.JsonObject(emptyMap())) }
                        .background(T.acentoSuave, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        "range", "scale" -> {
            val min = (q.min ?: 0.0).toFloat(); val max = (q.max ?: 10.0).toFloat()
            val v = actual?.toFloatOrNull() ?: min
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(v, { onCambio(JsonPrimitive(Math.round(it))) }, valueRange = min..max, steps = (max - min).toInt().coerceAtMost(20) - 1,
                    modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = T.acento, activeTrackColor = T.acento))
                Text("${v.toInt()}", style = MaterialTheme.typography.titleMedium, color = T.acento, modifier = Modifier.padding(start = 10.dp).width(32.dp))
            }
        }
        "date" -> CampoFecha(actual, falta) { onCambio(JsonPrimitive(it)) }
        else -> OutlinedTextField(
            actual.orEmpty(), { onCambio(JsonPrimitive(it)) }, placeholder = q.placeholder?.let { { Text(it) } },
            minLines = if (q.type == "textarea") 3 else 1, singleLine = q.type != "textarea", shape = RoundedCornerShape(16.dp),
            isError = falta, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = if (q.type == "number") KeyboardType.Number else KeyboardType.Text),
        )
    }
}

/** Opción grande con borde y "labio", que se pinta al elegirla (como las respuestas de Duolingo). */
@Composable
private fun Opcion(texto: String, sel: Boolean, onClick: () -> Unit) {
    val borde by animateColorAsState(if (sel) T.acento else T.borde, label = "b")
    val fondo by animateColorAsState(if (sel) T.acentoSuave else T.tarjeta, label = "f")
    Box(
        Modifier.fillMaxWidth().presionable(onClick = onClick).background(fondo, RoundedCornerShape(16.dp))
            .border(2.dp, borde, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = if (sel) T.acento else T.texto)
    }
}

/** Pregunta de fecha: abre el calendario y guarda YYYY-MM-DD (como el input date de la web). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CampoFecha(actual: String?, falta: Boolean, onFecha: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().presionable { abierto = true }.background(T.tarjeta, RoundedCornerShape(16.dp))
            .border(if (falta) 2.dp else 1.dp, if (falta) T.peligro else T.borde, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Text(actual?.takeIf { it.isNotBlank() }?.let { xyz.vanty.aba.util.fechaLarga(it) } ?: L("Elegir fecha", "Pick a date"),
            style = MaterialTheme.typography.bodyLarge, color = if (actual.isNullOrBlank()) T.terciario else T.texto)
    }
    if (abierto) {
        val estado = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = runCatching { java.time.LocalDate.parse(actual).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    estado.selectedDateMillis?.let { onFecha(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()) }
                    abierto = false
                }) { Text(L("Listo", "Done")) }
            },
        ) { androidx.compose.material3.DatePicker(estado) }
    }
}
