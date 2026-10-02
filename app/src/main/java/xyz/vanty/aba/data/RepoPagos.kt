package xyz.vanty.aba.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.vanty.aba.BuildConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class NinoTutor(val id: String, val name: String? = null, val tutor: PerfilRef? = null) {
    val nombre get() = name?.trim().orEmpty().ifEmpty { "—" }
}

/** Formulario "Nuevo cobro" de la web (SecretariaPagos). */
data class NuevoCobro(
    val childId: String?, val externo: String?, val monto: Double, val adelanto: Double, val concepto: String,
    val metodo: String, val estado: String, val notas: String, val fecha: LocalDate, val responsable: String, val especialistaId: String?,
    /** Vincular con la agenda: null = no; "existente" con citaId; "nueva" con hora. */
    val agenda: String?, val citaId: String?, val hora: String,
    /** Fecha de la sesión existente elegida: el cobro toma esa fecha. */
    val citaFecha: LocalDate? = null,
)

/** Formulario "Paquete de sesiones": un cobro por día elegido, cada uno con su sesión en la agenda (opcional). */
data class NuevoPaquete(
    val childId: String?, val externo: String?, val montoSesion: Double, val concepto: String, val metodo: String, val estado: String,
    val responsable: String, val especialistaId: String?, val dias: List<LocalDate>, val agendar: Boolean, val hora: String, val horas: Map<LocalDate, String>,
)

/** Pagos de la web: SecretariaPagos (jefe, admin y secretaría), con el vínculo a la Agenda. */
object RepoPagos {
    private val sb get() = Backend.supabase
    val METODOS = listOf("efectivo", "yape", "plin", "transferencia", "tarjeta", "otro")
    val ESTADOS = listOf("paid", "pending", "partial", "cancelled", "refunded")
    private val cols = Columns.raw("*, children(name), appointments(appointment_date, appointment_time, status), especialista:especialista_id(full_name)")

    /** Período del panel: semana (7 días), mes (desde el 1) o año (desde el 1 de enero), como la web. */
    fun desde(periodo: String): Instant {
        val hoy = LocalDate.now()
        val d = when (periodo) { "semana" -> hoy.minusDays(7); "anio" -> hoy.withDayOfYear(1); else -> hoy.withDayOfMonth(1) }
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant()
    }

    suspend fun pagos(periodo: String): List<Pago> =
        sb.from("payments").select(cols) {
            filter { gte("created_at", desde(periodo).toString()) }
            order("created_at", Order.DESCENDING); limit(500)
        }.decodeList()

    /** Deudas: todo lo pendiente o parcial, sin importar el período. */
    suspend fun deudas(): List<Pago> =
        sb.from("payments").select(cols) {
            filter { isIn("status", listOf("pending", "partial")) }
            order("created_at", Order.ASCENDING); limit(1000)
        }.decodeList()

    suspend fun ninos(): List<NinoTutor> =
        sb.from("children").select(Columns.raw("id, name, tutor:profiles!fk_children_parent(full_name)")) {
            filter { eq("is_active", true) }; order("name", Order.ASCENDING)
        }.decodeList()

    suspend fun tarifas(): List<Tarifa> =
        sb.from("service_rates").select() { order("amount", Order.ASCENDING) }.decodeList()

    /** Sesiones del paciente de los últimos 90 días (para vincular el cobro con una ya agendada). */
    suspend fun citasPaciente(childId: String): List<CitaEquipo> =
        sb.from("appointments").select(Columns.list("id", "appointment_date", "appointment_time", "service_type", "status")) {
            filter { eq("child_id", childId); neq("status", "cancelled"); gte("appointment_date", LocalDate.now().minusDays(90).toString()) }
            order("appointment_date", Order.DESCENDING); order("appointment_time", Order.DESCENDING); limit(60)
        }.decodeList()

    @Serializable private data class Dia(val id: String, @SerialName("appointment_date") val fecha: String)
    @Serializable private data class Creadas(val data: List<Dia> = emptyList())

