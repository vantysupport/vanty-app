package xyz.vanty.aba.ui.comp

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.vanty.aba.R
import xyz.vanty.aba.ui.theme.MarcaDesde
import xyz.vanty.aba.ui.theme.MarcaHasta
import xyz.vanty.aba.ui.theme.T
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Poses de ARIA (mismas imágenes de /public/aria de la web). */
object Aria {
    @DrawableRes val SALUDO = R.drawable.aria_pose_1
    @DrawableRes val CELEBRA = R.drawable.aria_pose_7
    @DrawableRes val NEUTRAL = R.drawable.aria_pose_3
    @DrawableRes val SENTADA = R.drawable.aria_pose_5
    @DrawableRes val POSE_2 = R.drawable.aria_pose_2
    @DrawableRes val POSE_4 = R.drawable.aria_pose_4
    @DrawableRes val POSE_8 = R.drawable.aria_pose_8
}

/** ARIA flotando suavemente (como el saludo animado del portal web). */
@Composable
fun AriaFlotando(@DrawableRes pose: Int, alto: Dp, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "aria")
    val y by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(pose) { entrada.snapTo(0.6f); entrada.animateTo(1f, spring(0.45f, 300f)) }
    Image(
        painter = painterResource(pose),
        contentDescription = "ARIA",
        modifier = modifier.height(alto).graphicsLayer {
            translationY = -y * 6.dp.toPx()
            rotationZ = (y - 0.5f) * 4f
            scaleX = entrada.value; scaleY = entrada.value
        },
    )
}

/** Entrada escalonada de tarjetas: suben y aparecen una tras otra. */
@Composable
fun Modifier.aparecer(indice: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L * indice)
        a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 210f))
    }
    return graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 36.dp.toPx()
    }
}

/** Escala con rebote al presionar + vibración corta. */
@Composable
fun Modifier.presionable(habilitado: Boolean = true, onClick: () -> Unit): Modifier {
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val escala by animateFloatAsState(if (presionado) 0.96f else 1f, spring(0.5f, 600f), label = "esc")
    val haptic = LocalHapticFeedback.current
    return graphicsLayer { scaleX = escala; scaleY = escala }
        .clickable(fuente, indication = null, enabled = habilitado) {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        }
}

@Composable
fun Tarjeta(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val forma = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.presionable(onClick = onClick) else Modifier)
            .background(T.tarjeta, forma)
            .border(1.dp, T.borde, forma)
            .padding(18.dp),
        content = content,
    )
}

