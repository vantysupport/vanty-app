package xyz.vanty.aba.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class Recurso(
    val id: String,
    @Serializable(with = TextoFlexible::class) val title: String? = null,
    @Serializable(with = TextoFlexible::class) val description: String? = null,
    @SerialName("resource_type") val tipo: String? = null,
    val url: String? = null,
    @SerialName("is_global") val global: Boolean? = null,
    @SerialName("created_at") val fecha: String? = null,
)

@Serializable
data class Documento(
    val id: String,
    @SerialName("file_name") val nombre: String? = null,
    @SerialName("file_url") val url: String? = null,
    @SerialName("file_type") val tipo: String? = null,
    @SerialName("uploader_name") val subidoPor: String? = null,
    @SerialName("created_at") val fecha: String? = null,
)

/** Lo que las familias abren desde "Más": recursos, documentos, y acciones sobre sus citas. */
object RepoFamilia {
    private val sb get() = Backend.supabase

    /** Biblioteca del centro: recursos globales, para la familia o para alguno de sus hijos (ResourcesView). */
    suspend fun recursos(parentId: String, hijos: List<String>): List<Recurso> {
        return sb.from("parent_resources").select(Columns.list("id", "title", "description", "resource_type", "url", "is_global", "created_at")) {
            filter {
                or {
                    eq("is_global", true)
                    eq("parent_id", parentId)
                    hijos.forEach { eq("child_id", it) }
                }
            }
            order("created_at", Order.DESCENDING)
        }.decodeList()
    }

    suspend fun documentos(childId: String): List<Documento> =
        sb.from("patient_documents").select(Columns.list("id", "file_name", "file_url", "file_type", "uploader_name", "created_at")) {
            filter { eq("child_id", childId) }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    /**
     * Pedir cambio o avisar que no podrá asistir: es una SOLICITUD al centro (la cita no se borra).
     * Devuelve null si salió bien o el código de error de /api/padre/citas.
     */
    suspend fun solicitarCita(citaId: String, cancelar: Boolean, motivo: String?, fecha: String?, hora: String?): String? {
        val r = Backend.apiPost("/api/padre/citas", buildJsonObject {
            put("id", citaId)
            put("accion", if (cancelar) "cancelar" else "reprogramar")
            put("motivo", motivo)
            if (!cancelar) { put("fecha", fecha); put("hora", hora) }
        })
        if (r.status.isSuccess()) return null
        return runCatching { ((Backend.json.parseToJsonElement(r.bodyAsText()) as JsonObject)["error"] as JsonPrimitive).content }.getOrDefault("error")
    }

    /** Sala de videollamada activa de una cita virtual (VideoCallModal). */
    suspend fun salaVideo(citaId: String): String? {
        val j = Backend.getOrNull<JsonObject>("/api/video-call?appointment_id=${java.net.URLEncoder.encode(citaId, "UTF-8")}")
        return ((j?.get("session") as? JsonObject)?.get("roomUrl") as? JsonPrimitive)?.content
    }
}

/** Resumen de ARIA sobre el niño (predicciones_ia), como "¿Cómo va…?" en el Inicio de la web. */
data class ResumenAria(val texto: String, val confianza: Int, val fortalezas: List<String>)

/** Mensaje que el equipo dejó para la familia (parent_messages). */
data class MensajeEquipo(val id: String, val titulo: String?, val cuerpo: String, val fecha: String?)

object RepoInicio {
    private val sb get() = Backend.supabase

    suspend fun resumenAria(childId: String): ResumenAria? {
        val fila = sb.from("predicciones_ia").select { filter { eq("child_id", childId) }; limit(1) }
            .decodeList<JsonObject>().firstOrNull() ?: return null
        fun txt(k: String) = TextoFlexible.texto(fila[k])
        // Igual que la web: la predicción a 30 días o, si no hay, el primer párrafo del análisis (sin títulos en negrita)
        val texto = txt("prediccion_30d") ?: txt("analisis_ia")?.split("\n\n")
            ?.firstOrNull { b -> b.isNotBlank() && !Regex("^\\*\\*[^*]+\\*\\*$").matches(b.trim()) }
            ?.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")?.trim()
        if (texto.isNullOrBlank()) return null
        val confianza = (fila["confianza"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0
        val fortalezas = (fila["areas_fortaleza"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { TextoFlexible.texto(it) }.orEmpty().take(3)
        return ResumenAria(texto, confianza, fortalezas)
    }

    suspend fun mensajesEquipo(childId: String): List<MensajeEquipo> =
        sb.from("parent_messages").select {
            filter { eq("child_id", childId) }
            order("created_at", Order.DESCENDING)
            limit(5)
        }.decodeList<JsonObject>().mapNotNull { m ->
            fun txt(vararg k: String) = k.firstNotNullOfOrNull { TextoFlexible.texto(m[it]) }
            val cuerpo = txt("body", "message", "content", "ai_message") ?: return@mapNotNull null
            MensajeEquipo(txt("id") ?: cuerpo.hashCode().toString(), txt("title", "subject", "source_title"), cuerpo, txt("created_at"))
        }
}
