package xyz.vanty.aba.util

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import xyz.vanty.aba.data.Prefs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Idioma de la app (ES | EN), igual que el selector de la web. Se guarda en el teléfono; la primera vez
 * se toma del idioma del sistema. Es un estado de Compose: al cambiarlo, toda la interfaz se redibuja.
 */
object Idioma {
    private val estado = mutableStateOf("es")
    var actual: String
        get() = estado.value
        set(v) { estado.value = if (v == "en") "en" else "es" }

    fun iniciar(ctx: Context) { actual = Prefs(ctx).idioma }
}

/** Tema claro/oscuro de la app ("system" sigue al teléfono). Lo cambia la web al elegir Apariencia. */
object TemaApp {
    private val estado = mutableStateOf("system")
    var actual: String
        get() = estado.value
        set(v) { estado.value = if (v == "light" || v == "dark") v else "system" }

    fun iniciar(ctx: Context) { actual = Prefs(ctx).tema }
}

val EN: Boolean get() = Idioma.actual == "en"

fun L(es: String, en: String) = if (EN) en else es

private val locale get() = if (EN) Locale.ENGLISH else Locale.forLanguageTag("es")

fun hoyIso(): String = LocalDate.now().toString()

/** Lunes a domingo de la semana actual, como en el registro de práctica de la web. */
fun semanaActual(): List<LocalDate> {
    val lunes = LocalDate.now().with(DayOfWeek.MONDAY)
    return (0..6).map { lunes.plusDays(it.toLong()) }
}

fun letraDia(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale)

fun fechaLarga(iso: String): String = runCatching {
    LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern(if (EN) "EEEE, MMM d" else "EEEE d 'de' MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }
}.getOrDefault(iso)

fun fechaCorta(iso: String): Pair<String, String> = runCatching {
    val d = LocalDate.parse(iso.take(10))
    d.dayOfMonth.toString() to d.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).trimEnd('.')
}.getOrDefault("" to "")

fun hora12(hhmm: String?): String {
    if (hhmm.isNullOrBlank()) return ""
    return runCatching {
        LocalTime.parse(hhmm.take(5)).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    }.getOrDefault(hhmm.take(5))
}

fun diasHasta(iso: String): Long = runCatching {
    java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(iso.take(10)))
}.getOrDefault(0)

/** Monto con el símbolo de la moneda del centro (la web usa PEN por defecto). */
fun monedaFmt(v: Double, moneda: String): String {
    val simbolo = when (moneda.uppercase()) {
        "PEN" -> "S/"; "USD", "MXN", "COP", "CLP", "ARS" -> "$"; "EUR" -> "€"; "BOB" -> "Bs"; "GTQ" -> "Q"
        else -> moneda.uppercase()
    }
    return "$simbolo ${String.format(Locale.US, "%,.2f", v)}"
}

/** "Lic. Paula Vega" → "Paula": primer nombre sin títulos profesionales. */
fun nombreDePila(completo: String?): String {
    val titulos = setOf("lic", "lic.", "licda", "licda.", "dr", "dr.", "dra", "dra.", "mg", "mg.", "mtro", "mtro.", "mtra", "mtra.", "psic", "psic.", "ps.", "tf", "tf.", "prof", "prof.")
    return completo.orEmpty().trim().split(Regex("\\s+")).firstOrNull { it.lowercase() !in titulos }.orEmpty()
}

fun calcularEdad(nacimiento: String?): Int? = nacimiento?.let {
    runCatching { java.time.Period.between(LocalDate.parse(it.take(10)), LocalDate.now()).years }.getOrNull()
}

fun saludoSegunHora(): String = when (LocalTime.now().hour) {
    in 5..11 -> L("Buenos días", "Good morning")
    in 12..18 -> L("Buenas tardes", "Good afternoon")
    else -> L("Buenas noches", "Good evening")
}