/** Botón "3D" con labio inferior, como los de Duolingo: baja al presionarlo. */
@Composable
fun BotonGrande(
    texto: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color = T.acento, labio: Color = MarcaHasta.copy(alpha = 0.9f), textoColor: Color = Color.White,
    icono: ImageVector? = null, cargando: Boolean = false, habilitado: Boolean = true,
) {
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val baja by animateDpAsState(if (presionado) 4.dp else 0.dp, spring(0.6f, 900f), label = "baja")
    val haptic = LocalHapticFeedback.current
    val forma = RoundedCornerShape(16.dp)
    val activo = habilitado && !cargando
    Box(modifier.height(56.dp).graphicsLayer { alpha = if (habilitado) 1f else 0.5f }) {
        Box(Modifier.fillMaxWidth().height(52.dp).offset(y = 4.dp).background(labio, forma))
        Row(
            Modifier.fillMaxWidth().height(52.dp).offset(y = baja).background(color, forma)
                .clickable(fuente, indication = null, enabled = activo) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cargando) {
                CircularProgressIndicator(Modifier.size(22.dp), color = textoColor, strokeWidth = 2.5.dp)
            } else {
                if (icono != null) {
                    Icon(icono, null, tint = textoColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(texto, color = textoColor, style = MaterialTheme.typography.labelLarge, letterSpacing = 0.3.sp)
            }
        }
    }
}

/** Número que cuenta desde 0 hasta el valor (como CountUp de la web). */
@Composable
fun Contador(valor: Int, style: TextStyle, color: Color = T.texto, sufijo: String = "", desde: Int = 0) {
    val a = remember { Animatable(desde.toFloat()) }
    LaunchedEffect(valor) { a.animateTo(valor.toFloat(), tween(1100, easing = FastOutSlowInEasing)) }
    Text("${kotlin.math.round(a.value).toInt()}$sufijo", style = style, color = color)
}

/** Llama de la racha dibujada a mano, con parpadeo. Gris si hoy todavía no practicaron. */
@Composable
fun Llama(tamano: Dp, encendida: Boolean, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "llama")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val fuera = if (encendida) Brush.verticalGradient(listOf(Color(0xFFFFB020), Color(0xFFFF7A1A), Color(0xFFFF5A1F)))
    else Brush.verticalGradient(listOf(Color(0xFFC9D2DE), Color(0xFFA7B3C2)))
    val dentro = if (encendida) Brush.verticalGradient(listOf(Color(0xFFFFF3B0), Color(0xFFFFD43B)))
    else Brush.verticalGradient(listOf(Color(0xFFEFF3F8), Color(0xFFD5DCE6)))
    Canvas(modifier.size(tamano)) {
        val w = size.width; val h = size.height
        val onda = sin(t * 2 * PI).toFloat()
        val sy = if (encendida) 1f + onda * 0.045f else 1f
        val giro = if (encendida) onda * 2.5f else 0f
        fun gota(ancho: Float, alto: Float, dx: Float, dy: Float) = Path().apply {
            moveTo(dx + ancho * 0.52f, dy)
            cubicTo(dx + ancho * 0.62f, dy + alto * 0.24f, dx + ancho * 0.98f, dy + alto * 0.40f, dx + ancho * 0.94f, dy + alto * 0.68f)
            cubicTo(dx + ancho * 0.90f, dy + alto * 0.92f, dx + ancho * 0.72f, dy + alto, dx + ancho * 0.5f, dy + alto)
            cubicTo(dx + ancho * 0.28f, dy + alto, dx + ancho * 0.08f, dy + alto * 0.90f, dx + ancho * 0.06f, dy + alto * 0.66f)
            cubicTo(dx + ancho * 0.04f, dy + alto * 0.46f, dx + ancho * 0.22f, dy + alto * 0.36f, dx + ancho * 0.30f, dy + alto * 0.18f)
            cubicTo(dx + ancho * 0.36f, dy + alto * 0.30f, dx + ancho * 0.40f, dy + alto * 0.10f, dx + ancho * 0.52f, dy)
            close()
        }
        rotate(giro, Offset(w / 2, h)) {
            scale(1f, sy, Offset(w / 2, h)) {
                drawPath(gota(w, h, 0f, 0f), fuera)
                val iw = w * 0.52f; val ih = h * 0.52f
                drawPath(gota(iw, ih, (w - iw) / 2, h - ih - h * 0.04f), dentro)
            }
        }
    }
}

/** Anillo de progreso animado con el degradado de la marca. */
@Composable
fun Anillo(pct: Int, tamano: Dp, grosor: Dp = 9.dp, content: @Composable () -> Unit = {}) {
    var objetivo by remember { mutableIntStateOf(0) }
    LaunchedEffect(pct) { objetivo = pct }
    val p by animateFloatAsState(objetivo / 100f, tween(1300, easing = FastOutSlowInEasing), label = "anillo")
    val pista = T.relleno
    Box(Modifier.size(tamano), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val g = grosor.toPx()
            val s = Size(size.width - g, size.height - g)
            val o = Offset(g / 2, g / 2)
            drawArc(pista, 0f, 360f, false, o, s, style = Stroke(g))
            drawArc(Brush.sweepGradient(listOf(MarcaDesde, MarcaHasta, MarcaDesde)), -90f, 360f * p, false, o, s, style = Stroke(g, cap = StrokeCap.Round))
        }
        content()
    }
}

private data class Papel(val x: Float, val vel: Float, val color: Color, val giro: Float, val ancho: Float, val fase: Float)

data class ItemBarra<K>(val clave: K, val icono: ImageVector, val texto: String, val insignia: Int = 0)

