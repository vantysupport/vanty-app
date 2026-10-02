package xyz.vanty.aba.data

import android.content.Context
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import xyz.vanty.aba.util.EN

@Serializable
data class ColumnaEv(val id: String, val label: String = "", val type: String? = null, val placeholder: String? = null, val options: List<String>? = null)

@Serializable
data class PreguntaEv(
    val id: String, val type: String = "text", val label: String = "", val placeholder: String? = null,
    val options: List<String>? = null, val required: Boolean? = null,
    val columns: List<ColumnaEv>? = null, val addLabel: String? = null, val minRows: Int? = null,
)

@Serializable
data class SeccionEv(val titulo: String = "", val descripcion: String? = null, val icono: String? = null, val preguntas: List<PreguntaEv> = emptyList())

@Serializable
data class SeccionesEv(val intake: List<SeccionEv> = emptyList(), val psico: List<SeccionEv> = emptyList(), val neuro: List<SeccionEv> = emptyList())

@Serializable
data class Evaluacion(
    val id: String,
    val estado: String? = null,
    val recomendacion: String? = null,
    @SerialName("mensaje_amigable_padre") @Serializable(with = TextoFlexible::class) val mensaje: String? = null,
    @SerialName("recomendacion_resumen") @Serializable(with = TextoFlexible::class) val resumen: String? = null,
    @SerialName("terapias_recomendadas") val terapiasRecomendadas: List<String>? = null,
)

@Serializable
private data class RespEvaluacion(val evaluacion: Evaluacion? = null)

/**
 * Evaluación inicial de la familia (EvaluacionInicialView): ficha inicial → análisis → recomendación →
 * anamnesis → elección de terapias. Mismas rutas /api/evaluacion-inicial de la web; las preguntas vienen
 * del mismo código, exportadas a assets/evaluacion_{es,en}.json.
 */
object EvaluacionInicial {
    private var cache: Pair<String, SeccionesEv>? = null

    fun secciones(ctx: Context): SeccionesEv {
        val idioma = if (EN) "en" else "es"
        cache?.takeIf { it.first == idioma }?.let { return it.second }
        val s = runCatching {
            Backend.json.decodeFromString(SeccionesEv.serializer(), ctx.assets.open("evaluacion_$idioma.json").bufferedReader().use { it.readText() })
        }.getOrDefault(SeccionesEv())
        cache = idioma to s
        return s
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    suspend fun obtener(childId: String, parentId: String): Evaluacion? =
        Backend.getOrNull<RespEvaluacion>("/api/evaluacion-inicial?child_id=${enc(childId)}&parent_id=${enc(parentId)}")?.evaluacion

    /** Envía la ficha inicial y pide el análisis (la IA sugiere el tipo de evaluación). */
    suspend fun enviarFicha(childId: String, parentId: String, respuestas: Map<String, JsonElement>): Boolean {
        val r = Backend.apiPost("/api/evaluacion-inicial", buildJsonObject {
            put("child_id", childId); put("parent_id", parentId); put("respuestas", JsonObject(respuestas))
        })
        if (!r.status.isSuccess()) return false
        val id = runCatching { r.body<RespEvaluacion>().evaluacion?.id }.getOrNull() ?: return false
        runCatching { Backend.apiPost("/api/evaluacion-inicial/analizar", buildJsonObject { put("id", id) }) }
        return true
    }

    suspend fun confirmar(evaluacionId: String, acepta: Boolean, motivo: String?): Boolean =
        Backend.apiPost("/api/evaluacion-inicial/confirmar", buildJsonObject {
            put("evaluacion_id", evaluacionId); put("acepta", acepta); if (!acepta) put("motivo_rechazo", motivo)
        }).status.isSuccess()

    suspend fun enviarAnamnesis(evaluacionId: String, respuestas: Map<String, JsonElement>): Boolean =
        Backend.apiPost("/api/evaluacion-inicial/anamnesis", buildJsonObject {
            put("evaluacion_id", evaluacionId); put("respuestas", JsonObject(respuestas))
        }).status.isSuccess()

    suspend fun recomendarTerapias(evaluacionId: String) = runCatching {
        Backend.apiPost("/api/evaluacion-inicial/recomendar-terapias", buildJsonObject { put("evaluacion_id", evaluacionId) })
    }

    suspend fun terapias(): List<Terapia> = Backend.getOrNull<TerapiasResp>("/api/terapias-catalogo")?.terapias.orEmpty()

    suspend fun seleccionar(evaluacionId: String, ids: List<String>, mensaje: String?): Boolean =
        Backend.apiPost("/api/evaluacion-inicial/seleccionar", buildJsonObject {
            put("evaluacion_id", evaluacionId)
            putJsonArray("terapia_ids") { ids.forEach { add(JsonPrimitive(it)) } }
            put("mensaje_al_especialista", mensaje?.ifBlank { null })
        }).status.isSuccess()
}

@Serializable
private data class TerapiasResp(val terapias: List<Terapia> = emptyList())

/** Convierte las secciones de la evaluación al formato de lección de los formularios. */
fun List<SeccionEv>.comoFormulario(id: String, titulo: String) = DefFormulario(
    id = id, title = titulo,
    sections = map { s ->
        SeccionForm(
            title = listOfNotNull(s.icono, s.titulo).joinToString(" "), description = s.descripcion,
            questions = s.preguntas.map { p ->
                Pregunta(
                    id = p.id, label = p.label, placeholder = p.placeholder, options = p.options, required = p.required,
                    type = when (p.type) { "checkbox" -> "multiselect"; "tabla_dinamica" -> "tabla"; else -> p.type },
                    columns = p.columns, addLabel = p.addLabel,
                )
            },
        )
    },
)
