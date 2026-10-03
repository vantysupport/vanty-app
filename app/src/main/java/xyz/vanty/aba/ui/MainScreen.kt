package xyz.vanty.aba.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.notif.Avisos
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.BarraNav
import xyz.vanty.aba.ui.comp.ItemBarra
import xyz.vanty.aba.ui.comp.Llama
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.screens.AriaFamiliaScreen
import xyz.vanty.aba.ui.screens.ChatCentroScreen
import xyz.vanty.aba.ui.screens.CitasScreen
import xyz.vanty.aba.ui.screens.DocumentosScreen
import xyz.vanty.aba.ui.screens.FormulariosScreen
import xyz.vanty.aba.ui.screens.EvaluacionInicialScreen
import xyz.vanty.aba.ui.screens.MasFamiliaScreen
import xyz.vanty.aba.ui.screens.RecursosScreen
import xyz.vanty.aba.ui.screens.HomeScreen
import xyz.vanty.aba.ui.screens.PerfilScreen
import xyz.vanty.aba.ui.screens.PracticaScreen
import xyz.vanty.aba.ui.theme.MarcaDegradado
import xyz.vanty.aba.ui.theme.Naranja
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L

@Composable
fun MainScreen(e: Estado, vm: AppViewModel) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(e.aviso) {
        e.aviso?.let { snack.showSnackbar(it); vm.avisoMostrado() }
    }
    PedirPermisoNotificaciones()

    // El apartado de familias es la web real (vanty.xyz/padre en "modo app"): mismas pantallas, misma
    // navegación y mismos datos que en la computadora. Lo nativo queda para notificaciones y widgets.
    val vistaWeb = when (e.pestana) {
        Pestana.Citas -> "miscitas"
        Pestana.Aria -> "chat"
        Pestana.Chat -> "chat-familias"
        Pestana.Practicar -> "engagement"
        Pestana.Perfil -> "profile"
        Pestana.Recursos, Pestana.Formularios -> "misformularios"
        Pestana.Documentos -> "documentos"
        Pestana.Evaluacion -> "evaluacion-inicial"
        else -> "home"
    }
    Box(Modifier.fillMaxSize().background(T.fondo)) {
        xyz.vanty.aba.ui.comp.PanelWeb("padre", vistaWeb, Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(), alSalir = vm::salir)
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
    }
}

@Composable
private fun BarraSuperior(e: Estado, vm: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selector de hijo/a
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.presionable(habilitado = e.hijos.size > 1) { menu = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).background(MarcaDegradado, CircleShape), contentAlignment = Alignment.Center) {
                    Text(e.hijo?.nombre?.take(1)?.uppercase() ?: "·", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(e.centro?.name?.let { "$it · " }.orEmpty() + L("Familia de", "Family of"), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = T.terciario)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.hijo?.nombre ?: L("Sin paciente", "No patient"), style = MaterialTheme.typography.titleMedium, color = T.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e.hijos.size > 1) Icon(Icons.Rounded.ExpandMore, null, tint = T.secundario)
                    }
                }
            }
            DropdownMenu(menu, { menu = false }) {
                e.hijos.forEach { h ->
                    DropdownMenuItem(text = { Text(h.nombre) }, onClick = { menu = false; vm.elegirHijo(h) })
                }
            }
        }
        // Chip de racha
        Row(
            Modifier.presionable { vm.irA(Pestana.Practicar) }
                .background(if (e.racha.hoy) Naranja.copy(alpha = 0.14f) else T.relleno, CircleShape)
                .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Llama(22.dp, e.racha.hoy)
            Spacer(Modifier.width(4.dp))
            Text("${e.racha.dias}", fontWeight = FontWeight.ExtraBold, color = if (e.racha.hoy) Naranja else T.terciario, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private data class ItemNav(val p: Pestana, val icono: ImageVector, val es: String, val en: String)

private val ITEMS = listOf(
    ItemNav(Pestana.Inicio, Icons.Rounded.Home, "Inicio", "Home"),
    ItemNav(Pestana.Citas, Icons.Rounded.CalendarMonth, "Citas", "Visits"),
    ItemNav(Pestana.Aria, Icons.Rounded.AutoAwesome, "ARIA", "ARIA"),
    ItemNav(Pestana.Chat, Icons.Rounded.Forum, "Chat", "Chat"),
    ItemNav(Pestana.Mas, Icons.Rounded.GridView, "Más", "More"),
)

@Composable
private fun BarraInferior(actual: Pestana, onClick: (Pestana) -> Unit) =
    BarraNav(ITEMS.map { ItemBarra(it.p, it.icono, L(it.es, it.en)) }, if (actual.enBarra) actual else Pestana.Mas, onClick)

/** Para no insistir dos veces en la misma apertura de la app (se vuelve a preguntar la próxima vez que se abra). */
private var permisoPreguntadoAhora = false

/**
 * Al entrar: si las notificaciones están apagadas, ARIA explica para qué sirven y pide el permiso del sistema.
 * Si la persona ya lo negó antes (Android no deja volver a mostrar el cuadro), el botón abre los ajustes de
 * notificaciones de la app. Se vuelve a preguntar en cada apertura hasta que estén activadas.
 */
@Composable
fun PedirPermisoNotificaciones(equipo: Boolean = false) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var mostrar by remember { mutableStateOf(!permisoPreguntadoAhora && !Avisos.permitidas(ctx)) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) Avisos.prueba(ctx)
    }
    if (!mostrar) return
    val actividad = ctx as? android.app.Activity
    // En Android 13+ el cuadro del sistema solo aparece si no se negó "para siempre"; si no, toca ir a ajustes
    val conCuadro = Build.VERSION.SDK_INT >= 33 &&
        ContextCompatPermiso.falta(ctx) &&
        (!prefs.permisoPedido || actividad?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true)
    fun cerrar() { mostrar = false; permisoPreguntadoAhora = true }
    AlertDialog(
        onDismissRequest = { cerrar() },
        icon = { AriaFlotando(Aria.SALUDO, 110.dp) },
        title = {
            Text(if (equipo) L("¿Te aviso de tus sesiones?", "Can I remind you about your sessions?")
            else L("¿Te aviso para no perder la racha?", "Can I remind you about your streak?"))
        },
        text = {
            Text((if (equipo) L("ARIA te enviará el resumen de tu día cada mañana y te avisará antes de cada sesión. Nada de spam.",
                "ARIA will send you your day's summary every morning and remind you before each session. No spam.")
            else L("ARIA te enviará un recordatorio para practicar en casa y te avisará antes de cada cita. Nada de spam.",
                "ARIA will remind you to practice at home and let you know before each appointment. No spam.")) +
                if (conCuadro) "" else L("\n\nActívalas en Ajustes → Notificaciones.", "\n\nTurn them on in Settings → Notifications."))
        },
        confirmButton = {
            TextButton({
                cerrar()
                if (conCuadro) {
                    prefs.permisoPedido = true
                    lanzador.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }) { Text(if (conCuadro) L("¡Sí, avísame!", "Yes, remind me!") else L("Abrir ajustes", "Open settings"), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton({ cerrar() }) { Text(L("Ahora no", "Not now")) } },
    )
}

private object ContextCompatPermiso {
    fun falta(ctx: android.content.Context) = Build.VERSION.SDK_INT >= 33 &&
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
}
