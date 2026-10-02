package xyz.vanty.aba.ui.equipo

import androidx.compose.material.icons.rounded.Search
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import xyz.vanty.aba.data.Colega
import xyz.vanty.aba.data.HiloFamilia
import xyz.vanty.aba.data.Paciente
import xyz.vanty.aba.data.RepoChat
import xyz.vanty.aba.data.ResumenContacto
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.TarjetaConsentimientoIA
import xyz.vanty.aba.ui.chat.ConversacionVM
import xyz.vanty.aba.ui.chat.PantallaChat
import xyz.vanty.aba.ui.chat.TipoChat
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.CabeceraSub
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.Vacio
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.fechaCorta

private sealed interface Hilo {
    data class Familia(val h: HiloFamilia) : Hilo
    data class Equipo(val c: Colega) : Hilo
}

/** Chat del equipo: conversaciones con familias y, si el plan lo incluye, el chat interno del equipo. */
@Composable
fun ChatEquipoScreen(e: EstadoEquipo, app: Estado, pad: PaddingValues) {
    val yo = e.perfil?.id ?: return
    val conEquipo = app.on("chat_especialistas") && e.rol != Rol.Secretaria
    var seccion by rememberSaveable { mutableStateOf(0) }
    var abierto by remember { mutableStateOf<Hilo?>(null) }
    var familias by remember { mutableStateOf<List<HiloFamilia>?>(null) }
    var colegas by remember { mutableStateOf<List<Colega>?>(null) }
    var resumen by remember { mutableStateOf<Map<String, ResumenContacto>>(emptyMap()) }
    var buscar by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            familias = runCatching { RepoChat.hilosFamilias(yo) }.getOrNull() ?: familias ?: emptyList()
            if (conEquipo) {
                if (colegas == null) colegas = runCatching { RepoChat.colegas(yo) }.getOrDefault(emptyList())
                resumen = runCatching { RepoChat.resumenEquipo() }.getOrDefault(resumen)
            }
            delay(15_000)
        }
    }

    abierto?.let { h ->
        BackHandler { abierto = null }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                CabeceraSub(when (h) { is Hilo.Familia -> L("Familia de ${h.h.paciente}", "${h.h.paciente}'s family"); is Hilo.Equipo -> h.c.nombre ?: "—" }) { abierto = null }
            }
            val tipo = when (h) { is Hilo.Familia -> TipoChat.Centro(h.h.childId, yo); is Hilo.Equipo -> TipoChat.Equipo(h.c.id, yo) }
            val chat: ConversacionVM = viewModel(key = tipo.clave)
            LaunchedEffect(tipo.clave) { chat.abrir(tipo) }
            val c by chat.e.collectAsStateWithLifecycle()
            PantallaChat(c, chat::enviar, refrescoCada = 6_000, onRefrescar = { chat.refrescar() },
                vacio = { AriaFlotando(Aria.POSE_8, 120.dp); Text(L("Escribe el primer mensaje", "Write the first message"), color = T.secundario) })
        }
        return
    }

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        if (conEquipo) item {
            Row(Modifier.fillMaxWidth().background(T.relleno, RoundedCornerShape(16.dp)).padding(4.dp)) {
                listOf(L("Familias", "Families") to (familias?.sumOf { it.sinLeer } ?: 0), L("Equipo", "Team") to resumen.values.sumOf { it.unread })
                    .forEachIndexed { i, (t, n) ->
                        val activo = i == seccion
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (activo) T.tarjeta else Color.Transparent)
                                .presionable { seccion = i }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(t, style = MaterialTheme.typography.titleSmall, color = if (activo) T.acento else T.secundario)
                            if (n > 0) { Spacer(Modifier.width(6.dp)); Etiqueta("$n", T.peligro, Color.White) }
                        }
                    }
            }
        }
        item {
            androidx.compose.material3.OutlinedTextField(
                buscar, { buscar = it }, singleLine = true, shape = RoundedCornerShape(18.dp),
                placeholder = { Text(if (seccion == 0 || !conEquipo) L("Buscar familia", "Search family") else L("Buscar en el equipo", "Search team")) },
                leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Search, null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (seccion == 0 || !conEquipo) {
            if (familias?.isEmpty() == true) item { Tarjeta { Vacio(Aria.SENTADA, L("Sin conversaciones", "No conversations"), L("Cuando una familia escriba, la verás aquí.", "When a family writes, you'll see it here.")) } }
            val filtradas = familias.orEmpty().filter { buscar.isBlank() || it.paciente.contains(buscar.trim(), ignoreCase = true) }
            itemsIndexed(filtradas, key = { _, h -> "f" + h.childId }) { i, h ->
                FilaHilo(h.paciente, L("Familia", "Family") + " · " + h.ultimo.ifBlank { L("Toca para escribir", "Tap to write") }, h.fecha, h.sinLeer, Modifier.aparecer(i.coerceAtMost(12))) { abierto = Hilo.Familia(h) }
            }
        } else {
            val ordenados = colegas.orEmpty().filter { buscar.isBlank() || (it.nombre ?: "").contains(buscar.trim(), ignoreCase = true) }
                .sortedByDescending { resumen[it.id]?.last?.fecha ?: "" }
            itemsIndexed(ordenados, key = { _, c -> "e" + c.id }) { i, c ->
                val r = resumen[c.id]
                FilaHilo(c.nombre ?: "—", r?.last?.content ?: (c.specialty ?: L("Toca para escribir", "Tap to write")), r?.last?.fecha, r?.unread ?: 0, Modifier.aparecer(i)) { abierto = Hilo.Equipo(c) }
            }
        }
    }
}

