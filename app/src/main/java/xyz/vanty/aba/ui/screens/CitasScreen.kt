package xyz.vanty.aba.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.presionable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.data.Cita
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.Vacio
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.theme.Naranja
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.diasHasta
import xyz.vanty.aba.util.fechaCorta
import xyz.vanty.aba.util.fechaLarga
import xyz.vanty.aba.util.hora12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitasScreen(e: Estado, vm: AppViewModel, pad: PaddingValues) {
    var hoja by remember { mutableStateOf<Cita?>(null) }
    PullToRefreshBox(e.refrescando, vm::refrescar, Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            item { ResumenMes(e) }
            item { Titulo(L("Próximas citas", "Upcoming")) }
            if (e.proximas.isEmpty()) item {
                Tarjeta {
                    Vacio(Aria.SENTADA, L("Sin citas próximas", "No upcoming appointments"),
                        L("El centro agenda las sesiones. Te avisaré un día antes y unas horas antes de cada una.",
                            "Your center schedules sessions. I'll remind you the day before and a few hours before each one."))
                }
            }
            itemsIndexed(e.proximas, key = { _, c -> c.id }) { i, c -> FilaCita(c, true, Modifier.aparecer(i)) { hoja = c } }

            // "Recíbelas en tu calendario" (ConectarCalendarios de Mis citas en la web)
            item {
                xyz.vanty.aba.ui.comp.VincularCalendarios(
                    e.perfil?.id, "padre", L("Recíbelas en tu calendario", "Get them on your calendar"),
                    L("Las citas aparecen y se actualizan solas.", "Appointments appear and update on their own."), vm::mostrarAviso,
                )
            }
            if (e.pasadas.isNotEmpty()) {
                item { Spacer(Modifier.width(1.dp)); Titulo(L("Historial", "History")) }
                itemsIndexed(e.pasadas, key = { _, c -> "p" + c.id }) { i, c -> FilaCita(c, false, Modifier.aparecer(i + e.proximas.size)) {} }
            }
            item { ContactoCentro(e) }
        }
    }
    hoja?.let { c -> HojaCitaFamilia(c, vm) { hoja = null } }
}

