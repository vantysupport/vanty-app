package xyz.vanty.aba.notif

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import xyz.vanty.aba.data.AgendaHoy
import xyz.vanty.aba.data.CitaEquipo
import xyz.vanty.aba.data.EstadoCita
import xyz.vanty.aba.data.ItemAgenda
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.notif.Avisos.Pose
import xyz.vanty.aba.notif.Avisos.Tema
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.hora12
import xyz.vanty.aba.util.monedaFmt
import xyz.vanty.aba.widget.ResumenWidget
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Avisos del equipo del centro, con el mismo estilo ilustrado de ARIA que los de las familias. */
object AvisosEquipo {
    private const val ID_RESUMEN = 400
    private const val ID_SESION = 500
    private const val ID_PENDIENTES = 600
    private const val ID_COBROS = 700
    private const val ID_LOGRO = 800

    /** Guarda la agenda de hoy para el widget y programa el aviso 15 min antes de cada sesión propia. */
    suspend fun guardarHoy(ctx: Context, citas: List<CitaEquipo>, programarRecordatorios: Boolean) {
        val p = Prefs(ctx)
        val activas = citas.filter { it.estado != EstadoCita.Cancelada }
        p.agendaHoy = AgendaHoy(
            fecha = LocalDate.now().toString(),
            total = activas.size,
            hechas = activas.count { it.estado == EstadoCita.Realizada },
            citas = activas.map { ItemAgenda(it.id, it.hora?.take(5).orEmpty(), it.paciente, it.estado.valor) },
        )
        ResumenWidget.actualizar(ctx)
        xyz.vanty.aba.widget.RachaWidget.actualizar(ctx)
        if (programarRecordatorios && p.avisosCitas) programarSesiones(ctx, activas)
    }

    private fun programarSesiones(ctx: Context, citas: List<CitaEquipo>) {
        val wm = WorkManager.getInstance(ctx)
        val ahora = LocalDateTime.now()
        citas.filter { it.estado == EstadoCita.Pendiente || it.estado == EstadoCita.Confirmada }.forEach { c ->
            val hora = c.hora?.take(5)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return@forEach
            val cuando = runCatching { LocalDate.parse(c.fecha.take(10)).atTime(hora).minusMinutes(15) }.getOrNull() ?: return@forEach
            val espera = Duration.between(ahora, cuando).toMillis()
            if (espera < 0) return@forEach
            val req = OneTimeWorkRequestBuilder<SesionWorker>()
                .setInitialDelay(espera, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("id" to c.id, "paciente" to c.paciente, "hora" to hora12(c.hora), "servicio" to (c.servicio ?: "")))
                .build()
            wm.enqueueUniqueWork("sesion_${c.id}", ExistingWorkPolicy.REPLACE, req)
        }
    }

    // ── Mensajes (pintados como los de las familias, con tu nombre) ─────────
    private fun hola(ctx: Context, es: String, en: String, conEs: (String) -> String, conEn: (String) -> String) =
        Prefs(ctx).nombreUsuario.let { if (it.isBlank()) L(es, en) else L(conEs(it), conEn(it)) }

    fun resumenEspecialista(ctx: Context, n: Int, primera: CitaEquipo?) = Avisos.mostrar(
        ctx, ID_RESUMEN, Avisos.CANAL_CITAS, if (n > 0) Pose.SALUDO else Pose.FELIZ,
        if (n > 0) hola(ctx, "☀️ ¡Buenos días! Hoy: $n ${ses(n)}", "☀️ Good morning! Today: $n session${if (n == 1) "" else "s"}",
            { "☀️ ¡Buenos días, $it! Hoy: $n ${ses(n)}" }, { "☀️ Good morning, $it! Today: $n session${if (n == 1) "" else "s"}" })
        else L("☕ Hoy no tienes sesiones", "☕ No sessions today"),
        if (primera != null) L("La primera es a las ${hora12(primera.hora)} con ${primera.paciente}. ¡Tú puedes! 💪", "First one at ${hora12(primera.hora)} with ${primera.paciente}. You've got this! 💪")
        else L("Aprovecha para preparar materiales o revisar programas.", "A good time to prepare materials or review programs."),
        "hoy", L("Ver mi día", "See my day"), tema = Tema.Azul,
    )

    fun resumenSecretaria(ctx: Context, n: Int, sinConfirmar: Int) = Avisos.mostrar(
        ctx, ID_RESUMEN, Avisos.CANAL_CITAS, Pose.LAPTOP,
        hola(ctx, "📅 Hoy hay $n ${if (n == 1) "cita" else "citas"}", "📅 $n appointment${if (n == 1) "" else "s"} today",
            { "📅 $it, hoy hay $n ${if (n == 1) "cita" else "citas"}" }, { "📅 $it, $n appointment${if (n == 1) "" else "s"} today" }),
        if (sinConfirmar > 0) L("$sinConfirmar todavía sin confirmar. ¡Un par de llamadas y listo! 📞", "$sinConfirmar still unconfirmed. A couple of calls and you're done! 📞")
        else L("Todas confirmadas. ¡Organización nivel experto! ✨", "All confirmed. Expert-level organization! ✨"),
        "agenda", L("Abrir agenda", "Open agenda"), tema = Tema.Morado,
    )

