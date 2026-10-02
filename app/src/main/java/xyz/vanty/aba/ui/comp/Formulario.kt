package xyz.vanty.aba.ui.comp

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.fechaLarga
import xyz.vanty.aba.util.hora12
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Piezas de formulario compartidas (agenda, pacientes, pagos…), con el estilo de la app. */

@Composable
fun Etiq(texto: String) = Text(texto, style = MaterialTheme.typography.labelLarge, color = T.secundario, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 12.dp))

@Composable
fun coloresCampo() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = T.acento, unfocusedBorderColor = T.borde,
    focusedContainerColor = T.relleno, unfocusedContainerColor = T.relleno,
)

@Composable
fun Campo(valor: String, onCambio: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, lineas: Int = 1, numerico: Boolean = false) {
    OutlinedTextField(
        valor, onCambio, modifier.fillMaxWidth(), placeholder = { Text(placeholder, color = T.terciario) },
        singleLine = lineas == 1, minLines = lineas, shape = RoundedCornerShape(16.dp), colors = coloresCampo(),
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
    )
}

/** Opciones tipo "pastilla" (una elegida), como los botones segmentados de la web. */
@Composable
fun <K> Opciones(opciones: List<Pair<K, String>>, sel: K, modifier: Modifier = Modifier, punto: @Composable (K) -> Color? = { null }, onSel: (K) -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (k, t) -> Pastilla(t, k == sel, punto(k)) { onSel(k) } }
    }
}

@Composable
fun Pastilla(texto: String, elegida: Boolean, punto: Color? = null, onClick: () -> Unit) {
    val fondo by animateColorAsState(if (elegida) T.acento else T.relleno, label = "p")
    Row(
        Modifier.clip(CircleShape).background(fondo).then(if (elegida) Modifier else Modifier.border(1.dp, T.borde, CircleShape))
            .presionable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (punto != null) { Box(Modifier.size(8.dp).background(if (elegida) Color.White else punto, CircleShape)); Spacer(Modifier.width(6.dp)) }
        Text(texto, style = MaterialTheme.typography.labelLarge, color = if (elegida) Color.White else T.texto)
    }
}

/** Botón-campo que abre un selector (fecha, hora, paciente…). */
@Composable
fun CampoBoton(texto: String, icono: ImageVector, modifier: Modifier = Modifier, vacio: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(T.relleno).border(1.dp, T.borde, RoundedCornerShape(16.dp))
            .presionable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icono, null, tint = T.acento, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge, color = if (vacio) T.terciario else T.texto, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(fecha: LocalDate?, onFecha: (LocalDate) -> Unit, modifier: Modifier = Modifier, placeholder: String = L("Elegir fecha", "Pick a date")) {
    var abierto by remember { mutableStateOf(false) }
    CampoBoton(fecha?.let { fechaLarga(it.toString()) } ?: placeholder, Icons.Rounded.CalendarMonth, modifier, vacio = fecha == null) { abierto = true }
    if (abierto) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (fecha ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { onFecha(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    abierto = false
                }) { Text(L("Listo", "Done"), color = T.acento) }
            },
            dismissButton = { TextButton({ abierto = false }) { Text(L("Cancelar", "Cancel"), color = T.secundario) } },
        ) { DatePicker(st) }
    }
}

/** Hora "HH:mm". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoHora(hora: String, onHora: (String) -> Unit, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    CampoBoton(hora12(hora).ifBlank { hora }, Icons.Rounded.Schedule, modifier) { abierto = true }
    if (abierto) {
        val (h, m) = hora.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 9) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        val st = rememberTimePickerState(h, m, is24Hour = false)
        AlertDialog(
            onDismissRequest = { abierto = false },
            confirmButton = { TextButton({ onHora("%02d:%02d".format(st.hour, st.minute)); abierto = false }) { Text(L("Listo", "Done"), color = T.acento) } },
            dismissButton = { TextButton({ abierto = false }) { Text(L("Cancelar", "Cancel"), color = T.secundario) } },
            containerColor = T.tarjeta,
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(st, colors = TimePickerDefaults.colors(selectorColor = T.acento, timeSelectorSelectedContainerColor = T.acentoSuave,
                        periodSelectorSelectedContainerColor = T.acentoSuave))
                }
            },
        )
    }
}

/** Confirmación simple ("¿Eliminar esta cita?"). */
@Composable
fun Confirmar(titulo: String, texto: String, accion: String, peligro: Boolean = true, onSi: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo, containerColor = T.tarjeta,
        title = { Text(titulo, color = T.texto) },
        text = { Text(texto, color = T.secundario) },
        confirmButton = { TextButton(onSi) { Text(accion, color = if (peligro) T.peligro else T.acento) } },
        dismissButton = { TextButton(onNo) { Text(L("Cancelar", "Cancel"), color = T.secundario) } },
    )
}
