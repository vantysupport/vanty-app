package xyz.vanty.aba.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import xyz.vanty.aba.MainActivity
import xyz.vanty.aba.R
import xyz.vanty.aba.data.Prefs
import xyz.vanty.aba.util.L
import java.time.LocalDate

/**
 * Notificaciones al estilo Duolingo: el aviso entero pintado de azul Vanty con ARIA a la derecha (así se
 * reconoce la app por el color y la mascota, sin nombre), título con emoji que te llama por tu nombre y
 * texto con gancho (a veces con un poquito de drama).
 */
object Avisos {
    const val CANAL_RACHA = "racha"
    const val CANAL_CITAS = "citas"
    const val CANAL_LOGROS = "logros"
    const val EXTRA_VISTA = "vista"

    /** Poses de ARIA con fondo transparente (las de /public/aria de la web). */
    enum class Pose(@DrawableRes val img: Int) {
        // Como la guía "ARIA adaptada para Vanty ABA": saluda, motiva, celebra, piensa, recuerda, acompaña, logro, descanso
        SALUDO(R.drawable.aria_saluda), CELEBRA(R.drawable.aria_festeja), PENSANDO(R.drawable.aria_preocupada),
        FELIZ(R.drawable.aria_contenta), GUINO(R.drawable.aria_pulgar_arriba), CORRE(R.drawable.aria_corre),
        LAPTOP(R.drawable.aria_laptop_sentada), NEUTRAL(R.drawable.aria_atenta), CAFE(R.drawable.aria_cafe),
    }

    /** Color de fondo de cada tipo de aviso (y el del texto del botón blanco). */
    enum class Tema(@DrawableRes val fondo: Int, val boton: Int) {
        Azul(R.drawable.notif_azul, Color.parseColor("#0057C2")),
        Naranja(R.drawable.notif_naranja, Color.parseColor("#E8590C")),
        Rojo(R.drawable.notif_rojo, Color.parseColor("#C92A2A")),
        Verde(R.drawable.notif_verde, Color.parseColor("#0057C2")),
        Morado(R.drawable.notif_morado, Color.parseColor("#1E40AF")),
        Dorado(R.drawable.notif_dorado, Color.parseColor("#D9480F")),
    }

