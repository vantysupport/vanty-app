package xyz.vanty.aba.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Rol de la cuenta, con los mismos nombres que `profiles.role` de la web. */
enum class Rol { Familia, Especialista, Secretaria, Admin, Otro;
    companion object {
        fun de(role: String?) = when (role) {
            "padre" -> Familia
            "especialista", "terapeuta" -> Especialista
            "secretaria" -> Secretaria
            "jefe", "admin" -> Admin
            else -> Otro
        }
    }
}

@Serializable
data class NombreRef(val name: String? = null)

@Serializable
data class CitaEquipo(
    val id: String,
    @SerialName("appointment_date") val fecha: String,
    @SerialName("appointment_time") val hora: String? = null,
    val status: String? = null,
    @SerialName("service_type") val servicio: String? = null,
    val modalidad: String? = null,
    @SerialName("child_id") val childId: String? = null,
    @SerialName("specialist_id") val especialistaId: String? = null,
    val children: NombreRef? = null,
    @SerialName("is_group") val grupal: Boolean? = null,
    @SerialName("group_name") val grupo: String? = null,
    val notes: String? = null,
    @SerialName("video_link") val videoLink: String? = null,
    val metadata: kotlinx.serialization.json.JsonObject? = null,
    /** Lo agrega GET /api/admin/appointments. */
    val specialist: PerfilRef? = null,
) {
    val paciente get() = children?.name?.trim().orEmpty().ifEmpty { "—" }
    private val repro get() = metadata?.get("reprogramacion") as? kotlinx.serialization.json.JsonObject
    private fun rp(k: String) = (repro?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it != "null" }
    /** La familia pidió moverla (metadata.reprogramacion.estado = "solicitada"). */
    val reproSolicitada get() = rp("estado") == "solicitada"
    val reproFecha get() = rp("fecha")
    val reproHora get() = rp("hora")
    val reproMotivo get() = rp("motivo")
    val estado get() = EstadoCita.de(status)
}

/** Los estados de cita que usa la agenda web (CalendarView): pending · confirmed · completed · cancelled. */
enum class EstadoCita(val valor: String) {
    Pendiente("pending"), Confirmada("confirmed"), Realizada("completed"), Cancelada("cancelled");
    companion object {
        fun de(s: String?) = when (s?.lowercase()) {
            "confirmed", "confirmada" -> Confirmada
            "completed", "completada", "realizada" -> Realizada
            "cancelled", "cancelada", "ausente" -> Cancelada
            else -> Pendiente
        }
    }
}

@Serializable
data class Paciente(
    val id: String,
    val name: String? = null,
    @SerialName("birth_date") val nacimiento: String? = null,
    val diagnosis: String? = null,
) {
    val nombre get() = name?.trim().orEmpty().ifEmpty { "—" }
}

@Serializable
data class Pago(
    val id: String,
    val amount: Double = 0.0,
    @SerialName("amount_paid") val pagado: Double? = null,
    val status: String? = null,
    @SerialName("payment_method") val metodo: String? = null,
    val concept: String? = null,
    @SerialName("created_at") val creado: String? = null,
    @SerialName("paciente_externo") val externo: String? = null,
    val abonos: List<Abono>? = null,
    val children: NombreRef? = null,
    @SerialName("fecha_cobro") val fechaCobro: String? = null,
    @SerialName("child_id") val childId: String? = null,
    @SerialName("appointment_id") val citaId: String? = null,
    @SerialName("paid_at") val pagadoEl: String? = null,
    val notes: String? = null,
    val responsable: String? = null,
    @SerialName("especialista_id") val especialistaId: String? = null,
    /** Sesión de la agenda vinculada (appointments(appointment_date, appointment_time, status)). */
    val appointments: CitaRef? = null,
    /** especialista:especialista_id(full_name) */
    val especialista: PerfilRef? = null,
) {
    val paciente get() = children?.name ?: externo ?: "—"
    /** Lo cobrado de este registro (lib/pagos.ts cobradoDe). */
    val cobrado get() = when (status) { "paid" -> amount; "partial" -> minOf(amount, pagado ?: 0.0); else -> 0.0 }
    /** Fecha del cobro (la de la sesión o la del registro). */
    val fecha get() = (fechaCobro ?: creado).orEmpty()
    /** Lo que falta cobrar: en parcial, total − pagado; en pendiente, el total. */
    val saldo get() = if (status == "partial") (amount - (pagado ?: 0.0)).coerceAtLeast(0.0) else amount
}

