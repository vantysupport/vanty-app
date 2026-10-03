package xyz.vanty.aba.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

/**
 * Datos guardados en el teléfono para el widget y los avisos en segundo plano
 * (se leen sin abrir la app). No contiene nada clínico: nombres, racha y citas próximas.
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("vanty_app", Context.MODE_PRIVATE)

    /** `profiles.role` de la sesión (padre, especialista, secretaria, jefe…) para los avisos en segundo plano. */
    var rol: String
        get() = sp.getString("rol", "") ?: ""
        set(v) = sp.edit { putString("rol", v) }

    /** Nombre de pila de la persona (sin títulos), para que ARIA la llame por su nombre en los avisos. */
    var nombreUsuario: String
        get() = sp.getString("nombre_usuario", "") ?: ""
        set(v) = sp.edit { putString("nombre_usuario", v) }

    var usuarioId: String?
        get() = sp.getString("usuario_id", null)
        set(v) = sp.edit { putString("usuario_id", v) }

    /** Resumen de agenda del día para el widget del equipo (JSON de [AgendaHoy]). */
    var agendaHoy: AgendaHoy?
        get() = leer("agenda_hoy", AgendaHoy.serializer())
        set(v) = if (v == null) sp.edit { remove("agenda_hoy") } else escribir("agenda_hoy", AgendaHoy.serializer(), v)

    /** Próxima cita de la familia para el widget: "YYYY-MM-DD|HH:MM". */
    var proximaCita: String?
        get() = sp.getString("proxima_cita", null)
        set(v) = sp.edit { putString("proxima_cita", v) }

    /** Próximas citas de la familia para el widget de agenda: líneas "YYYY-MM-DD|HH:MM|Nombre|estado". */
    var citasFamilia: List<String>
        get() = (sp.getString("citas_familia", "") ?: "").lines().filter { it.isNotBlank() }
        set(v) = sp.edit { putString("citas_familia", v.joinToString("\n")) }

    /** Avisos/mensajes sin leer, para el widget "Tu resumen". */
    var sinLeer: Int
        get() = sp.getInt("sin_leer", 0)
        set(v) = sp.edit { putInt("sin_leer", v) }

    /** Borrador de un formulario largo (JSON de respuestas), para seguir donde se quedó. */
    fun borrador(clave: String): String? = sp.getString("borrador_$clave", null)
    fun guardarBorrador(clave: String, json: String?) = sp.edit { if (json == null) remove("borrador_$clave") else putString("borrador_$clave", json) }

    /** Alertas del Inicio descartadas que no vienen de agente_alertas ("tipo:child_id"), como el localStorage de la web. */
    val alertasDescartadas: Set<String> get() = sp.getStringSet("alertas_descartadas", emptySet()).orEmpty()
    fun descartarAlerta(clave: String) = sp.edit { putStringSet("alertas_descartadas", alertasDescartadas + clave) }

    var resumenDiario: Boolean
        get() = sp.getBoolean("resumen_diario", true)
        set(v) = sp.edit { putBoolean("resumen_diario", v) }

    var hijoId: String?
        get() = sp.getString("hijo_id", null)
        set(v) = sp.edit { putString("hijo_id", v) }

    var nombreHijo: String
        get() = sp.getString("nombre_hijo", "") ?: ""
        set(v) = sp.edit { putString("nombre_hijo", v) }

    var hijos: List<Hijo>
        get() = leer("hijos", ListSerializer(Hijo.serializer())) ?: emptyList()
        set(v) = escribir("hijos", ListSerializer(Hijo.serializer()), v)

    var racha: Racha?
        get() = leer("racha", Racha.serializer())
        set(v) = if (v == null) sp.edit { remove("racha") } else escribir("racha", Racha.serializer(), v)

    /** Fecha (YYYY-MM-DD) en que se guardó la racha: si cambió el día, "hoy" ya no vale. */
    var rachaFecha: String
        get() = sp.getString("racha_fecha", "") ?: ""
        set(v) = sp.edit { putString("racha_fecha", v) }

    /**
     * Racha guardada, ajustada al día de hoy sin conexión (misma regla que lib/racha.ts):
     * si ayer se practicó sigue viva pero "hoy" vuelve a pendiente; si pasó más de un día, se apagó.
     */
    fun rachaVigente(): Racha {
        val r = racha ?: return Racha()
        val hoy = LocalDate.now()
        val guardada = runCatching { LocalDate.parse(rachaFecha) }.getOrNull() ?: return Racha()
        val dias = when (guardada) {
            hoy -> r.dias
            hoy.minusDays(1) -> if (r.hoy) r.dias else 0
            else -> 0
        }
        val hechos = r.semana.filter { it.hecho }.map { it.fecha }.toSet()
        val semana = (6 downTo 0).map { hoy.minusDays(it.toLong()).toString() }.map { DiaRacha(it, it in hechos) }
        return Racha(dias, guardada == hoy && r.hoy, semana)
    }

    /** "es" | "en". Sin elegir todavía: el idioma del teléfono (inglés → en; cualquier otro → es). */
    var idioma: String
        get() = sp.getString("idioma", null) ?: if (java.util.Locale.getDefault().language == "en") "en" else "es"
        set(v) = sp.edit { putString("idioma", v) }

    /** "system" | "light" | "dark": el tema elegido en Mi perfil (la web) también aplica a la app. */
    var tema: String
        get() = sp.getString("tema", null) ?: "system"
        set(v) = sp.edit { putString("tema", v) }

    var recordatorioActivo: Boolean
        get() = sp.getBoolean("recordatorio_activo", true)
        set(v) = sp.edit { putBoolean("recordatorio_activo", v) }

    /** Hora del recordatorio diario de práctica (0–23). */
    var recordatorioHora: Int
        get() = sp.getInt("recordatorio_hora", 19)
        set(v) = sp.edit { putInt("recordatorio_hora", v) }

    var avisosCitas: Boolean
        get() = sp.getBoolean("avisos_citas", true)
        set(v) = sp.edit { putBoolean("avisos_citas", v) }

    var permisoPedido: Boolean
        get() = sp.getBoolean("permiso_pedido", false)
        set(v) = sp.edit { putBoolean("permiso_pedido", v) }

    /** Última versión de la app que se abrió (para mostrar las "Novedades" una vez después de actualizar). */
    var versionVista: Int
        get() = sp.getInt("version_vista", 0)
        set(v) = sp.edit { putInt("version_vista", v) }

    /** "versión|fecha" en que se tocó "Más tarde" en el aviso de nueva versión (se vuelve a avisar al día siguiente). */
    var versionPospuesta: String
        get() = sp.getString("version_pospuesta", "") ?: ""
        set(v) = sp.edit { putString("version_pospuesta", v) }

    /** Avisos ya enviados (clave → fecha), para no repetirlos. */
    fun yaAvisado(clave: String) = sp.getBoolean("aviso_$clave", false)
    fun marcarAvisado(clave: String) = sp.edit { putBoolean("aviso_$clave", true) }

    fun limpiarSesion() = sp.edit {
        listOf("hijo_id", "nombre_hijo", "hijos", "racha", "racha_fecha", "rol", "usuario_id", "agenda_hoy", "nombre_usuario", "proxima_cita", "sin_leer").forEach { remove(it) }
        sp.all.keys.filter { it.startsWith("aviso_") }.forEach { remove(it) }
    }

    private fun <T> leer(clave: String, s: kotlinx.serialization.KSerializer<T>): T? =
        sp.getString(clave, null)?.let { runCatching { Backend.json.decodeFromString(s, it) }.getOrNull() }

    private fun <T> escribir(clave: String, s: kotlinx.serialization.KSerializer<T>, v: T) =
        sp.edit { putString(clave, Backend.json.encodeToString(s, v)) }
}
