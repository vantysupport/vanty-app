package xyz.vanty.aba.ui.equipo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.comp.Baldosa
import xyz.vanty.aba.ui.comp.Mosaico
import xyz.vanty.aba.ui.comp.Pintura
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L

/**
 * "Más" del equipo: el resto del menú web de su rol (lo que no cabe en la barra), con los mismos nombres
 * y en el mismo orden, filtrado por el plan del centro (/api/control). Sin precios ni enlaces de compra.
 * El Cerebro IA aparece como "Disponible en la PC".
 */
@Composable
fun MasEquipoScreen(e: EstadoEquipo, app: Estado, vm: EquipoViewModel, pad: PaddingValues) {
    val enBarra = e.tabs(app::on)
    val baldosas = buildList {
        e.apartados(app::on).filter { it !in enBarra }.forEach { t ->
            when (t) {
                TabEquipo.Pacientes -> add(Baldosa("pacientes", L("Pacientes", "Patients"), L("Fichas, programas y documentos", "Records, programs, documents"), Icons.Rounded.Groups, Pintura.verde))
                TabEquipo.Agenda -> add(Baldosa("agenda", L("Agenda", "Schedule"), L("Citas del centro", "Center appointments"), Icons.Rounded.CalendarMonth, Pintura.azul))
                TabEquipo.Inteligencia -> {
                    add(Baldosa("inteligencia", L("Análisis Predictivo", "Predictive Analysis"), L("Predicciones y patrones", "Predictions & patterns"), Icons.Rounded.Insights, Pintura.morado))
                    if (e.rol == Rol.Admin) add(Baldosa("cerebro", L("Cerebro IA", "AI Brain"), "", Icons.Rounded.Psychology, Pintura.gris, soloPc = true))
                }
                TabEquipo.Cobros -> add(Baldosa("cobros", L("Pagos", "Payments"), L("Cobros, abonos y tarifas", "Charges, payments, rates"), Icons.Rounded.Payments, Pintura.azul))
                TabEquipo.Reportes -> add(Baldosa("reportes", L("Reportes Financieros", "Financial Reports"), L("Ingresos y métodos", "Income and methods"), Icons.Rounded.BarChart, Pintura.turquesa))
                TabEquipo.Recursos -> add(Baldosa("recursos", L("Recursos Adicionales", "Additional Resources"), L("Materiales, tienda y terapias", "Materials, store, therapies"), Icons.AutoMirrored.Rounded.MenuBook, Pintura.verde))
                TabEquipo.Chat -> add(Baldosa("chat", if (e.esJefe) L("Chat Equipo", "Team Chat") else L("Chat", "Chat"), L("Mensajes del equipo", "Team messages"), Icons.Rounded.Forum, Pintura.azul))
                TabEquipo.Usuarios -> add(Baldosa("usuarios", L("Usuarios", "Users"), L("Equipo, familias e invitaciones", "Team, families, invites"), Icons.Rounded.ManageAccounts, Pintura.rosa))
                TabEquipo.Perfil -> add(Baldosa("perfil", L("Mi Perfil", "My Profile"), L("Idioma, avisos y cuenta", "Language, alerts, account"), Icons.Rounded.Person, Pintura.morado))
                else -> Unit
            }
        }
    }
    LazyColumn(contentPadding = pad) {
        item {
            Text(L("Más", "More"), style = MaterialTheme.typography.headlineSmall, color = T.texto, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            Mosaico(baldosas) { b ->
                when (b.clave) {
                    "pacientes" -> vm.irA(TabEquipo.Pacientes)
                    "agenda" -> vm.irA(TabEquipo.Agenda)
                    "cobros" -> vm.irA(TabEquipo.Cobros)
                    "perfil" -> vm.irA(TabEquipo.Perfil)
                    "reportes" -> vm.irA(TabEquipo.Reportes)
                    "usuarios" -> vm.irA(TabEquipo.Usuarios)
                    "recursos" -> vm.irA(TabEquipo.Recursos)
                    "inteligencia" -> vm.irA(TabEquipo.Inteligencia)
                    "chat" -> vm.irA(TabEquipo.Chat)
                    else -> vm.aviso(L("Esta herramienta está disponible en la versión de PC.", "This tool is available on the desktop version."))
                }
            }
        }
    }
}
