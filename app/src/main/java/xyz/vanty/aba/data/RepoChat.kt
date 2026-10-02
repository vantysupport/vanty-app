package xyz.vanty.aba.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Un mensaje en cualquier chat de la app (ARIA, centro o equipo), ya normalizado para la interfaz. */
data class Mensaje(
    val id: String,
    val texto: String,
    val mio: Boolean,
    val autor: String? = null,
    val fecha: String? = null,
    val archivo: String? = null,
    val nombreArchivo: String? = null,
    val leido: Boolean = false,
    val esAria: Boolean = false,
)

@Serializable
data class FilaAria(val id: String? = null, val rol: String? = null, val mensaje: String? = null, @SerialName("created_at") val fecha: String? = null)

@Serializable
private data class ListaAria(val data: List<FilaAria> = emptyList())

@Serializable
data class FilaCentro(
    val id: String,
    val content: String? = null,
    @SerialName("sender_id") val autorId: String? = null,
    @SerialName("sender_role") val autorRol: String? = null,
    @SerialName("sender_name") val autorNombre: String? = null,
    @SerialName("read_by") val leidoPor: List<String>? = null,
    @SerialName("message_type") val tipo: String? = null,
    @SerialName("file_url") val archivo: String? = null,
    @SerialName("file_name") val nombreArchivo: String? = null,
    @SerialName("created_at") val fecha: String? = null,
    @SerialName("child_id") val childId: String? = null,
    val children: NombreRef? = null,
)

@Serializable
private data class ListaCentro(val data: List<FilaCentro> = emptyList())

@Serializable
data class FilaEquipo(
    val id: String,
    @SerialName("sender_id") val de: String? = null,
    @SerialName("recipient_id") val para: String? = null,
    val content: String? = null,
    @SerialName("created_at") val fecha: String? = null,
    @SerialName("read_at") val leido: String? = null,
    @SerialName("message_type") val tipo: String? = null,
    @SerialName("file_url") val archivo: String? = null,
    @SerialName("file_name") val nombreArchivo: String? = null,
)

@Serializable
private data class ListaEquipo(val data: List<FilaEquipo> = emptyList())

@Serializable
data class ResumenContacto(val last: UltimoMsg? = null, val unread: Int = 0)

@Serializable
data class UltimoMsg(val content: String? = null, @SerialName("created_at") val fecha: String? = null)

@Serializable
private data class ListaResumen(val data: Map<String, ResumenContacto> = emptyMap())

@Serializable
data class Colega(
    val id: String,
    @SerialName("full_name") val nombre: String? = null,
    val specialty: String? = null,
    val role: String? = null,
    @SerialName("avatar_url") val avatar: String? = null,
)

/** Conversación de una familia con el centro (resumen para el equipo). */
@Serializable
data class NinoRef(val id: String, val name: String? = null)

data class HiloFamilia(val childId: String, val paciente: String, val ultimo: String, val fecha: String?, val sinLeer: Int)

/** Los tres chats de la web: ARIA (IA), familia ↔ centro y chat interno del equipo. Mismas rutas que la web. */
object RepoChat {
    private val sb get() = Backend.supabase
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // ── ARIA ────────────────────────────────────────────────────────────────
    suspend fun historialAria(childId: String, parentId: String): List<Mensaje> =
        Backend.getOrNull<ListaAria>("/api/parent-chat?child_id=${enc(childId)}&parent_user_id=${enc(parentId)}")?.data.orEmpty()
            .mapIndexed { i, f -> Mensaje(f.id ?: "h$i", f.mensaje.orEmpty(), mio = f.rol == "user", fecha = f.fecha, esAria = f.rol != "user") }

    /** Pregunta a ARIA. Familias: /api/parent-chat. Equipo: /api/admin-chat (sobre un paciente). Lanza [ErrorApi]. */
    suspend fun preguntarAria(pregunta: String, childId: String, childName: String?, equipo: Boolean): String {
        val r = if (equipo) Backend.apiPost("/api/admin-chat", buildJsonObject { put("question", pregunta); put("childId", childId) })
        else Backend.apiPost("/api/parent-chat", buildJsonObject { put("question", pregunta); put("childId", childId); put("childName", childName) })
        if (!r.status.isSuccess()) throw r.errorApi()
        val j = r.body<JsonObject>()
        return ((j["text"] ?: j["respuesta"]) as? JsonPrimitive)?.content.orEmpty()
    }

