package xyz.vanty.aba.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.data.Cita
import xyz.vanty.aba.data.Racha
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.Pestana
import xyz.vanty.aba.ui.comp.Anillo
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.Contador
import xyz.vanty.aba.ui.comp.IconoTono
import xyz.vanty.aba.ui.comp.Llama
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.theme.MarcaDegradado
import xyz.vanty.aba.ui.theme.Naranja
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.diasHasta
import xyz.vanty.aba.util.fechaCorta
import xyz.vanty.aba.util.fechaLarga
import xyz.vanty.aba.util.hora12
import xyz.vanty.aba.util.letraDia
import xyz.vanty.aba.util.saludoSegunHora
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(e: Estado, vm: AppViewModel, pad: PaddingValues) {
    PullToRefreshBox(e.refrescando, vm::refrescar, Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
            item { Saludo(e, vm, Modifier.aparecer(0)) }
            item { TarjetaRacha(e.racha, Modifier.aparecer(1)) { vm.irA(Pestana.Practicar) } }
            item { Kpis(e, Modifier.aparecer(2)) }
            item { ProximaCita(e.proximas.firstOrNull(), Modifier.aparecer(3)) { vm.irA(Pestana.Citas) } }
            item { Progreso(e, Modifier.aparecer(4)) }
        }
    }
}

@Composable
private fun Saludo(e: Estado, vm: AppViewModel, modifier: Modifier) {
    val padre = xyz.vanty.aba.util.nombreDePila(e.perfil?.nombre)
    val hijo = e.hijo?.primerNombre.orEmpty()
    val r = e.racha
    Box(modifier.fillMaxWidth().background(MarcaDegradado, RoundedCornerShape(26.dp))) {
        Row(Modifier.padding(start = 20.dp, top = 18.dp, bottom = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${saludoSegunHora()}${if (padre.isNotBlank()) ", $padre" else ""}!", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        r.hoy -> L("¡Hoy ya practicaron con $hijo! ARIA está feliz 🎉", "You already practiced with $hijo today! ARIA is happy 🎉")
                        r.dias > 0 -> L("Llevan ${r.dias} ${if (r.dias == 1) "día" else "días"} seguidos. ¡No rompan la racha!", "${r.dias}-day streak. Don't break it!")
                        else -> L("¿Practicamos hoy con $hijo? Solo toma unos minutos.", "Shall we practice with $hijo today? It only takes a few minutes.")
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f),
                )
                if (!r.hoy) {
                    Spacer(Modifier.height(14.dp))
                    BotonGrande(
                        L("PRACTICAR AHORA", "PRACTICE NOW"), { vm.irA(Pestana.Practicar) },
                        Modifier.fillMaxWidth(0.92f), color = Color.White, labio = Color(0x55002A66), textoColor = T.acento,
                    )
                }
            }
            AriaFlotando(if (r.hoy) Aria.CELEBRA else Aria.SALUDO, 130.dp)
        }
    }
}

