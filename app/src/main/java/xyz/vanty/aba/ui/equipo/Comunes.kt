package xyz.vanty.aba.ui.equipo

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.data.CitaEquipo
import xyz.vanty.aba.data.EstadoCita
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.IconoTono
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.hora12

data class Look(val texto: String, val color: Color, val icono: ImageVector)

@Composable
fun look(e: EstadoCita) = when (e) {
    EstadoCita.Pendiente -> Look(L("Pendiente", "Pending"), T.aviso, Icons.Rounded.HourglassTop)
    EstadoCita.Confirmada -> Look(L("Confirmada", "Confirmed"), T.exito, Icons.Rounded.ThumbUp)
    EstadoCita.Realizada -> Look(L("Realizada", "Done"), T.acento, Icons.Rounded.CheckCircle)
    EstadoCita.Cancelada -> Look(L("Cancelada", "Cancelled"), T.peligro, Icons.Rounded.Cancel)
}

/** Fila de la línea de tiempo: hora a la izquierda, punto de estado sobre la línea y tarjeta del paciente. */
@Composable
fun FilaTimeline(c: CitaEquipo, ultima: Boolean, ahora: Boolean = false, onClick: () -> Unit) {
    val l = look(c.estado)
    val inf = rememberInfiniteTransition(label = "pulso")
    val pulso by inf.animateFloat(1f, 1.6f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(62.dp).padding(top = 14.dp), horizontalAlignment = Alignment.End) {
            Text(hora12(c.hora).substringBefore(' '), style = MaterialTheme.typography.titleSmall, color = if (ahora) T.acento else T.texto)
            Text(hora12(c.hora).substringAfter(' ', ""), style = MaterialTheme.typography.labelSmall, color = T.terciario)
        }
        Box(Modifier.width(30.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!ultima) Box(Modifier.padding(top = 22.dp).width(2.dp).fillMaxHeight().background(T.borde))
            Box(Modifier.padding(top = 16.dp), contentAlignment = Alignment.Center) {
                if (ahora) Box(Modifier.size(14.dp).graphicsLayer { scaleX = pulso; scaleY = pulso; alpha = 2f - pulso }.background(l.color.copy(alpha = 0.4f), CircleShape))
                Box(Modifier.size(14.dp).background(l.color, CircleShape).border(3.dp, T.fondo, CircleShape))
            }
        }
        Row(
            Modifier.weight(1f).padding(bottom = 10.dp).presionable(onClick = onClick)
                .background(T.tarjeta, RoundedCornerShape(18.dp))
                .border(if (ahora) 2.dp else 1.dp, if (ahora) T.acento else T.borde, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.paciente, style = MaterialTheme.typography.titleMedium, color = if (c.estado == EstadoCita.Cancelada) T.terciario else T.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(c.servicio, if (c.modalidad == "virtual") L("Virtual", "Online") else null).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 1)
            }
            if (c.modalidad == "virtual") Icon(Icons.Rounded.Videocam, null, tint = T.acento, modifier = Modifier.padding(end = 6.dp).size(18.dp))
            Etiqueta(l.texto, l.color.copy(alpha = 0.14f), l.color)
        }
    }
}

/** Hoja inferior con las acciones de una cita (mismos estados que la agenda web). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaCita(c: CitaEquipo, onElegir: (EstadoCita) -> Unit, onCerrar: () -> Unit) {
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(c.paciente, style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Text(listOfNotNull(hora12(c.hora), c.servicio).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = T.secundario)
            Spacer(Modifier.height(18.dp))
            val opciones = listOf(
                EstadoCita.Realizada to L("Sesión realizada", "Session done"),
                EstadoCita.Confirmada to L("Confirmar cita", "Confirm appointment"),
                EstadoCita.Pendiente to L("Volver a pendiente", "Back to pending"),
                EstadoCita.Cancelada to L("Cancelar / no asistió", "Cancel / no-show"),
            ).filter { it.first != c.estado }
            opciones.forEach { (estado, texto) ->
                val l = look(estado)
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).presionable { onElegir(estado) }
                        .background(l.color.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconoTono(l.icono, l.color.copy(alpha = 0.18f), l.color, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(texto, style = MaterialTheme.typography.titleSmall, color = T.texto, modifier = Modifier.weight(1f))
                    if (estado == EstadoCita.Realizada) Icon(Icons.Rounded.Check, null, tint = l.color)
                }
            }
        }
    }
}

/** Encabezado de sección dentro de las listas. */
@Composable
fun Seccion(texto: String, extra: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(texto, style = MaterialTheme.typography.titleLarge, color = T.texto)
        if (extra != null) Text(extra, style = MaterialTheme.typography.labelMedium, color = T.terciario, fontWeight = FontWeight.SemiBold)
    }
}
