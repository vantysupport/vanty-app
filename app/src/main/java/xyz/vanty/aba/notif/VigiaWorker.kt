package xyz.vanty.aba.notif

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import xyz.vanty.aba.data.EstadoCita
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.data.RepoEquipo
import xyz.vanty.aba.data.Rol
import xyz.vanty.aba.data.Repo
import xyz.vanty.aba.util.hora12
import xyz.vanty.aba.util.hoyIso
import xyz.vanty.aba.widget.RachaWidget
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Corre cada hora en segundo plano (aunque la app esté cerrada):
 *  • actualiza la racha del widget (y la "apaga" al cambiar el día),
 *  • manda el recordatorio de práctica a la hora elegida y un último aviso a las 21 h,
 *  • avisa de las citas la tarde anterior y ~2 h antes.
 */
class VigiaWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val p = Prefs(ctx)
        val hoy = hoyIso()
        try {
            if (!Repo.haySesion()) return Result.success()
            val rol = Rol.de(p.rol)
            if (rol == Rol.Especialista || rol == Rol.Secretaria || rol == Rol.Admin) {
                vigilarEquipo(ctx, p, rol)
                return Result.success()
            }
            val hijoId = p.hijoId ?: return Result.success()

            val racha = Repo.racha(hijoId)
            if (racha != null) {
                p.racha = racha
                p.rachaFecha = hoy
            }
            p.usuarioId?.let { p.sinLeer = xyz.vanty.aba.data.Comun.sinLeer(it) }
            runCatching { Repo.citasProximas(hijoId) }.getOrNull()?.let { l ->
                p.proximaCita = l.firstOrNull()?.let { "${it.fecha.take(10)}|${it.hora?.take(5).orEmpty()}" }
                p.citasFamilia = xyz.vanty.aba.widget.lineasCitas(l, p.nombreHijo.substringBefore(' '))
            }
            xyz.vanty.aba.widget.actualizarWidgets(ctx)

            val hora = LocalTime.now().hour
            if (p.recordatorioActivo && racha != null && !racha.hoy) {
                if (hora >= p.recordatorioHora && hora < 22 && !p.yaAvisado("racha_$hoy")) {
                    Avisos.recordatorioRacha(ctx, racha.dias, p.nombreHijo)
                    p.marcarAvisado("racha_$hoy")
                } else if (racha.dias > 0 && hora in 21..22 && p.recordatorioHora < 21 && !p.yaAvisado("ultima_$hoy")) {
                    Avisos.ultimaOportunidad(ctx, racha.dias, p.nombreHijo)
                    p.marcarAvisado("ultima_$hoy")
                }
            }

            if (p.avisosCitas) avisarCitas(ctx, p)
        } catch (_: Exception) {
            // Sin conexión o sesión vencida: se reintenta en la próxima hora
        }
        return Result.success()
    }

    /**
     * Equipo del centro:
     *  • 7–10 h: resumen del día (una vez),
     *  • especialista: programa el aviso 15 min antes de cada sesión y, desde las 18 h, recuerda las sesiones sin registrar,
     *  • secretaría: a partir de las 17 h, los cobros pendientes.
     */
    private suspend fun vigilarEquipo(ctx: Context, p: Prefs, rol: Rol) {
        val hoy = hoyIso()
        val hora = LocalTime.now().hour
        val yo = if (rol == Rol.Especialista) p.usuarioId else null
        val citas = RepoEquipo.citasDelDia(hoy, yo)
        AvisosEquipo.guardarHoy(ctx, citas, programarRecordatorios = rol == Rol.Especialista)
        val activas = citas.filter { it.estado != EstadoCita.Cancelada }
        p.usuarioId?.let { p.sinLeer = xyz.vanty.aba.data.Comun.sinLeer(it) }
        xyz.vanty.aba.widget.actualizarWidgets(ctx)

        if (p.resumenDiario && hora in 7..10 && !p.yaAvisado("resumen_$hoy")) {
            when (rol) {
                Rol.Especialista -> AvisosEquipo.resumenEspecialista(ctx, activas.size, activas.firstOrNull { it.estado != EstadoCita.Realizada })
                Rol.Secretaria -> AvisosEquipo.resumenSecretaria(ctx, activas.size, activas.count { it.estado == EstadoCita.Pendiente })
                else -> {
                    val m = RepoEquipo.metricas()
                    AvisosEquipo.resumenAdmin(ctx, m?.hoy?.sesiones?.total ?: activas.size, m?.alertas?.urgentes ?: 0)
                }
            }
            p.marcarAvisado("resumen_$hoy")
        }

        if (rol == Rol.Especialista && hora in 18..21 && !p.yaAvisado("sinregistrar_$hoy")) {
            val ahora = LocalTime.now()
            val pasadas = activas.count { c ->
                (c.estado == EstadoCita.Pendiente || c.estado == EstadoCita.Confirmada) &&
                    (c.hora?.take(5)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }?.isBefore(ahora) ?: false)
            }
            if (pasadas > 0) {
                AvisosEquipo.sinRegistrar(ctx, pasadas)
                p.marcarAvisado("sinregistrar_$hoy")
            }
        }

        if (rol == Rol.Secretaria && p.resumenDiario && hora in 17..19 && !p.yaAvisado("cobros_$hoy")) {
            val deudas = RepoEquipo.deudas()
            if (deudas.isNotEmpty()) AvisosEquipo.cobrosPendientes(ctx, deudas.size, deudas.sumOf { it.saldo }, RepoEquipo.moneda())
            p.marcarAvisado("cobros_$hoy")
        }
    }

    private suspend fun avisarCitas(ctx: Context, p: Prefs) {
        val ahora = LocalDateTime.now()
        val manana = LocalDate.now().plusDays(1).toString()
        for (h in p.hijos) {
            for (c in Repo.citasProximas(h.id)) {
                val hora = c.hora?.take(5)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(9, 0)
                val cuando = runCatching { LocalDate.parse(c.fecha.take(10)).atTime(hora) }.getOrNull() ?: continue
                val minutos = Duration.between(ahora, cuando).toMinutes()
                if (minutos in 0..150 && !p.yaAvisado("pronto_${c.id}")) {
                    Avisos.citaPronto(ctx, c.id, hora12(c.hora), h.primerNombre)
                    p.marcarAvisado("pronto_${c.id}")
                } else if (c.fecha.take(10) == manana && ahora.hour >= 18 && !p.yaAvisado("dia_${c.id}")) {
                    Avisos.citaManana(ctx, c.id, hora12(c.hora), h.primerNombre)
                    p.marcarAvisado("dia_${c.id}")
                }
            }
        }
    }

    companion object {
        fun programar(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<VigiaWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("vigia", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancelar(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork("vigia")
    }
}