@Composable
private fun TarjetaRacha(r: Racha, modifier: Modifier, onClick: () -> Unit) {
    Tarjeta(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Llama(54.dp, r.hoy)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Contador(r.dias, MaterialTheme.typography.headlineMedium, if (r.hoy) Naranja else T.texto)
                    Spacer(Modifier.width(6.dp))
                    Text(L(if (r.dias == 1) "día de racha" else "días de racha", "day streak"), style = MaterialTheme.typography.titleSmall, color = T.secundario, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    if (r.hoy) L("¡Racha de hoy asegurada!", "Today's streak secured!") else L("Practica hoy para sumar un día", "Practice today to add a day"),
                    style = MaterialTheme.typography.bodySmall, color = if (r.hoy) T.exito else T.terciario,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val hoy = LocalDate.now().toString()
            val dias = r.semana.ifEmpty { (6 downTo 0).map { xyz.vanty.aba.data.DiaRacha(LocalDate.now().minusDays(it.toLong()).toString()) } }
            dias.forEachIndexed { i, d ->
                val esHoy = d.fecha == hoy
                val escala by animateFloatAsState(if (d.hecho) 1f else 0.85f, spring(0.4f, 300f), label = "d$i")
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(letraDia(LocalDate.parse(d.fecha)), style = MaterialTheme.typography.labelSmall, color = if (esHoy) T.acento else T.terciario)
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.size(32.dp).graphicsLayer { scaleX = escala; scaleY = escala }
                            .background(if (d.hecho) Naranja else T.relleno, CircleShape)
                            .then(if (esHoy && !d.hecho) Modifier.border(2.dp, T.acento, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d.hecho) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Kpis(e: Estado, modifier: Modifier) {
    val s = e.stats
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpi(Modifier.weight(1f), L("Sesiones", "Sessions"), s?.totalSesiones, "", Icons.Rounded.EventAvailable, T.exito, L("de terapia", "of therapy"))
            Kpi(Modifier.weight(1f), L("Objetivos", "Goals"), s?.goalsAchieved, "", Icons.Rounded.EmojiEvents, T.aviso,
                if (s != null) L("de ${s.totalGoals} logrados", "of ${s.totalGoals} achieved") else "")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Kpi(Modifier.weight(1f), L("Horas", "Hours"), s?.hoursTotal?.toInt(), "h", Icons.Rounded.AccessTime, T.acento, L("de trabajo", "of work"))
            Kpi(Modifier.weight(1f), L("Dominio", "Mastery"), s?.masteryRate, "%", Icons.Rounded.TrackChanges, T.acento, L("de objetivos", "of goals"))
        }
    }
}

@Composable
private fun Kpi(modifier: Modifier, titulo: String, valor: Int?, sufijo: String, icono: ImageVector, tono: Color, sub: String) {
    Tarjeta(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = T.secundario)
            IconoTono(icono, tono.copy(alpha = 0.14f), tono, 34.dp)
        }
        if (valor == null) Text("—", style = MaterialTheme.typography.headlineMedium, color = T.terciario)
        else Contador(valor, MaterialTheme.typography.headlineMedium, sufijo = sufijo)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = T.terciario, maxLines = 1)
    }
}

@Composable
private fun ProximaCita(c: Cita?, modifier: Modifier, onClick: () -> Unit) {
    Tarjeta(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoTono(Icons.Rounded.CalendarMonth, T.acentoSuave, T.acento)
            Spacer(Modifier.width(10.dp))
            Text(L("Próxima cita", "Next appointment"), style = MaterialTheme.typography.titleMedium, color = T.texto)
        }
        Spacer(Modifier.height(12.dp))
        if (c == null) {
            Text(L("No hay citas programadas. El centro te avisará cuando agende una.", "No upcoming appointments. Your center will let you know."), style = MaterialTheme.typography.bodyMedium, color = T.secundario)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (dia, mes) = fechaCorta(c.fecha)
                Column(
                    Modifier.background(T.acentoSuave, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(mes, style = MaterialTheme.typography.labelSmall, color = T.acento)
                    Text(dia, style = MaterialTheme.typography.headlineSmall, color = T.acento, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(fechaLarga(c.fecha), style = MaterialTheme.typography.titleSmall, color = T.texto)
                    Text(listOfNotNull(hora12(c.hora).ifBlank { null }, c.servicio).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                    val n = diasHasta(c.fecha)
                    Text(
                        when (n) { 0L -> L("¡Es hoy!", "It's today!"); 1L -> L("Mañana", "Tomorrow"); else -> L("En $n días", "In $n days") },
                        style = MaterialTheme.typography.labelMedium, color = if (n <= 1) Naranja else T.acento,
                    )
                }
            }
        }
    }
}

@Composable
private fun Progreso(e: Estado, modifier: Modifier) {
    val s = e.stats ?: return
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Anillo(s.masteryRate, 92.dp) {
                Contador(s.masteryRate, MaterialTheme.typography.titleLarge, sufijo = "%")
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(L("Nivel de progreso", "Progress level"), style = MaterialTheme.typography.labelMedium, color = T.secundario)
                Text(nivel(s.level), style = MaterialTheme.typography.titleLarge, color = T.texto)
                Spacer(Modifier.height(4.dp))
                Text(
                    L("${e.hijo?.primerNombre ?: "Tu peque"} domina ${s.goalsAchieved} de ${s.totalGoals} objetivos trabajados.",
                        "${e.hijo?.primerNombre ?: "Your child"} has mastered ${s.goalsAchieved} of ${s.totalGoals} goals."),
                    style = MaterialTheme.typography.bodySmall, color = T.terciario,
                )
            }
        }
    }
}

private fun nivel(l: String?) = when (l) {
    "Avanzado" -> L("Avanzado", "Advanced")
    "Intermedio" -> L("Intermedio", "Intermediate")
    "Básico" -> L("Básico", "Basic")
    else -> L("Inicial", "Getting started")
}
