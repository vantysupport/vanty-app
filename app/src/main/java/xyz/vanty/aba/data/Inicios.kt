package xyz.vanty.aba.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import xyz.vanty.aba.util.hoyIso
import java.time.LocalDate

/** Alerta clínica tal como la muestra el Inicio de la web (agente_alertas + pacientes sin sesión). */
data class AlertaClinica(val id: String?, val tipo: String, val childId: String?, val paciente: String, val mensaje: String, val prioridad: Int) {
    val esLogro get() = tipo.startsWith("logro_") || tipo == "criterio_alcanzado"
    val esSinSesion get() = tipo == "sin_sesion" || tipo.startsWith("sin_sesion_")
}

data class ProgramaActivo(val id: String, val titulo: String, val paciente: String, val ultimoPct: Int?, val criterio: Int)

data class InicioAdmin(
    val totalPacientes: Int = 0,
    val sesionesHoy: Int = 0,
    val realizadasHoy: Int = 0,
    val sinSesion: Int = 0,
    val programasAba: Int = 0,
    val semana: List<Pair<LocalDate, Int>> = emptyList(),
    val programasActivos: List<ProgramaActivo> = emptyList(),
    val alertas: List<AlertaClinica> = emptyList(),
    val proximas: List<CitaEquipo> = emptyList(),
    val alertasUrgentes: Int = 0,
)

@Serializable
private data class ProgFila(
    val id: String, val titulo: String? = null, @SerialName("child_id") val childId: String? = null,
    @SerialName("criterio_dominio_pct") val criterio: Double? = null,
    @SerialName("sesiones_datos_aba") val sesiones: List<SesFila> = emptyList(),
)

@Serializable
private data class SesFila(@SerialName("porcentaje_exito") val pct: Double? = null, val fecha: String? = null)

@Serializable
private data class NinosResp(val data: List<Paciente> = emptyList())

@Serializable
private data class SoloFecha(@SerialName("appointment_date") val fecha: String)

data class InicioEspecialista(
    val pacientes: Int = 0, val citas7: Int = 0, val evaluaciones: Int = 0, val enRevision: Int = 0, val aprobadas: Int = 0, val rechazadas: Int = 0,
    val ultimaSesion: String? = null, val sinSesion: Int = 0, val semana: List<Pair<LocalDate, Int>> = emptyList(),
    val citasHoy: List<CitaEquipo> = emptyList(), val recientes: List<Envio> = emptyList(), val pacientesRecientes: List<Paciente> = emptyList(),
)

data class InicioSecretaria(
    val hoy: Int = 0, val pendientes: Int = 0, val canceladas: Int = 0, val completadas: Int = 0, val pacientes: Int = 0,
    val semana: List<Pair<LocalDate, Int>> = emptyList(), val citasHoy: List<CitaEquipo> = emptyList(),
    val proximas: List<CitaEquipo> = emptyList(), val recientes: List<CitaEquipo> = emptyList(),
)

/** Los "Inicio" de cada rol, con exactamente las mismas consultas y cálculos que la web. */
object Inicios {
    /** Solo para la pantalla de demostración (debug): datos de ejemplo en lugar de la red. */
    var demoAdmin: InicioAdmin? = null
    var demoEspecialista: InicioEspecialista? = null
    var demoSecretaria: InicioSecretaria? = null
    private val sb get() = Backend.supabase
    private val TERMINADOS = setOf("cancelled", "cancelada", "completed", "completada", "done", "realizada")
    private val citaCols = Columns.raw("id, appointment_date, appointment_time, status, service_type, modalidad, child_id, specialist_id, children(name)")

    private fun prioridad(p: kotlinx.serialization.json.JsonElement?): Int {
        val prim = p as? JsonPrimitive ?: return 2
        prim.intOrNull?.let { return it }
        return when (prim.content.lowercase()) { "alta", "high", "urgent" -> 1; "media", "medium" -> 2; "baja", "low", "info" -> 3; else -> 2 }
    }

