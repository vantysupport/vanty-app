package xyz.vanty.aba.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
 * Widget "Hoy en Vanty": tres datos clave según el rol, cada uno abre su apartado.
 *  • Familia: racha de práctica, próxima cita y mensajes.
 *  • Especialista / dirección: sesiones de hoy (con barra de avance), siguiente sesión y mensajes.
 *  • Secretaría: citas de hoy, por confirmar y siguiente.
 * Nunca muestra diagnósticos ni datos clínicos (se ve en la pantalla de inicio).
 */
class ResumenWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(DpSize(150.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp)))

    private data class Dato(val etiqueta: String, val valor: String, val vista: String, val destacado: Boolean = false)
    private data class Datos(val titulo: String, val datos: List<Dato>, val progreso: Float?, val sinSesion: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = datos(Prefs(context))
        provideContent { Contenido(d) }
    }

    private fun mensajes(p: Prefs, rol: Rol) = Dato(L("Mensajes", "Messages"),
        if (p.sinLeer > 0) L("${p.sinLeer} ${if (p.sinLeer == 1) "nuevo" else "nuevos"}", "${p.sinLeer} new") else L("Al día", "All caught up"),
        Destino.mensajes(rol), destacado = p.sinLeer > 0)

    private fun datos(p: Prefs): Datos {
        val rol = rolDe(p)
        if (p.usuarioId == null) return Datos(L("Hoy en Vanty", "Today in Vanty"), emptyList(), null, true)
        if (rol == Rol.Familia) {
            val r = p.rachaVigente()
            val cita = p.proximaCita?.split("|")
            return Datos(p.nombreHijo.substringBefore(' ').ifBlank { L("Hoy en Vanty", "Today in Vanty") }, listOf(
                Dato(L("Racha", "Streak"), if (r.dias > 0) "🔥 ${r.dias} ${if (r.dias == 1) L("día", "day") else L("días", "days")}" else L("Empieza hoy", "Start today"), "practicar", destacado = r.hoy),
                Dato(L("Próxima cita", "Next visit"), cita?.let { cuando(it[0], it.getOrElse(1) { "" }) } ?: L("Sin citas", "None"), "citas"),
                mensajes(p, rol),
            ), null, false)
        }
        val a = p.agendaHoy?.takeIf { it.fecha == LocalDate.now().toString() }
        val ahora = LocalTime.now().toString().take(5)
        val activas = a?.citas.orEmpty().filter { it.estado == "pending" || it.estado == "confirmed" }
        val sig = activas.firstOrNull { it.hora >= ahora }
        val siguiente = Dato(L("Siguiente", "Next"), sig?.let { "${hora12(it.hora)} · ${it.paciente.substringBefore(' ')}" } ?: L("Nada pendiente", "Nothing pending"), "agenda")
        if (rol == Rol.Secretaria) {
            val porConfirmar = a?.citas.orEmpty().count { it.estado == "pending" }
            return Datos(L("Recepción hoy", "Front desk today"), listOf(
                Dato(L("Citas de hoy", "Today's visits"), "${a?.total ?: 0}", "agenda"),
                Dato(L("Por confirmar", "To confirm"), "$porConfirmar", "agenda", destacado = porConfirmar > 0),
                siguiente,
            ), null, false)
        }
        val total = a?.total ?: 0
        return Datos(L("Tu día", "Your day"), listOf(
            Dato(L("Sesiones", "Sessions"), if (total == 0) L("Día libre", "Free day") else L("${a?.hechas ?: 0} de $total", "${a?.hechas ?: 0} of $total"), "agenda"),
            siguiente,
            mensajes(p, rol),
        ), a?.takeIf { it.total > 0 }?.let { it.hechas / it.total.toFloat() }, false)
    }

    @Composable
    private fun Contenido(d: Datos) {
        val ctx = LocalContext.current
        val ancho = LocalSize.current.width >= 250.dp
        val alto = LocalSize.current.height >= 180.dp
        Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_tarjeta)).cornerRadius(24.dp).clickable(abrir(ctx, "inicio"))) {
            Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                Cabecera(d.titulo, if (ancho) hoyCorto() else null)
                if (d.sinSesion) {
                    Spacer(GlanceModifier.height(10.dp))
                    Text(L("Inicia sesión en Vanty para ver tu día", "Sign in to Vanty to see your day"), style = TextStyle(color = Wc.suave, fontSize = 13.sp))
                    return@Column
                }
                d.progreso?.let {
                    Spacer(GlanceModifier.height(8.dp))
                    LinearProgressIndicator(it, GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp), color = Wc.acento, backgroundColor = Wc.pista)
                }
                Spacer(GlanceModifier.height(10.dp))
                if (ancho && !alto) {
                    // Ancho: tres casillas en fila
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        d.datos.forEachIndexed { i, x ->
                            if (i > 0) Spacer(GlanceModifier.width(8.dp))
                            Casilla(x, GlanceModifier.defaultWeight().fillMaxHeight())
                        }
                    }
                } else {
                    // Alto o angosto: una fila por dato
                    d.datos.forEachIndexed { i, x ->
                        if (i > 0) Spacer(GlanceModifier.height(if (alto) 8.dp else 4.dp))
                        Fila(x, alto)
                    }
                }
            }
        }
    }

    @Composable
    private fun Casilla(x: Dato, modifier: GlanceModifier) {
        val ctx = LocalContext.current
        Column(modifier.background(ImageProvider(R.drawable.widget_casilla)).cornerRadius(16.dp).padding(horizontal = 10.dp, vertical = 8.dp).clickable(abrir(ctx, x.vista)),
            verticalAlignment = Alignment.CenterVertically) {
            Text(x.etiqueta, style = TextStyle(color = Wc.suave, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Spacer(GlanceModifier.height(2.dp))
            Text(x.valor, style = TextStyle(color = if (x.destacado) Wc.acento else Wc.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
        }
    }

    @Composable
    private fun Fila(x: Dato, alto: Boolean) {
        val ctx = LocalContext.current
        Row(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_casilla)).cornerRadius(14.dp)
            .padding(horizontal = 12.dp, vertical = if (alto) 9.dp else 5.dp).clickable(abrir(ctx, x.vista)),
            verticalAlignment = Alignment.CenterVertically) {
            Text(x.etiqueta, style = TextStyle(color = Wc.suave, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Text(x.valor, style = TextStyle(color = if (x.destacado) Wc.acento else Wc.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }

    companion object {
        suspend fun actualizar(ctx: Context) = runCatching { ResumenWidget().updateAll(ctx) }
    }
}

class ResumenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResumenWidget()
}