@Composable
private fun FilaHilo(nombre: String, ultimo: String, fecha: String?, sinLeer: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().background(T.tarjeta, RoundedCornerShape(18.dp)).presionable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(nombre)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, style = MaterialTheme.typography.titleSmall, color = T.texto, maxLines = 1)
            Text(ultimo, style = MaterialTheme.typography.bodySmall, color = if (sinLeer > 0) T.texto else T.secundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            fecha?.let { val (d, m) = fechaCorta(it); Text("$d $m", style = MaterialTheme.typography.labelSmall, color = T.terciario) }
            if (sinLeer > 0) Text("$sinLeer", color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp).background(T.acento, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp))
        }
    }
}

/** ARIA para el equipo: se elige un paciente y se conversa sobre él (/api/admin-chat). */
@Composable
fun AriaEquipoScreen(e: EstadoEquipo, app: Estado, appVm: AppViewModel, vm: EquipoViewModel, pad: PaddingValues) {
    var paciente by remember { mutableStateOf<Paciente?>(null) }
    val motivo = app.estadoIA?.motivo
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            CabeceraSub("ARIA") { vm.irA(TabEquipo.Mas) }
        }
        if (motivo != null) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                TarjetaConsentimientoIA(motivo, app.estadoIA.puedeDecidirCentro, app.guardandoIA, { appVm.decidirIA(true) }, { vm.irA(TabEquipo.Mas) })
            }
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            e.pacientes.forEach { p ->
                val sel = p.id == paciente?.id
                Text(p.nombre, style = MaterialTheme.typography.labelLarge, color = if (sel) Color.White else T.secundario,
                    modifier = Modifier.presionable { paciente = p }.background(if (sel) T.acento else T.relleno, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
        val p = paciente
        if (p == null) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                AriaFlotando(Aria.POSE_8, 150.dp)
                Spacer(Modifier.height(12.dp))
                Text(L("¿Sobre qué paciente conversamos?", "Which patient shall we talk about?"), style = MaterialTheme.typography.titleLarge, color = T.texto, textAlign = TextAlign.Center)
                Text(L("Elige un paciente arriba. ARIA usa su historial, programas y sesiones para responderte.",
                    "Pick a patient above. ARIA uses their history, programs and sessions to answer."), style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center)
            }
            return@Column
        }
        val chat: ConversacionVM = viewModel(key = "aria-eq-${p.id}")
        LaunchedEffect(p.id) { chat.abrir(TipoChat.Aria(p.id, p.nombre, e.perfil?.id ?: "", equipo = true)) }
        val c by chat.e.collectAsStateWithLifecycle()
        LaunchedEffect(c.sinIA) { if (c.sinIA != null) { appVm.refrescarIA(); chat.consentimientoResuelto() } }
        PantallaChat(
            c, chat::enviar, placeholder = L("Pregunta sobre ${p.nombre}…", "Ask about ${p.nombre}…"),
            sugerencias = listOf(L("Resumen del progreso", "Progress summary"), L("¿Qué objetivos están estancados?", "Which goals are stuck?"), L("Ideas para la próxima sesión", "Ideas for the next session")),
            vacio = { AriaFlotando(Aria.SALUDO, 130.dp); Text(L("Pregúntame sobre ${p.nombre}", "Ask me about ${p.nombre}"), style = MaterialTheme.typography.titleMedium, color = T.texto) },
        )
    }
}
