package xyz.vanty.aba.ui.equipo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.BuildConfig
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.notif.Avisos
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.IconoTono
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.TarjetaIdioma
import xyz.vanty.aba.ui.comp.ZonaCuenta
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L

fun nombreRol(r: Rol) = when (r) {
    Rol.Especialista -> L("Especialista", "Specialist")
    Rol.Secretaria -> L("Secretaría", "Front desk")
    Rol.Admin -> L("Administración", "Administrator")
    Rol.Familia -> L("Familia", "Family")
    Rol.Otro -> ""
}

@Composable
fun PerfilEquipoScreen(e: EstadoEquipo, app: Estado, appVm: AppViewModel, vm: EquipoViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var resumen by remember { mutableStateOf(prefs.resumenDiario) }
    var sesiones by remember { mutableStateOf(prefs.avisosCitas) }

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { xyz.vanty.aba.ui.comp.CabeceraSub(L("Mi perfil", "My profile")) { vm.irA(TabEquipo.Mas) } }
        item {
            Row(Modifier.aparecer(0).fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(e.perfil?.nombre ?: "?", 64.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(e.perfil?.nombre ?: L("Mi perfil", "My profile"), style = MaterialTheme.typography.titleLarge, color = T.texto)
                    Text(app.perfil?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                    Spacer(Modifier.height(4.dp))
                    Etiqueta(nombreRol(e.rol), T.acentoSuave, T.acento)
                }
            }
        }
        item { TarjetaIdioma(appVm, Modifier.aparecer(1)) { vm.cambioDeIdioma() } }
        item {
            Tarjeta(Modifier.aparecer(2)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoTono(Icons.Rounded.NotificationsActive, T.aviso.copy(alpha = 0.15f), T.aviso, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(L("Avisos de ARIA", "ARIA notifications"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                }
                Interruptor(
                    when (e.rol) {
                        Rol.Admin -> L("Resumen del centro cada mañana", "Center summary every morning")
                        Rol.Secretaria -> L("Resumen de la mañana y cobros de la tarde", "Morning summary and afternoon payments")
                        else -> L("Resumen de mi día cada mañana", "My day summary every morning")
                    }, resumen,
                ) { resumen = it; prefs.resumenDiario = it }
                if (e.rol == Rol.Especialista) {
                    Interruptor(L("Aviso 15 min antes de cada sesión", "Alert 15 min before each session"), sesiones) { sesiones = it; prefs.avisosCitas = it }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = T.borde)
                TextButton({ Avisos.prueba(ctx) }) {
                    Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(L("Probar notificación", "Test notification"))
                }
            }
        }
        item {
            Tarjeta(Modifier.aparecer(3)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoTono(Icons.Rounded.Widgets, T.exito.copy(alpha = 0.15f), T.exito, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(L("Widget \"Agenda de hoy\"", "\"Today's agenda\" widget"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                }
                Text(
                    L("Mantén presionada la pantalla de inicio → Widgets → Vanty ABA → Agenda de hoy. Verás tu progreso y la próxima sesión sin abrir la app.",
                        "Long-press your home screen → Widgets → Vanty ABA → Today's agenda. See your progress and next session without opening the app."),
                    style = MaterialTheme.typography.bodyMedium, color = T.secundario, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        // Mi perfil del especialista en la web tiene la sección de calendarios
        if (e.rol == xyz.vanty.aba.data.Rol.Especialista) item {
            xyz.vanty.aba.ui.comp.VincularCalendarios(e.perfil?.id, "especialista", L("Calendarios", "Calendars"), null, vm::aviso, Modifier.aparecer(3))
        }
        item { ZonaCuenta(app, appVm, Modifier.aparecer(4)) }
        item {
            Text("Vanty ABA ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = T.terciario,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Interruptor(texto: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = T.texto, modifier = Modifier.weight(1f))
        Switch(valor, onCambio, colors = SwitchDefaults.colors(checkedTrackColor = T.exito, checkedThumbColor = Color.White))
    }
}
