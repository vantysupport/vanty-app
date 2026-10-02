package xyz.vanty.aba.ui.equipo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.data.Pago
import xyz.vanty.aba.ui.comp.CabeceraSub
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.MarcaDesde
import xyz.vanty.aba.ui.theme.MarcaHasta
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.EN
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.monedaFmt
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val COLOR_METODO = mapOf(
    "efectivo" to Color(0xFF0063D8), "yape" to Color(0xFF1E40AF), "plin" to Color(0xFF01ABFC),
    "transferencia" to Color(0xFF38BDF8), "tarjeta" to Color(0xFF1D4ED8),
)
private val PALETA = listOf(Color(0xFF0063D8), Color(0xFF01ABFC), Color(0xFF38BDF8), Color(0xFF1E40AF), Color(0xFF7DD3FC), Color(0xFF3B82F6))

/** Mes (0–11) y año de un cobro, en hora local (la web usa la de Lima). */
private fun mesDe(p: Pago): Pair<Int, Int>? = runCatching {
    val f = p.fecha
    val d = if (f.length > 10) runCatching { java.time.OffsetDateTime.parse(f).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrElse { LocalDate.parse(f.take(10)) } else LocalDate.parse(f)
    d.monthValue - 1 to d.year
}.getOrNull()

/** Reportes financieros (mismos cálculos que AdminReportesFinancieros de la web). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportesScreen(e: EstadoEquipo, vm: EquipoViewModel, pad: PaddingValues) {
    LaunchedEffect(Unit) { if (e.pagosAnio.isEmpty()) vm.cargarPagos() }
    val hoy = LocalDate.now()
    val anio = hoy.year
    var mes by rememberSaveable { mutableStateOf(hoy.monthValue - 1) }
    val loc = if (EN) Locale.ENGLISH else Locale.forLanguageTag("es")
    val nombreMes = { m: Int -> java.time.Month.of(m + 1).getDisplayName(TextStyle.SHORT, loc).replaceFirstChar { it.titlecase(loc) }.trimEnd('.') }

    val delMes = { m: Int, y: Int -> e.pagosAnio.filter { mesDe(it) == (m to y) } }
    val actual = delMes(mes, anio)
    val previo = if (mes == 0) delMes(11, anio - 1) else delMes(mes - 1, anio)
    val cobrado = actual.sumOf { it.cobrado }
    val cobradoPrev = previo.sumOf { it.cobrado }
    val pendiente = actual.sumOf { it.saldo }
    val cobrables = actual.filter { it.status != "cancelled" && it.status != "refunded" }
    val tasa = if (cobrables.isEmpty()) 0 else cobrables.count { it.cobrado > 0 } * 100 / cobrables.size
    val enCurso = mes == hoy.monthValue - 1
    val delta = if (cobradoPrev > 0 && !enCurso) ((cobrado - cobradoPrev) / cobradoPrev * 100) else null
    val porMes = (0..11).map { m -> delMes(m, anio).sumOf { it.cobrado } }
    val pagados = actual.filter { it.cobrado > 0 }
    val porMetodo = pagados.groupBy { it.metodo ?: "efectivo" }.mapValues { (_, l) -> l.sumOf { it.cobrado } }.toList().sortedByDescending { it.second }
    val porPaciente = pagados.groupBy { it.paciente }.mapValues { (_, l) -> l.sumOf { it.cobrado } }.toList().sortedByDescending { it.second }.take(6)
    val porServicio = pagados.groupBy { it.concept?.replace(Regex("\\s*\\(\\d+/\\d+\\)$"), "")?.trim().orEmpty().ifBlank { L("Otro", "Other") } }
        .mapValues { (_, l) -> l.sumOf { it.cobrado } }.toList().sortedByDescending { it.second }.take(6)

    PullToRefreshBox(e.refrescando, { vm.cargarPagos() }, Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item { CabeceraSub(L("Reportes financieros", "Financial reports")) { vm.irA(TabEquipo.Mas) } }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..hoy.monthValue - 1).reversed().forEach { m ->
                        val sel = m == mes
                        Text(nombreMes(m), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (sel) Color.White else T.secundario,
                            modifier = Modifier.presionable { mes = m }.then(if (sel) Modifier.background(Brush.linearGradient(listOf(MarcaDesde, MarcaHasta)), CircleShape) else Modifier.background(T.relleno, CircleShape))
                                .padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                }
            }
            item {
                Row(Modifier.aparecer(0), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tarjeta(Modifier.weight(1f)) {
                        Text(L("Cobrado", "Collected"), style = MaterialTheme.typography.labelMedium, color = T.secundario)
                        Text(monedaFmt(cobrado, e.moneda), style = MaterialTheme.typography.titleLarge, color = T.exito, maxLines = 1)
                        if (delta != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (delta >= 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, null,
                                tint = if (delta >= 0) T.exito else T.peligro, modifier = Modifier.size(16.dp))
                            Text(" ${"%+.0f".format(delta)}% " + L("vs mes anterior", "vs last month"), style = MaterialTheme.typography.labelSmall, color = T.terciario)
                        }
                    }
                    Tarjeta(Modifier.weight(1f)) {
                        Text(L("Pendiente", "Pending"), style = MaterialTheme.typography.labelMedium, color = T.secundario)
                        Text(monedaFmt(pendiente, e.moneda), style = MaterialTheme.typography.titleLarge, color = T.peligro, maxLines = 1)
                        Text(L("Tasa de cobro $tasa %", "Collection rate $tasa%"), style = MaterialTheme.typography.labelSmall, color = T.terciario)
                    }
                }
            }
            item {
                Tarjeta(Modifier.aparecer(1)) {
                    Text(L("Ingresos $anio", "Income $anio"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                    Spacer(Modifier.height(12.dp))
                    Barras(porMes, mes, nombreMes) { mes = it }
                }
            }
            if (porMetodo.isNotEmpty()) item {
                Tarjeta(Modifier.aparecer(2)) {
                    Text(L("Métodos de pago", "Payment methods"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                    Spacer(Modifier.height(10.dp))
                    val total = porMetodo.sumOf { it.second }.coerceAtLeast(0.01)
                    // Barra apilada
                    Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
                        porMetodo.forEach { (m, v) -> Box(Modifier.weight((v / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(COLOR_METODO[m] ?: T.acento)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    porMetodo.forEach { (m, v) ->
                        val nombre = METODOS.firstOrNull { it.first == m }?.second?.let { L(it.first, it.second) } ?: m
                        LineaValor(COLOR_METODO[m] ?: T.acento, nombre, monedaFmt(v, e.moneda), "${(v / total * 100).toInt()} %")
                    }
                }
            }
            if (porServicio.isNotEmpty()) item {
                Tarjeta(Modifier.aparecer(3)) {
                    Text(L("Por servicio", "By service"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                    Spacer(Modifier.height(10.dp))
                    BarrasHorizontales(porServicio, e.moneda)
                }
            }
            if (porPaciente.isNotEmpty()) item {
                Tarjeta(Modifier.aparecer(4)) {
                    Text(L("Pacientes con más ingresos", "Top patients by income"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                    Spacer(Modifier.height(10.dp))
                    BarrasHorizontales(porPaciente, e.moneda)
                }
            }
        }
    }
}

@Composable
private fun LineaValor(color: Color, nombre: String, valor: String, extra: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(nombre, style = MaterialTheme.typography.bodyMedium, color = T.texto, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.titleSmall, color = T.texto)
        Spacer(Modifier.width(8.dp))
        Text(extra, style = MaterialTheme.typography.labelSmall, color = T.terciario, modifier = Modifier.width(40.dp))
    }
}

/** Barras por mes que crecen al entrar; la del mes elegido va con el degradado de la marca. */
@Composable
private fun Barras(valores: List<Double>, sel: Int, nombre: (Int) -> String, onSel: (Int) -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(valores) { p.snapTo(0f); p.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val max = (valores.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val pista = T.relleno
    val gris = T.acento.copy(alpha = 0.25f)
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val ancho = size.width / 12f
            valores.forEachIndexed { i, v ->
                val h = (v / max).toFloat() * size.height * p.value
                val x = i * ancho + ancho * 0.18f
                val w = ancho * 0.64f
                drawRoundRect(pista, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                if (h > 0) {
                    if (i == sel) drawRoundRect(Brush.verticalGradient(listOf(MarcaDesde, MarcaHasta), startY = size.height - h, endY = size.height), Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
                    else drawRoundRect(gris, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            (0..11).forEach { i ->
                Text(nombre(i).take(1), style = MaterialTheme.typography.labelSmall, color = if (i == sel) T.acento else T.terciario,
                    modifier = Modifier.weight(1f).presionable { onSel(i) }, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun BarrasHorizontales(datos: List<Pair<String, Double>>, moneda: String) {
    val max = (datos.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    val p = remember { Animatable(0f) }
    LaunchedEffect(datos) { p.snapTo(0f); p.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    datos.forEachIndexed { i, (nombre, v) ->
        Column(Modifier.padding(vertical = 5.dp)) {
            Row {
                Text(nombre, style = MaterialTheme.typography.bodySmall, color = T.texto, modifier = Modifier.weight(1f), maxLines = 1)
                Text(monedaFmt(v, moneda), style = MaterialTheme.typography.labelMedium, color = T.texto)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(T.relleno)) {
                Box(Modifier.fillMaxWidth((v / max).toFloat() * p.value).fillMaxHeight().clip(CircleShape).background(PALETA[i % PALETA.size]))
            }
        }
    }
}