/** Acciones de una cita próxima: unirse a la videollamada, pedir cambio o avisar que no podrá asistir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaCitaFamilia(c: Cita, vm: AppViewModel, onCerrar: () -> Unit) {
    var modo by remember { mutableStateOf<String?>(null) } // null | "cambio" | "cancelar"
    var motivo by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf<String?>(null) }
    var hora by remember { mutableStateOf<String?>(c.hora?.take(5)) }
    var enviando by remember { mutableStateOf(false) }
    var calendario by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(fechaLarga(c.fecha), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Text(listOfNotNull(hora12(c.hora).ifBlank { null }, c.servicio).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = T.secundario)
            if (c.solicitudPendiente) {
                Spacer(Modifier.height(10.dp))
                Etiqueta(L("Ya pediste un cambio: el centro te responderá", "Change requested: the center will reply"), T.aviso.copy(alpha = 0.15f), T.aviso)
            }
            Spacer(Modifier.height(16.dp))
            when (modo) {
                null -> {
                    if (c.modalidad == "virtual") {
                        BotonGrande(L("UNIRME A LA VIDEOLLAMADA", "JOIN VIDEO CALL"), {
                            alcance.launch {
                                val sala = xyz.vanty.aba.data.RepoFamilia.salaVideo(c.id)
                                if (sala != null) runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(sala))) }
                                else vm.mostrarAviso(L("La sala se abrirá cuando el terapeuta inicie la sesión.", "The room opens when the therapist starts the session."))
                            }
                        }, Modifier.fillMaxWidth(), color = T.exito, labio = Color(0xFF0B5C9E), icono = Icons.Rounded.Videocam)
                        Spacer(Modifier.height(10.dp))
                    }
                    BotonGrande(L("PEDIR CAMBIO DE FECHA", "REQUEST NEW DATE"), { modo = "cambio" }, Modifier.fillMaxWidth(), icono = Icons.Rounded.EditCalendar)
                    Spacer(Modifier.height(10.dp))
                    BotonGrande(L("NO PODRÉ ASISTIR", "I CAN'T MAKE IT"), { modo = "cancelar" }, Modifier.fillMaxWidth(),
                        color = T.relleno, labio = T.borde, textoColor = T.peligro, icono = Icons.Rounded.EventBusy)
                }
                else -> {
                    if (modo == "cambio") {
                        Text(L("¿Qué día te queda mejor?", "Which day works better?"), style = MaterialTheme.typography.titleSmall, color = T.texto)
                        Spacer(Modifier.height(8.dp))
                        BotonGrande(fecha?.let { fechaLarga(it) } ?: L("ELEGIR FECHA", "PICK A DATE"), { calendario = true }, Modifier.fillMaxWidth(),
                            color = T.relleno, labio = T.borde, textoColor = T.acento, icono = Icons.Rounded.CalendarMonth)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("08:00", "09:00", "10:00", "11:00", "12:00", "14:00", "15:00", "16:00", "17:00", "18:00").forEach { h ->
                                val sel = h == hora
                                Text(hora12(h), style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else T.secundario,
                                    modifier = Modifier.presionable { hora = h }.background(if (sel) T.acento else T.relleno, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(motivo, { motivo = it }, label = { Text(L("Motivo (opcional)", "Reason (optional)")) },
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    Spacer(Modifier.height(14.dp))
                    BotonGrande(L("ENVIAR AL CENTRO", "SEND TO CENTER"), {
                        enviando = true
                        vm.solicitarCita(c, modo == "cancelar", motivo.ifBlank { null }, fecha, hora) { ok -> enviando = false; if (ok) onCerrar() }
                    }, Modifier.fillMaxWidth(), cargando = enviando, habilitado = modo == "cancelar" || fecha != null)
                    androidx.compose.material3.TextButton({ modo = null }, Modifier.align(Alignment.CenterHorizontally)) { Text(L("Volver", "Back"), color = T.secundario) }
                }
            }
        }
    }
    if (calendario) {
        val manana = java.time.LocalDate.now().plusDays(1)
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = manana.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate().isBefore(manana)
            },
        )
        DatePickerDialog(
            onDismissRequest = { calendario = false },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    estado.selectedDateMillis?.let { fecha = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }
                    calendario = false
                }) { Text(L("Listo", "Done")) }
            },
        ) { DatePicker(estado) }
    }
}

@Composable
private fun Titulo(t: String) =
    Text(t, style = MaterialTheme.typography.titleLarge, color = T.texto, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))

@Composable
private fun FilaCita(c: Cita, futura: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Tarjeta(modifier, onClick = if (futura) onClick else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (dia, mes) = fechaCorta(c.fecha)
            Column(
                Modifier.background(if (futura) T.acentoSuave else T.relleno, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(mes, style = MaterialTheme.typography.labelSmall, color = if (futura) T.acento else T.terciario)
                Text(dia, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = if (futura) T.acento else T.secundario)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(fechaLarga(c.fecha), style = MaterialTheme.typography.titleSmall, color = T.texto)
                Text(listOfNotNull(hora12(c.hora).ifBlank { null }, c.servicio).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                c.modalidad?.let { m ->
                    Text(if (m.lowercase().startsWith("virt")) L("Virtual", "Virtual") else L("Presencial", "In person"),
                        style = MaterialTheme.typography.labelSmall, color = T.acento)
                }
            }
            val (texto, fondo, color) = if (futura && c.solicitudPendiente) Triple(L("Cambio pedido", "Change asked"), T.aviso.copy(alpha = 0.15f), T.aviso) else estado(c, futura)
            Etiqueta(texto, fondo, color)
        }
    }
}

@Composable
private fun estado(c: Cita, futura: Boolean): Triple<String, Color, Color> {
    val s = c.status.orEmpty().lowercase()
    return when {
        s in setOf("cancelled", "cancelada") -> Triple(L("Cancelada", "Cancelled"), T.peligro.copy(alpha = 0.12f), T.peligro)
        s in setOf("completed", "completada", "realizada") -> Triple(L("Realizada", "Done"), T.exito.copy(alpha = 0.14f), T.exito)
        futura && diasHasta(c.fecha) == 0L -> Triple(L("Hoy", "Today"), Naranja.copy(alpha = 0.15f), Naranja)
        futura && diasHasta(c.fecha) == 1L -> Triple(L("Mañana", "Tomorrow"), Naranja.copy(alpha = 0.15f), Naranja)
        s in setOf("confirmed", "confirmada") -> Triple(L("Confirmada", "Confirmed"), T.exito.copy(alpha = 0.14f), T.exito)
        futura -> Triple(L("Agendada", "Scheduled"), T.acentoSuave, T.acento)
        else -> Triple(L("Pasada", "Past"), T.relleno, T.terciario)
    }
}

/** Resumen del mes como en "Mis citas" de la web: próximas, realizadas, canceladas y asistencia. */
@Composable
private fun ResumenMes(e: Estado) {
    val mes = java.time.LocalDate.now().toString().take(7)
    val delMes = e.pasadas.filter { it.fecha.startsWith(mes) }
    val realizadas = delMes.count { it.status?.lowercase() in setOf("completed", "completada", "realizada") }
    val canceladas = delMes.count { it.status?.lowercase() in setOf("cancelled", "cancelada") }
    val asistencia = if (delMes.isEmpty()) null else realizadas * 100 / delMes.size
    Tarjeta {
        Text(L("Sesiones de ${e.hijo?.primerNombre.orEmpty()}", "${e.hijo?.primerNombre.orEmpty()}'s sessions"), style = MaterialTheme.typography.titleMedium, color = T.texto)
        Text(L("El centro las programa; aquí las ves todas.", "Your center schedules them; you see them all here."), style = MaterialTheme.typography.bodySmall, color = T.terciario)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Dato(e.proximas.size.toString(), L("Próximas", "Upcoming"))
            Dato(realizadas.toString(), L("Realizadas", "Done"))
            Dato(canceladas.toString(), L("Canceladas", "Cancelled"))
            Dato(asistencia?.let { "$it%" } ?: "—", L("Asistencia", "Attendance"))
        }
    }
}

