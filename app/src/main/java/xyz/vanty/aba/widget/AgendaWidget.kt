package xyz.vanty.aba.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import xyz.vanty.aba.R
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.hora12
import java.time.LocalDate
import java.time.LocalTime

/**
 * Widget "Agenda": la lista de citas con su hora y estado.
 *  • Equipo: las sesiones de hoy (las que faltan primero; la especialista solo ve las suyas).
 *  • Familia: las próximas citas de su peque.
 */
class AgendaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(250.dp, 280.dp)))

    private data class Item(val hora: String, val titulo: String, val sub: String?, val estado: String, val pasada: Boolean)
    private data class Datos(val titulo: String, val contador: String?, val items: List<Item>, val vacio: String, val vista: String, val sinSesion: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = datos(Prefs(context))
        provideContent { Contenido(d) }
    }

    private fun datos(p: Prefs): Datos {
        val rol = rolDe(p)
        if (p.usuarioId == null) return Datos(L("Agenda", "Schedule"), null, emptyList(), L("Inicia sesión en Vanty", "Sign in to Vanty"), "inicio", true)
        if (rol == Rol.Familia) {
            val items = p.citasFamilia.map { it.split("|") }.map { c ->
                Item(hora12(c.getOrElse(1) { "" }).ifBlank { "—" }, cuando(c[0], ""),
                    c.getOrElse(2) { "" }.ifBlank { null }, c.getOrElse(3) { "" }, false)
            }
            return Datos(L("Próximas citas", "Upcoming visits"), items.size.takeIf { it > 0 }?.toString(), items,
                L("No hay citas agendadas", "No visits scheduled"), "citas", false)
        }
        val a = p.agendaHoy?.takeIf { it.fecha == LocalDate.now().toString() }
        val ahora = LocalTime.now().toString().take(5)
        val items = a?.citas.orEmpty()
            .map { Item(hora12(it.hora).ifBlank { "—" }, it.paciente.split(' ').take(2).joinToString(" "), null, it.estado, it.estado == "completed" || (it.hora.isNotBlank() && it.hora < ahora)) }
            .sortedWith(compareBy<Item> { it.pasada }) // lo que falta, arriba
        return Datos(L("Agenda de hoy", "Today's schedule"), a?.takeIf { it.total > 0 }?.let { "${it.hechas}/${it.total}" }, items,
            L("Día libre: no hay sesiones hoy", "Free day: no sessions today"), "agenda", false)
    }

    @Composable
    private fun Contenido(d: Datos) {
        val ctx = LocalContext.current
        val alto = LocalSize.current.height
        val max = when { alto >= 280.dp -> 5; alto >= 180.dp -> 3; else -> 1 }
        Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_tarjeta)).cornerRadius(24.dp).clickable(abrir(ctx, d.vista))) {
            Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                Cabecera(d.titulo, d.contador ?: hoyCorto())
                Spacer(GlanceModifier.height(10.dp))
                if (d.items.isEmpty()) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(R.drawable.aria_cafe), "ARIA", GlanceModifier.size(if (alto >= 180.dp) 72.dp else 48.dp))
                        Spacer(GlanceModifier.width(10.dp))
                        Text(d.vacio, style = TextStyle(color = Wc.suave, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 3)
                    }
                    return@Column
                }
                d.items.take(max).forEachIndexed { i, it ->
                    if (i > 0) Spacer(GlanceModifier.height(6.dp))
                    Fila(it)
                }
                val resto = d.items.size - max
                if (resto > 0) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(L("+$resto más", "+$resto more"), style = TextStyle(color = Wc.acento, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    @Composable
    private fun Fila(x: Item) {
        val p = pastilla(x.estado)
        Row(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_casilla)).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(x.hora, style = TextStyle(color = if (x.pasada) Wc.suave else Wc.acento, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                modifier = GlanceModifier.width(74.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(x.titulo, style = TextStyle(color = if (x.pasada) Wc.suave else Wc.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (x.sub != null) Text(x.sub, style = TextStyle(color = Wc.suave, fontSize = 11.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(GlanceModifier.background(ImageProvider(p.fondo)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(p.texto, style = TextStyle(color = p.color, fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}
