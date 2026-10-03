package xyz.vanty.aba.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.exception.AuthRestException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.vanty.aba.data.Cita
import xyz.vanty.aba.data.Hijo
import xyz.vanty.aba.data.Perfil
import xyz.vanty.aba.data.Plan
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Programa
import xyz.vanty.aba.data.Racha
import xyz.vanty.aba.data.Repo
import xyz.vanty.aba.data.Stats
import xyz.vanty.aba.notif.Avisos
import xyz.vanty.aba.notif.VigiaWorker
import io.github.jan.supabase.auth.auth
import xyz.vanty.aba.data.Backend
import xyz.vanty.aba.data.Centro
import xyz.vanty.aba.data.Comun
import xyz.vanty.aba.data.ErrorApi
import xyz.vanty.aba.data.EstadoIA
import xyz.vanty.aba.data.TERMINOS_VERSION
import xyz.vanty.aba.data.errorApi
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.data.RepoFamilia
import xyz.vanty.aba.data.textos
import xyz.vanty.aba.data.traducido
import xyz.vanty.aba.util.EN
import xyz.vanty.aba.util.Idioma
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.hoyIso
import xyz.vanty.aba.util.semanaActual
import xyz.vanty.aba.widget.RachaWidget

enum class Fase { Cargando, Login, Requisitos, CentroInactivo, Bloqueado, App, Equipo, SoloFamilias }

/** Destinos de las familias: los 5 primeros van en la barra; el resto se abre desde "Más". */
enum class Pestana { Inicio, Citas, Aria, Chat, Mas, Practicar, Perfil, Recursos, Documentos, Formularios, Evaluacion;
    val enBarra get() = ordinal <= Mas.ordinal
}