    fun crearCanales(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Nombres en el idioma elegido en la app; se vuelven a crear al cambiar de idioma (solo cambia el texto)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CANAL_RACHA, L("Racha y práctica en casa", "Streak and home practice"), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = L("Recordatorios para no perder la racha", "Reminders so you never lose your streak") },
            NotificationChannel(CANAL_CITAS, L("Citas", "Appointments"), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = L("Avisos antes de cada cita", "Alerts before each appointment") },
            NotificationChannel(CANAL_LOGROS, L("Logros", "Achievements"), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = L("Celebraciones de rachas y objetivos", "Streak and goal celebrations") },
        ))
    }

    fun permitidas(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun abrir(ctx: Context, vista: String, req: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_VISTA, vista)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun mostrar(
        ctx: Context, id: Int, canal: String, pose: Pose, titulo: String, texto: String,
        vista: String, accion: String? = null, insistente: Boolean = false, tema: Tema = Tema.Azul,
    ) {
        if (!permitidas(ctx)) return
        val toque = abrir(ctx, vista, id)
        // Igual que el aviso pintado de Duolingo: la notificación entera es un bloque azul de Vanty con
        // ARIA, sin la cabecera "Vanty ABA • ahora" (sin DecoratedCustomViewStyle). En Android 11 o
        // menos ocupa todo el aviso; desde Android 12 el sistema siempre añade su cabecera pequeña.
        fun pintada(layout: Int, cara: Boolean) = RemoteViews(ctx.packageName, layout).apply {
            setInt(R.id.raiz, "setBackgroundResource", tema.fondo)
            setTextViewText(R.id.titulo, titulo)
            setTextViewText(R.id.texto, texto)
            if (cara) setImageViewBitmap(R.id.aria, caraAria(ctx, pose)) else setImageViewResource(R.id.aria, pose.img)
        }
        val chica = pintada(R.layout.notif_aria, cara = true)
        val b = NotificationCompat.Builder(ctx, canal)
            .setSmallIcon(R.drawable.ic_stat_vanty)
            .setColor(ContextCompat.getColor(ctx, R.color.vanty_blue))
            .setLargeIcon(caraAria(ctx, pose)) // relojes y vistas que no muestran el diseño pintado
            .setContentTitle(titulo)
            .setContentText(texto)
            .setCustomContentView(chica)
            .setCustomBigContentView(pintada(R.layout.notif_aria_grande, cara = false))
            .setCustomHeadsUpContentView(chica)
            .setContentIntent(toque)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (canal == CANAL_CITAS) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
        if (insistente) b.setDefaults(NotificationCompat.DEFAULT_ALL)
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (_: SecurityException) { /* permiso revocado */ }
    }

    // ── Mensajes de familias ────────────────────────────────────────────────
    /**
     * ARIA como "foto de perfil" (como Duo en Duolingo): círculo celeste claro con su cara y hombros grandes.
     * Sobre el fondo azul del aviso se distingue bien (ARIA es azul: sin el círculo solo se veían las orejas).
     */
    private fun caraAria(ctx: Context, pose: Pose): Bitmap {
        val lado = 256
        val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = lado / 2f
        c.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EAF3FF") })
        val d = ContextCompat.getDrawable(ctx, pose.img) ?: return bmp
        c.save()
        c.clipPath(Path().apply { addCircle(r, r, r, Path.Direction.CW) })
        // 1.3× el círculo y un poco hacia abajo: entra la cabeza completa y se ve el torso con el logo
        val t = (lado * 1.3f).toInt()
        val x = (lado - t) / 2
        val y = (lado * 0.04f).toInt()
        d.setBounds(x, y, x + t, y + t)
        d.draw(c)
        c.restore()
        return bmp
    }

    private fun <T> deHoy(opciones: List<T>): T = opciones[LocalDate.now().dayOfYear % opciones.size]
    private fun yo(ctx: Context) = Prefs(ctx).nombreUsuario
    private fun conNombre(ctx: Context, sin: String, con: (String) -> String) = yo(ctx).let { if (it.isBlank()) sin else con(it) }

    private data class Msg(val pose: Pose, val tema: Tema, val titulo: String, val texto: String)

    /** Recordatorio de la tarde, según cuántos días lleva la racha. */
    fun recordatorioRacha(ctx: Context, dias: Int, hijo: String) {
        val quien = hijo.ifBlank { L("tu peque", "your little one") }
        val n = yo(ctx)
        val m = if (dias <= 0) deHoy(listOf(
            Msg(Pose.SALUDO, Tema.Azul, conNombre(ctx, L("👋 ¡Hola!", "👋 Hi there!")) { L("👋 ¡Hola, $it!", "👋 Hi, $it!") },
                L("¿5 minutitos con $quien? ARIA ya preparó la actividad de hoy.", "5 minutes with $quien? ARIA already prepared today's activity.")),
            Msg(Pose.PENSANDO, Tema.Azul, conNombre(ctx, L("🤔 ¿Me olvidaste?", "🤔 Did you forget me?")) { L("🤔 $it, ¿me olvidaste?", "🤔 $it, did you forget me?") },
                L("Hoy todavía no practican. ¡Una actividad cortita y encendemos la llama!", "No practice yet today. One short activity lights the flame!")),
            Msg(Pose.CORRE, Tema.Azul, L("🔥 ¿Empezamos una racha?", "🔥 Shall we start a streak?"),
                L("Practicar un poquito cada día con $quien hace magia. ¡Hoy es el día uno!", "A little practice every day with $quien works wonders. Today is day one!")),
            Msg(Pose.PENSANDO, Tema.Azul, conNombre(ctx, L("💔 Adiós...", "💔 Goodbye...")) { L("💔 Adiós, $it", "💔 Goodbye, $it") },
                L("Ya no practicas conmigo. Me doy cuenta. Dejaré de molestarte... (mentira, mañana vuelvo 🙃)", "You don't practice with me anymore. I get it. I'll stop bothering you... (just kidding, see you tomorrow 🙃)")),
            Msg(Pose.CAFE, Tema.Azul, L("☕ Te guardé un cafecito", "☕ I saved you a coffee"),
                L("Y una actividad de 5 minutos para $quien. El café se enfría, la actividad no. 😌", "And a 5-minute activity for $quien. The coffee gets cold, the activity doesn't. 😌")),
        )) else deHoy(listOf(
            Msg(Pose.PENSANDO, Tema.Naranja, if (n.isBlank()) L("😰 ¡Tu racha de $dias ${palabraDias(dias)}!", "😰 Your $dias-day streak!") else L("😰 ¡$n, tu racha de $dias ${palabraDias(dias)}!", "😰 $n, your $dias-day streak!"),
                L("Se apaga a medianoche. ¿Practicamos con $quien ahora?", "It goes out at midnight. Practice with $quien now?")),
            Msg(Pose.CORRE, Tema.Naranja, L("🔥 ¡No dejes que se apague!", "🔥 Don't let it go out!"),
                L("$dias ${palabraDias(dias)} seguidos con $quien. ¡Hoy suman uno más!", "$dias days in a row with $quien. Add one more today!")),
            Msg(Pose.GUINO, Tema.Naranja, conNombre(ctx, L("😩 ¿En serio?", "😩 Seriously?")) { L("😩 ¿En serio, $it?", "😩 Seriously, $it?") },
                L("¿Abriste el celular y no practicaste? 5 minutos con $quien y te dejo en paz.", "You opened your phone and didn't practice? 5 minutes with $quien and I'll leave you alone.")),
            Msg(Pose.PENSANDO, Tema.Naranja, L("🥺 Estas notificaciones no funcionan", "🥺 These reminders aren't working"),
                L("Y tu racha de $dias ${palabraDias(dias)} lo sabe. ¿Una práctica rapidita con $quien?", "And your $dias-day streak knows it. A quick practice with $quien?")),
        ))
        mostrar(ctx, ID_RACHA, CANAL_RACHA, m.pose, m.titulo, m.texto, "practicar", L("Practicar ahora", "Practice now"), tema = m.tema)
    }

    /** Último aviso de la noche, solo si hay una racha que perder. */
    fun ultimaOportunidad(ctx: Context, dias: Int, hijo: String) {
        val quien = hijo.ifBlank { L("tu peque", "your little one") }
        mostrar(ctx, ID_RACHA, CANAL_RACHA, Pose.PENSANDO,
            conNombre(ctx, L("🚨 ¡Última oportunidad!", "🚨 Last chance!")) { L("🚨 ¡$it, última oportunidad!", "🚨 $it, last chance!") },
            L("Tu racha de $dias ${palabraDias(dias)} con $quien desaparece en unas horas. ¡Sálvala!", "Your $dias-day streak with $quien disappears in a few hours. Save it!"),
            "practicar", L("Salvar mi racha", "Save my streak"), insistente = true, tema = Tema.Rojo)
    }

    fun celebrarRacha(ctx: Context, dias: Int) {
        mostrar(ctx, ID_LOGRO, CANAL_LOGROS, Pose.CELEBRA,
            conNombre(ctx, L("🎉 ¡$dias ${palabraDias(dias)} seguidos!", "🎉 $dias days in a row!")) { L("🎉 ¡$dias ${palabraDias(dias)} seguidos, $it!", "🎉 $dias days in a row, $it!") },
            L("Qué constancia. ARIA está orgullosa de ustedes. 💙", "What consistency. ARIA is proud of you. 💙"),
            "inicio", L("Ver mi racha", "See my streak"), tema = Tema.Verde)
    }

    fun citaManana(ctx: Context, citaId: String, hora: String, hijo: String) {
        mostrar(ctx, ID_CITA + (citaId.hashCode() and 0xFFF), CANAL_CITAS, Pose.LAPTOP,
            L("📅 ¡Mañana hay cita!", "📅 Appointment tomorrow!"),
            L("${hijo.ifBlank { "Tu peque" }} tiene sesión mañana${if (hora.isNotBlank()) " a las $hora" else ""}. ¡Nos vemos!",
                "${hijo.ifBlank { "Your child" }} has a session tomorrow${if (hora.isNotBlank()) " at $hora" else ""}. See you there!"),
            "citas", L("Ver cita", "View"), tema = Tema.Morado)
    }

    fun citaPronto(ctx: Context, citaId: String, hora: String, hijo: String) {
        mostrar(ctx, ID_CITA + (citaId.hashCode() and 0xFFF), CANAL_CITAS, Pose.CORRE,
            L("⏰ ¡La cita es pronto!", "⏰ Appointment soon!"),
            L("La sesión de ${hijo.ifBlank { "tu peque" }} es a las $hora. ¡A prepararse!", "${hijo.ifBlank { "Your child" }}'s session is at $hora. Get ready!"),
            "citas", L("Ver cita", "View"), insistente = true, tema = Tema.Morado)
    }

    fun prueba(ctx: Context) = mostrar(ctx, ID_PRUEBA, CANAL_LOGROS, Pose.SALUDO,
        conNombre(ctx, L("👋 ¡Hola! Soy ARIA", "👋 Hi! I'm ARIA")) { L("👋 ¡Hola, $it! Soy ARIA", "👋 Hi, $it! I'm ARIA") },
        L("Así te voy a avisar. Prometo ser insistente solo cuando valga la pena. 😉", "This is how I'll remind you. I promise to nag only when it's worth it. 😉"),
        "inicio", L("¡Entendido!", "Got it!"))

    fun palabraDias(n: Int) = if (n == 1) "día" else "días"

    private const val ID_RACHA = 100
    private const val ID_LOGRO = 200
    private const val ID_CITA = 300
    private const val ID_PRUEBA = 900
}
