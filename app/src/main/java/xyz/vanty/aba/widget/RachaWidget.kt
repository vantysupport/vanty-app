package xyz.vanty.aba.widget

import android.content.Context
import androidx.annotation.DrawableRes
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import xyz.vanty.aba.MainActivity
import xyz.vanty.aba.R
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.util.L
import java.time.LocalDate
import java.time.LocalTime

/**
 * Widget de ARIA, como "¡Vuelve con Duo!": una escena pintada cuyo color, pose y frase cambian según tu estado.
 * Familias: según la racha (feliz si ya practicaron, preocupada si se va a perder, "¡Vuelve!" si se perdió).
 * Equipo: según el avance del día (sesiones registradas).
 */
class RachaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 200.dp)))

    private data class Animo(@DrawableRes val fondo: Int, @DrawableRes val pose: Int, val titulo: String, val texto: String, val pastilla: String?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val animo = animo(Prefs(context))
        provideContent { Escena(animo) }
    }

    private fun animo(p: Prefs): Animo {
        val rol = Rol.de(p.rol)
        val nombre = p.nombreUsuario
        if (p.usuarioId == null) return Animo(R.drawable.widget_animo_azul, R.drawable.aria_saluda,
            L("¡Hola! Soy ARIA", "Hi! I'm ARIA"), L("Inicia sesión en Vanty", "Sign in to Vanty"), null)

        if (rol == Rol.Familia) {
            val r = p.rachaVigente()
            val hijo = p.nombreHijo.substringBefore(' ')
            val noche = LocalTime.now().hour >= 18
            return when {
                r.hoy -> Animo(R.drawable.widget_animo_verde, R.drawable.aria_festeja,
                    L("¡Racha de ${r.dias} ${if (r.dias == 1) "día" else "días"}!", "${r.dias}-day streak!"),
                    L("¡Hoy ya practicaron! ARIA está feliz 💚", "You practiced today! ARIA is happy 💚"), "🔥 ${r.dias}")
                r.dias > 0 && noche -> Animo(R.drawable.widget_animo_naranja, R.drawable.aria_preocupada,
                    L("¡No me dejes! 😟", "Don't leave me! 😟"),
                    L("Tu racha de ${r.dias} ${if (r.dias == 1) "día" else "días"} se apaga a medianoche", "Your ${r.dias}-day streak ends at midnight"), "🔥 ${r.dias}")
                r.dias > 0 -> Animo(R.drawable.widget_animo_azul, R.drawable.aria_saluda,
                    L("¡A practicar hoy!", "Let's practice today!"),
                    if (hijo.isNotBlank()) L("5 minutos con $hijo y suman un día más", "5 minutes with $hijo adds one more day") else L("Suma un día más a tu racha", "Add one more day"), "🔥 ${r.dias}")
                else -> Animo(R.drawable.widget_animo_noche, R.drawable.aria_atenta,
                    if (nombre.isNotBlank()) L("¡Vuelve con ARIA, $nombre!", "Come back to ARIA, $nombre!") else L("¡Vuelve con ARIA!", "Come back to ARIA!"),
                    L("Empieza una nueva racha hoy 🌱", "Start a new streak today 🌱"), null)
            }
        }

        val a = p.agendaHoy?.takeIf { it.fecha == LocalDate.now().toString() }
        val total = a?.total ?: 0
        val hechas = a?.hechas ?: 0
        return when {
            total == 0 -> Animo(R.drawable.widget_animo_morado, R.drawable.aria_contenta,
                L("Día libre ☕", "Free day ☕"), L("No hay sesiones agendadas hoy", "No sessions scheduled today"), null)
            hechas >= total -> Animo(R.drawable.widget_animo_verde, R.drawable.aria_festeja,
                L("¡Día completado! 🎉", "Day complete! 🎉"), L("Registraste todas tus sesiones", "All your sessions recorded"), "✓ $hechas/$total")
            LocalTime.now().hour >= 18 -> Animo(R.drawable.widget_animo_naranja, R.drawable.aria_bienvenida,
                L("¡Ya casi! 😅", "Almost there! 😅"), L("Te faltan ${total - hechas} por registrar", "${total - hechas} left to record"), "$hechas/$total")
            else -> Animo(R.drawable.widget_animo_azul, R.drawable.aria_laptop,
                if (nombre.isNotBlank()) L("¡Vamos, $nombre!", "Let's go, $nombre!") else L("¡Vamos con todo!", "Let's go!"),
                L("Te quedan ${total - hechas} ${if (total - hechas == 1) "sesión" else "sesiones"} hoy", "${total - hechas} session${if (total - hechas == 1) "" else "s"} left today"), "$hechas/$total")
        }
    }

    @Composable
    private fun Escena(a: Animo) {
        val ancho = LocalSize.current.width >= 250.dp
        val alto = LocalSize.current.height >= 200.dp
        val blanco = ColorProvider(Color.White, Color.White)
        val suave = ColorProvider(Color(0xE6FFFFFF), Color(0xE6FFFFFF))
        Box(
            GlanceModifier.fillMaxSize().background(ImageProvider(a.fondo)).cornerRadius(28.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            // ARIA grande, asomándose desde abajo a la derecha
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                Image(ImageProvider(a.pose), "ARIA", GlanceModifier.size(if (alto) 150.dp else if (ancho) 115.dp else 96.dp).padding(end = 6.dp))
            }
            Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                Text(a.titulo, style = TextStyle(color = blanco, fontSize = if (ancho) 20.sp else 16.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                Spacer(GlanceModifier.height(4.dp))
                Text(a.texto, style = TextStyle(color = suave, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = if (ancho) 2 else 3,
                    modifier = GlanceModifier.padding(end = if (ancho) 96.dp else 0.dp))
                Spacer(GlanceModifier.defaultWeight())
                if (a.pastilla != null) {
                    Box(GlanceModifier.background(ImageProvider(R.drawable.widget_pastilla)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Text(a.pastilla, style = TextStyle(color = blanco, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }

    companion object {
        suspend fun actualizar(ctx: Context) = runCatching { RachaWidget().updateAll(ctx) }
    }
}

class RachaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RachaWidget()
}
