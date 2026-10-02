package xyz.vanty.aba.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Los textos que escribe el equipo o la IA a veces llegan como lista u objeto en vez de texto:
// este serializador los acepta igual y los convierte en texto legible.
object TextoFlexible : KSerializer<String> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor
    override fun deserialize(decoder: Decoder): String =
        texto((decoder as JsonDecoder).decodeJsonElement()) ?: ""
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    fun texto(el: JsonElement?): String? = when (el) {
        null, JsonNull -> null
        is JsonPrimitive -> el.content.takeIf { it.isNotBlank() }
        is JsonArray -> el.mapNotNull { texto(it) }.takeIf { it.isNotEmpty() }?.joinToString("\n") { "• $it" }
        is JsonObject -> el.values.mapNotNull { texto(it) }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

@Serializable
data class Perfil(
    val id: String,
    val email: String? = null,
    @SerialName("full_name") val nombre: String? = null,
    val role: String? = null,
    @SerialName("centro_id") val centroId: String? = null,
    @SerialName("nombre_confirmado") val nombreConfirmado: Boolean? = null,
    @SerialName("terminos_version") val terminosVersion: String? = null,
    @SerialName("ia_consentimiento") val iaConsentimiento: String? = null,
    val phone: String? = null,
)

@Serializable
data class Hijo(
    val id: String,
    val name: String? = null,
    @SerialName("birth_date") val nacimiento: String? = null,
    val diagnosis: String? = null,
) {
    val nombre get() = name?.trim().orEmpty().ifEmpty { "—" }
    val primerNombre get() = nombre.substringBefore(' ')
}

@Serializable
data class Cita(
    val id: String,
    @SerialName("appointment_date") val fecha: String,
    @SerialName("appointment_time") val hora: String? = null,
    val status: String? = null,
    @SerialName("service_type") val servicio: String? = null,
    val modalidad: String? = null,
    val metadata: kotlinx.serialization.json.JsonObject? = null,
) {
    /** La familia ya pidió reprogramar y el centro todavía no responde. */
    val solicitudPendiente: Boolean get() =
        ((metadata?.get("reprogramacion") as? kotlinx.serialization.json.JsonObject)?.get("estado") as? kotlinx.serialization.json.JsonPrimitive)?.content == "solicitada"
}

@Serializable
data class DiaRacha(val fecha: String, val hecho: Boolean = false)

@Serializable
data class Racha(val dias: Int = 0, val hoy: Boolean = false, val semana: List<DiaRacha> = emptyList())

@Serializable
data class Stats(
    val totalSesiones: Int = 0,
    val totalGoals: Int = 0,
    val goalsAchieved: Int = 0,
    val masteryRate: Int = 0,
    val hoursTotal: Double = 0.0,
    val level: String? = null,
)

@Serializable
data class Objetivo(
    val id: String,
    @Serializable(with = TextoFlexible::class) val nombre: String? = null,
    @Serializable(with = TextoFlexible::class) val descripcion: String? = null,
    val estado: String? = null,
    @SerialName("numero_set") val set: Int? = null,
)

@Serializable
data class Programa(
    val id: String,
    @Serializable(with = TextoFlexible::class) val titulo: String? = null,
    @Serializable(with = TextoFlexible::class) val descripcion: String? = null,
    @Serializable(with = TextoFlexible::class) val area: String? = null,
    @SerialName("fase_actual") val fase: String? = null,
    @SerialName("instrucciones_casa") @Serializable(with = TextoFlexible::class) val instrucciones: String? = null,
    @Serializable(with = TextoFlexible::class) val materiales: String? = null,
    @Serializable(with = TextoFlexible::class) val reforzadores: String? = null,
    val estado: String? = null,
    @SerialName("objetivos_cp") val objetivos: List<Objetivo> = emptyList(),
) {
    val dominado get() = estado == "dominado" || fase == "dominado"
    val archivado get() = estado == "archivado"
}

/** Textos del programa escritos por el equipo (los que se traducen al inglés). */
fun Programa.textos(): List<String?> =
    listOf(titulo, descripcion, area, instrucciones, materiales, reforzadores) + objetivos.flatMap { listOf(it.nombre, it.descripcion) }

fun Programa.traducido(t: Map<String, String>): Programa {
    fun tr(s: String?) = s?.let { t[it] ?: it }
    return copy(
        titulo = tr(titulo), descripcion = tr(descripcion), area = tr(area), instrucciones = tr(instrucciones),
        materiales = tr(materiales), reforzadores = tr(reforzadores),
        objetivos = objetivos.map { it.copy(nombre = tr(it.nombre), descripcion = tr(it.descripcion)) },
    )
}

@Serializable
data class ProgramasResp(val data: List<Programa> = emptyList())

@Serializable
data class PracticaCasa(
    @SerialName("programa_id") val programaId: String,
    @SerialName("child_id") val childId: String,
    val fecha: String,
)

@Serializable
data class Actividad(
    @Serializable(with = TextoFlexible::class) val titulo: String? = null,
    @Serializable(with = TextoFlexible::class) val descripcion: String? = null,
    @SerialName("duracion_minutos") @Serializable(with = TextoFlexible::class) val minutos: String? = null,
    val dificultad: String? = null,
    @Serializable(with = TextoFlexible::class) val area: String? = null,
    @SerialName("materiales_necesarios") @Serializable(with = TextoFlexible::class) val materiales: String? = null,
    @SerialName("por_que_importa") @Serializable(with = TextoFlexible::class) val porQue: String? = null,
    val completada: Boolean = false,
)

@Serializable
data class Plan(
    val id: String? = null,
    @SerialName("mensaje_motivacional") @Serializable(with = TextoFlexible::class) val mensaje: String? = null,
    val actividades: List<Actividad> = emptyList(),
)

@Serializable
data class PlanResp(val plan: Plan? = null)
