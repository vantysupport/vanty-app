package xyz.vanty.aba.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import xyz.vanty.aba.BuildConfig
import xyz.vanty.aba.util.EN

/**
 * Conexión con el mismo backend que la web:
 *  • Supabase (login y tablas con RLS) usando la clave pública.
 *  • Las rutas /api de vanty.xyz con `Authorization: Bearer <token de la sesión>`.
 */
object Backend {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    val supabase = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
        defaultSerializer = KotlinXSerializer(json)
        install(Auth) {
            // Vuelta del navegador tras entrar con Google / Microsoft (vantyaba://login)
            scheme = "vantyaba"
            host = "login"
            flowType = FlowType.PKCE
        }
        install(Postgrest)
    }

    val http = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000 // generar un plan con IA puede tardar
            connectTimeoutMillis = 15_000
        }
    }

    /** Para /api/files: se lee la redirección (URL firmada) en vez de seguirla. */
    val httpSinRedirecciones = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = false
    }

    suspend fun token(): String? {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentAccessTokenOrNull()
    }

    val idioma: String get() = if (EN) "en" else "es"

    suspend fun apiGet(path: String): HttpResponse {
        val t = token()
        return http.get(BuildConfig.API_BASE_URL + path) {
            if (t != null) bearerAuth(t)
            header("x-locale", idioma)
        }
    }

    suspend fun apiPost(path: String, body: JsonObject): HttpResponse {
        val t = token()
        return http.post(BuildConfig.API_BASE_URL + path) {
            if (t != null) bearerAuth(t)
            header("x-locale", idioma)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    suspend fun apiPatch(path: String, body: JsonObject): HttpResponse {
        val t = token()
        return http.patch(BuildConfig.API_BASE_URL + path) {
            if (t != null) bearerAuth(t)
            header("x-locale", idioma)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** Cualquier método con cuerpo JSON (objeto o lista), p. ej. POST de varias citas o DELETE con `{id}`. */
    suspend fun apiEnviar(metodo: io.ktor.http.HttpMethod, path: String, body: kotlinx.serialization.json.JsonElement): HttpResponse {
        val t = token()
        return http.request(BuildConfig.API_BASE_URL + path) {
            method = metodo
            if (t != null) bearerAuth(t)
            header("x-locale", idioma)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** GET a la API; null si falla (sin conexión, 401, etc.). */
    suspend inline fun <reified T> getOrNull(path: String): T? = try {
        val r = apiGet(path)
        if (r.status.isSuccess()) r.body<T>() else null
    } catch (e: Exception) {
        null
    }
}
