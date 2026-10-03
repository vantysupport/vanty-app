package xyz.vanty.aba.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import android.widget.RemoteViews
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import xyz.vanty.aba.MainActivity
import xyz.vanty.aba.R
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.notif.Avisos
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.diasHasta
import xyz.vanty.aba.util.fechaCorta
import xyz.vanty.aba.util.hora12
import java.time.LocalDate
import java.time.format.TextStyle as EstiloFecha
import java.util.Locale

/** Paleta de los widgets: blanco y azul Vanty de día, azul marino de noche (sigue el modo del teléfono). */
object Wc {
    val texto = ColorProvider(Color(0xFF0B1B33), Color(0xFFF2F6FC))
    val suave = ColorProvider(Color(0xFF5B6B82), Color(0xFF94A3B8))
    val acento = ColorProvider(Color(0xFF0B6BEA), Color(0xFF5AA2FF))
    val exito = ColorProvider(Color(0xFF0F7A43), Color(0xFF4ADE80))
    val ambar = ColorProvider(Color(0xFF9A5B00), Color(0xFFFBBF24))
    val pista = ColorProvider(Color(0xFFE3EEFF), Color(0xFF22324D))
}

/** Abre la app directo en un apartado (mismos nombres que usan las notificaciones). */
fun abrir(ctx: Context, vista: String): Action = actionStartActivity(
    Intent(ctx, MainActivity::class.java)
        .setData(Uri.parse("vanty://widget/$vista")) // un PendingIntent distinto por apartado
        .putExtra(Avisos.EXTRA_VISTA, vista)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

/** Apartado al que lleva cada dato del widget según el rol (la secretaría no tiene chat). */
object Destino {
    fun agenda(rol: Rol) = if (rol == Rol.Familia) "citas" else "agenda"
    fun mensajes(rol: Rol) = when (rol) { Rol.Familia -> "chat-familias"; Rol.Admin -> "chat"; else -> "hoy" }
}

/** Cabecera común: logo, título, subtítulo (fecha o contador) y, si se pasa, ARIA animada a la derecha. */
@Composable
fun Cabecera(titulo: String, derecha: String? = hoyCorto(), aria: List<Int>? = null) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.widget_logo), "Vanty", GlanceModifier.size(22.dp))
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(titulo, style = TextStyle(color = Wc.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            if (derecha != null) Text(derecha, style = TextStyle(color = Wc.suave, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        if (aria != null) AriaAnimada(aria, GlanceModifier.size(40.dp))
    }
}

/**
 * ARIA "animada": un ViewFlipper que cambia de pose solo cada ~3 s con fundido y un leve rebote.
 * (Los widgets de Android no admiten video ni Lottie; así lo hacen las apps profesionales.)
 */
@Composable
fun AriaAnimada(poses: List<Int>, modifier: GlanceModifier) {
    val ctx = LocalContext.current
    val ids = listOf(R.id.w_aria_1, R.id.w_aria_2, R.id.w_aria_3)
    val rv = RemoteViews(ctx.packageName, R.layout.widget_aria_animada).apply {
        ids.forEachIndexed { i, id -> setImageViewResource(id, poses[i % poses.size]) }
    }
    AndroidRemoteViews(rv, modifier)
}

/** Secuencias de poses de ARIA según el momento (3 cuadros que se repiten). */
object Secuencia {
    val saluda = listOf(R.drawable.aria_saluda, R.drawable.aria_hola, R.drawable.aria_bienvenida)
    val celebra = listOf(R.drawable.aria_festeja, R.drawable.aria_celebra, R.drawable.aria_salta)
    val preocupada = listOf(R.drawable.aria_preocupada, R.drawable.aria_pensando, R.drawable.aria_atenta)
    val anima = listOf(R.drawable.aria_corre, R.drawable.aria_pulgar_arriba, R.drawable.aria_brinca)
    val trabaja = listOf(R.drawable.aria_laptop, R.drawable.aria_trabajando, R.drawable.aria_laptop_sentada)
    val descansa = listOf(R.drawable.aria_cafe, R.drawable.aria_te, R.drawable.aria_descansa)
    val mensajes = listOf(R.drawable.aria_celular, R.drawable.aria_lee, R.drawable.aria_explica)
    val aldia = listOf(R.drawable.aria_pulgar_arriba, R.drawable.aria_contenta, R.drawable.aria_de_pie)
    val vuelve = listOf(R.drawable.aria_mochila, R.drawable.aria_espalda, R.drawable.aria_saluda)
}

fun hoyCorto(): String {
    val d = LocalDate.now()
    val loc = Locale(if (xyz.vanty.aba.util.EN) "en" else "es")
    val dia = d.dayOfWeek.getDisplayName(EstiloFecha.SHORT, loc).trimEnd('.')
    val mes = d.month.getDisplayName(EstiloFecha.SHORT, loc).trimEnd('.')
    return if (xyz.vanty.aba.util.EN) "$dia, $mes ${d.dayOfMonth}" else "$dia ${d.dayOfMonth} $mes"
}

/** "Hoy · 3:00 p. m.", "Mañana", "12 oct · 9:00 a. m." */
fun cuando(fecha: String, hora: String): String {
    val dia = when (diasHasta(fecha)) { 0L -> L("Hoy", "Today"); 1L -> L("Mañana", "Tomorrow"); else -> fechaCorta(fecha).let { (d, m) -> "$d $m" } }
    return if (hora.isNotBlank()) "$dia · ${hora12(hora)}" else dia
}

/** Estado de una cita para la pastilla de color. */
data class Pastilla(val texto: String, val fondo: Int, val color: androidx.glance.unit.ColorProvider)

fun pastilla(estado: String): Pastilla = when (estado) {
    "completed" -> Pastilla(L("Hecha", "Done"), R.drawable.widget_pill_verde, Wc.exito)
    "confirmed" -> Pastilla(L("Confirmada", "Confirmed"), R.drawable.widget_pill_azul, Wc.acento)
    "pending" -> Pastilla(L("Por confirmar", "To confirm"), R.drawable.widget_pill_ambar, Wc.ambar)
    else -> Pastilla(L("Agendada", "Scheduled"), R.drawable.widget_pill_gris, Wc.suave)
}

/** Actualiza todos los widgets de Vanty (después de cargar datos nuevos). */
suspend fun actualizarWidgets(ctx: Context) {
    runCatching { RachaWidget().updateAll(ctx) }
    runCatching { ResumenWidget().updateAll(ctx) }
    runCatching { AgendaWidget().updateAll(ctx) }
    runCatching { MensajesWidget().updateAll(ctx) }
}

/** Rol guardado de la sesión (para los widgets, que se pintan sin abrir la app). */
fun rolDe(p: Prefs): Rol = Rol.de(p.rol)

/** Próximas citas (sin las canceladas) en el formato que guarda [Prefs.citasFamilia]. */
fun lineasCitas(citas: List<xyz.vanty.aba.data.Cita>, nombre: String): List<String> =
    citas.filter { it.status !in setOf("cancelled", "cancelada", "ausente") }.take(4)
        .map { "${it.fecha.take(10)}|${it.hora?.take(5).orEmpty()}|$nombre|${it.status.orEmpty()}" }
