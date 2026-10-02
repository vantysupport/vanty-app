package xyz.vanty.aba

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xyz.vanty.aba.data.Actividad
import xyz.vanty.aba.data.CitaEquipo
import xyz.vanty.aba.data.DiaRacha
import xyz.vanty.aba.data.Envio
import xyz.vanty.aba.data.Hijo
import xyz.vanty.aba.data.Metricas
import xyz.vanty.aba.data.MetAlertas
import xyz.vanty.aba.data.MetFin
import xyz.vanty.aba.data.MetHoy
import xyz.vanty.aba.data.MetPacientes
import xyz.vanty.aba.data.MetSesiones
import xyz.vanty.aba.data.MetTareas
import xyz.vanty.aba.data.NombreRef
import xyz.vanty.aba.data.Paciente
import xyz.vanty.aba.data.Pago
import xyz.vanty.aba.data.Perfil
import xyz.vanty.aba.data.PerfilRef
import xyz.vanty.aba.data.Plan
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.Racha
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.data.Stats
import xyz.vanty.aba.notif.Avisos
import xyz.vanty.aba.notif.AvisosEquipo
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.Fase
import xyz.vanty.aba.ui.MainScreen
import xyz.vanty.aba.ui.Pestana
import xyz.vanty.aba.ui.equipo.EquipoScreen
import xyz.vanty.aba.ui.equipo.EquipoViewModel
import xyz.vanty.aba.ui.equipo.EstadoEquipo
import xyz.vanty.aba.ui.equipo.Festejo
import xyz.vanty.aba.ui.equipo.TabEquipo
import xyz.vanty.aba.ui.theme.VantyTheme
import xyz.vanty.aba.widget.RachaWidget
import java.time.LocalDate

/**
 * SOLO EN DEBUG (no existe en la versión de Google Play). Muestra cada rol con datos de ejemplo, sin red,
 * para revisar diseño y animaciones:
 *   adb shell am start -n xyz.vanty.aba/.DemoActivity --es rol especialista --es tab Hoy
 * rol: padre | especialista | secretaria | admin · tab: nombre de la pestaña · festejo: dia | cobro | bandeja | racha
 * avisos: true → lanza las notificaciones de ejemplo y llena los widgets.
 */
class DemoActivity : ComponentActivity() {
    /** Con --es pin racha|resumen, pide al sistema poner ese widget en la pantalla de inicio. */
    private fun anclar() {
        val cual = intent.getStringExtra("pin") ?: return
        val clase = if (cual == "resumen") xyz.vanty.aba.widget.ResumenWidgetReceiver::class.java else xyz.vanty.aba.widget.RachaWidgetReceiver::class.java
        val am = android.appwidget.AppWidgetManager.getInstance(this)
        if (am.isRequestPinAppWidgetSupported) am.requestPinAppWidget(android.content.ComponentName(this, clase), null, null)
    }

    private val appVm: AppViewModel by viewModels()
    private val equipoVm: EquipoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val rol = intent.getStringExtra("rol") ?: "especialista"
        val tab = intent.getStringExtra("tab")
        val festejo = intent.getStringExtra("festejo")
        val hoy = LocalDate.now().toString()

        val citas = listOf(
            CitaEquipo("c1", hoy, "08:30", "completed", "Terapia ABA", children = NombreRef("Mateo Rojas")),
            CitaEquipo("c2", hoy, "10:00", "completed", "Lenguaje", children = NombreRef("Valentina Cruz")),
            CitaEquipo("c3", hoy, "11:30", "confirmed", "Terapia ABA", children = NombreRef("Santiago Díaz")),
            CitaEquipo("c4", hoy, "15:00", "pending", "Terapia ocupacional", modalidad = "virtual", children = NombreRef("Lucía Paredes")),
            CitaEquipo("c5", hoy, "16:30", "cancelled", "Terapia ABA", children = NombreRef("Diego Flores")),
        )

