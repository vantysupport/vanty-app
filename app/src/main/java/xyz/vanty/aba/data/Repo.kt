package xyz.vanty.aba.data

import io.github.jan.supabase.auth.providers.Azure
import io.github.jan.supabase.auth.providers.Google
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.vanty.aba.BuildConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import xyz.vanty.aba.util.hoyIso
import java.net.URLEncoder

/** Todas las lecturas y escrituras de la app. Mismas tablas y rutas que el portal de familias web. */
object Repo {
    private val sb get() = Backend.supabase

    // ── Sesión ──────────────────────────────────────────────────────────────
    suspend fun haySesion(): Boolean {
        sb.auth.awaitInitialization()
        return sb.auth.currentSessionOrNull() != null
    }

    suspend fun entrar(email: String, clave: String) {
        sb.auth.signInWith(Email) {
            this.email = email.trim()
            password = clave
        }
    }

    suspend fun recuperarClave(email: String) = sb.auth.resetPasswordForEmail(email.trim())

    suspend fun salir() = runCatching { sb.auth.signOut() }

    /** Qué inicios externos están activos en Supabase (lib/use-oauth-providers.ts): no se muestra un botón que fallaría. */
    suspend fun proveedoresOAuth(): Pair<Boolean, Boolean> = runCatching {
        val r = Backend.http.get(BuildConfig.SUPABASE_URL + "/auth/v1/settings") { header("apikey", BuildConfig.SUPABASE_KEY) }
        val ext = Backend.json.parseToJsonElement(r.bodyAsText()).jsonObject["external"]?.jsonObject
        fun on(k: String) = ext?.get(k)?.jsonPrimitive?.booleanOrNull == true
        on("google") to on("azure")
    }.getOrDefault(false to false)

    /** Abre el navegador para entrar con Google o Microsoft ("azure"), como la web. */
    suspend fun entrarCon(proveedor: String) {
        // Vuelve por vanty.xyz/auth/callback?app=1 (dirección ya permitida en Supabase); esa página le pasa el código
        // a la app (vantyaba://login) y la app lo canjea con su verificador PKCE.
        val vuelta = BuildConfig.API_BASE_URL + "/auth/callback?app=1"
        if (proveedor == "azure") sb.auth.signInWith(Azure, redirectUrl = vuelta) { scopes.addAll(listOf("email", "profile", "openid", "offline_access")) }
        else sb.auth.signInWith(Google, redirectUrl = vuelta)
    }

    enum class ResultadoOAuth { Ok, SinCuenta }

    /**
     * Lo mismo que /auth/callback de la web tras Google/Microsoft: si la cuenta se creó sola y no pertenece
     * a ningún centro, /api/auth/cuenta-huerfana la borra y se avisa; si no hay perfil se crea; si falta el
     * nombre se toma el de Google/Microsoft.
     */
    suspend fun trasOAuth(): ResultadoOAuth {
        val user = sb.auth.currentUserOrNull() ?: return ResultadoOAuth.SinCuenta
        val meta = user.userMetadata
        fun m(k: String) = meta?.get(k)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val nombreOAuth = m("full_name") ?: m("name") ?: m("display_name") ?: m("preferred_username")
        val p = perfil()
        if (p == null || p.role == "padre") {
            val borrada = runCatching {
                val r = Backend.http.post(BuildConfig.API_BASE_URL + "/api/auth/cuenta-huerfana") { Backend.token()?.let { bearerAuth(it) } }
                Backend.json.parseToJsonElement(r.bodyAsText()).jsonObject["borrada"]?.jsonPrimitive?.booleanOrNull == true
            }.getOrDefault(false)
            if (borrada) { salir(); return ResultadoOAuth.SinCuenta }
        }
        if (p == null) {
            sb.from("profiles").insert(buildJsonObject {
                put("id", user.id); put("email", user.email)
                put("full_name", nombreOAuth ?: user.email?.substringBefore('@') ?: "Usuario"); put("role", "padre")
            })
        } else if (p.nombre.isNullOrBlank() && nombreOAuth != null) {
            sb.from("profiles").update(buildJsonObject { put("full_name", nombreOAuth) }) { filter { eq("id", user.id) } }
        }
        return ResultadoOAuth.Ok
    }

    enum class ResultadoEliminar { Ok, ClaveIncorrecta, Administrador, Error }

    /** Pide la contraseña otra vez (por seguridad) y elimina la cuenta con /api/suscripcion/eliminar-cuenta. */
    suspend fun eliminarCuenta(email: String, clave: String): ResultadoEliminar {
        try {
            sb.auth.signInWith(Email) { this.email = email; password = clave }
        } catch (e: io.github.jan.supabase.auth.exception.AuthRestException) {
            return ResultadoEliminar.ClaveIncorrecta
        } catch (e: Exception) {
            return ResultadoEliminar.Error
        }
        return try {
            val r = Backend.apiPost("/api/suscripcion/eliminar-cuenta", buildJsonObject { put("confirm", "ELIMINAR") })
            when {
                r.status.isSuccess() -> { runCatching { sb.auth.clearSession() }; ResultadoEliminar.Ok }
                r.status.value == 409 || r.status.value == 403 -> ResultadoEliminar.Administrador
                else -> ResultadoEliminar.Error
            }
        } catch (e: Exception) {
            ResultadoEliminar.Error
        }
    }

    // ── Perfil e hijos ──────────────────────────────────────────────────────
    suspend fun perfil(): Perfil? {
        val uid = sb.auth.currentUserOrNull()?.id ?: return null
        return sb.from("profiles").select(Columns.list("id", "email", "full_name", "role", "centro_id", "nombre_confirmado", "terminos_version", "ia_consentimiento", "phone")) {
            filter { eq("id", uid) }
        }.decodeSingleOrNull<Perfil>()
    }

