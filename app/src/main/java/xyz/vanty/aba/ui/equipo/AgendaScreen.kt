package xyz.vanty.aba.ui.equipo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import xyz.vanty.aba.data.CitaEquipo
import xyz.vanty.aba.data.Especialista
import xyz.vanty.aba.data.EstadoCita
import xyz.vanty.aba.data.NuevaCita
import xyz.vanty.aba.data.Paciente
import xyz.vanty.aba.data.RepoAgenda
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.Campo
import xyz.vanty.aba.ui.comp.CampoBoton
import xyz.vanty.aba.ui.comp.CampoFecha
import xyz.vanty.aba.ui.comp.CampoHora
import xyz.vanty.aba.ui.comp.Confirmar
import xyz.vanty.aba.ui.comp.Etiq
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.Opciones
import xyz.vanty.aba.ui.comp.Pastilla
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.Vacio
import xyz.vanty.aba.ui.comp.abrirEnlace
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.MarcaDegradado
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.EN
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.fechaLarga
import xyz.vanty.aba.util.hora12
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Agenda igual que la web:
 *  • jefe, admin y secretaría → CalendarView: calendario del mes, filtros, nueva cita (individual o grupal, varios
 *    especialistas, presencial/virtual, estado, repetir), editar horario, eliminar, cambiar estado,
 *    responder reprogramaciones de la familia y videollamada.
 *  • especialista → MiAgenda: su calendario del mes en modo consulta y conexión con Google / Outlook.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(e: EstadoEquipo, vm: EquipoViewModel, pad: PaddingValues) {
    val gestiona = e.rol != Rol.Especialista
    val alcance = rememberCoroutineScope()
    var citas by remember { mutableStateOf<List<CitaEquipo>?>(null) }
    var especialistas by remember { mutableStateOf<List<Especialista>>(emptyList()) }
    var refrescando by remember { mutableStateOf(false) }
    var mes by remember { mutableStateOf(YearMonth.now()) }
    var dir by remember { mutableIntStateOf(0) }
    var dia by remember { mutableStateOf(LocalDate.now()) }
    var filtroEstado by remember { mutableStateOf("todos") }
    var filtroEsp by remember { mutableStateOf("todos") }
    var nueva by remember { mutableStateOf<LocalDate?>(null) }
    var acciones by remember { mutableStateOf<CitaEquipo?>(null) }
    var editar by remember { mutableStateOf<CitaEquipo?>(null) }
    var borrar by remember { mutableStateOf<CitaEquipo?>(null) }
    var ocupado by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    suspend fun cargar() {
        val r = runCatching { if (gestiona) RepoAgenda.todas() else RepoAgenda.delEspecialista() }.getOrNull()
        if (r == null) { vm.aviso(L("Sin conexión. Desliza hacia abajo para reintentar.", "No connection. Pull down to retry.")); if (citas == null) citas = emptyList(); return }
        citas = r
        // Como la web: las sesiones que ya terminaron (inicio + 45 min) pasan a realizadas
        if (gestiona) {
            val hechas = RepoAgenda.completarVencidas(r)
            if (hechas.isNotEmpty()) citas = citas?.map { if (it.id in hechas) it.copy(status = "completed") else it }
        }
    }
    LaunchedEffect(Unit) {
        launch { if (gestiona) especialistas = runCatching { RepoAgenda.especialistas() }.getOrDefault(emptyList()) }
        if (e.pacientes.isEmpty()) vm.refrescar()
        while (true) { cargar(); delay(60_000) } // la web refresca cada minuto
    }
    fun accion(id: String, bloque: suspend () -> Boolean, ok: String) {
        ocupado = id
        alcance.launch {
            val bien = runCatching { bloque() }.getOrDefault(false)
            ocupado = null
            vm.aviso(if (bien) ok else L("No se pudo guardar. Intenta de nuevo.", "Couldn't save. Try again."))
            if (bien) cargar()
        }
    }

    val todas = citas.orEmpty()
    val filtradas = todas.filter { c ->
        (filtroEstado == "todos" || (c.status ?: "confirmed") == filtroEstado) && (filtroEsp == "todos" || c.especialistaId == filtroEsp)
    }
    val hoy = LocalDate.now()
    val delDia = filtradas.filter { it.fecha.take(10) == dia.toString() }.sortedBy { it.hora.orEmpty() }
    val solicitudes = todas.filter { it.reproSolicitada && it.fecha.take(10) >= hoy.toString() }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(refrescando, { alcance.launch { refrescando = true; cargar(); refrescando = false } }, Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                item { Kpis(todas, gestiona, mes, Modifier.aparecer(0)) }
                // Vincular agenda: en la web, la Agenda de cada rol tiene Google Calendar y Outlook (con "sincronizar" para el centro)
                item {
                    xyz.vanty.aba.ui.comp.VincularCalendarios(
                        e.perfil?.id, rolWeb(e), L("Calendarios vinculados", "Linked calendars"),
                        L("Las citas se crean, mueven y cancelan solas en tu calendario.", "Appointments are created, moved and cancelled in your calendar automatically."),
                        vm::aviso, Modifier.aparecer(1), sincronizar = gestiona,
                    )
                }
                item {
                    Mes(mes, dir, dia, filtradas, Modifier.aparecer(1),
                        onMes = { d -> dir = d; mes = mes.plusMonths(d.toLong()) },
                        onHoy = { dir = 0; mes = YearMonth.now(); dia = LocalDate.now() },
                        onDia = { dia = it })
                }
                if (gestiona) item {
                    Column(Modifier.aparecer(2)) {
                        Opciones(listOf("todos" to L("Todos", "All"), "confirmed" to L("Confirmadas", "Confirmed"), "pending" to L("Pendientes", "Pending"),
                            "completed" to L("Realizadas", "Completed"), "cancelled" to L("Canceladas", "Cancelled")), filtroEstado,
                            punto = { colorEstado(it) }) { filtroEstado = it }
                        if (especialistas.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Opciones(listOf("todos" to L("Todos los especialistas", "All specialists")) + especialistas.map { it.id to it.nombre.orEmpty() }, filtroEsp) { filtroEsp = it }
                        }
                    }
                }
                if (gestiona && solicitudes.isNotEmpty()) {
                    item { Seccion(L("Reprogramaciones solicitadas", "Reschedule requests"), "${solicitudes.size}") }
                    items(solicitudes, key = { "s" + it.id }) { c ->
                        TarjetaCita(c, gestiona, ocupado == c.id, mostrarFecha = true,
                            onAcciones = { acciones = c }, onEditar = { editar = c }, onBorrar = { borrar = c },
                            onReprogramar = { ap -> accion(c.id, { RepoAgenda.responderReprogramacion(c.id, ap) },
                                if (ap) L("Cita movida. La familia fue avisada.", "Appointment moved. The family was notified.") else L("Solicitud rechazada. La familia fue avisada.", "Request declined. The family was notified.")) },
                            onVideo = { video(c, alcance, ctx, vm) { ocupado = it } })
                    }
                }
                item { Seccion(fechaLarga(dia.toString()), L("${delDia.size} citas", "${delDia.size} appts")) }
                if (citas != null && delDia.isEmpty()) item {
                    Tarjeta { Vacio(Aria.SENTADA, L("Sin citas", "No appointments"), L("No hay citas para este día.", "No appointments on this day."),
                        if (gestiona) L("Nueva cita", "New appointment") else null) { nueva = dia } }
                }
                itemsIndexed(delDia, key = { _, c -> c.id }) { i, c ->
                    Box(Modifier.aparecer(i.coerceAtMost(8)).animateItem()) {
                        TarjetaCita(c, gestiona, ocupado == c.id, mostrarFecha = false,
                            onAcciones = { acciones = c }, onEditar = { editar = c }, onBorrar = { borrar = c },
                            onReprogramar = { ap -> accion(c.id, { RepoAgenda.responderReprogramacion(c.id, ap) },
                                if (ap) L("Cita movida. La familia fue avisada.", "Appointment moved. The family was notified.") else L("Solicitud rechazada. La familia fue avisada.", "Request declined. The family was notified.")) },
                            onVideo = { video(c, alcance, ctx, vm) { ocupado = it } })
                    }
                }
            }
        }
        if (gestiona) ExtendedFloatingActionButton(
            onClick = { nueva = dia }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = T.acento, contentColor = Color.White, shape = CircleShape,
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(L("Nueva cita", "New appointment")) },
        )
    }

    nueva?.let { f ->
        HojaNuevaCita(f, e.pacientes, especialistas, e.perfil?.id, onCerrar = { nueva = null }) { ok, n ->
            nueva = null
            vm.aviso(if (ok) (if (n > 1) L("$n citas agendadas", "$n appointments scheduled") else L("Cita agendada", "Appointment scheduled"))
                else L("No se pudo agendar. Intenta de nuevo.", "Couldn't schedule. Try again."))
            if (ok) alcance.launch { cargar() }
        }
    }
    acciones?.let { c ->
        HojaCita(c, { st -> acciones = null; accion(c.id, { xyz.vanty.aba.data.RepoEquipo.cambiarEstado(c.id, st) }, L("Estado actualizado", "Status updated")) }, { acciones = null })
    }
    editar?.let { c ->
        HojaHorario(c, { editar = null }) { f, h ->
            editar = null
            accion(c.id, { RepoAgenda.cambiarHorario(c.id, f, h) }, L("Horario actualizado", "Time updated"))
        }
    }
    borrar?.let { c ->
        Confirmar(L("¿Eliminar esta cita?", "Delete this appointment?"), "${c.paciente} · ${fechaLarga(c.fecha)} ${hora12(c.hora)}",
            L("Eliminar", "Delete"), onSi = { borrar = null; accion(c.id, { RepoAgenda.eliminar(c.id) }, L("Cita eliminada", "Appointment deleted")) }, onNo = { borrar = null })
    }
}

