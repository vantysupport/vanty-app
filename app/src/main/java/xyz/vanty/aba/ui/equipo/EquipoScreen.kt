package xyz.vanty.aba.ui.equipo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.vanty.aba.data.EstadoCita
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.PedirPermisoNotificaciones
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.BarraNav
import xyz.vanty.aba.ui.comp.FestejoPantalla
import xyz.vanty.aba.ui.comp.ItemBarra
import xyz.vanty.aba.ui.comp.Llama
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.monedaFmt

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EquipoScreen(app: Estado, appVm: AppViewModel) {
    val vm: EquipoViewModel = viewModel()
    val perfil = app.perfil ?: return
    LaunchedEffect(perfil.id) { vm.iniciar(perfil) }
    val e by vm.e.collectAsStateWithLifecycle()

    // Al tocar una notificación se abre la sección correspondiente
    LaunchedEffect(app.vista) { app.vista?.let { vm.irAVista(it); appVm.vistaUsada() } }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(e.aviso) { e.aviso?.let { snack.showSnackbar(it); vm.avisoMostrado() } }
    PedirPermisoNotificaciones(equipo = true)
    // Atrás: de una sección abierta desde "Más" vuelve a "Más"; de una pestaña vuelve a la primera
    val primera = e.tabs(app::on).first()
    androidx.activity.compose.BackHandler(enabled = e.tab != primera) {
        vm.irA(if (e.tab in e.tabs(app::on)) primera else TabEquipo.Mas)
    }

    Box(Modifier.fillMaxSize().background(T.fondo)) {
        // Con el teclado abierto: el contenido sube sobre el teclado y la barra inferior se oculta
        Column(Modifier.fillMaxSize().imePadding()) {
            Cabecera(e)
            AnimatedContent(
                targetState = e.tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(spring(0.85f, 400f)) { it / 5 * dir } + fadeIn()) togetherWith
                        (slideOutHorizontally(spring(0.85f, 400f)) { -it / 5 * dir } + fadeOut())
                },
                modifier = Modifier.weight(1f), label = "tabs",
            ) { tab ->
                val pad = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)
                // Los apartados del equipo se abren con la pantalla real de la web (mismas funciones que vanty.xyz)
                val web = apartadoWeb(tab, e.perfil?.role)
                if (web != null) xyz.vanty.aba.ui.comp.PanelWeb(web.first, web.second, alSalir = appVm::salir)
                else when (tab) {
                    TabEquipo.Hoy -> when (e.rol) {
                        Rol.Admin -> InicioAdminScreen(e, vm, pad)
                        Rol.Secretaria -> InicioSecretariaScreen(e, app, vm, pad)
                        else -> InicioEspecialistaScreen(e, vm, pad)
                    }
                    TabEquipo.Agenda -> AgendaScreen(e, vm, pad)
                    TabEquipo.Pacientes -> PacientesScreen(e, vm, pad)
                    TabEquipo.Cobros -> CobrosScreen(e, vm, pad)
                    TabEquipo.Perfil -> PerfilEquipoScreen(e, app, appVm, vm, pad)
                    TabEquipo.Chat -> ChatEquipoScreen(e, app, pad)
                    TabEquipo.Mas -> MasEquipoScreen(e, app, vm, pad)
                    TabEquipo.Aria -> AriaEquipoScreen(e, app, appVm, vm, pad)
                    TabEquipo.Reportes -> ReportesScreen(e, vm, pad)
                    TabEquipo.Usuarios -> UsuariosScreen(e, vm, pad)
                    TabEquipo.Recursos -> RecursosEquipoScreen(e, app, vm, pad)
                    TabEquipo.Inteligencia -> InteligenciaScreen(e, app, vm, pad)
                }
            }
            val tabs = e.tabs(app::on)
            if (!androidx.compose.foundation.layout.WindowInsets.isImeVisible) BarraNav(tabs.map { item(it, e) }, if (e.tab in tabs) e.tab else TabEquipo.Mas, vm::irA)
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))

        AnimatedVisibility(e.festejo != null, enter = fadeIn(), exit = fadeOut()) {
            when (val f = e.festejo) {
                is Festejo.DiaCompleto -> FestejoPantalla(
                    L("¡Día completado!", "Day complete!"),
                    L("Registraste tus ${f.sesiones} sesiones de hoy. ¡Qué gran trabajo! 👏", "You recorded all ${f.sesiones} sessions today. Great work! 👏"),
                    Aria.CELEBRA, vm::cerrarFestejo, grande = "${f.sesiones}/${f.sesiones}",
                )
                is Festejo.Cobro -> FestejoPantalla(
                    if (f.saldada) L("¡Deuda saldada!", "Debt paid off!") else L("¡Cobro registrado!", "Payment recorded!"),
                    L("Se guardó en el sistema del centro. 💰", "Saved to the center's records. 💰"),
                    Aria.POSE_4, vm::cerrarFestejo, monedas = true, grande = monedaFmt(f.monto, e.moneda),
                )
                null -> Unit
            }
        }
    }
}

