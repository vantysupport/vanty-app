package xyz.vanty.aba

import android.content.Intent
import io.github.jan.supabase.auth.handleDeeplinks
import xyz.vanty.aba.data.Backend
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.vanty.aba.notif.Avisos
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.CargandoScreen
import xyz.vanty.aba.ui.Fase
import xyz.vanty.aba.ui.LoginScreen
import xyz.vanty.aba.ui.MainScreen
import xyz.vanty.aba.ui.SoloFamiliasScreen
import xyz.vanty.aba.ui.RequisitosScreen
import xyz.vanty.aba.ui.CentroInactivoScreen
import xyz.vanty.aba.ui.BloqueoLimiteScreen
import xyz.vanty.aba.ui.equipo.EquipoScreen
import xyz.vanty.aba.ui.theme.VantyTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // La pantalla de arranque se queda hasta saber si hay sesión (sin parpadeo del login)
        splash.setKeepOnScreenCondition { vm.e.value.fase == Fase.Cargando }
        enableEdgeToEdge()
        abrirVista(intent)
        volverDeOAuth(intent)

        setContent {
            val sistemaOscuro = androidx.compose.foundation.isSystemInDarkTheme()
            val oscuro = when (xyz.vanty.aba.util.TemaApp.actual) { "dark" -> true; "light" -> false; else -> sistemaOscuro }
            // Íconos de la barra de estado claros u oscuros según el tema de la app
            androidx.compose.runtime.LaunchedEffect(oscuro) {
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (oscuro) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                    navigationBarStyle = if (oscuro) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                )
            }
            VantyTheme(oscuro = oscuro) {
                val e by vm.e.collectAsStateWithLifecycle()
                AnimatedContent(
                    targetState = e.fase,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
                    label = "fase",
                ) { fase ->
                    when (fase) {
                        Fase.Cargando -> CargandoScreen()
                        Fase.Login -> LoginScreen(e, vm)
                        Fase.Requisitos -> RequisitosScreen(e, vm)
                        Fase.CentroInactivo -> CentroInactivoScreen(e.centro?.name, vm)
                        Fase.Bloqueado -> BloqueoLimiteScreen(e.centro?.name, vm)
                        Fase.SoloFamilias -> SoloFamiliasScreen(vm)
                        Fase.App -> MainScreen(e, vm)
                        Fase.Equipo -> EquipoScreen(e, vm)
                    }
                }
                // Aviso de versión nueva / "Novedades" después de actualizar
                if (e.fase != Fase.Cargando) xyz.vanty.aba.ui.AvisoVersion()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        abrirVista(intent)
        volverDeOAuth(intent)
    }

    /** Vuelta del navegador tras Google / Microsoft (vantyaba://login?code=…). */
    private fun volverDeOAuth(i: Intent?) {
        if (i?.data?.scheme != "vantyaba") return
        // Vuelta de vincular Google Calendar / Outlook
        if (i.data?.host == "calendario") { xyz.vanty.aba.ui.comp.Calendarios.volvio(i.data!!); return }
        if (i.data?.getQueryParameter("code") == null) { vm.oauthCancelado(); return }
        Backend.supabase.handleDeeplinks(i) { vm.volvioDeOAuth() }
    }

    /** Al tocar una notificación se abre directo en la sección correspondiente. */
    private fun abrirVista(i: Intent?) {
        vm.irAVista(i?.getStringExtra(Avisos.EXTRA_VISTA))
        i?.removeExtra(Avisos.EXTRA_VISTA)
    }
}
