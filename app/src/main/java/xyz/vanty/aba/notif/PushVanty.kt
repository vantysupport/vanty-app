package xyz.vanty.aba.notif

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.vanty.aba.data.Backend
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Rol
import java.time.Instant
import kotlin.coroutines.resume

/**
 * Avisos al instante por Firebase Cloud Messaging.
 * Privacidad: por Firebase (Google) solo llega el id del aviso; el texto se lee de Supabase (tabla app_avisos)
 * con la sesión de la persona, así ningún nombre ni dato de salud pasa por Google.
 */
class PushVanty : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        alcance.launch { runCatching { PushApp.registrar(applicationContext, token) } }
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val id = m.data["aviso"] ?: return
        // Firebase da ~10 s para atender el mensaje: se lee el aviso y se muestra dentro de ese tiempo
        runBlocking { withTimeoutOrNull(8_000) { runCatching { PushApp.mostrar(applicationContext, id) } } }
    }

    private companion object {
        val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

object PushApp {
    @Serializable
    private data class Aviso(
        val id: String, val titulo: String, val cuerpo: String = "", val url: String? = null,
        val pose: String? = null, val tag: String? = null, val insistente: Boolean = false,
    )

    private suspend fun tokenActual(): String? = suspendCancellableCoroutine { c ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { c.resume(it) }
            .addOnFailureListener { c.resume(null) }
    }

    /** Guarda este celular para la persona con sesión (al entrar a la app y cuando Firebase renueva el token). */
    suspend fun registrar(ctx: Context, token: String? = null) {
        val yo = Backend.supabase.auth.currentUserOrNull()?.id ?: return
        val t = token ?: tokenActual() ?: return
        Backend.supabase.from("app_dispositivos").upsert(buildJsonObject {
            put("token", t); put("user_id", yo); put("plataforma", "android"); put("updated_at", Instant.now().toString())
        })
        Prefs(ctx).tokenPush = t
    }

    /** Al cerrar sesión: este celular deja de recibir los avisos de esa cuenta. */
    suspend fun olvidar(ctx: Context) {
        val p = Prefs(ctx)
        val t = p.tokenPush ?: return
        runCatching { Backend.supabase.from("app_dispositivos").delete { filter { eq("token", t) } } }
        p.tokenPush = null
    }

    suspend fun mostrar(ctx: Context, id: String) {
        val p = Prefs(ctx)
        if (p.yaAvisado("push_$id")) return
        val a = Backend.supabase.from("app_avisos").select(Columns.list("id", "titulo", "cuerpo", "url", "pose", "tag", "insistente")) {
            filter { eq("id", id) }
        }.decodeSingleOrNull<Aviso>() ?: return
        // Una campaña de /control llega por aquí y también la revisa Campanas: se muestra una sola vez
        val campana = a.tag?.takeIf { it.startsWith("campana:") }?.removePrefix("campana:")
        if (campana != null) {
            if (p.yaAvisado("campanaid_$campana")) return
            p.marcarAvisado("campanaid_$campana")
        }
        val familia = Rol.de(p.rol) == Rol.Familia
        val vista = a.url?.substringAfter("vista=", "")?.substringBefore('&')?.ifBlank { null } ?: if (familia) "inicio" else "hoy"
        Avisos.mostrar(
            ctx, ID_PUSH + ((a.tag ?: a.id).hashCode() and 0xFFF), if (a.url?.contains("cita") == true || a.url?.contains("agenda") == true) Avisos.CANAL_CITAS else Avisos.CANAL_LOGROS,
            pose(a.pose), a.titulo, a.cuerpo, vista, insistente = a.insistente,
        )
        p.marcarAvisado("push_$id")
    }

    private fun pose(nombre: String?) = when (nombre) {
        "celebra" -> Avisos.Pose.CELEBRA
        "feliz" -> Avisos.Pose.FELIZ
        "guino" -> Avisos.Pose.GUINO
        "pensando" -> Avisos.Pose.PENSANDO
        "laptop" -> Avisos.Pose.LAPTOP
        "corre" -> Avisos.Pose.CORRE
        else -> Avisos.Pose.SALUDO
    }

    private const val ID_PUSH = 6000
}