    /** Sesiones ya agendadas del paciente en esos días (el paquete se vincula a ellas en vez de duplicarlas). */
    suspend fun citasEnDias(childId: String, dias: List<String>): Map<String, String> =
        sb.from("appointments").select(Columns.list("id", "appointment_date")) {
            filter { eq("child_id", childId); neq("status", "cancelled"); isIn("appointment_date", dias) }
        }.decodeList<Dia>().groupBy { it.fecha }.mapValues { it.value.first().id }

    /** Agenda sesiones con la misma ruta que la Agenda (avisa a la familia y sincroniza Google / Outlook). */
    private suspend fun agendar(childId: String, dias: List<Pair<LocalDate, String>>, servicio: String, especialistaId: String?, yo: String?): Map<String, String> {
        val r = Backend.apiEnviar(HttpMethod.Post, "/api/admin/appointments?sincronizar=1", buildJsonArray {
            dias.forEach { (d, h) ->
                add(buildJsonObject {
                    put("child_id", childId); put("appointment_date", d.toString()); put("appointment_time", "$h:00")
                    put("service_type", servicio); put("is_group", false); put("status", "confirmed"); put("modalidad", "presencial")
                    put("created_by", yo); if (especialistaId != null) put("specialist_id", especialistaId)
                })
            }
        })
        if (!r.status.isSuccess()) error("agenda")
        return Backend.json.decodeFromString<Creadas>(r.bodyAsText()).data.associate { it.fecha.take(10) to it.id }
    }

    private fun mediodia(d: LocalDate) = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toString()
    private fun abonos(l: List<Abono>) = Backend.json.encodeToJsonElement(ListSerializer(Abono.serializer()), l)

    suspend fun crearCobro(n: NuevoCobro, tutor: String?, yo: String?) {
        var citaId: String? = null
        var fechaSesion = n.fecha
        if (n.agenda != null && n.childId != null) {
            if (n.agenda == "existente") citaId = n.citaId
            else citaId = agendar(n.childId, listOf(n.fecha to n.hora), n.concepto.trim(), n.especialistaId, yo)[n.fecha.toString()]
                ?: error("agenda")
        }
        if (n.agenda == "existente" && n.citaFecha != null) fechaSesion = n.citaFecha
        val fecha = mediodia(fechaSesion)
        val pagado = when (n.estado) { "paid" -> n.monto; "partial" -> n.adelanto; else -> 0.0 }
        sb.from("payments").insert(buildJsonObject {
            put("appointment_id", citaId)
            put("responsable", n.responsable.trim().ifBlank { if (n.externo != null) null else tutor })
            put("especialista_id", n.especialistaId)
            put("child_id", n.childId); put("paciente_externo", n.externo?.trim())
            put("amount", n.monto); put("concept", n.concepto.trim())
            put("payment_method", n.metodo); put("status", n.estado); put("notes", n.notas.ifBlank { null })
            put("paid_at", if (n.estado == "paid") fecha else null); put("fecha_cobro", fecha); put("amount_paid", pagado)
            put("abonos", abonos(if (pagado > 0) listOf(Abono(pagado, fecha, n.metodo)) else emptyList()))
            put("created_by", yo)
        })
    }

    suspend fun crearPaquete(n: NuevoPaquete, tutor: String?, yo: String?, notaPaquete: String) {
        val dias = n.dias.sorted()
        val sesion = mutableMapOf<String, String>()
        if (n.agendar && n.childId != null) {
            sesion += citasEnDias(n.childId, dias.map { it.toString() })
            val nuevas = dias.filter { it.toString() !in sesion }
            if (nuevas.isNotEmpty()) sesion += agendar(n.childId, nuevas.map { it to (n.horas[it] ?: n.hora) }, n.concepto.trim(), n.especialistaId, yo)
        }
        sb.from("payments").insert(buildJsonArray {
            dias.forEachIndexed { i, d ->
                add(buildJsonObject {
                    put("appointment_id", sesion[d.toString()])
                    put("responsable", n.responsable.trim().ifBlank { if (n.externo != null) null else tutor })
                    put("especialista_id", n.especialistaId)
                    put("child_id", n.childId); put("paciente_externo", n.externo?.trim())
                    put("amount", n.montoSesion); put("concept", "${n.concepto.trim()} (${i + 1}/${dias.size})")
                    put("payment_method", n.metodo); put("status", n.estado)
                    put("paid_at", if (n.estado == "paid") mediodia(d) else null); put("fecha_cobro", mediodia(d))
                    put("notes", notaPaquete); put("created_by", yo)
                })
            }
        })
    }

