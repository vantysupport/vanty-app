package xyz.vanty.aba.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.vanty.aba.data.Comun
import xyz.vanty.aba.data.Mensaje
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.MarcaDegradado
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pantalla de chat reutilizable: burbujas (las propias con el degradado de la marca), ARIA "escribiendo"
 * con puntos que rebotan, formato básico (**negrita** y viñetas), sugerencias rápidas y envío con vibración.
 */
@Composable
fun PantallaChat(
    e: EstadoChat,
    onEnviar: (String) -> Unit,
    modifier: Modifier = Modifier,
    vacio: @Composable () -> Unit = {},
    sugerencias: List<String> = emptyList(),
    placeholder: String = L("Escribe un mensaje…", "Write a message…"),
    refrescoCada: Long? = null,
    onRefrescar: () -> Unit = {},
) {
    val lista = rememberLazyListState()
    var texto by rememberSaveable { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()

    LaunchedEffect(e.mensajes.size, e.escribiendo) {
        val total = e.mensajes.size + if (e.escribiendo) 1 else 0
        if (total > 0) lista.animateScrollToItem(total - 1)
    }
    if (refrescoCada != null) LaunchedEffect(Unit) { while (true) { delay(refrescoCada); onRefrescar() } }

    Column(modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (e.mensajes.isEmpty() && !e.cargando && !e.escribiendo) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { vacio() }
            }
            LazyColumn(
                state = lista, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(e.mensajes, key = { it.id }) { m ->
                    Burbuja(m, Modifier.animateItem()) {
                        alcance.launch {
                            Comun.abrirArchivo(m.archivo)?.let { url ->
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                            }
                        }
                    }
                }
                if (e.escribiendo) item(key = "escribiendo") { Escribiendo() }
            }
        }
        e.error?.let {
            Text(it, color = T.peligro, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().background(T.peligro.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 6.dp))
        }
        if (sugerencias.isNotEmpty() && e.mensajes.size <= 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sugerencias.forEach { s ->
                    Text(s, style = MaterialTheme.typography.labelLarge, color = T.acento,
                        modifier = Modifier.presionable { onEnviar(s) }.background(T.acentoSuave, CircleShape)
                            .border(1.dp, T.acento.copy(alpha = 0.25f), CircleShape).padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
        // Barra para escribir
        Row(
            Modifier.fillMaxWidth().background(T.tarjeta).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                texto, { texto = it }, placeholder = { Text(placeholder) }, maxLines = 5,
                shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = T.relleno, unfocusedContainerColor = T.relleno,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            val listo = texto.isNotBlank() && !e.escribiendo
            val escala by animateFloatAsState(if (listo) 1f else 0.85f, spring(0.45f, 500f), label = "env")
            Box(
                Modifier.padding(start = 8.dp, bottom = 4.dp).size(48.dp).graphicsLayer { scaleX = escala; scaleY = escala }
                    .clip(CircleShape)
                    .then(if (listo) Modifier.background(MarcaDegradado) else Modifier.background(T.relleno))
                    .presionable(habilitado = listo) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onEnviar(texto); texto = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, L("Enviar", "Send"), tint = if (listo) Color.White else T.terciario)
            }
        }
    }
}

@Composable
private fun Burbuja(m: Mensaje, modifier: Modifier, onArchivo: () -> Unit) {
    val forma = if (m.mio) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (m.mio) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (m.esAria) {
            Image(painterResource(Aria.SALUDO), "ARIA", Modifier.size(30.dp).padding(end = 4.dp))
        }
        Column(
            Modifier.widthIn(max = 300.dp).clip(forma)
                .then(if (m.mio) Modifier.background(MarcaDegradado) else Modifier.background(T.tarjeta).border(1.dp, T.borde, forma))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!m.mio && !m.autor.isNullOrBlank()) {
                Text(m.autor, style = MaterialTheme.typography.labelSmall, color = T.acento)
                Spacer(Modifier.height(2.dp))
            }
            if (m.texto.isNotBlank()) Text(conFormato(m.texto), style = MaterialTheme.typography.bodyMedium, color = if (m.mio) Color.White else T.texto)
            if (m.archivo != null) {
                Row(
                    Modifier.padding(top = 6.dp).presionable(onClick = onArchivo)
                        .background(if (m.mio) Color.White.copy(alpha = 0.2f) else T.relleno, RoundedCornerShape(12.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AttachFile, null, tint = if (m.mio) Color.White else T.acento, modifier = Modifier.size(18.dp))
                    Text(m.nombreArchivo ?: L("Archivo adjunto", "Attachment"), style = MaterialTheme.typography.labelMedium,
                        color = if (m.mio) Color.White else T.acento, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Row(Modifier.align(Alignment.End).padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(hora(m.fecha), style = MaterialTheme.typography.labelSmall, color = if (m.mio) Color.White.copy(alpha = 0.75f) else T.terciario)
                if (m.mio && m.leido) Icon(Icons.Rounded.DoneAll, L("Leído", "Read"), tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(start = 3.dp).size(14.dp))
            }
        }
    }
}

/** ARIA escribiendo: tres puntos que rebotan uno tras otro. */
@Composable
private fun Escribiendo() {
    val inf = rememberInfiniteTransition(label = "esc")
    Row(verticalAlignment = Alignment.Bottom) {
        AriaFlotando(Aria.SALUDO, 34.dp)
        Row(
            Modifier.padding(start = 4.dp).background(T.tarjeta, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .border(1.dp, T.borde, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(3) { i ->
                val y by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(420, delayMillis = i * 140), RepeatMode.Reverse), label = "d$i")
                Box(Modifier.size(8.dp).graphicsLayer { translationY = -y * 6.dp.toPx() }.background(T.acento.copy(alpha = 0.4f + 0.6f * y), CircleShape))
            }
        }
    }
}

/** **negrita** y viñetas ("- " / "* " al inicio de línea), como responde ARIA. */
fun conFormato(texto: String): AnnotatedString = buildAnnotatedString {
    texto.replace("\r", "").lines().forEachIndexed { n, linea0 ->
        if (n > 0) append("\n")
        val linea = linea0.replace(Regex("^\\s*[-*•]\\s+"), "• ").replace(Regex("^#{1,4}\\s*"), "")
        val partes = linea.split("**")
        partes.forEachIndexed { k, p -> if (k % 2 == 1 && k < partes.lastIndex) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p) } else append(p) }
    }
}

private fun hora(iso: String?): String = runCatching {
    val instante = runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrElse { Instant.parse(iso) }
    instante.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrElse { "" }
