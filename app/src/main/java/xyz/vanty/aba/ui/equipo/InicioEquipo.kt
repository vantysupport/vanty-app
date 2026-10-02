package xyz.vanty.aba.ui.equipo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.vanty.aba.data.AlertaClinica
import xyz.vanty.aba.data.CitaEquipo
import xyz.vanty.aba.data.InicioAdmin
import xyz.vanty.aba.data.InicioEspecialista
import xyz.vanty.aba.data.InicioSecretaria
import xyz.vanty.aba.data.Inicios
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.ui.comp.Anillo
import xyz.vanty.aba.ui.comp.Contador
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.IconoTono
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.MarcaDegradado
import xyz.vanty.aba.ui.theme.MarcaDesde
import xyz.vanty.aba.ui.theme.MarcaHasta
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.EN
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.fechaCorta
import xyz.vanty.aba.util.hora12
import xyz.vanty.aba.util.letraDia
import xyz.vanty.aba.util.nombreDePila
import xyz.vanty.aba.util.saludoSegunHora
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Piezas comunes de los Inicio ────────────────────────────────────────────

@Composable
private fun Saludo(nombre: String?, chips: List<Pair<String, Color>>, modifier: Modifier) {
    val loc = if (EN) Locale.ENGLISH else Locale.forLanguageTag("es")
    Box(modifier.fillMaxWidth().background(MarcaDegradado, RoundedCornerShape(26.dp))) {
        Column(Modifier.padding(20.dp)) {
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern(if (EN) "EEEE, MMMM d, yyyy" else "EEEE, d 'de' MMMM 'de' yyyy", loc)).replaceFirstChar { it.titlecase(loc) },
                style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${saludoSegunHora()}, ${nombreDePila(nombre)} 👋", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.weight(1f))
                Text(LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)), style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Chips blancos translúcidos con un punto del color del estado (sin mezclar tonos sobre el azul de marca)
                    chips.forEach { (t, c) ->
                        Row(Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(c, CircleShape)); Spacer(Modifier.width(5.dp))
                            Text(t, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KpiWeb(modifier: Modifier, titulo: String, valor: String, sub: String, icono: ImageVector, tono: Color, alerta: Boolean = false, onClick: (() -> Unit)? = null) {
    Tarjeta(modifier, onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = T.secundario, modifier = Modifier.weight(1f), maxLines = 2)
            IconoTono(icono, tono.copy(alpha = 0.14f), tono, 34.dp)
        }
        val n = valor.toIntOrNull()
        if (n != null) Contador(n, MaterialTheme.typography.headlineMedium, if (alerta && n > 0) tono else T.texto)
        else Text(valor, style = MaterialTheme.typography.headlineSmall, color = T.texto, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = T.terciario, maxLines = 1)
    }
}

@Composable
private fun BarrasSemana(dias: List<Pair<LocalDate, Int>>) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(dias) { p.snapTo(0f); p.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val max = (dias.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val pista = T.relleno
    val hoy = LocalDate.now()
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val ancho = size.width / dias.size.coerceAtLeast(1)
            dias.forEachIndexed { i, (d, n) ->
                val w = ancho * 0.56f; val x = i * ancho + (ancho - w) / 2
                drawRoundRect(pista, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                val h = n / max.toFloat() * size.height * p.value
                if (h > 0) drawRoundRect(Brush.verticalGradient(listOf(MarcaDesde, MarcaHasta), startY = size.height - h, endY = size.height),
                    Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            dias.forEach { (d, n) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(letraDia(d), style = MaterialTheme.typography.labelSmall, color = if (d == hoy) T.acento else T.terciario)
                    Text("$n", style = MaterialTheme.typography.labelSmall, color = T.texto)
                }
            }
        }
    }
}

@Composable
private fun Titulo(icono: ImageVector, texto: String, n: Int? = null, accion: String? = null, onAccion: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconoTono(icono, T.acentoSuave, T.acento, 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, color = T.texto)
        if (n != null) { Spacer(Modifier.width(6.dp)); Etiqueta("$n", T.acentoSuave, T.acento) }
        Spacer(Modifier.weight(1f))
        if (accion != null) Text("$accion →", style = MaterialTheme.typography.labelMedium, color = T.acento, modifier = Modifier.presionable(onClick = onAccion))
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun FilaCita(c: CitaEquipo) {
    val hoy = c.fecha.take(10) == LocalDate.now().toString()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(54.dp).background(if (hoy) T.acentoSuave else T.relleno, RoundedCornerShape(12.dp)).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val (d, m) = fechaCorta(c.fecha)
            Text(m, style = MaterialTheme.typography.labelSmall, color = if (hoy) T.acento else T.terciario)
            Text(d, style = MaterialTheme.typography.titleSmall, color = if (hoy) T.acento else T.texto)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.paciente, style = MaterialTheme.typography.titleSmall, color = T.texto, maxLines = 1)
            Text(listOfNotNull(hora12(c.hora).ifBlank { null }, c.servicio).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 1)
        }
        val l = look(c.estado)
        Etiqueta(l.texto, l.color.copy(alpha = 0.14f), l.color)
    }
}

