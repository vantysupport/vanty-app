package xyz.vanty.aba.notif

import android.content.Context
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import xyz.vanty.aba.data.Backend
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Rol
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Avisos que se envían desde vanty.xyz/control → Notificaciones. La web los guarda en la campana
 * (notificaciones del equipo / notifications de las familias); la app los revisa al abrirse y cada hora
 * y los muestra como notificación de ARIA en el celular (una sola vez cada uno).
 */
object Campanas {
    @Serializable private data class AvisoEquipo(val id: String, val titulo: String? = null, val mensaje: String? = null, val metadata: JsonObject? = null)
    @Serializable private data class AvisoFamilia(val id: String, val title: String? = null, val message: String? = null, val metadata: JsonObject? = null)

    private fun pose(nombre: String?) = when (nombre) {
        "celebra" -> Avisos.Pose.CELEBRA
        "feliz" -> Avisos.Pose.FELIZ
        "guino" -> Avisos.Pose.GUINO
        "pensando" -> Avisos.Pose.PENSANDO
        "laptop" -> Avisos.Pose.LAPTOP
        "corre" -> Avisos.Pose.CORRE
        else -> Avisos.Pose.SALUDO
    }

    suspend fun revisar(ctx: Context) {
        val p = Prefs(ctx)
        val yo = p.usuarioId ?: return
        if (!Avisos.permitidas(ctx)) return
        val desde = Instant.now().minus(3, ChronoUnit.DAYS).toString()
        val familia = Rol.de(p.rol) == Rol.Familia
        val avisos: List<Triple<String, String, Triple<String, String?, String?>>> = runCatching {
            if (familia) Backend.supabase.from("notifications").select(Columns.list("id", "title", "message", "metadata")) {
                filter { eq("user_id", yo); eq("type", "aviso_plataforma"); gte("created_at", desde) }
                order("created_at", Order.ASCENDING); limit(5)
            }.decodeList<AvisoFamilia>().map { Triple(it.id, it.title.orEmpty(), Triple(it.message.orEmpty(), it.metadata?.get("pose")?.jsonPrimitive?.contentOrNull, it.metadata?.get("campana_id")?.jsonPrimitive?.contentOrNull)) }
            else Backend.supabase.from("notificaciones").select(Columns.list("id", "titulo", "mensaje", "metadata")) {
                filter { eq("user_id", yo); eq("tipo", "aviso_plataforma"); gte("created_at", desde) }
                order("created_at", Order.ASCENDING); limit(5)
            }.decodeList<AvisoEquipo>().map { Triple(it.id, it.titulo.orEmpty(), Triple(it.mensaje.orEmpty(), it.metadata?.get("pose")?.jsonPrimitive?.contentOrNull, it.metadata?.get("campana_id")?.jsonPrimitive?.contentOrNull)) }
        }.getOrDefault(emptyList())
        for ((id, titulo, resto) in avisos) {
            if (titulo.isBlank() || p.yaAvisado("campana_$id")) continue
            // Si ya llegó al instante por Firebase (misma campaña), no se repite
            val campana = resto.third
            if (campana != null && p.yaAvisado("campanaid_$campana")) { p.marcarAvisado("campana_$id"); continue }
            campana?.let { p.marcarAvisado("campanaid_$it") }
            Avisos.mostrar(ctx, ID_CAMPANA + (id.hashCode() and 0xFFF), Avisos.CANAL_LOGROS, pose(resto.second), titulo, resto.first,
                if (familia) "inicio" else "hoy")
            p.marcarAvisado("campana_$id")
        }
    }

    private const val ID_CAMPANA = 5000
}