    suspend fun hijos(parentId: String): List<Hijo> =
        sb.from("children").select(Columns.list("id", "name", "birth_date", "diagnosis")) {
            filter { eq("parent_id", parentId) }
            order("created_at", Order.ASCENDING)
        }.decodeList()

    // ── Inicio ──────────────────────────────────────────────────────────────
    suspend fun racha(childId: String): Racha? =
        Backend.getOrNull("/api/padre/racha?child_id=${enc(childId)}&hoy=${hoyIso()}")

    suspend fun stats(childId: String): Stats? =
        Backend.getOrNull("/api/padre/stats?child_id=${enc(childId)}")

    private val columnasCita = Columns.list("id", "appointment_date", "appointment_time", "status", "service_type", "modalidad", "metadata")

    private val citaCerrada = setOf("cancelled", "completed", "cancelada", "completada", "realizada")

    suspend fun citasProximas(childId: String): List<Cita> =
        sb.from("appointments").select(columnasCita) {
            filter {
                eq("child_id", childId)
                gte("appointment_date", hoyIso())
            }
            order("appointment_date", Order.ASCENDING)
            order("appointment_time", Order.ASCENDING)
            limit(30)
        }.decodeList<Cita>().filter { it.status !in citaCerrada }

    suspend fun citasPasadas(childId: String): List<Cita> =
        sb.from("appointments").select(columnasCita) {
            filter {
                eq("child_id", childId)
                lt("appointment_date", hoyIso())
            }
            order("appointment_date", Order.DESCENDING)
            limit(20)
        }.decodeList()

    // ── Programas ABA y práctica en casa ────────────────────────────────────
    suspend fun programas(childId: String): List<Programa> =
        Backend.getOrNull<ProgramasResp>("/api/programas-aba?child_id=${enc(childId)}")?.data.orEmpty()

    /** Días practicados esta semana: pares (programaId, fecha). */
    suspend fun practicaSemana(childId: String, fechas: List<String>): Set<Pair<String, String>> =
        sb.from("programa_practica_casa").select(Columns.list("programa_id", "child_id", "fecha")) {
            filter {
                eq("child_id", childId)
                isIn("fecha", fechas)
            }
        }.decodeList<PracticaCasa>().map { it.programaId to it.fecha.take(10) }.toSet()

    suspend fun marcarPractica(programaId: String, childId: String, fecha: String, hecho: Boolean) {
        if (hecho) {
            sb.from("programa_practica_casa").upsert(PracticaCasa(programaId, childId, fecha))
        } else {
            sb.from("programa_practica_casa").delete {
                filter {
                    eq("programa_id", programaId)
                    eq("child_id", childId)
                    eq("fecha", fecha)
                }
            }
        }
    }

    // ── Plan semanal de actividades (engagement) ────────────────────────────
    suspend fun plan(childId: String): Plan? =
        Backend.getOrNull<PlanResp>("/api/engagement-padres?child_id=${enc(childId)}&locale=${Backend.idioma}")?.plan

    suspend fun guardarActividades(childId: String, plan: Plan): Boolean {
        val total = plan.actividades.size.coerceAtLeast(1)
        val hechas = plan.actividades.count { it.completada }
        val r = Backend.apiPost("/api/engagement-padres", buildJsonObject {
            put("childId", childId)
            put("accion", "actualizar_completadas")
            put("hoy", hoyIso())
            put("planId", plan.id)
            put("completadas_pct", hechas * 100 / total)
            putJsonArray("actividades") {
                plan.actividades.forEach { a -> add(buildJsonObject { put("completada", a.completada) }) }
            }
        })
        return r.status.isSuccess()
    }

    /** Pide un plan nuevo a la IA. Devuelve null si salió bien o el mensaje de error del servidor. */
    suspend fun generarPlan(childId: String): String? {
        val r = Backend.apiPost("/api/engagement-padres", buildJsonObject {
            put("childId", childId)
            put("accion", "generar_plan")
            put("locale", Backend.idioma)
        })
        if (r.status.isSuccess()) return null
        return runCatching {
            val body = Backend.json.parseToJsonElement(r.bodyAsText())
            ((body as? JsonObject)?.get("error") as? JsonPrimitive)?.content
        }.getOrNull() ?: "error"
    }

    // ── Traducción de textos del equipo (igual que useTraducir de la web) ───
    // El equipo escribe en español; en inglés se traducen con /api/traducir, que guarda cada
    // traducción en el servidor. Aquí además se recuerdan en memoria durante la sesión.
    private val memoriaEn = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun traducir(textos: Collection<String?>): Map<String, String> {
        val unicos = textos.filterNotNull().filter { it.isNotBlank() }.toSet()
        val faltan = unicos.filter { !memoriaEn.containsKey(it) }
        faltan.chunked(60).forEach { lote ->
            runCatching {
                val r = Backend.apiPost("/api/traducir", buildJsonObject {
                    put("idioma", "en")
                    putJsonArray("textos") { lote.forEach { add(JsonPrimitive(it)) } }
                })
                val lista = (Backend.json.parseToJsonElement(r.bodyAsText()) as? JsonObject)?.get("textos") as? kotlinx.serialization.json.JsonArray
                lista?.forEachIndexed { i, el -> (el as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { memoriaEn[lote[i]] = it } }
            }
        }
        return unicos.associateWith { memoriaEn[it] ?: it }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