        if (rol == "padre") {
            val semana = (6 downTo 0).map { DiaRacha(LocalDate.now().minusDays(it.toLong()).toString(), it != 3 && it != 0) }
            val r = Racha(dias = 2, hoy = festejo == "racha", semana = semana)
            val estado = Estado(
                fase = Fase.App, pestana = runCatching { Pestana.valueOf(tab ?: "Inicio") }.getOrDefault(Pestana.Inicio),
                perfil = Perfil("p", "familia@demo.com", "Carla Rojas", "padre"),
                hijos = listOf(Hijo("h1", "Mateo Rojas", "2019-03-10", "TEA nivel 1")), hijo = Hijo("h1", "Mateo Rojas", "2019-03-10", "TEA nivel 1"),
                racha = r, stats = Stats(totalSesiones = 34, totalGoals = 18, goalsAchieved = 11, masteryRate = 61, hoursTotal = 25.5, level = "Intermedio"),
                plan = Plan("pl", "¡Esta semana Mateo va a brillar con estas actividades!", listOf(
                    Actividad("Pedir con palabras", "Durante la merienda, espera a que Mateo pida lo que quiere.", "10", "facil", "comunicacion", "• Galletas\n• Jugo", "Refuerza la comunicación funcional.", completada = true),
                    Actividad("Imitar movimientos", "Juega a 'Simón dice' con 5 movimientos.", "15", "media", "habilidades"),
                    Actividad("Turnos con un juego", "Arma una torre por turnos.", "10", "facil", "socializacion"),
                )),
                celebrar = if (festejo == "racha") 3 else null,
            )
            appVm.demo(estado)
            setContent { VantyTheme { val e by appVm.e.collectAsState(); MainScreen(e.copy(fase = Fase.App), appVm) } }
            if (intent.getBooleanExtra("avisos", false)) lifecycleScope.launch {
                Prefs(this@DemoActivity).apply {
                    racha = r; rachaFecha = hoy; nombreHijo = "Mateo Rojas"; hijoId = "h1"; this.rol = "padre"
                    usuarioId = "demo"; nombreUsuario = "Carla"; sinLeer = 2
                    proximaCita = "${LocalDate.now().plusDays(1)}|15:00"
                }
                RachaWidget.actualizar(this@DemoActivity)
                xyz.vanty.aba.widget.ResumenWidget.actualizar(this@DemoActivity)
                anclar()
                Avisos.recordatorioRacha(this@DemoActivity, 2, "Mateo")
            }
            return
        }

