package xyz.vanty.aba.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.vanty.aba.data.ErrorApi
import xyz.vanty.aba.data.Mensaje
import xyz.vanty.aba.data.RepoChat
import xyz.vanty.aba.util.L

/** De qué conversación se trata. */
sealed interface TipoChat {
    val clave: String
    data class Aria(val childId: String, val childName: String?, val yo: String, val equipo: Boolean) : TipoChat { override val clave = "aria:$childId:$equipo" }
    data class Centro(val childId: String, val yo: String) : TipoChat { override val clave = "centro:$childId" }
    data class Equipo(val con: String, val yo: String) : TipoChat { override val clave = "equipo:$con" }
}

data class EstadoChat(
    val mensajes: List<Mensaje> = emptyList(),
    val cargando: Boolean = true,
    val escribiendo: Boolean = false,
    val error: String? = null,
    /** "centro" o "propio" si ARIA está bloqueada por consentimiento de IA. */
    val sinIA: String? = null,
    val centroInactivo: Boolean = false,
)

/** Estado de una conversación (una instancia por chat abierto). */
class ConversacionVM(app: Application) : AndroidViewModel(app) {
    private val _e = MutableStateFlow(EstadoChat())
    val e: StateFlow<EstadoChat> = _e
    private var tipo: TipoChat? = null

    fun abrir(t: TipoChat) {
        if (tipo?.clave == t.clave) return
        tipo = t
        _e.value = EstadoChat()
        viewModelScope.launch { cargar(primera = true) }
    }

    /** Vuelve a leer (los chats con personas se refrescan cada pocos segundos mientras están abiertos). */
    fun refrescar() = viewModelScope.launch { cargar(primera = false) }

    private suspend fun cargar(primera: Boolean) {
        val t = tipo ?: return
        val lista = runCatching {
            when (t) {
                is TipoChat.Aria -> if (t.equipo) _e.value.mensajes else RepoChat.historialAria(t.childId, t.yo)
                is TipoChat.Centro -> RepoChat.mensajesCentro(t.childId, t.yo).also { RepoChat.marcarLeidoCentro(t.childId, t.yo) }
                is TipoChat.Equipo -> RepoChat.mensajesEquipo(t.con, t.yo)
            }
        }.getOrNull()
        _e.update {
            it.copy(
                mensajes = lista ?: it.mensajes, cargando = false,
                error = if (lista == null && primera) L("Sin conexión. Desliza para reintentar.", "No connection. Pull to retry.") else null,
            )
        }
    }

    fun enviar(texto: String) {
        val t = tipo ?: return
        val limpio = texto.trim()
        if (limpio.isEmpty() || _e.value.escribiendo) return
        val propio = Mensaje("tmp${System.nanoTime()}", limpio, mio = true, fecha = java.time.Instant.now().toString())
        _e.update { it.copy(mensajes = it.mensajes + propio, escribiendo = t is TipoChat.Aria, error = null) }
        viewModelScope.launch {
            try {
                when (t) {
                    is TipoChat.Aria -> {
                        val resp = RepoChat.preguntarAria(limpio, t.childId, t.childName, t.equipo)
                        _e.update { it.copy(escribiendo = false, mensajes = it.mensajes + Mensaje("r${System.nanoTime()}", resp, mio = false, esAria = true)) }
                    }
                    is TipoChat.Centro -> { RepoChat.enviarCentro(t.childId, t.yo, limpio); cargar(false) }
                    is TipoChat.Equipo -> { RepoChat.enviarEquipo(t.con, limpio); cargar(false) }
                }
            } catch (ex: ErrorApi.SinIA) {
                _e.update { it.copy(escribiendo = false, sinIA = ex.motivo, mensajes = it.mensajes - propio) }
            } catch (ex: ErrorApi.CentroInactivo) {
                _e.update { it.copy(escribiendo = false, centroInactivo = true, mensajes = it.mensajes - propio) }
            } catch (ex: ErrorApi.Limite) {
                _e.update {
                    it.copy(escribiendo = false, mensajes = it.mensajes + Mensaje("l${System.nanoTime()}",
                        if (t is TipoChat.Aria) L("Llegaste al límite de mensajes de hoy. ¡Vuelve mañana y seguimos! 💙", "You've reached today's message limit. Come back tomorrow! 💙")
                        else L("Espera un momento e inténtalo de nuevo.", "Wait a moment and try again."),
                        mio = false, esAria = t is TipoChat.Aria))
                }
            } catch (ex: Exception) {
                _e.update { it.copy(escribiendo = false, error = L("No se pudo enviar. Revisa tu conexión.", "Couldn't send. Check your connection."), mensajes = it.mensajes - propio) }
            }
        }
    }

    fun consentimientoResuelto() = _e.update { it.copy(sinIA = null) }
}