@Composable
private fun Vacio(texto: String) = Text(texto, style = MaterialTheme.typography.bodyMedium, color = T.terciario, modifier = Modifier.padding(vertical = 10.dp))

@Composable
private fun BotonAria(vm: EquipoViewModel, modifier: Modifier) {
    FloatingActionButton({ vm.irA(TabEquipo.Aria) }, modifier, containerColor = T.acento, contentColor = Color.White, shape = CircleShape) {
        Icon(Icons.Rounded.AutoAwesome, "ARIA")
    }
}

// ── Inicio de la dirección (DashboardHome.tsx) ──────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioAdminScreen(e: EstadoEquipo, vm: EquipoViewModel, pad: PaddingValues) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val alcance = rememberCoroutineScope()
    var d by remember { mutableStateOf<InicioAdmin?>(null) }
    var refrescando by remember { mutableStateOf(false) }
    suspend fun cargar() {
        val r = runCatching { Inicios.admin(prefs.alertasDescartadas) }.getOrNull()
        // Los mensajes de alertas los genera el sistema en español: se traducen si la app está en inglés
        d = r?.let { x -> if (!EN) x else { val t = xyz.vanty.aba.data.Repo.traducir(x.alertas.map { it.mensaje }); x.copy(alertas = x.alertas.map { a -> a.copy(mensaje = t[a.mensaje] ?: a.mensaje) }) } }
            ?: d ?: InicioAdmin()
    }
    LaunchedEffect(Unit) { cargar() }
    val x = d
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(refrescando, { alcance.launch { refrescando = true; cargar(); refrescando = false } }, Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Saludo(e.perfil?.nombre, listOfNotNull(
                        x?.let { L("${it.sesionesHoy} sesiones hoy", "${it.sesionesHoy} sessions today") to T.exito },
                        x?.sinSesion?.takeIf { it > 0 }?.let { L("$it sin sesión (30d)", "$it without session (30d)") to T.aviso },
                        x?.alertasUrgentes?.takeIf { it > 0 }?.let { L(if (it == 1) "1 alerta urgente" else "$it alertas urgentes", if (it == 1) "1 urgent alert" else "$it urgent alerts") to T.peligro },
                    ), Modifier.aparecer(0))
                }
                item {
                    Column(Modifier.aparecer(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiWeb(Modifier.weight(1f), L("Pacientes", "Patients"), x?.totalPacientes?.toString() ?: "—", L("Total registrados", "Total registered"), Icons.Rounded.Groups, T.acento) { vm.irA(TabEquipo.Pacientes) }
                            KpiWeb(Modifier.weight(1f), L("Sesiones hoy", "Sessions today"), x?.sesionesHoy?.toString() ?: "—", L("${x?.realizadasHoy ?: 0} realizadas", "${x?.realizadasHoy ?: 0} done"), Icons.Rounded.EventAvailable, T.exito) { vm.irA(TabEquipo.Agenda) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiWeb(Modifier.weight(1f), L("Sin sesión (30 días)", "No session (30 days)"), x?.sinSesion?.toString() ?: "—", L("Requieren seguimiento", "Need follow-up"), Icons.Rounded.Warning, T.aviso, alerta = true) { vm.irA(TabEquipo.Pacientes) }
                            KpiWeb(Modifier.weight(1f), L("Programas ABA", "ABA programs"), x?.programasAba?.toString() ?: "—", L("Activos", "Active"), Icons.AutoMirrored.Rounded.Assignment, T.acento)
                        }
                    }
                }
                item {
                    Tarjeta(Modifier.aparecer(2)) {
                        Titulo(Icons.Rounded.CalendarMonth, L("Sesiones · últimos 7 días", "Sessions · last 7 days"), x?.semana?.sumOf { it.second })
                        x?.let { BarrasSemana(it.semana) }
                        Spacer(Modifier.height(14.dp))
                        val total = x?.totalPacientes ?: 0
                        val retenidos = (total - (x?.sinSesion ?: 0)).coerceAtLeast(0)
                        Row(Modifier.fillMaxWidth().background(T.relleno, RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Anillo(if (total > 0) retenidos * 100 / total else 0, 70.dp) { Text("${if (total > 0) retenidos * 100 / total else 0}%", style = MaterialTheme.typography.titleSmall, color = T.texto) }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(L("Retención activa", "Active retention"), style = MaterialTheme.typography.labelMedium, color = T.secundario)
                                Text("$retenidos / $total", style = MaterialTheme.typography.titleLarge, color = T.texto)
                                Text(L("pacientes con sesión reciente", "patients with a recent session"), style = MaterialTheme.typography.bodySmall, color = T.terciario)
                            }
                        }
                    }
                }
                item {
                    Tarjeta(Modifier.aparecer(3)) {
                        Titulo(Icons.AutoMirrored.Rounded.Assignment, L("Programas ABA activos", "Active ABA programs"), x?.programasActivos?.size, L("Ver todos", "View all")) { vm.irA(TabEquipo.Pacientes) }
                        if (x != null && x.programasActivos.isEmpty()) Vacio(L("Sin programas activos.", "No active programs."))
                        x?.programasActivos?.take(8)?.forEach { p ->
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.titulo, style = MaterialTheme.typography.titleSmall, color = T.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(p.paciente, style = MaterialTheme.typography.bodySmall, color = T.terciario)
                                    }
                                    Text(p.ultimoPct?.let { "$it%" } ?: "—", style = MaterialTheme.typography.titleSmall,
                                        color = if ((p.ultimoPct ?: 0) >= p.criterio) T.exito else T.secundario)
                                }
                                Spacer(Modifier.height(5.dp))
                                LinearProgressIndicator(progress = { (p.ultimoPct ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                                    color = if ((p.ultimoPct ?: 0) >= p.criterio) T.exito else T.acento, trackColor = T.relleno, drawStopIndicator = {})
                            }
                        }
                    }
                }
                item {
                    Tarjeta(Modifier.aparecer(4)) {
                        Titulo(Icons.Rounded.NotificationsActive, L("Alertas clínicas", "Clinical alerts"), x?.alertas?.size)
                        if (x != null && x.alertas.isEmpty()) Vacio(L("Sin alertas por ahora. ✨", "No alerts right now. ✨"))
                        x?.alertas?.take(15)?.forEach { a -> FilaAlerta(a) {
                            d = x.copy(alertas = x.alertas - a)
                            alcance.launch { if (a.id == null) prefs.descartarAlerta("${a.tipo}:${a.childId}") else Inicios.descartar(a) }
                        } }
                    }
                }
                item {
                    Tarjeta(Modifier.aparecer(5)) {
                        Titulo(Icons.Rounded.CalendarMonth, L("Próximas citas", "Upcoming appointments"), x?.proximas?.size, L("Ver en calendario", "View in calendar")) { vm.irA(TabEquipo.Agenda) }
                        if (x != null && x.proximas.isEmpty()) Vacio(L("No hay citas próximas.", "No upcoming appointments."))
                        x?.proximas?.forEach { FilaCita(it) }
                    }
                }
            }
        }
        BotonAria(vm, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

@Composable
private fun FilaAlerta(a: AlertaClinica, onDescartar: () -> Unit) {
    val (texto, color) = when {
        a.esLogro -> L("Logro", "Achievement") to T.exito
        a.esSinSesion -> L("Sin sesión", "No session") to T.aviso
        a.prioridad == 1 -> L("Urgente", "Urgent") to T.peligro
        else -> L("Alerta", "Alert") to T.aviso
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        IconoTono(if (a.esLogro) Icons.Rounded.TaskAlt else Icons.Rounded.Warning, color.copy(alpha = 0.14f), color, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.paciente, style = MaterialTheme.typography.titleSmall, color = T.texto, maxLines = 1)
                Spacer(Modifier.width(6.dp)); Etiqueta(texto, color.copy(alpha = 0.14f), color)
            }
            Text(a.mensaje, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 3)
        }
        Icon(Icons.Rounded.Close, L("Descartar", "Dismiss"), tint = T.terciario, modifier = Modifier.size(22.dp).presionable(onClick = onDescartar))
    }
}

