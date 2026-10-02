package xyz.vanty.aba.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.vanty.aba.util.hoyIso
import java.time.Instant

/**
 * Consultas del equipo del centro (especialista, secretaria, admin). Mismas tablas y rutas que los
 * paneles web; RLS limita todo al centro de la persona, igual que en la web.
 */
object RepoEquipo {
    private val sb get() = Backend.supabase

    private val columnasCita = Columns.raw(
        "id, appointment_date, appointment_time, status, service_type, modalidad, child_id, specialist_id, children(name)",
    )

    suspend fun citasDelDia(fecha: String, especialistaId: String? = null): List<CitaEquipo> =
        sb.from("appointments").select(columnasCita) {
            filter {
                eq("appointment_date", fecha)
                if (especialistaId != null) eq("specialist_id", especialistaId)
            }
            order("appointment_time", Order.ASCENDING)
        }.decodeList()

    suspend fun citasRango(desde: String, hasta: String): List<CitaEquipo> =
        sb.from("appointments").select(columnasCita) {
            filter {
                gte("appointment_date", desde)
                lte("appointment_date", hasta)
            }
            order("appointment_date", Order.ASCENDING)
            order("appointment_time", Order.ASCENDING)
            limit(500)
        }.decodeList()

    /** Cambia el estado con la misma ruta que la agenda web (sincroniza calendarios y avisa a la familia). */
    suspend fun cambiarEstado(citaId: String, estado: EstadoCita): Boolean =
        Backend.apiPatch("/api/admin/appointments", buildJsonObject {
            put("id", citaId)
            put("status", estado.valor)
            put("locale", Backend.idioma)
        }).status.isSuccess()

    suspend fun pacientes(): List<Paciente> =
        sb.from("children").select(Columns.list("id", "name", "birth_date", "diagnosis")) {
            filter { eq("is_active", true) }
            order("name", Order.ASCENDING)
        }.decodeList()

    // ── Secretaría: cobros ──────────────────────────────────────────────────
    suspend fun deudas(): List<Pago> =
        sb.from("payments").select(Columns.raw("id, amount, amount_paid, status, payment_method, concept, created_at, paciente_externo, abonos, children(name)")) {
            filter { isIn("status", listOf("pending", "partial")) }
            order("created_at", Order.ASCENDING)
            limit(1000)
        }.decodeList()

    suspend fun cobradoHoy(): Double =
        sb.from("payments").select(Columns.raw("id, amount, amount_paid, status, abonos")) {
            filter { gte("updated_at", "${hoyIso()}T00:00:00") }
        }.decodeList<Pago>().sumOf { p -> p.abonos.orEmpty().filter { it.fecha.startsWith(hoyIso()) }.sumOf { it.monto } }

    /** Registra un abono como SecretariaPagos.registrarAbono de la web. Devuelve true si saldó la deuda. */
    suspend fun registrarAbono(p: Pago, monto: Double, metodo: String): Boolean {
        val sigueParcial = p.status == "partial"
        val pagadoAntes = if (sigueParcial) p.pagado ?: 0.0 else 0.0
        val completo = pagadoAntes + monto >= p.amount - 0.001
        val ahora = Instant.now().toString()
        val abonos = (if (sigueParcial) p.abonos.orEmpty() else emptyList()) + Abono(monto, ahora, metodo)
        sb.from("payments").update(buildJsonObject {
            put("amount_paid", if (completo) p.amount else Math.round((pagadoAntes + monto) * 100) / 100.0)
            put("abonos", Backend.json.encodeToJsonElement(ListSerializer(Abono.serializer()), abonos))
            put("status", if (completo) "paid" else "partial")
            if (completo) put("paid_at", ahora) else put("paid_at", kotlinx.serialization.json.JsonNull)
            put("payment_method", if (sigueParcial) p.metodo ?: metodo else metodo)
        }) { filter { eq("id", p.id) } }
        return completo
    }

    private val columnasPago = Columns.raw("id, amount, amount_paid, status, payment_method, concept, created_at, fecha_cobro, paciente_externo, child_id, abonos, children(name)")

    /** Cobros registrados en un rango (como SecretariaPagos / AdminReportesFinancieros). */
    suspend fun pagos(desde: String, hasta: String): List<Pago> =
        sb.from("payments").select(columnasPago) {
            filter { gte("created_at", desde); lte("created_at", "${hasta}T23:59:59") }
            order("created_at", Order.DESCENDING)
            limit(1500)
        }.decodeList()

    suspend fun tarifas(): List<Tarifa> =
        sb.from("service_rates").select(Columns.list("id", "name", "amount", "is_active")) {
            order("amount", Order.ASCENDING)
        }.decodeList<Tarifa>().filter { it.activa != false }

    /** Informes generados (Word) de un paciente o, sin paciente, los últimos del centro. Sin el archivo (pesa). */
    suspend fun informes(childId: String?): List<Informe> =
        sb.from("reportes_generados").select(Columns.raw("id, titulo, tipo_reporte, nombre_archivo, mime_type, created_at, children(name)")) {
            if (childId != null) filter { eq("child_id", childId) }
            order("created_at", Order.DESCENDING)
            limit(if (childId != null) 30 else 60)
        }.decodeList()

    /** Descarga el informe (guardado en base64) a la caché y devuelve el archivo para abrirlo. */
    suspend fun archivoInforme(ctx: android.content.Context, id: String): java.io.File? {
        val fila = sb.from("reportes_generados").select(Columns.list("nombre_archivo", "file_data")) {
            filter { eq("id", id) }
        }.decodeSingleOrNull<ArchivoInforme>() ?: return null
        val datos = fila.datos?.substringAfter("base64,") ?: return null
        val dir = java.io.File(ctx.cacheDir, "informes").apply { mkdirs() }
        val f = java.io.File(dir, (fila.nombre ?: "informe.docx").replace(Regex("[\\/:*?\"<>|]"), "_"))
        f.writeBytes(android.util.Base64.decode(datos, android.util.Base64.DEFAULT))
        return f
    }

    suspend fun moneda(): String = Backend.getOrNull<MonedaResp>("/api/centro/moneda")?.moneda ?: "PEN"

    // ── Admin ───────────────────────────────────────────────────────────────
    suspend fun metricas(): Metricas? = Backend.getOrNull("/api/dashboard/metricas")

    suspend fun alertas(): List<Alerta> =
        sb.from("agente_alertas").select(Columns.raw("id, tipo, titulo, mensaje, prioridad, created_at, children(name)")) {
            filter { eq("resuelta", false) }
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeList()

    suspend fun resolverAlerta(id: String) {
        sb.from("agente_alertas").update(buildJsonObject { put("resuelta", true) }) { filter { eq("id", id) } }
    }

    /** Envíos del especialista (para su resumen). */
    suspend fun misEnvios(especialistaId: String): List<Envio> =
        sb.from("specialist_submissions").select(Columns.raw("id, titulo, tipo, status, created_at, children(name)")) {
            filter { eq("specialist_id", especialistaId) }
            order("created_at", Order.DESCENDING)
            limit(5)
        }.decodeList()
}