private fun video(c: CitaEquipo, alcance: kotlinx.coroutines.CoroutineScope, ctx: android.content.Context, vm: EquipoViewModel, ocupado: (String?) -> Unit) {
    ocupado(c.id)
    alcance.launch {
        when (val r = RepoAgenda.iniciarVideo(c)) {
            is RepoAgenda.Video.Sala -> { vm.aviso(L("Sala creada. La familia fue avisada.", "Room created. The family was notified.")); abrirEnlace(ctx, r.url) }
            RepoAgenda.Video.Limite -> vm.aviso(L("Se alcanzó el límite mensual de minutos de video.", "Monthly video minutes limit reached."))
            RepoAgenda.Video.Error -> c.videoLink?.let { abrirEnlace(ctx, it) } ?: vm.aviso(L("No se pudo iniciar la videollamada.", "Couldn't start the video call."))
        }
        ocupado(null)
    }
}

@Composable
private fun colorEstado(s: String): Color? = when (s) {
    "confirmed" -> T.exito; "pending" -> T.aviso; "completed" -> T.acento; "cancelled" -> T.peligro; else -> null
}

@Composable
private fun Kpis(citas: List<CitaEquipo>, gestiona: Boolean, mes: YearMonth, modifier: Modifier) {
    val hoy = LocalDate.now()
    val h = hoy.toString()
    val activas = citas.filter { it.status != "cancelled" }
    val datos = if (gestiona) {
        val lunes = hoy.minusDays(hoy.dayOfWeek.value % 7L) // la web cuenta la semana de domingo a sábado
        listOf(
            L("Total", "Total") to citas.size,
            L("Hoy", "Today") to citas.count { it.fecha.take(10) == h },
            L("Semana", "Week") to citas.count { val d = it.fecha.take(10); d >= lunes.toString() && d <= lunes.plusDays(6).toString() },
            L("Virtuales", "Virtual") to citas.count { it.modalidad == "virtual" },
        )
    } else listOf(
        L("Este mes", "This month") to activas.count { it.fecha.startsWith(mes.toString()) },
        L("Hoy", "Today") to activas.count { it.fecha.take(10) == h },
        L("Virtuales", "Virtual") to activas.count { it.modalidad == "virtual" && it.fecha.take(10) >= h },
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        datos.forEachIndexed { i, (t, n) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).then(if (i == 0) Modifier.background(MarcaDegradado) else Modifier.background(T.tarjeta)).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                xyz.vanty.aba.ui.comp.Contador(n, MaterialTheme.typography.titleLarge, if (i == 0) Color.White else T.texto)
                Text(t, style = MaterialTheme.typography.labelSmall, color = if (i == 0) Color.White.copy(alpha = 0.85f) else T.terciario)
            }
        }
    }
}

