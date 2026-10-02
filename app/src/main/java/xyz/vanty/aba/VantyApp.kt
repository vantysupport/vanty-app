package xyz.vanty.aba

import android.app.Application
import xyz.vanty.aba.notif.Avisos
import xyz.vanty.aba.util.Idioma

class VantyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Idioma.iniciar(this)
        Avisos.crearCanales(this)
    }
}
