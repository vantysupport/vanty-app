package xyz.vanty.aba.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.TarjetaConsentimientoIA
import xyz.vanty.aba.ui.chat.ConversacionVM
import xyz.vanty.aba.ui.chat.PantallaChat
import xyz.vanty.aba.ui.chat.TipoChat
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L

/** ARIA para familias: pide el consentimiento de IA si falta y luego conversa sobre el hijo/a elegido. */
@Composable
fun AriaFamiliaScreen(e: Estado, vm: AppViewModel) {
    val hijo = e.hijo
    val perfil = e.perfil
    if (hijo == null || perfil == null) { SinHijo(); return }
    val motivo = e.estadoIA?.motivo
    if (motivo != null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TarjetaConsentimientoIA(motivo, e.estadoIA.puedeDecidirCentro, e.guardandoIA, { vm.decidirIA(true) }, { vm.irA(xyz.vanty.aba.ui.Pestana.Inicio) })
        }
        return
    }
    val chat: ConversacionVM = viewModel(key = "aria-${hijo.id}")
    LaunchedEffect(hijo.id) { chat.abrir(TipoChat.Aria(hijo.id, hijo.nombre, perfil.id, equipo = false)) }
    val c by chat.e.collectAsStateWithLifecycle()
    LaunchedEffect(c.sinIA) { if (c.sinIA != null) { vm.refrescarIA(); chat.consentimientoResuelto() } }
    PantallaChat(
        c, chat::enviar,
        placeholder = L("Pregúntale a ARIA…", "Ask ARIA…"),
        sugerencias = listOf(
            L("¿Cómo va ${hijo.primerNombre}?", "How is ${hijo.primerNombre} doing?"),
            L("Ideas para practicar hoy", "Ideas to practice today"),
            L("¿Cómo manejo una rabieta?", "How do I handle a tantrum?"),
            L("Me siento cansada/o", "I feel tired"),
        ),
        vacio = {
            AriaFlotando(Aria.SALUDO, 150.dp)
            Spacer(Modifier.height(12.dp))
            Text(L("¡Hola! Soy ARIA 💙", "Hi! I'm ARIA 💙"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Text(
                L("Pregúntame sobre el progreso de ${hijo.primerNombre}, ideas para casa o cómo te sientes. Estoy para ayudarte.",
                    "Ask me about ${hijo.primerNombre}'s progress, ideas for home or how you feel. I'm here to help."),
                style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
            )
        },
    )
}

/** Chat de la familia con el equipo del centro (se actualiza solo mientras está abierto). */
@Composable
fun ChatCentroScreen(e: Estado) {
    val hijo = e.hijo
    val perfil = e.perfil
    if (hijo == null || perfil == null) { SinHijo(); return }
    val chat: ConversacionVM = viewModel(key = "centro-${hijo.id}")
    LaunchedEffect(hijo.id) { chat.abrir(TipoChat.Centro(hijo.id, perfil.id)) }
    val c by chat.e.collectAsStateWithLifecycle()
    PantallaChat(
        c, chat::enviar, refrescoCada = 6_000, onRefrescar = { chat.refrescar() },
        placeholder = L("Escribe al equipo de ${e.centro?.name ?: "tu centro"}…", "Write to ${e.centro?.name ?: "your center"}'s team…"),
        vacio = {
            AriaFlotando(Aria.POSE_8, 140.dp)
            Spacer(Modifier.height(12.dp))
            Text(L("Habla con el equipo", "Talk to the team"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Text(L("Los terapeutas de ${hijo.primerNombre} leerán tus mensajes aquí.", "${hijo.primerNombre}'s therapists will read your messages here."),
                style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        },
    )
}

/** Sin hijos vinculados todavía: el centro debe vincularlo. */
@Composable
fun SinHijo() {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        AriaFlotando(Aria.SENTADA, 150.dp)
        Spacer(Modifier.height(12.dp))
        Text(L("Tu centro vinculará a tu hijo/a", "Your center will link your child"), style = MaterialTheme.typography.headlineSmall, color = T.texto, textAlign = TextAlign.Center)
        Text(L("Cuando lo hagan, aquí verás su progreso, citas y actividades.", "Once they do, you'll see their progress, appointments and activities here."),
            style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center)
    }
}
