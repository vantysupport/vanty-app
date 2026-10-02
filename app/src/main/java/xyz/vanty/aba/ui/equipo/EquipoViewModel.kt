package xyz.vanty.aba.ui.equipo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.vanty.aba.data.Alerta
import xyz.vanty.aba.data.CitaEquipo
import xyz.vanty.aba.data.Envio
import xyz.vanty.aba.data.EstadoCita
import xyz.vanty.aba.data.Metricas
import xyz.vanty.aba.data.Paciente
import xyz.vanty.aba.data.Pago
import xyz.vanty.aba.data.Perfil
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Programa
import xyz.vanty.aba.data.Repo
import xyz.vanty.aba.data.RepoEquipo
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.data.textos
import xyz.vanty.aba.data.traducido
import xyz.vanty.aba.notif.AvisosEquipo
import xyz.vanty.aba.util.EN
import xyz.vanty.aba.util.L
import java.time.LocalDate

/** Destinos del equipo. Los de la barra dependen del rol y del plan del centro; el resto se abre desde "Más". */
/**
 * Apartados del equipo, con los mismos nombres que el menú de la web:
 * Hoy = "Inicio", Cobros = "Pagos", Inteligencia = "Análisis Predictivo", Recursos = "Recursos Adicionales".
 */
enum class TabEquipo { Hoy, Agenda, Pacientes, Cobros, Chat, Mas, Aria, Perfil, Reportes, Usuarios, Recursos, Inteligencia }

/** Qué celebrar con la animación de pantalla completa. */
sealed interface Festejo {
    data class DiaCompleto(val sesiones: Int) : Festejo
    data class Cobro(val monto: Double, val saldada: Boolean) : Festejo
}

data class EstadoEquipo(
    val rol: Rol = Rol.Especialista,
    val perfil: Perfil? = null,
    val tab: TabEquipo = TabEquipo.Hoy,
    val hoy: List<CitaEquipo> = emptyList(),
    val fechaAgenda: LocalDate = LocalDate.now(),
    val agenda: List<CitaEquipo> = emptyList(),
    /** Cantidad de citas por día de la semana visible en la agenda (para los puntitos). */
    val conteoSemana: Map<String, Int> = emptyMap(),
    val soloMias: Boolean = true,
    val pacientes: List<Paciente> = emptyList(),
    val deudas: List<Pago> = emptyList(),
    /** Cobros del año (para Registros y Reportes financieros). */
    val pagosAnio: List<Pago> = emptyList(),
    val tarifas: List<xyz.vanty.aba.data.Tarifa> = emptyList(),
    val cobradoHoy: Double = 0.0,
    val moneda: String = "PEN",
    val metricas: Metricas? = null,
    val misEnvios: List<Envio> = emptyList(),
    val cargando: Boolean = true,
    val refrescando: Boolean = false,
    val festejo: Festejo? = null,
    val aviso: String? = null,
) {
    /** "jefe" (dueño) o "admin": en la web solo el jefe ve Reportes Financieros y Chat Equipo. */
    val esJefe get() = perfil?.role == "jefe"

    /**
     * Apartados del menú web de cada rol, en el mismo orden (Sidebar.tsx + funciones del plan con `on`).
     * El Cerebro IA queda fuera: solo PC.
     */
    fun apartados(on: (String) -> Boolean): List<TabEquipo> = when (rol) {
        Rol.Admin -> listOfNotNull(
            TabEquipo.Hoy, TabEquipo.Agenda.takeIf { on("agenda") }, TabEquipo.Pacientes.takeIf { on("ninos") },
            TabEquipo.Inteligencia.takeIf { on("inteligencia") }, TabEquipo.Cobros.takeIf { on("pagos") },
            TabEquipo.Reportes.takeIf { esJefe && on("reportes_financieros") }, TabEquipo.Recursos.takeIf { on("recursos_adicionales") },
            TabEquipo.Chat.takeIf { esJefe && on("chat_especialistas") }, TabEquipo.Usuarios, TabEquipo.Perfil,
        )
        Rol.Secretaria -> listOfNotNull(
            TabEquipo.Hoy, TabEquipo.Agenda.takeIf { on("agenda") }, TabEquipo.Cobros.takeIf { on("pagos") },
            TabEquipo.Reportes.takeIf { on("reportes_financieros") }, TabEquipo.Recursos.takeIf { on("recursos_adicionales") }, TabEquipo.Perfil,
        )
        else -> listOfNotNull(
            TabEquipo.Hoy, TabEquipo.Agenda.takeIf { on("agenda") }, TabEquipo.Pacientes.takeIf { on("ninos") },
            TabEquipo.Inteligencia.takeIf { on("inteligencia") }, TabEquipo.Chat, TabEquipo.Perfil,
        )
    }

    /** Barra inferior: los 4 primeros apartados de la web (dando prioridad al chat del jefe) y "Más" con el resto. */
    fun tabs(on: (String) -> Boolean): List<TabEquipo> {
        val todos = apartados(on) - TabEquipo.Perfil
        val fijos = todos.take(3) + listOfNotNull((if (TabEquipo.Chat in todos) TabEquipo.Chat else todos.getOrNull(3)))
        return fijos.distinct().take(4) + TabEquipo.Mas
    }

    val porCobrar get() = deudas.sumOf { it.saldo }
}

class EquipoViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val _e = MutableStateFlow(EstadoEquipo())
    val e: StateFlow<EstadoEquipo> = _e
    private var iniciado = false
    private var programasCache = mutableMapOf<String, List<Programa>>()

    fun iniciar(perfil: Perfil) {
        if (iniciado) return
        iniciado = true
        val rol = Rol.de(perfil.role)
        _e.update { it.copy(rol = rol, perfil = perfil, tab = TabEquipo.Hoy, soloMias = rol == Rol.Especialista) }
        viewModelScope.launch { cargar() }
    }

    private val miId get() = _e.value.perfil?.id

    private suspend fun cargar() {
        val rol = _e.value.rol
        try {
            coroutineScope {
                val hoy = async { RepoEquipo.citasDelDia(LocalDate.now().toString(), if (rol == Rol.Especialista) miId else null) }
                val moneda = async { RepoEquipo.moneda() }
                val pacientes = async { runCatching { RepoEquipo.pacientes() }.getOrDefault(emptyList()) }
                val deudas = async { if (rol == Rol.Secretaria || rol == Rol.Admin) runCatching { RepoEquipo.deudas() }.getOrDefault(emptyList()) else emptyList() }
                val cobrado = async { if (rol == Rol.Secretaria) runCatching { RepoEquipo.cobradoHoy() }.getOrDefault(0.0) else 0.0 }
                val metricas = async { if (rol == Rol.Admin) RepoEquipo.metricas() else null }
                val misEnvios = async { if (rol == Rol.Especialista && miId != null) runCatching { RepoEquipo.misEnvios(miId!!) }.getOrDefault(emptyList()) else emptyList() }
                val citasHoy = hoy.await()
                _e.update {
                    it.copy(
                        hoy = citasHoy, moneda = moneda.await(), pacientes = pacientes.await(), deudas = deudas.await(),
                        cobradoHoy = cobrado.await(), metricas = metricas.await(), 
                        misEnvios = misEnvios.await(), cargando = false,
                    )
                }
                AvisosEquipo.guardarHoy(getApplication(), citasHoy, programarRecordatorios = rol == Rol.Especialista)
            }
            cargarAgenda()
        } catch (ex: Exception) {
            _e.update { it.copy(cargando = false, aviso = L("Sin conexión. Desliza hacia abajo para reintentar.", "No connection. Pull down to retry.")) }
        }
    }

    fun refrescar() {
        _e.update { it.copy(refrescando = true) }
        viewModelScope.launch {
            cargar()
            _e.update { it.copy(refrescando = false) }
        }
    }

    fun irA(t: TabEquipo) = _e.update { it.copy(tab = t) }

    fun irAVista(v: String?) = when (v) {
        "agenda" -> irA(TabEquipo.Agenda)
        "cobros" -> irA(TabEquipo.Cobros)
        "chat" -> irA(TabEquipo.Chat)
        "hoy", "bandeja" -> irA(TabEquipo.Hoy)
        else -> Unit
    }

    // ── Agenda ──────────────────────────────────────────────────────────────
    fun elegirFecha(d: LocalDate) {
        _e.update { it.copy(fechaAgenda = d) }
        viewModelScope.launch { cargarAgenda() }
    }

    fun alternarSoloMias() {
        _e.update { it.copy(soloMias = !it.soloMias) }
        viewModelScope.launch { cargarAgenda() }
    }

    private suspend fun cargarAgenda() {
        val s = _e.value
        val lunes = s.fechaAgenda.with(java.time.DayOfWeek.MONDAY)
        val semana = runCatching { RepoEquipo.citasRango(lunes.toString(), lunes.plusDays(6).toString()) }.getOrNull() ?: return
        val visibles = semana.filter { !s.soloMias || s.rol != Rol.Especialista || it.especialistaId == miId }
        _e.update {
            it.copy(
                agenda = visibles.filter { c -> c.fecha.take(10) == it.fechaAgenda.toString() },
                conteoSemana = visibles.filter { c -> c.estado != EstadoCita.Cancelada }.groupingBy { c -> c.fecha.take(10) }.eachCount(),
            )
        }
    }

    /** Cambia el estado de una cita (optimista). Al registrar la última sesión del día: ¡día completado! */
    fun marcarCita(c: CitaEquipo, estado: EstadoCita) {
        fun aplicar(lista: List<CitaEquipo>, st: String?) = lista.map { if (it.id == c.id) it.copy(status = st) else it }
        val antes = c.status
        _e.update { it.copy(hoy = aplicar(it.hoy, estado.valor), agenda = aplicar(it.agenda, estado.valor)) }
        viewModelScope.launch {
            val ok = runCatching { RepoEquipo.cambiarEstado(c.id, estado) }.getOrDefault(false)
            if (!ok) {
                _e.update { it.copy(hoy = aplicar(it.hoy, antes), agenda = aplicar(it.agenda, antes), aviso = L("No se pudo actualizar la cita.", "Couldn't update the appointment.")) }
                return@launch
            }
            val hoy = _e.value.hoy
            AvisosEquipo.guardarHoy(getApplication(), hoy, programarRecordatorios = _e.value.rol == Rol.Especialista)
            val abiertas = hoy.count { it.estado == EstadoCita.Pendiente || it.estado == EstadoCita.Confirmada }
            val hechas = hoy.count { it.estado == EstadoCita.Realizada }
            if (estado == EstadoCita.Realizada && abiertas == 0 && hechas > 0 && _e.value.rol == Rol.Especialista) {
                _e.update { it.copy(festejo = Festejo.DiaCompleto(hechas)) }
            } else {
                _e.update { it.copy(aviso = when (estado) {
                    EstadoCita.Realizada -> L("¡Sesión registrada! ✓", "Session recorded! ✓")
                    EstadoCita.Confirmada -> L("Cita confirmada", "Appointment confirmed")
                    EstadoCita.Cancelada -> L("Cita cancelada", "Appointment cancelled")
                    EstadoCita.Pendiente -> L("Cita marcada como pendiente", "Marked as pending")
                }) }
            }
        }
    }

    // ── Pacientes ───────────────────────────────────────────────────────────
    suspend fun programasDe(childId: String): List<Programa> {
        programasCache[childId]?.let { return it }
        val lista = Repo.programas(childId).filter { !it.archivado }
        val final = if (EN) { val t = Repo.traducir(lista.flatMap { it.textos() }); lista.map { it.traducido(t) } } else lista
        programasCache[childId] = final
        return final
    }

    // ── Cobros ──────────────────────────────────────────────────────────────
    fun registrarAbono(p: Pago, monto: Double, metodo: String) {
        if (monto <= 0 || monto > p.saldo + 0.001) {
            _e.update { it.copy(aviso = L("Monto inválido: máximo ${"%.2f".format(p.saldo)}", "Invalid amount: max ${"%.2f".format(p.saldo)}")) }
            return
        }
        viewModelScope.launch {
            try {
                val saldada = RepoEquipo.registrarAbono(p, monto, metodo)
                _e.update { it.copy(festejo = Festejo.Cobro(monto, saldada)) }
                val (deudas, cobrado) = coroutineScope {
                    val d = async { RepoEquipo.deudas() }; val c = async { RepoEquipo.cobradoHoy() }
                    d.await() to c.await()
                }
                _e.update { it.copy(deudas = deudas, cobradoHoy = cobrado) }
            } catch (ex: Exception) {
                _e.update { it.copy(aviso = L("No se pudo registrar el cobro.", "Couldn't record the payment.")) }
            }
        }
    }

    /** Cobros del año (desde diciembre anterior, para comparar enero) y tarifas del centro. */
    fun cargarPagos() = viewModelScope.launch {
        val anio = LocalDate.now().year
        val (pagos, tarifas) = coroutineScope {
            val p = async { runCatching { RepoEquipo.pagos("${anio - 1}-12-01", "$anio-12-31") }.getOrDefault(emptyList()) }
            val t = async { runCatching { RepoEquipo.tarifas() }.getOrDefault(emptyList()) }
            p.await() to t.await()
        }
        _e.update { it.copy(pagosAnio = pagos, tarifas = tarifas) }
    }

    fun cambioDeIdioma() { programasCache.clear(); refrescar() }

    /** Solo para la pantalla de demostración de la versión debug (datos de ejemplo, sin red). */
    fun demo(estado: EstadoEquipo) { iniciado = true; _e.value = estado }

    fun cerrarFestejo() = _e.update { it.copy(festejo = null) }
    fun aviso(t: String) = _e.update { it.copy(aviso = t) }
    fun avisoMostrado() = _e.update { it.copy(aviso = null) }
}