@Serializable
data class Informe(
    val id: String,
    val titulo: String? = null,
    @SerialName("tipo_reporte") val tipo: String? = null,
    @SerialName("nombre_archivo") val archivo: String? = null,
    @SerialName("mime_type") val mime: String? = null,
    @SerialName("created_at") val fecha: String? = null,
    val children: NombreRef? = null,
)

@Serializable
data class ArchivoInforme(@SerialName("nombre_archivo") val nombre: String? = null, @SerialName("file_data") val datos: String? = null)

@Serializable
data class Tarifa(
    val id: String, val name: String? = null, val amount: Double = 0.0, @SerialName("is_active") val activa: Boolean? = true,
    val description: String? = null, @SerialName("duration_min") val duracion: Int? = null,
)

@Serializable
data class CitaRef(@SerialName("appointment_date") val fecha: String? = null, @SerialName("appointment_time") val hora: String? = null, val status: String? = null)

@Serializable
data class Abono(val monto: Double, val fecha: String, val metodo: String? = null)

@Serializable
data class Alerta(
    val id: String,
    val tipo: String? = null,
    @Serializable(with = TextoFlexible::class) val titulo: String? = null,
    @Serializable(with = TextoFlexible::class) val mensaje: String? = null,
    val prioridad: String? = null,
    @SerialName("created_at") val creada: String? = null,
    val children: NombreRef? = null,
)

@Serializable
data class Envio(
    val id: String,
    @Serializable(with = TextoFlexible::class) val titulo: String? = null,
    val tipo: String? = null,
    @Serializable(with = TextoFlexible::class) val observaciones: String? = null,
    @Serializable(with = TextoFlexible::class) val recomendaciones: String? = null,
    val status: String? = null,
    @SerialName("created_at") val creado: String? = null,
    val children: NombreRef? = null,
    val profiles: PerfilRef? = null,
)

@Serializable
data class PerfilRef(@SerialName("full_name") val nombre: String? = null, val specialty: String? = null)

// ── /api/dashboard/metricas ─────────────────────────────────────────────────
@Serializable
data class Metricas(
    val hoy: MetHoy = MetHoy(),
    val pacientes: MetPacientes = MetPacientes(),
    val alertas: MetAlertas = MetAlertas(),
    val tareas: MetTareas = MetTareas(),
    val financiero: MetFin = MetFin(),
)

@Serializable
data class MetHoy(val sesiones: MetSesiones = MetSesiones(), val tasaAsistencia: Int = 100)

@Serializable
data class MetSesiones(val total: Int = 0, val realizadas: Int = 0, val canceladas: Int = 0, val programadas: Int = 0)

@Serializable
data class MetPacientes(val total: Int = 0, val nuevosMes: Int = 0, val progresoPromedio: Double = 0.0)

@Serializable
data class MetAlertas(val total: Int = 0, val urgentes: Int = 0)

@Serializable
data class MetTareas(val completitudPct: Int = 0, val formPendientes: Int = 0)

@Serializable
data class MetFin(val ingresosMes: Double = 0.0, val facturasPendientes: Int = 0)

/** Resumen del día que se guarda en el teléfono para el widget "Agenda de hoy" y los avisos. */
@Serializable
data class AgendaHoy(val fecha: String, val total: Int = 0, val hechas: Int = 0, val citas: List<ItemAgenda> = emptyList())

@Serializable
data class ItemAgenda(val id: String, val hora: String, val paciente: String, val estado: String)

@Serializable
data class MonedaResp(val moneda: String = "PEN")