    /** Cambiar el estado desde la lista (como cambiarEstado de la web). "partial" se hace con un abono. */
    suspend fun cambiarEstado(p: Pago, k: String) {
        val ahora = Instant.now().toString()
        val previos = p.abonos.orEmpty()
        sb.from("payments").update(buildJsonObject {
            put("status", k)
            when (k) {
                "paid" -> {
                    put("paid_at", ahora); put("amount_paid", p.amount)
                    put("abonos", abonos(if (p.status == "partial" && p.saldo > 0) previos + Abono(p.saldo, ahora, p.metodo)
                        else previos.ifEmpty { listOf(Abono(p.amount, ahora, p.metodo)) }))
                }
                "pending" -> { put("paid_at", JsonNull); put("amount_paid", 0.0); put("abonos", JsonArray(emptyList())) }
                else -> put("paid_at", JsonNull)
            }
            put("fecha_cobro", p.fecha.ifBlank { null })
        }) { filter { eq("id", p.id) } }
    }

    suspend fun eliminar(ids: List<String>) { sb.from("payments").delete { filter { isIn("id", ids) } } }

    /** "Eliminar también la sesión agendada (agenda y calendarios)". Devuelve cuántas fallaron. */
    suspend fun eliminarSesiones(ids: List<String?>): Int =
        ids.filterNotNull().distinct().count { !runCatching { RepoAgenda.eliminar(it) }.getOrDefault(false) }

    // ── Tarifas ──
    suspend fun guardarTarifa(id: String?, nombre: String, descripcion: String, monto: Double, minutos: Int) {
        val datos = buildJsonObject {
            put("name", nombre.trim()); put("description", descripcion.trim().ifBlank { null }); put("amount", monto)
            put("duration_min", minutos); put("is_active", true)
        }
        if (id != null) sb.from("service_rates").update(datos) { filter { eq("id", id) } } else sb.from("service_rates").insert(datos)
    }

    suspend fun borrarTarifa(id: String) { sb.from("service_rates").delete { filter { eq("id", id) } } }

    // ── Recibos ──
    /** Descarga el recibo PDF (de un cobro o de un paquete) a la caché para abrirlo. */
    suspend fun recibo(ctx: android.content.Context, ids: List<String>): java.io.File? = runCatching {
        val t = Backend.token()
        val ruta = if (ids.size == 1) "/api/pagos/recibo-pdf?id=${ids[0]}&lang=${Backend.idioma}" else "/api/pagos/recibo-paquete?ids=${ids.joinToString(",")}&lang=${Backend.idioma}"
        val r: HttpResponse = Backend.http.get(BuildConfig.API_BASE_URL + ruta) { if (t != null) bearerAuth(t) }
        if (!r.status.isSuccess()) return null
        val dir = java.io.File(ctx.cacheDir, "informes").apply { mkdirs() }
        java.io.File(dir, "recibo_${ids[0].take(8)}.pdf").apply { writeBytes(r.body<ByteArray>()) }
    }.getOrNull()

    sealed interface Envio { data class Ok(val email: String) : Envio; data object SinCorreo : Envio; data object Error : Envio }

    /** Enviar el recibo por correo: vacío = correo de la familia registrada. */
    suspend fun enviarRecibo(ids: List<String>, email: String?): Envio = runCatching {
        val r = Backend.apiPost("/api/pagos/recibo-pdf", buildJsonObject {
            if (ids.size == 1) put("id", ids[0]) else put("ids", buildJsonArray { ids.forEach { add(it) } })
            if (!email.isNullOrBlank()) put("email", email.trim())
            put("lang", Backend.idioma)
        })
        val j = runCatching { Backend.json.parseToJsonElement(r.bodyAsText()) as kotlinx.serialization.json.JsonObject }.getOrNull()
        fun c(k: String) = (j?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content
        when {
            r.status.isSuccess() -> Envio.Ok(c("email") ?: email.orEmpty())
            c("error") == "sin_correo" -> Envio.SinCorreo
            else -> Envio.Error
        }
    }.getOrDefault(Envio.Error)

}