/** Calendario del mes (domingo a sábado, como la web) con puntitos de color por estado. */
@Composable
private fun Mes(mes: YearMonth, dir: Int, sel: LocalDate, citas: List<CitaEquipo>, modifier: Modifier, onMes: (Int) -> Unit, onHoy: () -> Unit, onDia: (LocalDate) -> Unit) {
    val loc = if (EN) Locale.ENGLISH else Locale.forLanguageTag("es")
    val porDia = remember(citas) { citas.groupBy { it.fecha.take(10) } }
    Column(modifier.fillMaxWidth().background(T.tarjeta, RoundedCornerShape(24.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mes.month.getDisplayName(TextStyle.FULL, loc).replaceFirstChar { it.titlecase(loc) } + " " + mes.year,
                style = MaterialTheme.typography.titleMedium, color = T.texto, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Text(L("Hoy", "Today"), style = MaterialTheme.typography.labelLarge, color = T.acento,
                modifier = Modifier.clip(CircleShape).background(T.acentoSuave).presionable(onClick = onHoy).padding(horizontal = 12.dp, vertical = 6.dp))
            IconButton({ onMes(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, L("Mes anterior", "Previous month"), tint = T.secundario) }
            IconButton({ onMes(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, L("Mes siguiente", "Next month"), tint = T.secundario) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
            (0..6).forEach { i ->
                val d = java.time.DayOfWeek.SUNDAY.plus(i.toLong())
                Text(d.getDisplayName(TextStyle.NARROW, loc).uppercase(loc), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = T.terciario)
            }
        }
        AnimatedContent(mes, transitionSpec = {
            (slideInHorizontally { it / 4 * (if (dir >= 0) 1 else -1) } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * (if (dir >= 0) 1 else -1) } + fadeOut())
        }, label = "mes") { m ->
            val vacios = m.atDay(1).dayOfWeek.value % 7
            val total = m.lengthOfMonth()
            Column {
                (0 until (vacios + total + 6) / 7).forEach { semana ->
                    Row(Modifier.fillMaxWidth()) {
                        (0..6).forEach { col ->
                            val n = semana * 7 + col - vacios + 1
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                                if (n in 1..total) {
                                    val d = m.atDay(n)
                                    val lista = porDia[d.toString()].orEmpty()
                                    val elegido = d == sel
                                    Column(
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                                            .then(if (elegido) Modifier.background(MarcaDegradado) else if (d == LocalDate.now()) Modifier.background(T.acentoSuave) else Modifier)
                                            .presionable { onDia(d) },
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text("$n", style = MaterialTheme.typography.titleSmall, color = if (elegido) Color.White else if (d == LocalDate.now()) T.acento else T.texto)
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp).padding(top = 2.dp)) {
                                            lista.take(3).forEach { c ->
                                                Box(Modifier.size(4.dp).background(if (elegido) Color.White else colorEstado(c.status ?: "confirmed") ?: T.acento, CircleShape))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaCita(
    c: CitaEquipo, gestiona: Boolean, ocupado: Boolean, mostrarFecha: Boolean,
    onAcciones: () -> Unit, onEditar: () -> Unit, onBorrar: () -> Unit, onReprogramar: (Boolean) -> Unit, onVideo: () -> Unit,
) {
    val l = look(c.estado)
    val hoy = LocalDate.now().toString()
    val proxima = c.fecha.take(10) >= hoy && c.estado != EstadoCita.Cancelada && c.estado != EstadoCita.Realizada
    Tarjeta(Modifier.animateContentSize(), onClick = if (gestiona) onAcciones else null) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.width(58.dp).background(l.color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hora12(c.hora).substringBefore(' ').ifBlank { "—" }, style = MaterialTheme.typography.titleSmall, color = l.color)
                Text(hora12(c.hora).substringAfter(' ', ""), style = MaterialTheme.typography.labelSmall, color = l.color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.paciente, style = MaterialTheme.typography.titleMedium, color = T.texto, maxLines = 1)
                Text(listOfNotNull(if (mostrarFecha) fechaLarga(c.fecha) else null, c.servicio).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 2)
                c.specialist?.nombre?.let { Text("👤 $it" + (c.specialist.specialty?.let { s -> " · $s" } ?: ""), style = MaterialTheme.typography.bodySmall, color = T.terciario, maxLines = 1) }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Etiqueta(l.texto, l.color.copy(alpha = 0.14f), l.color)
                    if (c.modalidad == "virtual") Etiqueta("🎥 " + L("Virtual", "Virtual"), T.acentoSuave, T.acento)
                    else Etiqueta("📍 " + L("Presencial", "In person"), T.relleno, T.secundario)
                    if (c.grupal == true) Etiqueta("👥 " + L("Grupal", "Group"), T.relleno, T.secundario)
                }
            }
            if (gestiona) Column {
                IconButton(onEditar, Modifier.size(36.dp), enabled = !ocupado) { Icon(Icons.Rounded.Edit, L("Editar horario", "Edit time"), tint = T.secundario, modifier = Modifier.size(20.dp)) }
                IconButton(onBorrar, Modifier.size(36.dp), enabled = !ocupado) { Icon(Icons.Rounded.DeleteOutline, L("Eliminar", "Delete"), tint = T.peligro, modifier = Modifier.size(20.dp)) }
            }
        }
        Cronometro(c)
        if (c.reproSolicitada && gestiona) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().background(T.aviso.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EventRepeat, null, tint = T.aviso, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(L("La familia pide moverla", "The family asks to move it"), style = MaterialTheme.typography.titleSmall, color = T.texto)
                }
                Text(listOfNotNull(c.reproFecha?.let { fechaLarga(it) }, c.reproHora?.let { hora12(it) }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = T.texto)
                c.reproMotivo?.let { Text("“$it”", style = MaterialTheme.typography.bodySmall, color = T.secundario) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonGrande(L("Aprobar", "Approve"), { onReprogramar(true) }, Modifier.weight(1f), icono = Icons.Rounded.Check, cargando = ocupado)
                    BotonGrande(L("Rechazar", "Decline"), { onReprogramar(false) }, Modifier.weight(1f), color = T.relleno, labio = T.borde, textoColor = T.texto, habilitado = !ocupado)
                }
            }
        }
        if (c.modalidad == "virtual" && proxima) {
            Spacer(Modifier.height(10.dp))
            BotonGrande(if (gestiona) L("Iniciar videollamada", "Start video call") else L("Entrar a la videollamada", "Join video call"),
                onVideo, Modifier.fillMaxWidth(), icono = Icons.Rounded.Videocam, cargando = ocupado)
        }
    }
}

/** Cronómetro de 45 min mientras la sesión está en curso (SessionTimer de la web). */
@Composable
private fun Cronometro(c: CitaEquipo) {
    if (c.estado == EstadoCita.Cancelada || c.estado == EstadoCita.Realizada) return
    val inicio = remember(c.fecha, c.hora) {
        runCatching { LocalDateTime.of(LocalDate.parse(c.fecha.take(10)), LocalTime.parse(c.hora.orEmpty().take(5))) }.getOrNull()
    } ?: return
    val ahora by produceState(LocalDateTime.now(), inicio) { while (true) { value = LocalDateTime.now(); delay(1000) } }
    val fin = inicio.plusMinutes(45)
    if (ahora.isBefore(inicio) || !ahora.isBefore(fin)) return
    val resta = java.time.Duration.between(ahora, fin).seconds
    val color = when { resta <= 300 -> T.peligro; resta <= 600 -> T.aviso; else -> T.acento }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Timer, null, tint = color, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(if (resta <= 300) L("⚠️ Finalizando", "⚠️ Ending") else L("Sesión en curso", "Session in progress"), style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.weight(1f))
                Text("%02d:%02d".format(resta / 60, resta % 60), style = MaterialTheme.typography.labelLarge, color = color)
            }
            LinearProgressIndicator(progress = { resta / 2700f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(CircleShape),
                color = color, trackColor = T.relleno, drawStopIndicator = {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaHorario(c: CitaEquipo, onCerrar: () -> Unit, onGuardar: (LocalDate, String) -> Unit) {
    var fecha by remember { mutableStateOf(runCatching { LocalDate.parse(c.fecha.take(10)) }.getOrDefault(LocalDate.now())) }
    var hora by remember { mutableStateOf(c.hora?.take(5) ?: "09:00") }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(L("Editar horario", "Edit time"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Text(c.paciente, style = MaterialTheme.typography.bodyMedium, color = T.secundario)
            Etiq(L("Fecha", "Date")); CampoFecha(fecha, { fecha = it })
            Etiq(L("Hora", "Time")); CampoHora(hora, { hora = it })
            Text(L("Se avisa a la familia y se actualizan los calendarios conectados.", "The family is notified and connected calendars are updated."),
                style = MaterialTheme.typography.bodySmall, color = T.terciario, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(16.dp))
            BotonGrande(L("GUARDAR", "SAVE"), { onGuardar(fecha, hora) }, Modifier.fillMaxWidth())
        }
    }
}

/** "Nueva cita" de la agenda web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaNuevaCita(
    fechaInicial: LocalDate, pacientes: List<Paciente>, especialistas: List<Especialista>, yo: String?,
    onCerrar: () -> Unit, onListo: (Boolean, Int) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var grupal by remember { mutableStateOf(false) }
    var elegidos by remember { mutableStateOf<List<String>>(emptyList()) }
    var grupo by remember { mutableStateOf("") }
    var servicio by remember { mutableStateOf(if (EN) "ABA Therapy" else "Terapia ABA") }
    var esps by remember { mutableStateOf<List<Especialista>>(emptyList()) }
    var fecha by remember { mutableStateOf(fechaInicial) }
    var hora by remember { mutableStateOf("09:00") }
    var virtual by remember { mutableStateOf(false) }
    var estado by remember { mutableStateOf("confirmed") }
    var repetir by remember { mutableIntStateOf(0) }
    var semanas by remember { mutableIntStateOf(4) }
    var notas by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var elegirPaciente by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(L("Nueva cita", "New appointment"), style = MaterialTheme.typography.headlineSmall, color = T.texto)

            Etiq(L("Tipo de sesión", "Session type"))
            Opciones(listOf(false to "👤 " + L("Individual", "Individual"), true to "👥 " + L("Grupal", "Group")), grupal) { grupal = it; elegidos = elegidos.take(if (it) 99 else 1) }

            Etiq(if (grupal) L("Participantes *", "Participants *") else L("Paciente *", "Patient *"))
            val nombres = pacientes.associate { it.id to it.nombre }
            CampoBoton(
                if (elegidos.isEmpty()) L("Seleccionar paciente", "Select patient") else elegidos.joinToString(", ") { nombres[it] ?: "—" },
                if (grupal) Icons.Rounded.Groups else Icons.Rounded.Person, vacio = elegidos.isEmpty(),
            ) { elegirPaciente = true }
            if (grupal) { Etiq(L("Nombre del grupo", "Group name")); Campo(grupo, { grupo = it }, L("Ej: Habilidades sociales", "E.g. Social skills")) }

            Etiq(L("Servicio", "Service"))
            Campo(servicio, { servicio = it }, L("Tipo de servicio", "Service type"))
            Spacer(Modifier.height(6.dp))
            Opciones((if (EN) RepoAgenda.SERVICIOS_EN else RepoAgenda.SERVICIOS_ES).map { it to it }, servicio) { servicio = it }

            Etiq(L("Especialista asignado (puedes elegir varios)", "Assigned specialist (you can pick several)"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                especialistas.forEach { s ->
                    val on = esps.any { it.id == s.id }
                    Pastilla(s.nombre.orEmpty() + (s.specialty?.let { " · $it" } ?: ""), on) { esps = if (on) esps.filter { it.id != s.id } else esps + s }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.3f)) { Etiq(L("Fecha *", "Date *")); CampoFecha(fecha, { fecha = it }) }
                Column(Modifier.weight(1f)) { Etiq(L("Hora *", "Time *")); CampoHora(hora, { hora = it }) }
            }

            Etiq(L("Modalidad", "Mode"))
            Opciones(listOf(false to "📍 " + L("Presencial", "In person"), true to "🎥 " + L("Virtual", "Virtual")), virtual) { virtual = it }
            if (virtual) Text(L("Al iniciar se crea el enlace de video y se avisa a la familia.", "A video link is created and the family is notified."),
                style = MaterialTheme.typography.bodySmall, color = T.acento, modifier = Modifier.padding(top = 6.dp, start = 4.dp))

            Etiq(L("Estado", "Status"))
            Opciones(listOf("confirmed" to L("Confirmada", "Confirmed"), "pending" to L("Pendiente", "Pending"), "completed" to L("Realizada", "Completed"), "cancelled" to L("Cancelada", "Cancelled")),
                estado, punto = { colorEstado(it) }) { estado = it }

            Etiq("🔁 " + L("Repetir cita", "Repeat appointment"))
            Opciones(listOf(0 to L("No repetir", "Don't repeat"), 7 to L("Semanal", "Weekly"), 14 to L("Quincenal", "Every 2 weeks")), repetir) { repetir = it }
            if (repetir > 0) {
                Spacer(Modifier.height(8.dp))
                Opciones(listOf(2, 4, 6, 8, 12).map { it to L("$it veces", "$it times") }, semanas) { semanas = it }
                Text(L("Se crearán $semanas citas a partir de la fecha elegida, ${if (repetir == 7) "cada semana" else "cada 2 semanas"}.",
                    "$semanas appointments will be created from the chosen date, ${if (repetir == 7) "every week" else "every 2 weeks"}."),
                    style = MaterialTheme.typography.bodySmall, color = T.terciario, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
            }

            Etiq(L("Notas (opcional)", "Notes (optional)"))
            Campo(notas, { notas = it }, L("Observaciones", "Observations"), lineas = 2)

            error?.let { Text(it, color = T.peligro, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(18.dp))
            BotonGrande(
                when { virtual -> L("AGENDAR VIRTUAL", "SCHEDULE VIRTUAL"); grupal -> L("AGENDAR GRUPO", "SCHEDULE GROUP"); else -> L("CONFIRMAR CITA", "CONFIRM APPOINTMENT") },
                {
                    if (elegidos.isEmpty()) { error = if (grupal) L("Selecciona participantes", "Select participants") else L("Selecciona un paciente", "Select a patient"); return@BotonGrande }
                    guardando = true; error = null
                    val n = NuevaCita(grupal, elegidos, grupo, servicio.ifBlank { L("Terapia ABA", "ABA Therapy") }, esps, fecha, hora, virtual, estado, repetir, semanas, notas)
                    alcance.launch {
                        val ok = runCatching { RepoAgenda.crear(n, yo) }.getOrDefault(false)
                        guardando = false
                        onListo(ok, elegidos.size * (if (repetir == 0) 1 else semanas))
                    }
                },
                Modifier.fillMaxWidth(), icono = Icons.Rounded.CalendarMonth, cargando = guardando,
            )
        }
    }
    if (elegirPaciente) ElegirPacientes(pacientes, elegidos, multiple = grupal, onCerrar = { elegirPaciente = false }) { elegidos = it; if (!grupal) elegirPaciente = false }
}

/** Lista con buscador para elegir paciente(s). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElegirPacientes(pacientes: List<Paciente>, elegidos: List<String>, multiple: Boolean, onCerrar: () -> Unit, onCambio: (List<String>) -> Unit) {
    var q by remember { mutableStateOf("") }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(if (multiple) L("Participantes", "Participants") else L("Paciente", "Patient"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Spacer(Modifier.height(10.dp))
            Campo(q, { q = it }, L("Buscar…", "Search…"))
            val lista = pacientes.filter { q.isBlank() || it.nombre.contains(q.trim(), ignoreCase = true) }.sortedBy { it.nombre }
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                items(lista, key = { it.id }) { p ->
                    val on = p.id in elegidos
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).then(if (on) Modifier.background(T.acentoSuave) else Modifier)
                            .presionable { onCambio(if (multiple) (if (on) elegidos - p.id else elegidos + p.id) else listOf(p.id)) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p.nombre, 36.dp); Spacer(Modifier.width(10.dp))
                        Text(p.nombre, style = MaterialTheme.typography.bodyLarge, color = T.texto, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Rounded.Check, null, tint = T.acento)
                    }
                }
            }
            if (multiple) BotonGrande(L("LISTO (${elegidos.size})", "DONE (${elegidos.size})"), onCerrar, Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}

/** El rol tal como lo usa la web en el permiso de calendario (vuelve al panel correcto). */
fun rolWeb(e: EstadoEquipo) = when (e.rol) { Rol.Especialista -> "especialista"; Rol.Secretaria -> "secretaria"; else -> "admin" }
