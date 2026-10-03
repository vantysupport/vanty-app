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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import xyz.vanty.aba.R
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.util.L

/** Widget "Mensajes": cuántos mensajes y avisos sin leer hay; al tocarlo abre el chat (o el inicio, en recepción). */
class MensajesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(200.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val p = Prefs(context)
        val sesion = p.usuarioId != null
        val n = if (sesion) p.sinLeer else 0
        val rol = rolDe(p)
        provideContent { Contenido(sesion, n, Destino.mensajes(rol), rol == Rol.Familia) }
    }

    @Composable
    private fun Contenido(sesion: Boolean, n: Int, vista: String, familia: Boolean) {
        val ctx = LocalContext.current
        val ancho = LocalSize.current.width >= 200.dp
        Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_tarjeta)).cornerRadius(24.dp).clickable(abrir(ctx, if (sesion) vista else "inicio"))) {
            if (ancho) Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                Image(ImageProvider(if (n > 0) R.drawable.aria_celular else R.drawable.aria_pulgar_arriba), "ARIA", GlanceModifier.size(92.dp).padding(end = 4.dp))
            }
            Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
                Cabecera(L("Mensajes", "Messages"), null)
                Spacer(GlanceModifier.defaultWeight())
                if (!sesion) {
                    Text(L("Inicia sesión", "Sign in"), style = TextStyle(color = Wc.suave, fontSize = 13.sp))
                    return@Column
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (n > 99) "99+" else "$n", style = TextStyle(color = if (n > 0) Wc.acento else Wc.texto, fontSize = 34.sp, fontWeight = FontWeight.Bold))
                }
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    when {
                        n == 0 -> L("Estás al día ✓", "All caught up ✓")
                        familia -> L(if (n == 1) "mensaje del centro" else "mensajes del centro", if (n == 1) "message from the center" else "messages from the center")
                        else -> L(if (n == 1) "sin leer" else "sin leer", "unread")
                    },
                    style = TextStyle(color = Wc.suave, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 2,
                    modifier = GlanceModifier.fillMaxWidth().padding(end = if (ancho) 90.dp else 0.dp),
                )
            }
        }
    }
}

class MensajesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MensajesWidget()
}