    // ── Familia ↔ centro ────────────────────────────────────────────────────
    suspend fun mensajesCentro(childId: String, yo: String): List<Mensaje> =
        Backend.getOrNull<ListaCentro>("/api/chat-familias?child_id=${enc(childId)}&user_id=${enc(yo)}&limit=80")?.data.orEmpty().map { f ->
            Mensaje(
                f.id, f.content.orEmpty(), mio = f.autorId == yo, autor = f.autorNombre, fecha = f.fecha,
                archivo = f.archivo, nombreArchivo = f.nombreArchivo, leido = (f.leidoPor?.size ?: 0) > 1,
            )
        }

    suspend fun enviarCentro(childId: String, yo: String, texto: String) {
        val r = Backend.apiPost("/api/chat-familias", buildJsonObject {
            put("child_id", childId); put("sender_id", yo); put("content", texto); put("message_type", "text")
        })
        if (!r.status.isSuccess()) throw r.errorApi()
    }

    suspend fun marcarLeidoCentro(childId: String, yo: String) = runCatching {
        Backend.apiPatch("/api/chat-familias", buildJsonObject { put("child_id", childId); put("user_id", yo) })
    }

    /** Para el equipo: conversaciones con familias, una por paciente, con mensajes sin leer. */
    /** Como la web: conversaciones con mensajes primero (no leídos arriba) y luego todas las familias activas. */
    suspend fun hilosFamilias(yo: String): List<HiloFamilia> {
        val conMensajes = Backend.getOrNull<ListaCentro>("/api/chat-familias?resumen=1")?.data.orEmpty()
            .filter { it.childId != null }
            .groupBy { it.childId!! }
            .map { (id, filas) ->
                val ultimo = filas.first()
                HiloFamilia(
                    id, ultimo.children?.name ?: "—", vistaPrevia(ultimo), ultimo.fecha,
                    filas.count { it.autorRol == "padre" && yo !in it.leidoPor.orEmpty() },
                )
            }
        val ids = conMensajes.map { it.childId }.toSet()
        val resto = runCatching {
            sb.from("children").select(Columns.list("id", "name")) {
                filter { eq("is_active", true) }
                order("name", Order.ASCENDING)
            }.decodeList<NinoRef>()
        }.getOrDefault(emptyList())
            .filter { it.id !in ids }
            .map { HiloFamilia(it.id, it.name ?: "—", "", null, 0) }
        return conMensajes.sortedWith(compareByDescending<HiloFamilia> { it.sinLeer }.thenByDescending { it.fecha.orEmpty() }) + resto
    }

    private fun vistaPrevia(f: FilaCentro): String = when {
        f.tipo == "image" -> "📷 Imagen"
        f.tipo == "audio" || f.content.orEmpty().startsWith("🎤 [Audio] http") -> "🎤 Audio"
        f.tipo == "document" || Regex("^📎 \\[.+?\\] https?://").containsMatchIn(f.content.orEmpty()) -> "📎 Documento"
        else -> f.content.orEmpty()
    }

    // ── Chat del equipo (texto cifrado en el servidor: siempre por /api/chat-equipo) ─────
    suspend fun colegas(yo: String): List<Colega> =
        sb.from("profiles").select(Columns.list("id", "full_name", "specialty", "role", "avatar_url")) {
            filter { isIn("role", listOf("especialista", "terapeuta", "admin", "jefe", "secretaria")); neq("id", yo) }
            order("full_name", Order.ASCENDING)
        }.decodeList()

    suspend fun resumenEquipo(): Map<String, ResumenContacto> =
        Backend.getOrNull<ListaResumen>("/api/chat-equipo?resumen=1")?.data.orEmpty()

    suspend fun mensajesEquipo(con: String, yo: String): List<Mensaje> =
        Backend.getOrNull<ListaEquipo>("/api/chat-equipo?con=${enc(con)}")?.data.orEmpty().map { f ->
            Mensaje(f.id, f.content.orEmpty(), mio = f.de == yo, fecha = f.fecha, archivo = f.archivo, nombreArchivo = f.nombreArchivo, leido = f.leido != null)
        }

    suspend fun enviarEquipo(para: String, texto: String) {
        val r = Backend.apiPost("/api/chat-equipo", buildJsonObject { put("recipient_id", para); put("content", texto); put("message_type", "text") })
        if (!r.status.isSuccess()) throw r.errorApi()
    }
}
