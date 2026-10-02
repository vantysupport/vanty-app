package xyz.vanty.aba.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Serializable
data class Especialista(val id: String, @SerialName("full_name") val nombre: String? = null, val specialty: String? = null, val role: String? = null)

@Serializable
private data class CitasResp(val data: List<CitaEquipo> = emptyList())

/** Datos de una cita nueva, como el formulario "Nueva cita" de la agenda web (CalendarView). */
data class NuevaCita(
    val grupal: Boolean,
    val pacientes: List<String>,
    val nombreGrupo: String,
    val servicio: String,
    val especialistas: List<Especialista>,
    val fecha: LocalDate,
    val hora: String,
    val virtual: Boolean,
    val estado: String,
    /** 0 = no repetir, 7 = semanal, 14 = quincenal. */
    val cadaDias: Int,
    val semanas: Int,
    val notas: String,
)

/** La Agenda de la web: CalendarView (jefe, admin, secretaría) y MiAgenda (especialista). */
object RepoAgenda {
    private val sb get() = Backend.supabase

    /** Solo para la pantalla de demostración (debug). */
    var demo: List<CitaEquipo>? = null
    var demoEspecialistas: List<Especialista>? = null

    /** Servicios sugeridos en la web. */
    val SERVICIOS_ES = listOf("Terapia ABA", "Evaluación Inicial", "Seguimiento BRIEF-2", "Evaluación ADOS-2", "Evaluación Vineland-3",
        "Evaluación WISC-V", "Evaluación BASC-3", "Sesión Familiar", "Sesión de Orientación", "Visita Domiciliaria")
    val SERVICIOS_EN = listOf("ABA Therapy", "Initial Assessment", "BRIEF-2 Follow-up", "ADOS-2 Assessment", "Vineland-3 Assessment",
        "WISC-V Assessment", "BASC-3 Assessment", "Family Session", "Guidance Session", "Home Visit")

    /** Todas las citas del centro con el especialista asignado (GET /api/admin/appointments). */
    suspend fun todas(): List<CitaEquipo> {
        demo?.let { return it }
        val r = Backend.apiGet("/api/admin/appointments")
        if (!r.status.isSuccess()) error("HTTP ${r.status.value}")
        return Backend.json.decodeFromString<CitasResp>(r.bodyAsText()).data
    }

    /** MiAgenda: las citas que el especialista puede ver (RLS). */
    suspend fun delEspecialista(): List<CitaEquipo> = demo ?:
        sb.from("appointments").select(Columns.raw("*, children(name)")) {
            order("appointment_date", Order.ASCENDING); order("appointment_time", Order.ASCENDING)
        }.decodeList()

    suspend fun especialistas(): List<Especialista> = demoEspecialistas ?:
        sb.from("profiles").select(Columns.list("id", "full_name", "specialty", "role")) {
            filter { isIn("role", listOf("especialista", "terapeuta", "admin", "jefe")); eq("is_active", true) }
            order("full_name", Order.ASCENDING)
        }.decodeList()

    /**
     * Crea la(s) cita(s). Igual que la web: una por participante si es grupal, el primer especialista en `specialist_id`
     * y los demás en las notas. Las repeticiones van en el mismo envío y `sincronizar=1` lleva las citas a
     * Google / Outlook desde el servidor.
     */
    suspend fun crear(n: NuevaCita, yo: String?): Boolean {
        val primero = n.especialistas.firstOrNull()
        val extra = if (n.notas.isNotBlank()) " " + n.notas else ""
        val notas = if (n.especialistas.size > 1) "[Especialistas: ${n.especialistas.joinToString(", ") { it.nombre.orEmpty() }}]$extra" else n.notas
        val fechas = if (n.cadaDias == 0) listOf(n.fecha) else (0 until n.semanas.coerceIn(1, 52)).map { n.fecha.plusDays((it * n.cadaDias).toLong()) }
        val cuerpo = buildJsonArray {
            fechas.forEach { f ->
                n.pacientes.forEach { cid ->
                    add(buildJsonObject {
                        put("child_id", cid)
                        put("appointment_date", f.toString())
                        put("appointment_time", n.hora + ":00")
                        put("service_type", if (n.grupal) "${n.servicio} (Grupal: ${n.nombreGrupo.ifBlank { "Sin nombre" }})" else n.servicio)
                        put("is_group", n.grupal)
                        if (n.grupal) put("group_name", n.nombreGrupo)
                        put("notes", notas)
                        put("status", n.estado)
                        put("modalidad", if (n.virtual) "virtual" else "presencial")
                        put("created_by", yo)
                        put("specialist_id", primero?.id)
                    })
                }
            }
        }
        return Backend.apiEnviar(HttpMethod.Post, "/api/admin/appointments?sincronizar=1", cuerpo).status.isSuccess()
    }

    /** Editar fecha y hora (la web lo hace en línea con el lápiz). */
    suspend fun cambiarHorario(id: String, fecha: LocalDate, hora: String): Boolean =
        Backend.apiPatch("/api/admin/appointments", buildJsonObject {
            put("id", id); put("appointment_date", fecha.toString()); put("appointment_time", hora)
        }).status.isSuccess()

    suspend fun eliminar(id: String): Boolean =
        Backend.apiEnviar(HttpMethod.Delete, "/api/admin/appointments", buildJsonObject { put("id", id) }).status.isSuccess()

    /** Respuesta a la reprogramación que pidió la familia. */
    suspend fun responderReprogramacion(id: String, aprobar: Boolean): Boolean =
        Backend.apiPatch("/api/admin/appointments", buildJsonObject {
            put("id", id); put("reprogramacion", if (aprobar) "aprobar" else "rechazar"); put("locale", Backend.idioma)
        }).status.isSuccess()

    sealed interface Video {
        data class Sala(val url: String) : Video
        data object Limite : Video
        data object Error : Video
    }

    /** Iniciar videollamada (crea la sala y avisa al padre), como el botón de la agenda web. */
    suspend fun iniciarVideo(c: CitaEquipo): Video = runCatching {
        val r = Backend.apiPost("/api/video-call", buildJsonObject {
            put("appointment_id", c.id); put("child_id", c.childId); put("initiated_by", "admin"); put("locale", Backend.idioma)
        })
        val j = Backend.json.parseToJsonElement(r.bodyAsText()) as JsonObject
        val sala = (j["room_url"] as? JsonPrimitive)?.content?.takeIf { it != "null" }
        when {
            (j["limitReached"] as? JsonPrimitive)?.content == "true" -> Video.Limite
            sala != null -> Video.Sala(sala)
            else -> Video.Error
        }
    }.getOrDefault(Video.Error)

    /**
     * La web marca como realizadas las citas cuya sesión ya terminó (inicio + 45 min) al abrir la agenda.
     * Devuelve los ids marcados.
     */
    suspend fun completarVencidas(citas: List<CitaEquipo>): Set<String> {
        if (demo != null) return emptySet()
        val ahora = LocalDateTime.now()
        return citas.filter { c ->
            c.status != "cancelled" && c.status != "completed" && c.hora != null &&
                runCatching { LocalDateTime.of(LocalDate.parse(c.fecha.take(10)), LocalTime.parse(c.hora.take(5))).plusMinutes(45) }.getOrNull()?.isBefore(ahora) == true
        }.filter { runCatching { RepoEquipo.cambiarEstado(it.id, EstadoCita.Realizada) }.getOrDefault(false) }.map { it.id }.toSet()
    }
}
