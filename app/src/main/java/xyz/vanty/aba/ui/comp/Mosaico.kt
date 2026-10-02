package xyz.vanty.aba.ui.comp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L

/** Colores de las tarjetas del mosaico "Más" (pintadas, como las notificaciones). */
object Pintura {
    // Todos de la paleta de Vanty (degradado de marca #01ABFC → #0063D8 y sus variantes)
    val azul = Brush.linearGradient(listOf(Color(0xFF01ABFC), Color(0xFF0063D8)))
    val verde = Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF0369A1)))      // celeste profundo
    val naranja = Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8)))    // aviso de Vanty (racha)
    val morado = Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF1E40AF)))     // azul índigo
    val rosa = Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB)))       // azul medio
    val turquesa = Brush.linearGradient(listOf(Color(0xFF7DD3FC), Color(0xFF0EA5E9)))   // cielo
    val dorado = Brush.linearGradient(listOf(Color(0xFF7DD3FC), Color(0xFF1D4ED8)))
    val gris = Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B)))
}

/** Una tarjeta del mosaico. `bloqueada`: no incluida en el plan del centro (sin precios ni enlaces de compra). */
data class Baldosa(
    val clave: String, val titulo: String, val subtitulo: String, val icono: ImageVector, val pintura: Brush,
    val insignia: Int = 0, val bloqueada: Boolean = false, val soloPc: Boolean = false,
)

/** Mosaico de tarjetas grandes y coloridas, dos por fila, con entrada escalonada. */
@Composable
fun Mosaico(baldosas: List<Baldosa>, onClick: (Baldosa) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        baldosas.chunked(2).forEachIndexed { fila, par ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                par.forEachIndexed { i, b -> TarjetaBaldosa(b, Modifier.weight(1f).aparecer(fila * 2 + i)) { onClick(b) } }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TarjetaBaldosa(b: Baldosa, modifier: Modifier, onClick: () -> Unit) {
    val apagada = b.bloqueada || b.soloPc
    Box(
        modifier.height(128.dp).clip(RoundedCornerShape(24.dp))
            .background(if (apagada) Pintura.gris else b.pintura)
            .presionable(onClick = onClick).padding(16.dp),
    ) {
        Icon(b.icono, null, tint = Color.White.copy(alpha = 0.22f), modifier = Modifier.size(84.dp).align(Alignment.BottomEnd).padding(start = 20.dp))
        Column(Modifier.align(Alignment.BottomStart)) {
            Icon(b.icono, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(b.titulo, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    b.bloqueada -> L("No incluido en tu plan", "Not in your plan")
                    b.soloPc -> L("Disponible en la PC", "Available on desktop")
                    else -> b.subtitulo
                },
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (apagada) Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).size(18.dp))
        else if (b.insignia > 0) Text(
            if (b.insignia > 9) "9+" else "${b.insignia}", color = T.peligro, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.TopEnd).background(Color.White, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Encabezado de una subsección abierta desde "Más", con flecha para volver. */
@Composable
fun CabeceraSub(titulo: String, onVolver: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(T.relleno).presionable(onClick = onVolver), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, L("Volver", "Back"), tint = T.texto)
        }
        Text(titulo, style = MaterialTheme.typography.headlineSmall, color = T.texto, modifier = Modifier.padding(start = 12.dp))
    }
}
