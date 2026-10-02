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
