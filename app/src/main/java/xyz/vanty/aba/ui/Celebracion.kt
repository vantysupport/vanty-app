package xyz.vanty.aba.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.vanty.aba.data.Racha
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.Confeti
import xyz.vanty.aba.ui.comp.Contador
import xyz.vanty.aba.ui.comp.Llama
import xyz.vanty.aba.ui.theme.Naranja
import xyz.vanty.aba.ui.theme.Oro
import xyz.vanty.aba.util.L
import java.time.LocalDate

/** Pantalla completa al sumar un día a la racha, como la de Duolingo. */
@Composable
fun Celebracion(dias: Int, racha: Racha, onCerrar: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val llama = remember { Animatable(0f) }
    val texto = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        llama.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 220f))
        texto.animateTo(1f, spring(0.7f, 260f))
        delay(250)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xF20B1B33))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Confeti()
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Llama(170.dp, true, Modifier.graphicsLayer { scaleX = llama.value; scaleY = llama.value })
            }
            Contador(
                dias, desde = (dias - 1).coerceAtLeast(0),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 84.sp, fontWeight = FontWeight.Black),
                color = Oro,
            )
            Column(
                Modifier.graphicsLayer { alpha = texto.value; translationY = (1 - texto.value) * 40f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (dias == 1) L("¡día de racha!", "day streak!") else L("¡días de racha!", "day streak!"),
                    style = MaterialTheme.typography.headlineMedium, color = Naranja,
                )
                Spacer(Modifier.height(18.dp))
                Semana(racha)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AriaFlotando(Aria.CELEBRA, 96.dp)
                    Text(
                        mensaje(dias), color = Color.White, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                }
                Spacer(Modifier.height(24.dp))
                BotonGrande(L("¡CONTINUAR!", "CONTINUE!"), onCerrar, Modifier.fillMaxWidth(), color = Naranja, labio = Color(0xFF0B5C9E))
            }
        }
    }
}

@Composable
private fun Semana(r: Racha) {
    val hoy = LocalDate.now().toString()
    Row(
        Modifier.background(Color.White.copy(alpha = 0.08f), CircleShape).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        r.semana.forEach { d ->
            val hecho = d.hecho || (d.fecha == hoy && r.hoy)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    xyz.vanty.aba.util.letraDia(LocalDate.parse(d.fecha)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d.fecha == hoy) Oro else Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.size(26.dp).background(if (hecho) Naranja else Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hecho) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun mensaje(dias: Int) = when {
    dias >= 30 -> L("¡Un mes de constancia! Esto es lo que transforma el aprendizaje.", "A month of consistency! This is what transforms learning.")
    dias >= 7 -> L("¡Una semana completa! La práctica diaria multiplica los avances.", "A full week! Daily practice multiplies progress.")
    dias >= 3 -> L("¡Van muy bien! Sigan así mañana.", "You're doing great! Keep it up tomorrow.")
    else -> L("¡Gran comienzo! Vuelvan mañana para sumar otro día.", "Great start! Come back tomorrow to add another day.")
}
