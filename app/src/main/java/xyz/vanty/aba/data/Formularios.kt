package xyz.vanty.aba.data

import android.content.Context
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.vanty.aba.util.EN

@Serializable
data class Pregunta(
    val id: String,
    val label: String = "",
    val type: String = "text",
    val placeholder: String? = null,
    val options: List<String>? = null,
    val min: Double? = null,
    val max: Double? = null,
    val required: Boolean? = null,
    val helpText: String? = null,
    /** Solo para preguntas tipo "tabla" (filas que se agregan): columnas de cada fila. */
    val columns: List<ColumnaEv>? = null,
    val addLabel: String? = null,
)

@Serializable
data class SeccionForm(val title: String = "", val description: String? = null, val questions: List<Pregunta> = emptyList())

@Serializable
data class DefFormulario(val id: String, val title: String = "", val description: String? = null, val sections: List<SeccionForm> = emptyList())

@Serializable
data class FormularioPadre(
    val id: String,
    @SerialName("form_type") val tipo: String,
    @SerialName("form_title") val titulo: String? = null,
    @SerialName("message_to_parent") val mensaje: String? = null,
    val deadline: String? = null,
    val status: String? = null,
    @SerialName("child_id") val childId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("completed_at") val completado: String? = null,
)

/**
 * Formularios que el centro envía a la familia. Las preguntas salen de los mismos catálogos que la web
 * (carpeta app/admin/data), exportados a assets/formularios_{es,en}.json.
 */
object Formularios {
    private val cache = mutableMapOf<String, Map<String, DefFormulario>>()

    fun catalogo(ctx: Context): Map<String, DefFormulario> {
        val idioma = if (EN) "en" else "es"
        return cache.getOrPut(idioma) {
            runCatching {
                val txt = ctx.assets.open("formularios_$idioma.json").bufferedReader().use { it.readText() }
                Backend.json.decodeFromString(MapSerializer(String.serializer(), DefFormulario.serializer()), txt)
            }.getOrDefault(emptyMap())
        }
    }

    suspend fun deFamilia(parentId: String): List<FormularioPadre> =
        Backend.supabase.from("parent_forms").select(Columns.list("id", "form_type", "form_title", "message_to_parent", "deadline", "status", "child_id", "parent_id", "completed_at")) {
            filter { eq("parent_id", parentId); neq("form_type", "wellbeing") }
            order("created_at", Order.DESCENDING)
        }.decodeList()

    /** Envía las respuestas (como ParentFormsView) y pide el análisis para el equipo (si falla, no importa). */
    suspend fun enviar(f: FormularioPadre, respuestas: Map<String, JsonElement>, parentId: String): Boolean {
        val r = Backend.apiPatch("/api/admin/forms", buildJsonObject {
            put("id", f.id); put("status", "completed"); put("responses", JsonObject(respuestas))
            put("completed_at", java.time.Instant.now().toString())
        })
        if (!r.status.isSuccess()) return false
        runCatching {
            Backend.apiPost("/api/analyze-parent-form-submission", buildJsonObject {
                put("formId", f.id); put("formType", f.tipo); put("formTitle", f.titulo); put("responses", JsonObject(respuestas))
                put("childId", f.childId); put("parentId", f.parentId ?: parentId)
            })
        }
        return true
    }
}
