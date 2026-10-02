package xyz.vanty.aba.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.vanty.aba.BuildConfig

/** Versión vigente de los Términos (igual que lib/terminos.ts de la web). */
const val TERMINOS_VERSION = "2026-10"

@Serializable
data class Centro(
    val id: String,
    val name: String? = null,
    @SerialName("logo_url") val logo: String? = null,
    val telefono: String? = null,
    val email: String? = null,
    val status: String? = null,
    @SerialName("ia_estado") val iaEstado: String? = null,
)

@Serializable
data class EstadoIA(
    val rol: String? = null,
    val centro: String? = null,
    val propio: String? = null,
    val puedeDecidirCentro: Boolean = false,
) {
    /** null si puede usar IA; "centro" o "propio" si falta ese permiso (lib/ia-consentimiento.ts). */
    val motivo: String? get() = when {
        centro != "aceptada" -> "centro"
        rol == "padre" && propio != "aceptada" -> "propio"
        else -> null
    }
}

/**
 * Lo que comparten todos los roles: funciones habilitadas por el plan del centro (/api/control),
 * datos del centro, términos, consentimiento de IA y archivos privados.
 */
object Comun {
    private val sb get() = Backend.supabase

    /**
     * Funciones habilitadas para el centro: la web combina el plan (Starter/Professional/Clinic/Fundador),
     * los ajustes globales de /control y los apagados por centro. Clave ausente = habilitada.
     */
    suspend fun funciones(): Map<String, Boolean> {
        val j = Backend.getOrNull<JsonObject>("/api/control") ?: return emptyMap()
        val f = j["features"] as? JsonObject ?: return emptyMap()
        return f.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.booleanOrNull?.let { k to it } }.toMap()
    }

    suspend fun centro(id: String?): Centro? = id?.let {
        runCatching {
            sb.from("centros").select(Columns.list("id", "name", "logo_url", "telefono", "email", "status", "ia_estado")) {
                filter { eq("id", it) }
            }.decodeSingleOrNull<Centro>()
        }.getOrNull()
    }

    /** Guarda nombre y/o aceptación de términos y comprueba que la fila se actualizó (NombrePerfilGuard). */
    suspend fun completarPerfil(id: String, nombre: String?, aceptaTerminos: Boolean): Boolean {
        val ahora = java.time.Instant.now().toString()
        val filas = sb.from("profiles").update(buildJsonObject {
            if (nombre != null) { put("full_name", nombre.trim().take(120)); put("nombre_confirmado", true) }
            if (aceptaTerminos) { put("terminos_version", TERMINOS_VERSION); put("terminos_aceptados_at", ahora) }
            put("updated_at", ahora)
        }) {
            select(Columns.list("id"))
            filter { eq("id", id) }
        }.decodeList<Perfil>()
        return filas.isNotEmpty()
    }

    suspend fun estadoIA(): EstadoIA? = Backend.getOrNull("/api/ia/consentimiento")

    suspend fun decidirIA(ambito: String, aceptar: Boolean): Boolean = runCatching {
        Backend.apiPost("/api/ia/consentimiento", buildJsonObject {
            put("ambito", ambito); put("decision", if (aceptar) "aceptada" else "rechazada")
        }).status.isSuccess()
    }.getOrDefault(false)

    /**
     * Enlace temporal (firmado) para abrir un archivo guardado. Los privados ("r2:..." o buckets privados de
     * Supabase) pasan por /api/files, que revisa permisos y redirige a una URL firmada de corta duración.
     */
    suspend fun abrirArchivo(guardado: String?): String? {
        if (guardado.isNullOrBlank()) return null
        val privados = listOf("patient-documents", "chat-files", "chat-media", "knowledge-base")
        val r2 = Regex("^r2:([^/]+)/(.+)$").find(guardado)
        val st = Regex("/storage/v1/object/(?:public|sign|authenticated)/([^/]+)/([^?#]+)").find(guardado)
        val ruta = when {
            r2 != null -> "/api/files?r=1&b=${enc(r2.groupValues[1])}&p=${enc(r2.groupValues[2])}"
            st != null && st.groupValues[1] in privados -> "/api/files?b=${enc(st.groupValues[1])}&p=${enc(java.net.URLDecoder.decode(st.groupValues[2], "UTF-8"))}"
            else -> return guardado
        }
        val t = Backend.token() ?: return null
        val r = Backend.httpSinRedirecciones.get(BuildConfig.API_BASE_URL + ruta) { bearerAuth(t) }
        return r.headers[HttpHeaders.Location] ?: if (r.status.isSuccess()) BuildConfig.API_BASE_URL + ruta else null
    }

    /** Avisos sin leer de la campana (cada mensaje nuevo del chat también crea uno). */
    suspend fun sinLeer(userId: String): Int = runCatching {
        sb.from("notifications").select(Columns.list("id")) {
            count(io.github.jan.supabase.postgrest.query.Count.EXACT)
            filter { eq("user_id", userId); eq("is_read", false) }
            limit(1)
        }.countOrNull()?.toInt() ?: 0
    }.getOrDefault(0)

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

/** Respuestas especiales de la API (sección 4 de APP-MOVIL.md). */
sealed class ErrorApi(msg: String) : Exception(msg) {
    class CentroInactivo : ErrorApi("centro_inactive")
    class SinIA(val motivo: String) : ErrorApi("ia_no_autorizada")
    class Limite(val texto: String?) : ErrorApi("429")
    class Otro(val codigo: Int, val texto: String?) : ErrorApi("http_$codigo")
}

/** Interpreta un error de la API: centro inactivo, IA sin consentimiento o límite. */
suspend fun io.ktor.client.statement.HttpResponse.errorApi(): ErrorApi {
    val cuerpo = runCatching { Backend.json.parseToJsonElement(bodyAsText()) as? JsonObject }.getOrNull()
    val error = (cuerpo?.get("error") as? JsonPrimitive)?.content
    val texto = (cuerpo?.get("text") as? JsonPrimitive)?.content ?: error
    return when {
        status.value == 403 && error == "centro_inactive" -> ErrorApi.CentroInactivo()
        status.value == 403 && error == "ia_no_autorizada" -> ErrorApi.SinIA((cuerpo?.get("motivo") as? JsonPrimitive)?.content ?: "centro")
        status.value == 429 -> ErrorApi.Limite(texto)
        else -> ErrorApi.Otro(status.value, texto)
    }
}
