package xyz.vanty.aba.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import xyz.vanty.aba.MainActivity
import xyz.vanty.aba.R
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.diasHasta
import xyz.vanty.aba.util.fechaCorta
import xyz.vanty.aba.util.hora12
import java.time.LocalDate
import java.time.LocalTime

/**
 * Widget "Tu resumen": tres filas con emoji, al estilo de los widgets de Duolingo.
 * Familias: racha, próxima cita y mensajes nuevos. Equipo: avance de hoy, siguiente sesión y avisos.
 * Nunca muestra diagnósticos ni datos clínicos (se ve en la pantalla de inicio).
 */
class ResumenWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp)))

    private data class Fila(val emoji: String, val titulo: String, val valor: String)
    private data class Datos(val filas: List<Fila>, val progreso: Float?, val sinSesion: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = datos(Prefs(context))
        provideContent { Contenido(d) }
    }

    private fun cuando(fecha: String, hora: String): String {
        val n = diasHasta(fecha)
        val dia = when (n) { 0L -> L("Hoy", "Today"); 1L -> L("Mañana", "Tomorrow"); else -> fechaCorta(fecha).let { (d, m) -> "$d $m" } }
        return if (hora.isNotBlank()) "$dia · ${hora12(hora)}" else dia
    }

    private fun datos(p: Prefs): Datos {
        if (p.usuarioId == null) return Datos(emptyList(), null, true)
        val msgs = if (p.sinLeer > 0) L("${p.sinLeer} ${if (p.sinLeer == 1) "nuevo" else "nuevos"}", "${p.sinLeer} new") else L("Al día ✓", "All caught up ✓")
        if (Rol.de(p.rol) == Rol.Familia) {
            val r = p.rachaVigente()
            val cita = p.proximaCita?.split("|")
            return Datos(listOf(
                Fila("🔥", L("Racha", "Streak"), if (r.dias > 0) L("${r.dias} ${if (r.dias == 1) "día" else "días"}${if (r.hoy) " ✓" else ""}", "${r.dias} day${if (r.dias == 1) "" else "s"}${if (r.hoy) " ✓" else ""}") else L("¡Empieza hoy!", "Start today!")),
                Fila("📅", L("Próxima cita", "Next visit"), cita?.let { cuando(it[0], it.getOrElse(1) { "" }) } ?: L("Sin citas", "None")),
                Fila("💬", L("Mensajes", "Messages"), msgs),
            ), null, false)
        }
        val a = p.agendaHoy?.takeIf { it.fecha == LocalDate.now().toString() }
        val ahora = LocalTime.now().toString().take(5)
        val sig = a?.citas?.filter { it.estado == "pending" || it.estado == "confirmed" }?.let { l -> l.firstOrNull { it.hora >= ahora } ?: l.firstOrNull() }
        return Datos(listOf(
            Fila("📅", L("Hoy", "Today"), if (a == null || a.total == 0) L("Sin sesiones", "No sessions") else L("${a.hechas} de ${a.total} sesiones", "${a.hechas} of ${a.total} sessions")),
            Fila("⏭️", L("Siguiente", "Next"), sig?.let { "${hora12(it.hora)} · ${it.paciente.substringBefore(' ')}" } ?: L("Nada pendiente", "Nothing pending")),
            Fila("🔔", L("Avisos", "Alerts"), msgs),
        ), a?.takeIf { it.total > 0 }?.let { it.hechas / it.total.toFloat() }, false)
    }

    @Composable
    private fun Contenido(d: Datos) {
        val grande = LocalSize.current.height >= 180.dp
        val blanco = ColorProvider(Color.White, Color.White)
        val suave = ColorProvider(Color(0xCCFFFFFF), Color(0xCCFFFFFF))
        Box(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_animo_azul)).cornerRadius(28.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("Tu resumen", "Your summary"), style = TextStyle(color = blanco, fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                    Image(ImageProvider(R.drawable.aria_saluda), "ARIA", GlanceModifier.size(if (grande) 40.dp else 30.dp))
                }
                if (d.sinSesion) {
                    Text(L("Inicia sesión en Vanty", "Sign in to Vanty"), style = TextStyle(color = suave, fontSize = 13.sp))
                    return@Column
                }
                d.progreso?.let {
                    Spacer(GlanceModifier.height(4.dp))
                    LinearProgressIndicator(it, GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                        color = ColorProvider(Color(0xFF5AC8FA), Color(0xFF5AC8FA)), backgroundColor = ColorProvider(Color(0x40FFFFFF), Color(0x40FFFFFF)))
                }
                Spacer(GlanceModifier.height(6.dp))
                d.filas.forEach { f ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = if (grande) 5.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.emoji, style = TextStyle(fontSize = if (grande) 18.sp else 14.sp))
                        Spacer(GlanceModifier.width(8.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            if (grande) Text(f.titulo, style = TextStyle(color = suave, fontSize = 11.sp))
                            Text(f.valor, style = TextStyle(color = blanco, fontSize = if (grande) 14.sp else 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    companion object {
        suspend fun actualizar(ctx: Context) = runCatching { ResumenWidget().updateAll(ctx) }
    }
}

class ResumenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ResumenWidget()
}
