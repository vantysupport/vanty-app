package xyz.vanty.aba.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.BuildConfig
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.notif.Avisos
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.IconoTono
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.MarcaDegradado
import xyz.vanty.aba.ui.theme.Naranja
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.ui.comp.TarjetaIdioma
import xyz.vanty.aba.ui.comp.ZonaCuenta
import xyz.vanty.aba.util.Idioma
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.calcularEdad

@Composable
fun PerfilScreen(e: Estado, vm: AppViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var recordatorio by remember { mutableStateOf(prefs.recordatorioActivo) }
    var hora by remember { mutableIntStateOf(prefs.recordatorioHora) }
    var citas by remember { mutableStateOf(prefs.avisosCitas) }

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { xyz.vanty.aba.ui.comp.CabeceraSub(L("Mi perfil", "My profile")) { vm.irA(xyz.vanty.aba.ui.Pestana.Mas) } }
        item {
            Row(Modifier.aparecer(0).fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).background(MarcaDegradado, CircleShape), contentAlignment = Alignment.Center) {
                    Text(e.perfil?.nombre?.take(1)?.uppercase() ?: "·", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(e.perfil?.nombre ?: L("Mi perfil", "My profile"), style = MaterialTheme.typography.titleLarge, color = T.texto)
                    Text(e.perfil?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                }
            }
        }

        item {
            Tarjeta(Modifier.aparecer(1)) {
                Titulo(Icons.Rounded.ChildCare, L("Mis hijos", "My children"), T.acento)
                e.hijos.forEach { h ->
                    val sel = h.id == e.hijo?.id
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp)
                            .background(if (sel) T.acentoSuave else T.relleno, RoundedCornerShape(14.dp))
                            .presionable { vm.elegirHijo(h) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(h.nombre, style = MaterialTheme.typography.titleSmall, color = T.texto)
                            val edad = calcularEdad(h.nacimiento)
                            Text(listOfNotNull(edad?.let { L("$it años", "$it years old") }, h.diagnosis).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                        }
                        if (sel) Text(L("Viendo", "Viewing"), style = MaterialTheme.typography.labelSmall, color = T.acento)
                    }
                }
            }
        }

        item { TarjetaIdioma(vm, Modifier.aparecer(2)) }
        if (e.estadoIA?.centro == "aceptada") item {
            Tarjeta(Modifier.aparecer(2)) {
                Titulo(Icons.Rounded.AutoAwesome, L("ARIA (inteligencia artificial)", "ARIA (artificial intelligence)"), T.acento)
                Interruptor(L("Usar ARIA para preguntas sobre mi hijo/a", "Use ARIA for questions about my child"), e.estadoIA.propio == "aceptada") { vm.decidirIA(it) }
            }
        }

        item {
            Tarjeta(Modifier.aparecer(3)) {
                Titulo(Icons.Rounded.LocalFireDepartment, L("Recordatorio de racha", "Streak reminder"), Naranja)
                Interruptor(L("Recordarme practicar cada día", "Remind me to practice daily"), recordatorio) {
                    recordatorio = it; prefs.recordatorioActivo = it
                }
                if (recordatorio) {
                    Text(L("¿A qué hora?", "At what time?"), style = MaterialTheme.typography.labelMedium, color = T.secundario, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(8, 12, 16, 18, 19, 20).forEach { h ->
                            val sel = h == hora
                            Text(
                                if (h < 12) "$h:00 AM" else if (h == 12) "12:00 PM" else "${h - 12}:00 PM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else T.secundario,
                                modifier = Modifier.background(if (sel) Naranja else T.relleno, CircleShape)
                                    .presionable { hora = h; prefs.recordatorioHora = h }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = T.borde)
                Titulo(Icons.Rounded.CalendarMonth, L("Citas", "Appointments"), T.acento)
                Interruptor(L("Avisarme la tarde anterior y antes de cada cita", "Remind me the evening before and before each appointment"), citas) {
                    citas = it; prefs.avisosCitas = it
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton({ Avisos.prueba(ctx) }) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(L("Probar notificación", "Test notification"))
                    }
                    if (!Avisos.permitidas(ctx)) TextButton({
                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }) { Text(L("Activar permisos", "Enable"), color = T.peligro) }
                }
            }
        }

        item {
            Tarjeta(Modifier.aparecer(4)) {
                Titulo(Icons.Rounded.Widgets, L("Widget de racha", "Streak widget"), T.exito)
                Text(
                    L("Mantén presionada la pantalla de inicio de tu teléfono → Widgets → Vanty ABA, y lleva tu racha siempre a la vista.",
                        "Long-press your phone's home screen → Widgets → Vanty ABA to keep your streak always in sight."),
                    style = MaterialTheme.typography.bodyMedium, color = T.secundario, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item {
            xyz.vanty.aba.ui.comp.VincularCalendarios(e.perfil?.id, "padre", L("Calendarios vinculados", "Linked calendars"), null, vm::mostrarAviso, Modifier.aparecer(4))
        }
        item { ZonaCuenta(e, vm, Modifier.aparecer(5)) }
        item {
            Text("Vanty ABA ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = T.terciario,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun Titulo(icono: ImageVector, texto: String, tono: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconoTono(icono, tono.copy(alpha = 0.14f), tono, 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, color = T.texto)
    }
}

@Composable
private fun Interruptor(texto: String, valor: Boolean, onCambio: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = T.texto, modifier = Modifier.weight(1f))
        Switch(valor, onCambio, colors = SwitchDefaults.colors(checkedTrackColor = T.exito, checkedThumbColor = Color.White))
    }
}