data class Estado(
    val fase: Fase = Fase.Cargando,
    val pestana: Pestana = Pestana.Inicio,
    val perfil: Perfil? = null,
    val hijos: List<Hijo> = emptyList(),
    val hijo: Hijo? = null,
    val racha: Racha = Racha(),
    val stats: Stats? = null,
    val proximas: List<Cita> = emptyList(),
    val pasadas: List<Cita> = emptyList(),
    val programas: List<Programa> = emptyList(),
    val resumenAria: xyz.vanty.aba.data.ResumenAria? = null,
    val mensajesEquipo: List<xyz.vanty.aba.data.MensajeEquipo> = emptyList(),
    /** (programaId, fecha) practicados esta semana */
    val practicados: Set<Pair<String, String>> = emptySet(),
    val plan: Plan? = null,
    val cargandoDatos: Boolean = false,
    val refrescando: Boolean = false,
    val generandoPlan: Boolean = false,
    val entrando: Boolean = false,
    val errorLogin: String? = null,
    /** Días de racha a celebrar con la animación de pantalla completa */
    val celebrar: Int? = null,
    val aviso: String? = null,
    /** Sección pedida al tocar una notificación (para el equipo; las familias usan `pestana`). */
    val vista: String? = null,
    val eliminando: Boolean = false,
    val errorEliminar: String? = null,
    val centro: Centro? = null,
    /** Funciones habilitadas por el plan del centro (/api/control). Clave ausente = habilitada. */
    val funciones: Map<String, Boolean> = emptyMap(),
    val estadoIA: EstadoIA? = null,
    val guardandoIA: Boolean = false,
    /** Inicios externos activos en Supabase: Google y Microsoft (como la web). */
    val conGoogle: Boolean = false,
    val conMicrosoft: Boolean = false,
) {
    fun on(clave: String) = funciones[clave] != false
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val _e = MutableStateFlow(Estado(racha = prefs.rachaVigente()))
    val e: StateFlow<Estado> = _e

    init {
        viewModelScope.launch {
            val sesion = runCatching { Repo.haySesion() }.getOrDefault(false)
            if (sesion) cargarTodo() else _e.update { it.copy(fase = Fase.Login) }
        }
        viewModelScope.launch {
            val (g, m) = Repo.proveedoresOAuth()
            _e.update { it.copy(conGoogle = g, conMicrosoft = m) }
        }
    }

    /** Google ("google") o Microsoft ("azure"): abre el navegador; la vuelta llega por [volvioDeOAuth]. */
    fun entrarCon(proveedor: String) {
        _e.update { it.copy(entrando = true, errorLogin = null) }
        viewModelScope.launch {
            runCatching { Repo.entrarCon(proveedor) }.onFailure {
                _e.update { it.copy(entrando = false, errorLogin = L("No se pudo iniciar sesión con ese proveedor.", "Couldn't sign in with that provider.")) }
            }
        }
    }

    /** El navegador volvió sin completar el inicio (la persona lo cerró). */
    fun oauthCancelado() { if (_e.value.fase == Fase.Login) _e.update { it.copy(entrando = false) } }

    fun volvioDeOAuth() {
        _e.update { it.copy(entrando = true, errorLogin = null) }
        viewModelScope.launch {
            when (runCatching { Repo.trasOAuth() }.getOrNull()) {
                Repo.ResultadoOAuth.Ok -> cargarTodo()
                Repo.ResultadoOAuth.SinCuenta -> _e.update {
                    it.copy(fase = Fase.Login, entrando = false, errorLogin = L(
                        "Ese correo no tiene una cuenta en Vanty. Pide una invitación a tu centro o crea tu centro desde vanty.xyz.",
                        "That email doesn't have a Vanty account. Ask your center for an invitation or create your center at vanty.xyz."))
                }
                null -> _e.update { it.copy(entrando = false, errorLogin = L("Sin conexión. Revisa tu internet e intenta de nuevo.", "No connection. Check your internet and try again.")) }
            }
        }
    }

    // ── Sesión ──────────────────────────────────────────────────────────────
    fun entrar(email: String, clave: String) {
        if (email.isBlank() || clave.isBlank()) {
            _e.update { it.copy(errorLogin = L("Escribe tu correo y contraseña", "Enter your email and password")) }
            return
        }
        _e.update { it.copy(entrando = true, errorLogin = null) }
        viewModelScope.launch {
            try {
                Repo.entrar(email, clave)
                cargarTodo()
            } catch (ex: AuthRestException) {
                _e.update { it.copy(entrando = false, errorLogin = L("Correo o contraseña incorrectos", "Incorrect email or password")) }
            } catch (ex: Exception) {
                _e.update { it.copy(entrando = false, errorLogin = L("Sin conexión. Revisa tu internet e intenta de nuevo.", "No connection. Check your internet and try again.")) }
            }
        }
    }

    fun recuperarClave(email: String) {
        if (email.isBlank()) {
            _e.update { it.copy(errorLogin = L("Escribe tu correo para recuperar la contraseña", "Enter your email to reset your password")) }
            return
        }
        viewModelScope.launch {
            runCatching { Repo.recuperarClave(email) }
            mostrarAviso(L("Si el correo está registrado, te enviamos un enlace para cambiar la contraseña.", "If the email is registered, we sent you a link to reset your password."))
        }
    }

    fun salir() {
        viewModelScope.launch {
            runCatching { xyz.vanty.aba.notif.PushApp.olvidar(getApplication()) } // antes de cerrar la sesión (RLS)
            Repo.salir()
            prefs.limpiarSesion()
            VigiaWorker.cancelar(getApplication())
            xyz.vanty.aba.widget.actualizarWidgets(getApplication())
            _e.value = Estado(fase = Fase.Login)
        }
    }

    /** Eliminar la cuenta (requisito de Google Play). Pide la contraseña de nuevo antes de borrar. */
    fun eliminarCuenta(clave: String) {
        val email = _e.value.perfil?.email ?: Backend.supabase.auth.currentUserOrNull()?.email
        if (clave.isBlank() || email == null) {
            _e.update { it.copy(errorEliminar = L("Escribe tu contraseña para confirmar", "Enter your password to confirm")) }
            return
        }
        _e.update { it.copy(eliminando = true, errorEliminar = null) }
        viewModelScope.launch {
            when (Repo.eliminarCuenta(email, clave)) {
                Repo.ResultadoEliminar.Ok -> {
                    prefs.limpiarSesion()
                    VigiaWorker.cancelar(getApplication())
                    xyz.vanty.aba.widget.actualizarWidgets(getApplication())
                    _e.value = Estado(fase = Fase.Login, aviso = L("Tu cuenta fue eliminada. ¡Gracias por usar Vanty!", "Your account was deleted. Thanks for using Vanty!"))
                }
                Repo.ResultadoEliminar.ClaveIncorrecta -> _e.update { it.copy(eliminando = false, errorEliminar = L("Contraseña incorrecta", "Incorrect password")) }
                Repo.ResultadoEliminar.Administrador -> _e.update {
                    it.copy(eliminando = false, errorEliminar = L(
                        "Eres la persona encargada del centro: para no dejarlo sin responsable, primero transfiere el centro o elimínalo desde vanty.xyz en tu computadora.",
                        "You're the person in charge of the center: so it's never left without someone responsible, first transfer or delete the center at vanty.xyz on your computer."))
                }
                Repo.ResultadoEliminar.Error -> _e.update { it.copy(eliminando = false, errorEliminar = L("No se pudo eliminar. Revisa tu conexión e intenta de nuevo.", "Couldn't delete. Check your connection and try again.")) }
            }
        }
    }

    fun completarRequisitos(nombre: String?, aceptaTerminos: Boolean) {
        val id = _e.value.perfil?.id ?: return
        _e.update { it.copy(entrando = true, errorLogin = null) }
        viewModelScope.launch {
            val ok = runCatching { Comun.completarPerfil(id, nombre, aceptaTerminos) }.getOrDefault(false)
            if (ok) cargarTodo()
            else _e.update { it.copy(entrando = false, errorLogin = L("No se pudo guardar. Revisa tu conexión e intenta de nuevo.", "Couldn't save. Check your connection and try again.")) }
        }
    }

    fun reintentar() {
        _e.update { it.copy(fase = Fase.Cargando) }
        viewModelScope.launch { cargarTodo() }
    }

    /** Acepta o rechaza el uso de IA: "propio" (familia) o "centro" (dirección). */
    fun decidirIA(aceptar: Boolean, onListo: () -> Unit = {}) {
        val ia = _e.value.estadoIA
        val ambito = if (ia?.motivo == "centro") "centro" else "propio"
        _e.update { it.copy(guardandoIA = true) }
        viewModelScope.launch {
            Comun.decidirIA(ambito, aceptar)
            _e.update { it.copy(guardandoIA = false, estadoIA = Comun.estadoIA() ?: it.estadoIA) }
            onListo()
        }
    }

    /** Vuelve a leer el consentimiento de IA (p. ej. si el servidor respondió ia_no_autorizada). */
    fun refrescarIA() = viewModelScope.launch { Comun.estadoIA()?.let { ia -> _e.update { it.copy(estadoIA = ia) } } }

    /** Pedir cambio o avisar que no podrá asistir: es una solicitud; el centro decide (SolicitudCita.tsx). */
    fun solicitarCita(c: Cita, cancelar: Boolean, motivo: String?, fecha: String?, hora: String?, onListo: (Boolean) -> Unit) {
        viewModelScope.launch {
            val error = runCatching { RepoFamilia.solicitarCita(c.id, cancelar, motivo, fecha, hora) }.getOrElse { "error" }
            mostrarAviso(when (error) {
                null -> if (cancelar) L("Avisamos al centro que no podrás asistir.", "We let the center know you can't make it.")
                        else L("¡Solicitud enviada! El centro te confirmará la nueva fecha.", "Request sent! The center will confirm the new date.")
                "fecha_pasada" -> L("Elige una fecha a partir de mañana.", "Pick a date from tomorrow on.")
                "no_aplica" -> L("Esta cita ya no se puede cambiar.", "This appointment can no longer be changed.")
                else -> L("No se pudo enviar. Intenta de nuevo.", "Couldn't send. Try again.")
            })
            onListo(error == null)
            if (error == null) refrescar()
        }
    }

    fun limpiarErrorEliminar() = _e.update { it.copy(errorEliminar = null) }

    // ── Carga de datos ──────────────────────────────────────────────────────
    private suspend fun cargarTodo() {
        try {
            val perfil = Repo.perfil()
            if (perfil == null) {
                _e.update { it.copy(fase = Fase.Login, entrando = false, errorLogin = L("No encontramos tu perfil.", "We couldn't find your profile.")) }
                Repo.salir(); return
            }
            prefs.rol = perfil.role.orEmpty()
            prefs.usuarioId = perfil.id
            prefs.nombreUsuario = xyz.vanty.aba.util.nombreDePila(perfil.nombre)
            val rol = Rol.de(perfil.role)
            if (rol != Rol.Otro && (perfil.nombreConfirmado == false || perfil.terminosVersion != TERMINOS_VERSION)) {
                _e.update { it.copy(fase = Fase.Requisitos, perfil = perfil, entrando = false, errorLogin = null) }
                return
            }
            if (rol != Rol.Otro) {
                // Centro, funciones del plan y consentimiento de IA (esta llamada también detecta el centro inactivo)
                val (centro, funciones, ia) = coroutineScope {
                    val c = async { Comun.centro(perfil.centroId) }
                    val f = async { Comun.funciones() }
                    val r = async { runCatching { Backend.apiGet("/api/ia/consentimiento") }.getOrNull() }
                    Triple(c.await(), f.await(), r.await())
                }
                if (ia != null && ia.status.value == 403 && ia.errorApi() is ErrorApi.CentroInactivo) {
                    _e.update { it.copy(fase = Fase.CentroInactivo, perfil = perfil, centro = centro, entrando = false) }
                    return
                }
                val estadoIA = ia?.takeIf { it.status.isSuccess() }?.let { runCatching { Backend.json.decodeFromString(EstadoIA.serializer(), it.bodyAsText()) }.getOrNull() }
                _e.update { it.copy(centro = centro, funciones = funciones, estadoIA = estadoIA) }
                if (rol == Rol.Familia) {
                    val limite = Backend.getOrNull<kotlinx.serialization.json.JsonObject>("/api/padre/limite")
                    if ((limite?.get("allowed") as? kotlinx.serialization.json.JsonPrimitive)?.content == "false") {
                        _e.update { it.copy(fase = Fase.Bloqueado, perfil = perfil, entrando = false) }
                        return
                    }
                }
            }
            when (rol) {
                Rol.Familia -> Unit
                Rol.Otro -> {
                    _e.update { it.copy(fase = Fase.SoloFamilias, perfil = perfil, entrando = false) }
                    return
                }
                else -> {
                    _e.update { it.copy(fase = Fase.Equipo, perfil = perfil, entrando = false, errorLogin = null) }
                    VigiaWorker.programar(getApplication())
                    return
                }
            }
            val hijos = Repo.hijos(perfil.id)
            val hijo = hijos.firstOrNull { it.id == prefs.hijoId } ?: hijos.firstOrNull()
            prefs.hijos = hijos
            hijosOrig = hijos
            _e.update { it.copy(fase = Fase.App, perfil = perfil, hijos = hijos, hijo = hijo, entrando = false, errorLogin = null) }
            VigiaWorker.programar(getApplication())
            if (hijo != null) cargarHijo(hijo)
        } catch (ex: Exception) {
            // Sin conexión al abrir: mostramos lo guardado y dejamos reintentar
            val equipo = Rol.de(prefs.rol) in setOf(Rol.Especialista, Rol.Secretaria, Rol.Admin)
            _e.update {
                it.copy(
                    fase = if (it.fase == Fase.Cargando) (if (equipo) Fase.Equipo else Fase.App) else it.fase, entrando = false,
                    perfil = it.perfil ?: if (equipo) Perfil(prefs.usuarioId ?: "", role = prefs.rol) else null,
                    hijos = prefs.hijos, hijo = prefs.hijos.firstOrNull { h -> h.id == prefs.hijoId },
                    aviso = L("Sin conexión. Desliza hacia abajo para reintentar.", "No connection. Pull down to retry."),
                )
            }
        }
    }

    private suspend fun cargarHijo(h: Hijo) {
        prefs.hijoId = h.id
        prefs.nombreHijo = h.nombre
        _e.update { it.copy(cargandoDatos = true) }
        val fechas = semanaActual().map { it.toString() }
        coroutineScope {
            val racha = async { Repo.racha(h.id) }
            val stats = async { Repo.stats(h.id) }
            val prox = async { runCatching { Repo.citasProximas(h.id) }.getOrDefault(emptyList()) }
            val pas = async { runCatching { Repo.citasPasadas(h.id) }.getOrDefault(emptyList()) }
            val progs = async { Repo.programas(h.id) }
            val practica = async { runCatching { Repo.practicaSemana(h.id, fechas) }.getOrDefault(emptySet()) }
            val plan = async { Repo.plan(h.id) }
            val resumen = async { runCatching { xyz.vanty.aba.data.RepoInicio.resumenAria(h.id) }.getOrNull() }
            val mensajes = async { runCatching { xyz.vanty.aba.data.RepoInicio.mensajesEquipo(h.id) }.getOrDefault(emptyList()) }
            val r = racha.await()
            if (r != null) guardarRacha(r)
            programasOrig = progs.await(); proximasOrig = prox.await(); pasadasOrig = pas.await()
            prefs.proximaCita = proximasOrig.firstOrNull()?.let { "${it.fecha.take(10)}|${it.hora?.take(5).orEmpty()}" }
            prefs.citasFamilia = xyz.vanty.aba.widget.lineasCitas(proximasOrig, h.primerNombre)
            _e.value.perfil?.id?.let { prefs.sinLeer = Comun.sinLeer(it) }
            xyz.vanty.aba.widget.actualizarWidgets(getApplication())
            _e.update {
                it.copy(
                    racha = r ?: it.racha, stats = stats.await(), proximas = proximasOrig, pasadas = pasadasOrig,
                    programas = programasOrig, practicados = practica.await(), plan = plan.await(), cargandoDatos = false,
                    resumenAria = resumen.await(), mensajesEquipo = mensajes.await(),
                )
            }
        }
        runCatching { mostrarEnIdioma() }
    }

    // ── Idioma (ES | EN, como el selector de la web) ────────────────────────
    // Lo que escribe el equipo llega en español: se guardan los originales y, en inglés, se muestran traducidos.
    private var programasOrig: List<Programa> = emptyList()
    private var proximasOrig: List<Cita> = emptyList()
    private var pasadasOrig: List<Cita> = emptyList()
    private var hijosOrig: List<Hijo> = emptyList()

    private suspend fun mostrarEnIdioma() {
        if (!EN) {
            _e.update { e -> e.copy(programas = programasOrig, proximas = proximasOrig, pasadas = pasadasOrig, hijos = hijosOrig.ifEmpty { e.hijos }) }
            return
        }
        val t = Repo.traducir(
            programasOrig.flatMap { it.textos() } + (proximasOrig + pasadasOrig).map { it.servicio } + hijosOrig.map { it.diagnosis },
        )
        if (!EN) return // cambiaron de idioma mientras se traducía
        fun tr(s: String?) = s?.let { t[it] ?: it }
        _e.update { e ->
            e.copy(
                programas = programasOrig.map { it.traducido(t) },
                proximas = proximasOrig.map { it.copy(servicio = tr(it.servicio)) },
                pasadas = pasadasOrig.map { it.copy(servicio = tr(it.servicio)) },
                hijos = hijosOrig.map { it.copy(diagnosis = tr(it.diagnosis)) }.ifEmpty { e.hijos },
            )
        }
    }

    fun cambiarIdioma(codigo: String) {
        if (codigo == Idioma.actual) return
        prefs.idioma = codigo
        Idioma.actual = codigo
        val app = getApplication<Application>()
        Avisos.crearCanales(app)
        viewModelScope.launch {
            xyz.vanty.aba.widget.actualizarWidgets(app)
            // Sin conexión, la traducción falla: se queda el texto original (nunca cerrar la app)
            runCatching { mostrarEnIdioma() }
            // El plan semanal lo traduce el servidor según el idioma pedido
            val h = _e.value.hijo ?: return@launch
            if (_e.value.fase == Fase.App) runCatching { Repo.plan(h.id) }.getOrNull()?.let { p -> _e.update { it.copy(plan = p) } }
        }
    }

    fun refrescar() {
        val h = _e.value.hijo
        _e.update { it.copy(refrescando = true) }
        viewModelScope.launch {
            if (h != null) runCatching { cargarHijo(h) } else cargarTodo()
            _e.update { it.copy(refrescando = false) }
        }
    }

    fun elegirHijo(h: Hijo) {
        if (h.id == _e.value.hijo?.id) return
        _e.update { it.copy(hijo = h, stats = null, programas = emptyList(), plan = null, proximas = emptyList(), pasadas = emptyList(), racha = Racha()) }
        viewModelScope.launch { runCatching { cargarHijo(h) } }
    }

    fun irA(p: Pestana) = _e.update { it.copy(pestana = p) }

    fun vistaUsada() = _e.update { it.copy(vista = null) }

    fun irAVista(vista: String?) = if (_e.value.fase == Fase.Equipo || Rol.de(prefs.rol) != Rol.Familia && vista != null) {
        _e.update { it.copy(vista = vista) }
    } else when (vista) {
        "practicar" -> irA(Pestana.Practicar)
        "citas", "agenda", "miscitas" -> irA(Pestana.Citas)
        "perfil" -> irA(Pestana.Perfil)
        "inicio" -> irA(Pestana.Inicio)
        "chat", "chat-familias" -> irA(Pestana.Chat)
        "aria" -> irA(Pestana.Aria)
        else -> Unit
    }

    // ── Práctica en casa ────────────────────────────────────────────────────
    fun alternarPractica(programaId: String, fecha: String) {
        val h = _e.value.hijo ?: return
        if (fecha > hoyIso()) return
        val clave = programaId to fecha
        val hecho = clave !in _e.value.practicados
        _e.update { it.copy(practicados = if (hecho) it.practicados + clave else it.practicados - clave) }
        viewModelScope.launch {
            try {
                Repo.marcarPractica(programaId, h.id, fecha, hecho)
                actualizarRacha(h)
            } catch (ex: Exception) {
                _e.update { it.copy(practicados = if (hecho) it.practicados - clave else it.practicados + clave) }
                mostrarAviso(L("No se pudo guardar. Revisa tu conexión.", "Couldn't save. Check your connection."))
            }
        }
    }

    fun alternarActividad(indice: Int) {
        val h = _e.value.hijo ?: return
        val plan = _e.value.plan ?: return
        val nuevo = plan.copy(actividades = plan.actividades.mapIndexed { i, a -> if (i == indice) a.copy(completada = !a.completada) else a })
        _e.update { it.copy(plan = nuevo) }
        viewModelScope.launch {
            val ok = runCatching { Repo.guardarActividades(h.id, nuevo) }.getOrDefault(false)
            if (!ok) {
                _e.update { it.copy(plan = plan) }
                mostrarAviso(L("No se pudo guardar. Revisa tu conexión.", "Couldn't save. Check your connection."))
            } else actualizarRacha(h)
        }
    }

    fun generarPlan() {
        val h = _e.value.hijo ?: return
        _e.update { it.copy(generandoPlan = true) }
        viewModelScope.launch {
            val error = runCatching { Repo.generarPlan(h.id) }.getOrElse { "error" }
            if (error == null) {
                val plan = Repo.plan(h.id)
                _e.update { it.copy(plan = plan, generandoPlan = false) }
                mostrarAviso(L("¡ARIA preparó tu plan de la semana!", "ARIA prepared your weekly plan!"))
            } else {
                _e.update { it.copy(generandoPlan = false) }
                mostrarAviso(if (error == "error") L("No se pudo generar el plan. Intenta más tarde.", "Couldn't generate the plan. Try again later.") else error)
            }
        }
    }

    /** Vuelve a pedir la racha; si hoy pasó de "pendiente" a "hecho", lanza la celebración. */
    private suspend fun actualizarRacha(h: Hijo) {
        val antes = _e.value.racha
        val r = Repo.racha(h.id) ?: return
        guardarRacha(r)
        _e.update { it.copy(racha = r, celebrar = if (!antes.hoy && r.hoy) r.dias else it.celebrar) }
        if (!antes.hoy && r.hoy && r.dias in HITOS) Avisos.celebrarRacha(getApplication(), r.dias)
    }

    private suspend fun guardarRacha(r: Racha) {
        prefs.racha = r
        prefs.rachaFecha = hoyIso()
        xyz.vanty.aba.widget.actualizarWidgets(getApplication())
    }

    /** Solo para la pantalla de demostración de la versión debug. */
    fun demo(estado: Estado) { _e.value = estado }

    fun cerrarCelebracion() = _e.update { it.copy(celebrar = null) }

    // ── Ajustes de avisos ───────────────────────────────────────────────────
    fun mostrarAviso(t: String) = _e.update { it.copy(aviso = t) }
    fun avisoMostrado() = _e.update { it.copy(aviso = null) }

    companion object {
        private val HITOS = setOf(3, 7, 14, 30, 50, 100, 150, 200, 365)
    }
}