    private fun txt(o: JsonObject, k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    /** DashboardHome.tsx */
    suspend fun admin(descartadas: Set<String>): InicioAdmin = demoAdmin ?: coroutineScope {
        val hoy = hoyIso()
        val dias = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
        val m = async { Backend.getOrNull<JsonObject>("/api/dashboard/metricas?periodo=7d") }
        val proximas = async {
            runCatching {
                sb.from("appointments").select(citaCols) {
                    filter { gte("appointment_date", hoy) }
                    order("appointment_date", Order.ASCENDING); order("appointment_time", Order.ASCENDING); limit(20)
                }.decodeList<CitaEquipo>().filter { it.status !in TERMINADOS }.take(6)
            }.getOrDefault(emptyList())
        }
        val hoyApts = async { runCatching { sb.from("appointments").select(Columns.list("appointment_date")) { filter { eq("appointment_date", hoy); neq("status", "cancelled") } }.decodeList<SoloFecha>().size }.getOrNull() }
        val semana = async {
            runCatching {
                sb.from("appointments").select(Columns.list("appointment_date")) {
                    filter { gte("appointment_date", dias.first().toString()); lte("appointment_date", dias.last().toString()); neq("status", "cancelled") }
                }.decodeList<SoloFecha>()
            }.getOrDefault(emptyList())
        }
        val ninos = async { Backend.getOrNull<NinosResp>("/api/admin/children")?.data.orEmpty() }
        val progs = async {
            runCatching {
                sb.from("programas_aba").select(Columns.raw("id, titulo, child_id, criterio_dominio_pct, sesiones_datos_aba(porcentaje_exito, fecha)")) {
                    filter { eq("estado", "activo") }; order("updated_at", Order.DESCENDING)
                }.decodeList<ProgFila>()
            }.getOrDefault(emptyList())
        }
        val conteoProgs = async {
            runCatching {
                sb.from("programas_aba").select(Columns.list("id")) { count(Count.EXACT); filter { eq("estado", "activo") }; limit(1) }.countOrNull()?.toInt()
            }.getOrNull()
        }

        val met = m.await()
        val nombres = ninos.await().associate { it.id to it.nombre }
        val porDia = semana.await().groupingBy { it.fecha.take(10) }.eachCount()
        val sinSesionLista = (met?.get("pacientesSinSesion") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val pacientesTotal = ((met?.get("pacientes") as? JsonObject)?.get("total") as? JsonPrimitive)?.intOrNull ?: nombres.size
        val hoyMet = (((met?.get("hoy") as? JsonObject)?.get("sesiones")) as? JsonObject)
        val alertasMet = met?.get("alertas") as? JsonObject

        // Alertas: agente_alertas (sin_sesion agrupadas por niño) + pacientes sin sesión que no estén ya
        val recientes = (alertasMet?.get("recientes") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { "${txt(it, "tipo").orEmpty()}:${txt(it, "child_id").orEmpty()}" !in descartadas }
        val (sinSes, otras) = recientes.partition { val t = txt(it, "tipo").orEmpty(); (t == "sin_sesion" || t.startsWith("sin_sesion_")) && txt(it, "child_id") != null }
        fun aAlerta(o: JsonObject, mensaje: String? = null) = AlertaClinica(
            txt(o, "id"), txt(o, "tipo").orEmpty(), txt(o, "child_id"),
            ((o["children"] as? JsonObject)?.let { txt(it, "name") }) ?: nombres[txt(o, "child_id")] ?: "Paciente",
            mensaje ?: txt(o, "descripcion") ?: txt(o, "mensaje").orEmpty(), prioridad(o["prioridad"]),
        )
        val agrupadas = sinSes.groupBy { txt(it, "child_id")!! }.values.map { g ->
            val rep = g.minBy { prioridad(it["prioridad"]) }
            val base = txt(rep, "descripcion") ?: txt(rep, "mensaje").orEmpty()
            aAlerta(rep, if (g.size > 1) "${g.size} programas sin sesiones recientes. $base" else base)
        }
        val api = otras.map { aAlerta(it) } + agrupadas
        val ids = api.mapNotNull { it.childId }.toSet()
        val extra = sinSesionLista.filter { txt(it, "id") !in ids && "sin_sesion:${txt(it, "id")}" !in descartadas }
            .map { AlertaClinica(null, "sin_sesion", txt(it, "id"), txt(it, "name") ?: "Paciente", "Sin sesión en los últimos 30 días.", 2) }
        val alertas = (api + extra).sortedWith(compareBy<AlertaClinica> { if (it.esLogro) 1 else 0 }.thenBy { it.prioridad })

        InicioAdmin(
            totalPacientes = pacientesTotal,
            sesionesHoy = hoyApts.await() ?: (hoyMet?.get("total") as? JsonPrimitive)?.intOrNull ?: 0,
            realizadasHoy = (hoyMet?.get("realizadas") as? JsonPrimitive)?.intOrNull ?: 0,
            sinSesion = sinSesionLista.size,
            programasAba = conteoProgs.await() ?: progs.await().size,
            semana = dias.map { it to (porDia[it.toString()] ?: 0) },
            programasActivos = progs.await().map { p ->
                val ult = p.sesiones.sortedByDescending { it.fecha.orEmpty() }.firstOrNull()?.pct
                ProgramaActivo(p.id, p.titulo ?: "—", nombres[p.childId] ?: "Paciente", ult?.toInt(), (p.criterio ?: 90.0).toInt())
            },
            alertas = alertas,
            proximas = proximas.await(),
            alertasUrgentes = (alertasMet?.get("urgentes") as? JsonPrimitive)?.intOrNull ?: 0,
        )
    }

    /** Descartar una alerta: si viene de agente_alertas se marca resuelta en la base (como la web). */
    suspend fun descartar(a: AlertaClinica) {
        a.id?.let { id -> runCatching { RepoEquipo.resolverAlerta(id) } }
    }

    /** EspecialistaHome.tsx */
    suspend fun especialista(yo: String): InicioEspecialista = demoEspecialista ?: coroutineScope {
        val hoy = LocalDate.now()
        val dias = (6 downTo 0).map { hoy.minusDays(it.toLong()) }
        val envios = async { runCatching { sb.from("specialist_submissions").select(Columns.list("id", "status")) { filter { eq("specialist_id", yo) } }.decodeList<Envio>() }.getOrDefault(emptyList()) }
        val citasHoy = async {
            runCatching {
                sb.from("appointments").select(citaCols) { filter { eq("appointment_date", hoy.toString()); neq("status", "cancelled") }; order("appointment_time", Order.ASCENDING) }.decodeList<CitaEquipo>()
            }.getOrDefault(emptyList())
        }
        val nPac = async { runCatching { sb.from("children").select(Columns.list("id")) { count(Count.EXACT); filter { eq("is_active", true) }; limit(1) }.countOrNull()?.toInt() ?: 0 }.getOrDefault(0) }
        val sem = async {
            runCatching {
                sb.from("appointments").select(Columns.list("appointment_date")) { filter { neq("status", "cancelled"); gte("appointment_date", dias.first().toString()); lte("appointment_date", hoy.toString()) } }.decodeList<SoloFecha>()
            }.getOrDefault(emptyList())
        }
        val ultima = async {
            runCatching {
                sb.from("appointments").select(Columns.list("appointment_date")) { filter { neq("status", "cancelled"); lte("appointment_date", hoy.toString()) }; order("appointment_date", Order.DESCENDING); limit(1) }.decodeList<SoloFecha>().firstOrNull()?.fecha
            }.getOrNull()
        }
        val act30 = async {
            runCatching {
                sb.from("appointments").select(Columns.list("child_id")) { filter { neq("status", "cancelled"); gte("appointment_date", hoy.minusDays(30).toString()); lte("appointment_date", hoy.toString()) } }
                    .decodeList<CitaEquipo2>().mapNotNull { it.childId }.toSet().size
            }.getOrDefault(0)
        }
        val recientes = async {
            runCatching {
                sb.from("specialist_submissions").select(Columns.raw("id, titulo, status, created_at, children(name)")) { filter { eq("specialist_id", yo) }; order("created_at", Order.DESCENDING); limit(5) }.decodeList<Envio>()
            }.getOrDefault(emptyList())
        }
        val pacientes = async {
            runCatching { sb.from("children").select(Columns.list("id", "name", "birth_date")) { filter { eq("is_active", true) }; order("created_at", Order.DESCENDING); limit(8) }.decodeList<Paciente>() }.getOrDefault(emptyList())
        }
        val subs = envios.await()
        val porDia = sem.await().groupingBy { it.fecha.take(10) }.eachCount()
        val total = nPac.await()
        InicioEspecialista(
            pacientes = total, citas7 = sem.await().size,
            evaluaciones = subs.count { it.status in setOf("pending_approval", "approved", "rejected") },
            enRevision = subs.count { it.status == "pending_approval" }, aprobadas = subs.count { it.status == "approved" }, rechazadas = subs.count { it.status == "rejected" },
            ultimaSesion = ultima.await(), sinSesion = (total - act30.await()).coerceAtLeast(0),
            semana = dias.map { it to (porDia[it.toString()] ?: 0) }, citasHoy = citasHoy.await(), recientes = recientes.await(), pacientesRecientes = pacientes.await(),
        )
    }

    /** SecretariaHome.tsx */
    suspend fun secretaria(): InicioSecretaria = demoSecretaria ?: coroutineScope {
        val hoy = LocalDate.now()
        val lunes = hoy.with(java.time.DayOfWeek.MONDAY)
        val dias = (0..6).map { lunes.plusDays(it.toLong()) }
        val todas = async {
            runCatching {
                sb.from("appointments").select(citaCols) { filter { gte("appointment_date", hoy.minusDays(30).toString()) }; order("appointment_date", Order.ASCENDING); order("appointment_time", Order.ASCENDING); limit(400) }.decodeList<CitaEquipo>()
            }.getOrDefault(emptyList())
        }
        val nPac = async { runCatching { sb.from("children").select(Columns.list("id")) { count(Count.EXACT); filter { eq("is_active", true) }; limit(1) }.countOrNull()?.toInt() ?: 0 }.getOrDefault(0) }
        val apts = todas.await()
        val activas = apts.filter { it.status != "cancelled" }
        val h = hoy.toString()
        InicioSecretaria(
            hoy = activas.count { it.fecha.take(10) == h },
            pendientes = apts.count { it.status == "pending" && it.fecha.take(10) >= h },
            canceladas = apts.count { it.status == "cancelled" && it.fecha.take(10) <= h },
            completadas = apts.count { it.status in setOf("completed", "realizada") },
            pacientes = nPac.await(),
            semana = dias.map { d -> d to activas.count { it.fecha.take(10) == d.toString() } },
            citasHoy = apts.filter { it.fecha.take(10) == h },
            proximas = activas.filter { it.fecha.take(10) > h }.take(6),
            recientes = apts.filter { it.fecha.take(10) < h }.reversed().take(6),
        )
    }
}

@Serializable
private data class CitaEquipo2(@SerialName("child_id") val childId: String? = null)