// ── Inicio del especialista (EspecialistaHome.tsx) ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioEspecialistaScreen(e: EstadoEquipo, vm: EquipoViewModel, pad: PaddingValues) {
    val alcance = rememberCoroutineScope()
    var d by remember { mutableStateOf<InicioEspecialista?>(null) }
    var refrescando by remember { mutableStateOf(false) }
    suspend fun cargar() { d = runCatching { Inicios.especialista(e.perfil?.id ?: "") }.getOrNull() ?: d ?: InicioEspecialista() }
    LaunchedEffect(Unit) { cargar() }
    val x = d
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(refrescando, { alcance.launch { refrescando = true; cargar(); refrescando = false } }, Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                item { Saludo(e.perfil?.nombre, emptyList(), Modifier.aparecer(0)) }
                item {
                    Column(Modifier.aparecer(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiWeb(Modifier.weight(1f), L("Pacientes", "Patients"), x?.pacientes?.toString() ?: "—", L("Activos", "Active"), Icons.Rounded.Groups, T.acento) { vm.irA(TabEquipo.Pacientes) }
                            KpiWeb(Modifier.weight(1f), L("Citas", "Appointments"), x?.citas7?.toString() ?: "—", L("Últimos 7 días", "Last 7 days"), Icons.Rounded.EventAvailable, T.exito) { vm.irA(TabEquipo.Agenda) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            KpiWeb(Modifier.weight(1f), L("Evaluaciones", "Assessments"), x?.evaluaciones?.toString() ?: "—",
                                if ((x?.enRevision ?: 0) > 0) L("${x?.enRevision} en revisión", "${x?.enRevision} under review") else L("Total registradas", "Total recorded"),
                                Icons.AutoMirrored.Rounded.Assignment, T.aviso) { vm.irA(TabEquipo.Pacientes) }
                            KpiWeb(Modifier.weight(1f), L("Última sesión", "Last session"), x?.ultimaSesion?.let { val (dd, m) = fechaCorta(it); "$dd $m" } ?: "—",
                                L("Fecha más reciente", "Most recent date"), Icons.Rounded.History, T.acento) { vm.irA(TabEquipo.Agenda) }
                        }
                    }
                }
                item {
                    Tarjeta(Modifier.aparecer(2)) {
                        Titulo(Icons.Rounded.CalendarMonth, L("Citas de hoy", "Today's appointments"), x?.citasHoy?.size, L("Agenda", "Schedule")) { vm.irA(TabEquipo.Agenda) }
                        if (x != null && x.citasHoy.isEmpty()) Vacio(L("Sin citas para hoy.", "No appointments today."))
                        x?.citasHoy?.forEach { FilaCita(it) }
                    }
                }
                item {
                    Tarjeta(Modifier.aparecer(3)) {
                        Titulo(Icons.Rounded.EventAvailable, L("Últimos 7 días", "Last 7 days"), x?.citas7)
                        x?.let { BarrasSemana(it.semana) }
                        Spacer(Modifier.height(12.dp))
                        val total = x?.pacientes ?: 0
                        val con = (total - (x?.sinSesion ?: 0)).coerceAtLeast(0)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Anillo(if (total > 0) con * 100 / total else 0, 64.dp) { Text("${if (total > 0) con * 100 / total else 0}%", style = MaterialTheme.typography.labelLarge, color = T.texto) }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(L("Retención", "Retention"), style = MaterialTheme.typography.labelMedium, color = T.secundario)
                                Text(L("$con de $total pacientes con sesión en 30 días", "$con of $total patients with a session in 30 days"), style = MaterialTheme.typography.bodySmall, color = T.texto)
                            }
                        }
                    }
                }
                item {
                    Tarjeta(Modifier.aparecer(4)) {
                        Titulo(Icons.AutoMirrored.Rounded.Assignment, L("Evaluaciones recientes", "Recent assessments"))
                        if (x != null && x.recientes.isEmpty()) Vacio(L("Aún no enviaste evaluaciones.", "You haven't submitted assessments yet."))
                        x?.recientes?.forEach { v ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(v.titulo ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto, maxLines = 1)
                                    Text(v.children?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = T.terciario)
                                }
                                val (t, c) = when (v.status) {
                                    "approved" -> L("Aprobada", "Approved") to T.exito; "rejected" -> L("Rechazada", "Rejected") to T.peligro
                                    else -> L("En revisión", "Under review") to T.aviso
                                }
                                Etiqueta(t, c.copy(alpha = 0.14f), c)
                            }
                        }
                    }
                }
                item {
                    Tarjeta(Modifier.aparecer(5)) {
                        Titulo(Icons.Rounded.Groups, L("Pacientes recientes", "Recent patients"), null, L("Ver todos", "View all")) { vm.irA(TabEquipo.Pacientes) }
                        x?.pacientesRecientes?.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.nombre, 34.dp); Spacer(Modifier.width(10.dp))
                                Text(p.nombre, style = MaterialTheme.typography.bodyMedium, color = T.texto, modifier = Modifier.weight(1f))
                                xyz.vanty.aba.util.calcularEdad(p.nacimiento)?.let { Text(L("$it años", "$it y/o"), style = MaterialTheme.typography.bodySmall, color = T.terciario) }
                            }
                        }
                    }
                }
            }
        }
        BotonAria(vm, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
}