        val r = Rol.de(if (rol == "admin") "jefe" else rol)
        val perfil = Perfil("yo", "equipo@demo.com", when (r) { Rol.Admin -> "Andrea Salas"; Rol.Secretaria -> "Rosa Medina"; else -> "Lic. Paula Vega" }, if (rol == "admin") "jefe" else rol)
        val estadoEq = EstadoEquipo(
            rol = r, perfil = perfil,
            tab = runCatching { TabEquipo.valueOf(tab ?: "") }.getOrDefault(TabEquipo.Hoy),
            hoy = citas, agenda = citas, conteoSemana = mapOf(hoy to 4, LocalDate.now().plusDays(1).toString() to 6, LocalDate.now().minusDays(1).toString() to 2),
            pacientes = listOf(Paciente("1", "Mateo Rojas", "2019-03-10", "TEA nivel 1"), Paciente("2", "Valentina Cruz", "2018-07-02", "TDAH"),
                Paciente("3", "Santiago Díaz", "2020-01-15", "Retraso del lenguaje"), Paciente("4", "Lucía Paredes", "2017-11-30", "TEA nivel 2")),
            deudas = listOf(Pago("d1", 120.0, null, "pending", "efectivo", "Sesión de terapia", "${hoy}T10:00:00Z", children = NombreRef("Mateo Rojas")),
                Pago("d2", 480.0, 200.0, "partial", "yape", "Paquete 4 sesiones", "${hoy}T09:00:00Z", children = NombreRef("Valentina Cruz")),
                Pago("d3", 90.0, null, "pending", "efectivo", "Evaluación", "${hoy}T08:00:00Z", externo = "Paciente externo")),
            cobradoHoy = 350.0, moneda = "PEN",
            metricas = Metricas(MetHoy(MetSesiones(12, 7, 1, 4), 92), MetPacientes(48, 3, 64.0), MetAlertas(5, 2), MetTareas(71, 4), MetFin(18450.0, 3)),
            misEnvios = listOf(Envio("m1", "Informe mensual", status = "approved", children = NombreRef("Mateo Rojas")), Envio("m2", "Evaluación inicial", status = "pending", children = NombreRef("Lucía Paredes"))),
            cargando = false,
            festejo = when (festejo) { "dia" -> Festejo.DiaCompleto(4); "cobro" -> Festejo.Cobro(120.0, true); else -> null },
        )
        val semana = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }.zip(listOf(9, 11, 8, 12, 10, 4, 7))
        val proximas = citas.filter { it.status != "cancelled" && it.status != "completed" }
        xyz.vanty.aba.data.Inicios.demoAdmin = xyz.vanty.aba.data.InicioAdmin(
            totalPacientes = 22, sesionesHoy = 7, realizadasHoy = 2, sinSesion = 3, programasAba = 14, semana = semana,
            programasActivos = listOf(
                xyz.vanty.aba.data.ProgramaActivo("p1", "Mandos de un paso", "Mateo Rojas", 85, 80),
                xyz.vanty.aba.data.ProgramaActivo("p2", "Imitación motora", "Valentina Cruz", 60, 90),
                xyz.vanty.aba.data.ProgramaActivo("p3", "Contacto visual", "Santiago Díaz", null, 90)),
            alertas = listOf(
                xyz.vanty.aba.data.AlertaClinica("a1", "regresion", "c1", "Mateo Rojas", "Bajó de 80 % a 55 % en mandos en las últimas 3 sesiones.", 1),
                xyz.vanty.aba.data.AlertaClinica(null, "sin_sesion", "c2", "Diego Flores", "Sin sesión en los últimos 30 días.", 2),
                xyz.vanty.aba.data.AlertaClinica("a3", "criterio_alcanzado", "c3", "Valentina Cruz", "Alcanzó el criterio en Imitación motora.", 3)),
            proximas = proximas, alertasUrgentes = 1,
        )
        xyz.vanty.aba.data.Inicios.demoEspecialista = xyz.vanty.aba.data.InicioEspecialista(
            pacientes = 9, citas7 = 18, evaluaciones = 6, enRevision = 2, ultimaSesion = hoy, sinSesion = 1, semana = semana,
            citasHoy = citas, recientes = listOf(Envio("m1", "Informe mensual", status = "approved", children = NombreRef("Mateo Rojas")), Envio("m2", "Evaluación inicial", status = "pending_approval", children = NombreRef("Lucía Paredes"))),
            pacientesRecientes = listOf(Paciente("c1", "Mateo Rojas", "2019-04-02"), Paciente("c2", "Valentina Cruz", "2020-08-15")),
        )
        xyz.vanty.aba.data.Inicios.demoSecretaria = xyz.vanty.aba.data.InicioSecretaria(
            hoy = 4, pendientes = 3, canceladas = 2, completadas = 41, pacientes = 22, semana = semana, citasHoy = citas, proximas = proximas,
        )
        val manana = LocalDate.now().plusDays(1).toString()
        val ahora = java.time.LocalTime.now().minusMinutes(20).withSecond(0).toString().take(5)
        xyz.vanty.aba.data.RepoAgenda.demoEspecialistas = listOf(
            xyz.vanty.aba.data.Especialista("e1", "Lic. Paula Vega", "Lenguaje"), xyz.vanty.aba.data.Especialista("e2", "Lic. Jorge Ríos", "ABA"))
        xyz.vanty.aba.data.RepoAgenda.demo = citas.map { it.copy(especialistaId = "e1", specialist = PerfilRef("Lic. Paula Vega", "Lenguaje")) } + listOf(
            CitaEquipo("c6", hoy, ahora, "confirmed", "Terapia ABA", children = NombreRef("Camila Soto"), especialistaId = "e2", specialist = PerfilRef("Lic. Jorge Ríos", "ABA")),
            CitaEquipo("c7", manana, "10:00", "confirmed", "Terapia ABA", children = NombreRef("Mateo Rojas"),
                metadata = kotlinx.serialization.json.buildJsonObject {
                    put("reprogramacion", kotlinx.serialization.json.buildJsonObject {
                        put("estado", kotlinx.serialization.json.JsonPrimitive("solicitada")); put("fecha", kotlinx.serialization.json.JsonPrimitive(LocalDate.now().plusDays(3).toString()))
                        put("hora", kotlinx.serialization.json.JsonPrimitive("16:00")); put("motivo", kotlinx.serialization.json.JsonPrimitive("Tenemos control médico"))
                    })
                }),
            CitaEquipo("c8", manana, "15:00", "pending", "Evaluación Inicial", modalidad = "virtual", children = NombreRef("Lucía Paredes"), grupal = false),
        )
        equipoVm.demo(estadoEq)
        val app = Estado(fase = Fase.Equipo, perfil = perfil)
        setContent { VantyTheme { EquipoScreen(app, appVm) } }

        if (intent.getBooleanExtra("avisos", false)) lifecycleScope.launch {
            Prefs(this@DemoActivity).apply { this.rol = perfil.role.orEmpty(); usuarioId = "demo"; nombreUsuario = "Paula"; sinLeer = 3 }
            AvisosEquipo.guardarHoy(this@DemoActivity, citas, programarRecordatorios = false)
            when (r) {
                Rol.Especialista -> { AvisosEquipo.resumenEspecialista(this@DemoActivity, 4, citas[2]); AvisosEquipo.sesionPronto(this@DemoActivity, "c4", "Lucía Paredes", "3:00 PM", "Terapia ocupacional") }
                Rol.Secretaria -> { AvisosEquipo.resumenSecretaria(this@DemoActivity, 12, 3); AvisosEquipo.cobrosPendientes(this@DemoActivity, 3, 490.0, "PEN") }
                else -> AvisosEquipo.resumenAdmin(this@DemoActivity, 12, 2)
            }
            anclar()
        }
    }
}