/** Barra inferior: el ícono elegido crece con rebote dentro de una pastilla; insignia roja con el número pendiente. */
@Composable
fun <K> BarraNav(items: List<ItemBarra<K>>, actual: K, onClick: (K) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(T.tarjeta).border(1.dp, T.borde)
            .navigationBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEach { it ->
            val sel = it.clave == actual
            val escala by animateFloatAsState(if (sel) 1.12f else 1f, spring(0.4f, 500f), label = "nav")
            Column(
                Modifier.weight(1f).presionable { onClick(it.clave) }.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Box(
                        Modifier.graphicsLayer { scaleX = escala; scaleY = escala }
                            .background(if (sel) T.acentoSuave else Color.Transparent, RoundedCornerShape(14.dp))
                            .border(if (sel) 1.5.dp else 0.dp, if (sel) T.acento.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                    ) {
                        Icon(it.icono, null, tint = if (sel) T.acento else T.terciario, modifier = Modifier.size(25.dp))
                    }
                    if (it.insignia > 0) {
                        Text(
                            if (it.insignia > 9) "9+" else "${it.insignia}", color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                                .background(T.peligro, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(it.texto, style = MaterialTheme.typography.labelSmall, color = if (sel) T.acento else T.terciario, maxLines = 1)
            }
        }
    }
}

/** Lluvia de confeti que dura ~2.8 s. Con `monedas`, caen monedas doradas (cobros). */
@Composable
fun Confeti(modifier: Modifier = Modifier, monedas: Boolean = false) {
    if (monedas) { LluviaMonedas(modifier); return }
    val colores = listOf(MarcaDesde, MarcaHasta, Color(0xFFFFC53D), Color(0xFF38BDF8), Color(0xFF93C5FD), Color.White)
    val papeles = remember { List(90) { Papel(Random.nextFloat(), 0.6f + Random.nextFloat() * 0.8f, colores.random(), Random.nextFloat() * 720f, 6f + Random.nextFloat() * 8f, Random.nextFloat() * 6f) } }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(2800, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val v = p.value
        papeles.forEach { q ->
            val y = -30f + v * (size.height + 60f) * q.vel * 1.3f
            val x = q.x * size.width + sin(v * 8f + q.fase) * 28f
            rotate(q.giro * v, Offset(x, y)) {
                drawRect(q.color.copy(alpha = (1.4f - v).coerceIn(0f, 1f)), Offset(x, y), Size(q.ancho, q.ancho * 0.55f))
            }
        }
    }
}

private data class Moneda(val x: Float, val vel: Float, val tam: Float, val fase: Float)

@Composable
private fun LluviaMonedas(modifier: Modifier) {
    val monedas = remember { List(45) { Moneda(Random.nextFloat(), 0.7f + Random.nextFloat() * 0.7f, 14f + Random.nextFloat() * 14f, Random.nextFloat() * 6f) } }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(2600, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val v = p.value
        monedas.forEach { m ->
            val y = -40f + v * (size.height + 80f) * m.vel * 1.25f
            val x = m.x * size.width
            // Giro de la moneda: se "aplana" en horizontal como si diera vueltas
            val giro = kotlin.math.abs(sin(v * 14f + m.fase))
            val a = (1.3f - v).coerceIn(0f, 1f)
            val r = m.tam
            drawOval(Color(0xFFE5A100).copy(alpha = a), Offset(x - r * giro, y - r), Size(2 * r * giro + 1f, 2 * r))
            drawOval(Color(0xFFFFD43B).copy(alpha = a), Offset(x - r * 0.75f * giro, y - r * 0.75f), Size(1.5f * r * giro + 1f, 1.5f * r))
        }
    }
}

@Composable
fun Etiqueta(texto: String, fondo: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        texto, color = color, style = MaterialTheme.typography.labelSmall,
        modifier = modifier.background(fondo, CircleShape).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
fun IconoTono(icono: ImageVector, fondo: Color, color: Color, tamano: Dp = 38.dp) {
    Box(Modifier.size(tamano).background(fondo, RoundedCornerShape(30)), contentAlignment = Alignment.Center) {
        Icon(icono, null, tint = color, modifier = Modifier.size(tamano * 0.48f))
    }
}

@Composable
fun Vacio(@DrawableRes pose: Int, titulo: String, texto: String, accion: String? = null, onAccion: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AriaFlotando(pose, 120.dp)
        Spacer(Modifier.height(14.dp))
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = T.texto)
        Spacer(Modifier.height(4.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (accion != null) {
            Spacer(Modifier.height(18.dp))
            BotonGrande(accion, onAccion, Modifier.fillMaxWidth(0.8f))
        }
    }
}

/**
 * Selector de idioma ES | EN (el mismo de la web): la pastilla se desliza al elegido.
 * `sobreAzul` = versión blanca translúcida para ponerla sobre el degradado de la marca.
 */
@Composable
fun SelectorIdioma(actual: String, onCambio: (String) -> Unit, modifier: Modifier = Modifier, sobreAzul: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val x by animateDpAsState(if (actual == "en") 44.dp else 0.dp, spring(0.7f, 500f), label = "idioma")
    val fondo = if (sobreAzul) Color.White.copy(alpha = 0.2f) else T.relleno
    val pastilla = if (sobreAzul) Color.White else T.tarjeta
    Box(modifier.background(fondo, CircleShape).padding(3.dp)) {
        Box(Modifier.offset(x = x).size(44.dp, 30.dp).background(pastilla, CircleShape))
        Row {
            listOf("es" to "ES", "en" to "EN").forEach { (codigo, texto) ->
                val sel = codigo == actual
                Box(
                    Modifier.size(44.dp, 30.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onCambio(codigo)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        texto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
                        color = when {
                            sel -> T.acento
                            sobreAzul -> Color.White
                            else -> T.secundario
                        },
                    )
                }
            }
        }
    }
}

val Negrita = FontWeight.ExtraBold