    fun resumenAdmin(ctx: Context, sesiones: Int, urgentes: Int) = Avisos.mostrar(
        ctx, ID_RESUMEN, Avisos.CANAL_CITAS, if (urgentes > 0) Pose.PENSANDO else Pose.SALUDO,
        if (urgentes > 0) hola(ctx, "🚨 $urgentes ${if (urgentes == 1) "alerta urgente" else "alertas urgentes"}", "🚨 $urgentes urgent alert${if (urgentes == 1) "" else "s"}",
            { "🚨 $it, hay $urgentes ${if (urgentes == 1) "alerta urgente" else "alertas urgentes"}" }, { "🚨 $it, $urgentes urgent alert${if (urgentes == 1) "" else "s"}" })
        else hola(ctx, "☀️ Tu centro hoy", "☀️ Your center today", { "☀️ ¡Buenos días, $it!" }, { "☀️ Good morning, $it!" }),
        buildList {
            add(L("$sesiones ${ses(sesiones)} hoy", "$sesiones session${if (sesiones == 1) "" else "s"} today"))
            if (urgentes == 0) add(L("todo en orden ✨", "all good ✨"))
        }.joinToString(" · "),
        "hoy", L("Abrir", "Open"),
        tema = if (urgentes > 0) Tema.Rojo else Tema.Azul,
    )

    fun sesionPronto(ctx: Context, id: String, paciente: String, hora: String, servicio: String) = Avisos.mostrar(
        ctx, ID_SESION + (id.hashCode() and 0xFFF), Avisos.CANAL_CITAS, Pose.CORRE,
        L("⏰ En 15 min: $paciente", "⏰ In 15 min: $paciente"),
        listOf(hora, servicio).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { L("¡Prepara los materiales!", "Get your materials ready!") } + " 🧩",
        "hoy", L("Ver sesión", "View session"), insistente = true, tema = Tema.Morado,
    )

    fun sinRegistrar(ctx: Context, n: Int) = Avisos.mostrar(
        ctx, ID_PENDIENTES, Avisos.CANAL_RACHA, Pose.GUINO,
        hola(ctx, "😅 ¿Y esas sesiones?", "😅 What about those sessions?", { "😅 $it, ¿y esas sesiones?" }, { "😅 $it, what about those sessions?" }),
        L("Te ${if (n == 1) "falta registrar 1 sesión" else "faltan registrar $n sesiones"} de hoy. ¡Ciérralas y el día queda al 100 %!",
            "$n session${if (n == 1) "" else "s"} from today not recorded. Close them and your day hits 100%!"),
        "hoy", L("Registrar ahora", "Record now"), tema = Tema.Naranja,
    )

    fun cobrosPendientes(ctx: Context, n: Int, total: Double, moneda: String) = Avisos.mostrar(
        ctx, ID_COBROS, Avisos.CANAL_RACHA, Pose.PENSANDO,
        L("💰 ${monedaFmt(total, moneda)} por cobrar", "💰 ${monedaFmt(total, moneda)} to collect"),
        L("$n ${if (n == 1) "cobro pendiente" else "cobros pendientes"}. ¿Cerramos algunos antes de irte?", "$n pending payment${if (n == 1) "" else "s"}. Close a few before you leave?"),
        "cobros", L("Ver cobros", "View payments"), tema = Tema.Dorado,
    )

    fun diaCompleto(ctx: Context, n: Int) = Avisos.mostrar(
        ctx, ID_LOGRO, Avisos.CANAL_LOGROS, Pose.CELEBRA,
        hola(ctx, "🎉 ¡Día completado!", "🎉 Day complete!", { "🎉 ¡Día completado, $it!" }, { "🎉 Day complete, $it!" }),
        L("Registraste tus $n ${ses(n)} de hoy. ARIA te aplaude 👏", "You recorded all $n sessions today. ARIA applauds you 👏"),
        "hoy", L("Ver mi día", "See my day"), tema = Tema.Verde,
    )

    private fun ses(n: Int) = if (n == 1) "sesión" else "sesiones"
}

/** Se dispara 15 minutos antes de cada sesión del especialista. */
class SesionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val d = inputData
        val id = d.getString("id") ?: return Result.success()
        // Si ya la cancelaron o registraron, no se avisa
        val sigue = Prefs(applicationContext).agendaHoy?.citas?.firstOrNull { it.id == id }
        if (sigue != null && (sigue.estado == "completed" || sigue.estado == "cancelled")) return Result.success()
        AvisosEquipo.sesionPronto(applicationContext, id, d.getString("paciente").orEmpty(), d.getString("hora").orEmpty(), d.getString("servicio").orEmpty())
        return Result.success()
    }
}
