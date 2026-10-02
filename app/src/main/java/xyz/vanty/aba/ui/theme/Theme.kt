package xyz.vanty.aba.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Mismos tokens que app/vanty-theme.css de la web
val MarcaDesde = Color(0xFF01ABFC)
val MarcaHasta = Color(0xFF0063D8)
val MarcaDegradado = Brush.linearGradient(listOf(MarcaDesde, MarcaHasta))
val Fuego = Brush.verticalGradient(listOf(Color(0xFFBAE6FD), Color(0xFF01ABFC), Color(0xFF0063D8)))
val Oro = Color(0xFF5AC8FA)
val Naranja = Color(0xFF0A8CF0)

@Immutable
data class Tonos(
    val fondo: Color, val tarjeta: Color, val borde: Color, val relleno: Color,
    val texto: Color, val secundario: Color, val terciario: Color,
    val acento: Color, val acentoSuave: Color, val exito: Color, val aviso: Color, val peligro: Color,
)

private val Claro = Tonos(
    fondo = Color(0xFFF3F8FE), tarjeta = Color.White, borde = Color(0xFFE2EAF5), relleno = Color(0xFFEEF3FA),
    texto = Color(0xFF0B1B33), secundario = Color(0xFF4A5B78), terciario = Color(0xFF7A8BA6),
    acento = Color(0xFF0069DB), acentoSuave = Color(0x1A0199F5), exito = Color(0xFF0EA5E9), aviso = Color(0xFF1D4ED8), peligro = Color(0xFFE5484D),
)
private val Oscuro = Tonos(
    fondo = Color(0xFF0D1117), tarjeta = Color(0xFF161B22), borde = Color(0xFF262D36), relleno = Color(0xFF1F262E),
    texto = Color(0xFFE6EDF3), secundario = Color(0xFF9BA7B4), terciario = Color(0xFF6E7681),
    acento = Color(0xFF3BB6FF), acentoSuave = Color(0x243BB6FF), exito = Color(0xFF38BDF8), aviso = Color(0xFF60A5FA), peligro = Color(0xFFFF6B6F),
)

val LocalTonos = staticCompositionLocalOf { Claro }
val T: Tonos @Composable get() = LocalTonos.current

private val Tipos = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun VantyTheme(oscuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val t = if (oscuro) Oscuro else Claro
    val esquema = if (oscuro) darkColorScheme(
        primary = t.acento, onPrimary = Color.White, background = t.fondo, surface = t.tarjeta,
        onBackground = t.texto, onSurface = t.texto, surfaceVariant = t.relleno, onSurfaceVariant = t.secundario,
        outline = t.borde, error = t.peligro,
    ) else lightColorScheme(
        primary = t.acento, onPrimary = Color.White, background = t.fondo, surface = t.tarjeta,
        onBackground = t.texto, onSurface = t.texto, surfaceVariant = t.relleno, onSurfaceVariant = t.secundario,
        outline = t.borde, error = t.peligro,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalTonos provides t) {
        MaterialTheme(
            colorScheme = esquema,
            typography = Tipos,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