@Composable
private fun Dato(valor: String, titulo: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, style = MaterialTheme.typography.headlineSmall, color = T.acento, fontWeight = FontWeight.ExtraBold)
        Text(titulo, style = MaterialTheme.typography.labelSmall, color = T.secundario)
    }
}

/** "¿Necesitas un cambio?": escribir, llamar o enviar correo al centro (como en la web). */
@Composable
private fun ContactoCentro(e: Estado) {
    val c = e.centro ?: return
    val tel = c.telefono?.filter { it.isDigit() || it == '+' }.orEmpty()
    if (tel.isBlank() && c.email.isNullOrBlank()) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun abrir(uri: String) = runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Tarjeta {
        Text(L("¿Necesitas un cambio?", "Need a change?"), style = MaterialTheme.typography.titleMedium, color = T.texto)
        Text(L("Cambios, cancelaciones o nuevas citas", "Changes, cancellations or new appointments"), style = MaterialTheme.typography.bodySmall, color = T.terciario)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tel.isNotBlank()) {
                BotonContacto(L("Escribir", "Message"), Modifier.weight(1f)) { abrir("https://wa.me/${tel.filter { it.isDigit() }}") }
                BotonContacto(L("Llamar", "Call"), Modifier.weight(1f)) { abrir("tel:$tel") }
            }
            if (!c.email.isNullOrBlank()) BotonContacto(L("Correo", "Email"), Modifier.weight(1f)) { abrir("mailto:${c.email}") }
        }
    }
}

@Composable
private fun BotonContacto(texto: String, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier.presionable(onClick = onClick).background(T.acentoSuave, RoundedCornerShape(50)).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, style = MaterialTheme.typography.labelLarge, color = T.acento) }
}
