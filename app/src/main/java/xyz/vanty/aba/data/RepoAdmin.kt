package xyz.vanty.aba.data

import io.ktor.client.call.body
import io.github.jan.supabase.postgrest.from
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class PerfilUsuario(
    @SerialName("full_name") val nombre: String? = null,
    val role: String? = null,
    val specialty: String? = null,
    @SerialName("is_active") val activo: Boolean? = null,
)

@Serializable
data class Usuario(
    val id: String,
    val email: String? = null,
    @SerialName("last_sign_in_at") val ultimoIngreso: String? = null,
    val profile: PerfilUsuario? = null,
    val principal: Boolean = false,
) {
    val nombre get() = profile?.nombre?.takeIf { it.isNotBlank() } ?: email ?: "—"
    val rol get() = profile?.role.orEmpty()
    val activo get() = profile?.activo != false
}

@Serializable
private data class ListaUsuarios(val data: List<Usuario> = emptyList(), val soyPrincipal: Boolean = false)

@Serializable
data class Invitacion(
    val id: String,
    val role: String? = null,
    val email: String? = null,
    val paciente: String? = null,
    val estado: String? = null,
    val link: String? = null,
    @SerialName("expires_at") val vence: String? = null,
)

@Serializable
private data class ListaInvitaciones(val data: List<Invitacion> = emptyList())

@Serializable
private data class RespInvitacion(val data: Invitacion? = null, val emailed: Boolean = false)

@Serializable
data class Terapia(
    val id: String,
    val nombre: String? = null,
    @Serializable(with = TextoFlexible::class) val descripcion: String? = null,
    val precio: Double? = null,
    val moneda: String? = null,
    val duracion: String? = null,
    val modalidad: String? = null,
    val activo: Boolean? = true,
)

@Serializable
private data class ListaTerapias(val terapias: List<Terapia> = emptyList())

@Serializable
data class ItemPedido(@SerialName("product_nombre") val producto: String? = null, val cantidad: Int = 1, @SerialName("precio_unitario") val precio: Double = 0.0)

@Serializable
data class Pedido(
    val id: String,
    @SerialName("parent_name") val familia: String? = null,
    @SerialName("parent_phone") val telefono: String? = null,
    @SerialName("total_soles") val total: Double = 0.0,
    val estado: String? = null,
    val notas: String? = null,
    @SerialName("created_at") val fecha: String? = null,
    @SerialName("store_order_items") val items: List<ItemPedido> = emptyList(),
)

@Serializable
data class Producto(
    val id: String,
    val nombre: String? = null,
    @SerialName("precio_soles") val precio: Double = 0.0,
    val stock: Int? = null,
    val categoria: String? = null,
    val activo: Boolean? = true,
    @SerialName("imagen_url") val imagen: String? = null,
)

/** Gestión de la dirección: usuarios del centro e invitaciones (UserManagementView / InvitacionesPanel). */
object RepoAdmin {
    suspend fun usuarios(): Pair<List<Usuario>, Boolean> =
        Backend.getOrNull<ListaUsuarios>("/api/admin/users")?.let { it.data to it.soyPrincipal } ?: (emptyList<Usuario>() to false)

    /** Activa o desactiva una cuenta. Devuelve null si salió bien o el mensaje de error. */
    suspend fun alternarActivo(userId: String): String? = accion(buildJsonObject { put("action", "toggle_active"); put("userId", userId) })

    suspend fun enviarCambioClave(email: String): String? =
        accion(buildJsonObject { put("action", "send_reset_email"); put("email", email); put("locale", Backend.idioma) })

    private suspend fun accion(body: JsonObject): String? {
        val r = Backend.apiPost("/api/admin/users", body)
        if (r.status.isSuccess()) return null
        val j = runCatching { Backend.json.parseToJsonElement(r.bodyAsText()) as JsonObject }.getOrNull()
        val code = (j?.get("code") as? JsonPrimitive)?.content
        return when (code) {
            "seat_limit" -> "seat_limit"
            "solo_admin_principal" -> "solo_admin_principal"
            else -> (j?.get("error") as? JsonPrimitive)?.content ?: "error"
        }
    }

    suspend fun invitaciones(): List<Invitacion> = Backend.getOrNull<ListaInvitaciones>("/api/admin/invitaciones")?.data.orEmpty()

    /** Crea una invitación (y la envía por correo si hay email). Devuelve la invitación con su enlace. */
    suspend fun invitar(rol: String, email: String?, childId: String?, dias: Int): Pair<Invitacion?, Boolean> {
        val r = Backend.apiPost("/api/admin/invitaciones", buildJsonObject {
            put("role", rol); put("email", email?.trim()?.ifBlank { null }); put("child_id", childId); put("days", dias); put("locale", Backend.idioma)
        })
        if (!r.status.isSuccess()) return null to false
        val j = r.body<RespInvitacion>()
        return j.data to j.emailed
    }

    suspend fun revocar(id: String): Boolean = runCatching {
        val t = Backend.token()
        Backend.http.delete(xyz.vanty.aba.BuildConfig.API_BASE_URL + "/api/admin/invitaciones?id=" + java.net.URLEncoder.encode(id, "UTF-8")) {
            if (t != null) bearerAuth(t)
        }.status.isSuccess()
    }.getOrDefault(false)

    // ── Recursos adicionales ────────────────────────────────────────────────
    private val sb get() = Backend.supabase

    suspend fun recursosCentro(): List<Recurso> =
        Backend.getOrNull<ListaRecursos>("/api/admin/resources")?.data.orEmpty()

    /** Comparte un recurso con todas las familias o con la de un paciente (notifica a la familia). */
    suspend fun crearRecurso(titulo: String, descripcion: String?, url: String, tipo: String, childId: String?): Boolean =
        Backend.apiPost("/api/admin/resources", buildJsonObject {
            put("title", titulo.trim()); put("description", descripcion?.trim()?.ifBlank { null }); put("url", url.trim())
            put("resource_type", tipo); put("is_global", childId == null); put("child_id", childId)
        }).status.isSuccess()

    suspend fun terapias(): List<Terapia> = Backend.getOrNull<ListaTerapias>("/api/terapias-catalogo?all=1")?.terapias.orEmpty()

    suspend fun pedidos(): List<Pedido> =
        sb.from("store_orders").select(io.github.jan.supabase.postgrest.query.Columns.raw("id, parent_name, parent_phone, total_soles, estado, notas, created_at, store_order_items(product_nombre, cantidad, precio_unitario)")) {
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            limit(100)
        }.decodeList()

    suspend fun estadoPedido(id: String, estado: String) {
        sb.from("store_orders").update(buildJsonObject { put("estado", estado); put("updated_at", java.time.Instant.now().toString()) }) { filter { eq("id", id) } }
    }

    suspend fun productos(): List<Producto> =
        sb.from("store_products").select(io.github.jan.supabase.postgrest.query.Columns.list("id", "nombre", "precio_soles", "stock", "categoria", "activo", "imagen_url")) {
            order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        }.decodeList()

    suspend fun activarProducto(id: String, activo: Boolean) {
        sb.from("store_products").update(buildJsonObject { put("activo", activo) }) { filter { eq("id", id) } }
    }
}

@Serializable
private data class ListaRecursos(val data: List<Recurso> = emptyList())