// ── Inicio de secretaría (SecretariaHome.tsx) ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioSecretariaScreen(e: EstadoEquipo, app: xyz.vanty.aba.ui.Estado, vm: EquipoViewModel, pad: PaddingValues) {
    val alcance = rememberCoroutineScope()
    var d by remember { mutableStateOf<InicioSecretaria?>(null) }
    var refrescando by remember { mutableStateOf(false) }
    suspend fun cargar() { d = runCatching { Inicios.secretaria() }.getOrNull() ?: d ?: InicioSecretaria() }
    LaunchedEffect(Unit) { cargar() }
    val x = d
    PullToRefreshBox(refrescando, { alcance.launch { refrescando = true; cargar(); refrescando = false } }, Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item { Saludo(e.perfil?.nombre, emptyList(), Modifier.aparecer(0)) }
            item {
                Column(Modifier.aparecer(1), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiWeb(Modifier.weight(1f), L("Citas hoy", "Today"), x?.hoy?.toString() ?: "—", L("Programadas", "Scheduled"), Icons.Rounded.CalendarMonth, T.acento) { vm.irA(TabEquipo.Agenda) }
                        KpiWeb(Modifier.weight(1f), L("Pendientes", "Pending"), x?.pendientes?.toString() ?: "—", L("Por confirmar", "To confirm"), Icons.Rounded.HourglassTop, T.aviso, alerta = true) { vm.irA(TabEquipo.Agenda) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiWeb(Modifier.weight(1f), L("Canceladas", "Cancelled"), x?.canceladas?.toString() ?: "—", L("Últimos 30 días", "Last 30 days"), Icons.Rounded.EventBusy, T.peligro) { vm.irA(TabEquipo.Agenda) }
                        KpiWeb(Modifier.weight(1f), L("Completadas", "Completed"), x?.completadas?.toString() ?: "—", L("Últimos 30 días", "Last 30 days"), Icons.Rounded.TaskAlt, T.exito) { vm.irA(TabEquipo.Agenda) }
                    }
                }
            }
            item {
                Row(Modifier.aparecer(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOfNotNull(
                        Triple(Icons.Rounded.CalendarMonth, L("Agenda", "Schedule"), TabEquipo.Agenda),
                        Triple(Icons.Rounded.Payments, L("Pagos", "Payments"), TabEquipo.Cobros).takeIf { app.on("pagos") },
                        Triple(Icons.Rounded.TrackChanges, L("Reportes", "Reports"), TabEquipo.Reportes).takeIf { app.on("reportes_financieros") },
                    ).forEachIndexed { i, (ic, t, tab) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).then(if (i == 0) Modifier.background(MarcaDegradado) else Modifier.background(T.tarjeta))
                                .presionable { vm.irA(tab) }.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(ic, null, tint = if (i == 0) Color.White else T.acento)
                            Text(t, style = MaterialTheme.typography.labelMedium, color = if (i == 0) Color.White else T.texto)
                        }
                    }
                }
            }
            item {
                Tarjeta(Modifier.aparecer(3)) {
                    Titulo(Icons.Rounded.CalendarMonth, L("Esta semana", "This week"), x?.semana?.sumOf { it.second }, L("Agenda", "Schedule")) { vm.irA(TabEquipo.Agenda) }
                    x?.let { BarrasSemana(it.semana) }
                }
            }
            item {
                Tarjeta(Modifier.aparecer(4)) {
                    Titulo(Icons.Rounded.TrackChanges, L("Resumen", "Summary"))
                    listOf(
                        L("Pacientes activos", "Active patients") to (x?.pacientes ?: 0),
                        L("Próximas citas", "Upcoming appointments") to (x?.proximas?.size ?: 0),
                        L("Completadas (30 d)", "Completed (30 d)") to (x?.completadas ?: 0),
                    ).forEach { (t, n) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(t, style = MaterialTheme.typography.bodyMedium, color = T.secundario, modifier = Modifier.weight(1f))
                            Text("$n", style = MaterialTheme.typography.titleSmall, color = T.texto)
                        }
                    }
                }
            }
            item {
                Tarjeta(Modifier.aparecer(5)) {
                    Titulo(Icons.Rounded.CalendarMonth, L("Hoy", "Today"), x?.citasHoy?.size, L("Ver agenda", "View schedule")) { vm.irA(TabEquipo.Agenda) }
                    if (x != null && x.citasHoy.isEmpty()) Vacio(L("Sin citas para hoy.", "No appointments today."))
                    x?.citasHoy?.forEach { FilaCita(it) }
                }
            }
            item {
                Tarjeta(Modifier.aparecer(6)) {
                    val lista = x?.proximas?.ifEmpty { x.recientes }.orEmpty()
                    Titulo(Icons.Rounded.CalendarMonth, if (x?.proximas?.isNotEmpty() == true) L("Próximas citas", "Upcoming appointments") else L("Citas recientes", "Recent appointments"),
                        null, L("Ver todas", "View all")) { vm.irA(TabEquipo.Agenda) }
                    if (x != null && lista.isEmpty()) Vacio(L("Sin citas registradas.", "No appointments recorded."))
                    lista.forEach { FilaCita(it) }
                }
            }
        }
    }
}

@Suppress("unused") private val flecha = Icons.AutoMirrored.Rounded.KeyboardArrowRight