@Composable
fun item(t: TabEquipo, e: EstadoEquipo) = when (t) {
    TabEquipo.Hoy -> ItemBarra(t, Icons.Rounded.Home, L("Inicio", "Home"))
    TabEquipo.Agenda -> ItemBarra(t, Icons.Rounded.CalendarMonth, L("Agenda", "Schedule"))
    TabEquipo.Pacientes -> ItemBarra(t, Icons.Rounded.Groups, L("Pacientes", "Patients"))
    TabEquipo.Cobros -> ItemBarra(t, Icons.Rounded.Payments, L("Pagos", "Payments"), e.deudas.size)
    TabEquipo.Perfil -> ItemBarra(t, Icons.Rounded.Person, L("Mi Perfil", "My Profile"))
    TabEquipo.Chat -> ItemBarra(t, Icons.Rounded.Forum, if (e.esJefe) L("Chat Equipo", "Team Chat") else L("Chat", "Chat"))
    TabEquipo.Mas -> ItemBarra(t, Icons.Rounded.GridView, L("Más", "More"))
    TabEquipo.Aria -> ItemBarra(t, Icons.Rounded.AutoAwesome, "ARIA")
    TabEquipo.Inteligencia -> ItemBarra(t, Icons.Rounded.Insights, L("Análisis", "Analysis"))
    TabEquipo.Reportes -> ItemBarra(t, Icons.Rounded.BarChart, L("Reportes", "Reports"))
    TabEquipo.Recursos -> ItemBarra(t, Icons.AutoMirrored.Rounded.MenuBook, L("Recursos", "Resources"))
    TabEquipo.Usuarios -> ItemBarra(t, Icons.Rounded.ManageAccounts, L("Usuarios", "Users"))
}

@Composable
private fun Cabecera(e: EstadoEquipo) {
    val activas = e.hoy.filter { it.estado != EstadoCita.Cancelada }
    val hechas = activas.count { it.estado == EstadoCita.Realizada }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(e.perfil?.nombre ?: "?", 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(nombreRol(e.rol), style = MaterialTheme.typography.labelSmall, color = T.terciario)
            Text(e.perfil?.nombre ?: "", style = MaterialTheme.typography.titleMedium, color = T.texto, maxLines = 1)
        }
        // Chip del día: sesiones realizadas / total, con la llama encendida al completar
        val completo = activas.isNotEmpty() && hechas == activas.size
        Row(
            Modifier.background(if (completo) T.exito.copy(alpha = 0.14f) else T.relleno, CircleShape).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Llama(20.dp, completo)
            Spacer(Modifier.width(4.dp))
            Text("$hechas/${activas.size}", fontWeight = FontWeight.ExtraBold, color = if (completo) T.exito else T.secundario, style = MaterialTheme.typography.titleSmall)
        }
    }
}


/** Panel y vista de la web para cada apartado (los mismos ids que usa ?vista= en vanty.xyz). null = pantalla nativa. */
fun apartadoWeb(tab: TabEquipo, rol: String?): Pair<String, String>? {
    val panel = when (rol) { "jefe", "admin", "terapeuta" -> "admin"; "secretaria" -> "secretaria"; else -> "especialista" }
    val vista = when (panel) {
        "admin" -> when (tab) {
            TabEquipo.Hoy -> "inicio"; TabEquipo.Agenda -> "agenda"; TabEquipo.Pacientes -> "ninos"; TabEquipo.Inteligencia -> "inteligencia"
            TabEquipo.Cobros -> "pagos"; TabEquipo.Reportes -> "reportes-financieros"; TabEquipo.Recursos -> "recursos-adicionales"
            TabEquipo.Chat -> "chat-especialistas"; TabEquipo.Usuarios -> "usuarios"; TabEquipo.Perfil -> "config"
            else -> null
        }
        "secretaria" -> when (tab) {
            TabEquipo.Hoy -> "inicio"; TabEquipo.Agenda -> "agenda"; TabEquipo.Cobros -> "pagos"; TabEquipo.Reportes -> "reportes-financieros"
            TabEquipo.Recursos -> "recursos-adicionales"; TabEquipo.Perfil -> "perfil"
            else -> null
        }
        else -> when (tab) {
            TabEquipo.Hoy -> "inicio"; TabEquipo.Agenda -> "agenda"; TabEquipo.Pacientes -> "pacientes"; TabEquipo.Inteligencia -> "prediccion"
            TabEquipo.Chat -> "evaluaciones"; TabEquipo.Perfil -> "perfil"
            else -> null
        }
    } ?: return null
    return panel to vista
}
