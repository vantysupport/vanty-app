package xyz.vanty.aba.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.BuildConfig
import xyz.vanty.aba.data.TERMINOS_VERSION
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.EN
import xyz.vanty.aba.util.L

/** Antes de entrar (como NombrePerfilGuard de la web): nombre y apellido y/o aceptar Términos y Privacidad. */
@Composable
fun RequisitosScreen(e: Estado, vm: AppViewModel) {
    val p = e.perfil ?: return
    val pideNombre = p.nombreConfirmado == false
    val pideTerminos = p.terminosVersion != TERMINOS_VERSION
    var nombre by remember { mutableStateOf(p.nombre.orEmpty()) }
    var acepta by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val valido = (!pideNombre || nombre.trim().length >= 2) && (!pideTerminos || acepta)
    val direccion = p.role == "jefe" || p.role == "admin"

    Column(
        Modifier.fillMaxSize().background(T.fondo).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        AriaFlotando(Aria.SALUDO, 150.dp)
        Spacer(Modifier.height(12.dp))
        Text(L("¡Un paso antes de empezar!", "One step before we start!"), style = MaterialTheme.typography.headlineSmall, color = T.texto, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Tarjeta {
            if (pideNombre) {
                Text(L("¿Cómo te llamas?", "What's your name?"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                Text(L("Nombre y apellido, como quieres que te vea el centro.", "First and last name, as the center will see you."), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(nombre, { nombre = it }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    label = { Text(L("Nombre y apellido", "Full name")) })
                if (pideTerminos) Spacer(Modifier.height(18.dp))
            }
            if (pideTerminos) {
                Text(L("Términos y privacidad", "Terms and privacy"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                Text(
                    if (direccion) L("Como dirección del centro, aceptas además el Acuerdo de Encargo de Tratamiento de datos.", "As the center's director, you also accept the Data Processing Agreement.")
                    else L("Actualizamos cómo cuidamos tus datos. Léelos y acepta para continuar.", "We updated how we care for your data. Read and accept to continue."),
                    style = MaterialTheme.typography.bodySmall, color = T.secundario,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(acepta, { acepta = it }, colors = CheckboxDefaults.colors(checkedColor = T.acento))
                    Text(L("Acepto los Términos y la Política de privacidad", "I accept the Terms and the Privacy Policy"), style = MaterialTheme.typography.bodyMedium, color = T.texto)
                }
                Row {
                    TextButton({ abrirWeb(ctx, "terminos") }) { Text(L("Leer Términos", "Read Terms"), color = T.acento) }
                    TextButton({ abrirWeb(ctx, "privacidad") }) { Text(L("Leer Privacidad", "Read Privacy"), color = T.acento) }
                }
            }
            e.errorLogin?.let { Text(it, color = T.peligro, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(14.dp))
            BotonGrande(L("CONTINUAR", "CONTINUE"), { vm.completarRequisitos(if (pideNombre) nombre else null, pideTerminos && acepta) },
                Modifier.fillMaxWidth(), habilitado = valido, cargando = e.entrando)
        }
        TextButton(vm::salir, Modifier.padding(top = 10.dp)) { Text(L("Cerrar sesión", "Sign out"), color = T.secundario) }
    }
}

private fun abrirWeb(ctx: android.content.Context, ruta: String) = runCatching {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.API_BASE_URL}/${if (EN) "en" else "es"}/$ruta")))
}

/** Aviso de consentimiento para la IA (ConsentimientoIA.tsx). `motivo`: "centro" o "propio". */
@Composable
fun TarjetaConsentimientoIA(motivo: String, puedeDecidirCentro: Boolean, cargando: Boolean, onAceptar: () -> Unit, onRechazar: () -> Unit) {
    val ctx = LocalContext.current
    Tarjeta {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { AriaFlotando(Aria.NEUTRAL, 110.dp) }
        Spacer(Modifier.height(10.dp))
        if (motivo == "centro" && !puedeDecidirCentro) {
            Text(L("La IA está desactivada en tu centro", "AI is turned off in your center"), style = MaterialTheme.typography.titleMedium, color = T.texto)
            Spacer(Modifier.height(6.dp))
            Text(L("Para usar ARIA, la dirección de tu centro debe activar las funciones de IA en Configuración → Centro.",
                "To use ARIA, your center's director must turn on AI features in Settings → Center."), style = MaterialTheme.typography.bodyMedium, color = T.secundario)
            return@Tarjeta
        }
        Text(
            if (motivo == "centro") L("Funciones de inteligencia artificial", "Artificial intelligence features") else L("Usar ARIA, el asistente con IA", "Use ARIA, the AI assistant"),
            style = MaterialTheme.typography.titleMedium, color = T.texto,
        )
        Text(L("Antes de usarlo, así se tratan tus datos:", "Before using it, this is how your data is handled:"), style = MaterialTheme.typography.bodySmall, color = T.secundario)
        Spacer(Modifier.height(10.dp))
        val proveedor = L("Groq, Inc. (y DeepInfra, Inc. como respaldo), Estados Unidos", "Groq, Inc. (and DeepInfra, Inc. as backup), United States")
        listOf(
            Icons.Rounded.Dns to if (motivo == "centro") L("A nuestros proveedores de IA, $proveedor, solo se envía el contexto necesario para cada consulta. Puede incluir datos clínicos de tus pacientes.",
                "Only the context needed for each request is sent to our AI providers, $proveedor. It may include clinical data of your patients.")
            else L("Para responderte, ARIA envía tu pregunta y el contexto necesario sobre el progreso de tu hijo o hija a nuestros proveedores de IA, $proveedor.",
                "To answer you, ARIA sends your question and the context needed about your child's progress to our AI providers, $proveedor."),
            Icons.Rounded.Lock to L("No se usa para entrenar modelos de IA ni para ningún otro fin.", "It is not used to train AI models or for any other purpose."),
            Icons.Rounded.DeleteOutline to L("El proveedor principal no lo guarda (retención cero); el de respaldo trabaja sin retención o con retención limitada.",
                "The main provider does not store it (zero data retention); the backup one works without or with limited retention."),
            Icons.Rounded.Block to if (motivo == "centro") L("Puedes desactivarla cuando quieras. Sin IA, el resto de Vanty funciona igual.", "You can turn it off anytime. Without AI, the rest of Vanty works the same.")
            else L("Puedes desactivarlo cuando quieras desde tu Perfil.", "You can turn it off anytime from your Profile."),
        ).forEach { (icono, texto) -> Punto(icono, texto) }
        TextButton({ abrirWeb(ctx, "privacidad") }) { Text(L("Más detalles en la Política de privacidad", "Read more in the Privacy Policy"), color = T.acento) }
        Spacer(Modifier.height(6.dp))
        BotonGrande(L("ACEPTAR Y USAR ARIA", "ACCEPT AND USE ARIA"), onAceptar, Modifier.fillMaxWidth(), cargando = cargando)
        TextButton(onRechazar, Modifier.align(Alignment.CenterHorizontally)) { Text(L("Ahora no", "Not now"), color = T.secundario) }
    }
}

@Composable
private fun Punto(icono: ImageVector, texto: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Icon(icono, null, tint = T.acento, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = T.secundario)
    }
}

/** Centro sin acceso (403 centro_inactive). A las familias nunca se les habla de pagos. */
@Composable
fun CentroInactivoScreen(centro: String?, vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().background(T.fondo).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        AriaFlotando(Aria.SENTADA, 150.dp)
        Spacer(Modifier.height(16.dp))
        Text(L("El portal de ${centro ?: "tu centro"} no está disponible por ahora", "${centro ?: "Your center"}'s portal is not available right now"),
            style = MaterialTheme.typography.headlineSmall, color = T.texto, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(L("La información de tu hijo/a está segura. Si necesitas algo urgente, comunícate con el centro.",
            "Your child's information is safe. If you need something urgent, contact the center."),
            style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        BotonGrande(L("REINTENTAR", "TRY AGAIN"), vm::reintentar, Modifier.fillMaxWidth())
        TextButton(vm::salir) { Text(L("Cerrar sesión", "Sign out"), color = T.secundario) }
    }
}

/** Familia que excede el tope de cuentas del plan del centro (/api/padre/limite → allowed:false). */
@Composable
fun BloqueoLimiteScreen(centro: String?, vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().background(T.fondo).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        AriaFlotando(Aria.NEUTRAL, 150.dp)
        Spacer(Modifier.height(16.dp))
        Text(L("Tu acceso todavía no está habilitado", "Your access isn't enabled yet"), style = MaterialTheme.typography.headlineSmall, color = T.texto, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(L("Comunícate con ${centro ?: "tu centro"} para que habiliten tu cuenta. Tu información está segura.",
            "Contact ${centro ?: "your center"} so they can enable your account. Your information is safe."),
            style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        BotonGrande(L("REINTENTAR", "TRY AGAIN"), vm::reintentar, Modifier.fillMaxWidth())
        TextButton(vm::salir) { Text(L("Cerrar sesión", "Sign out"), color = T.secundario) }
    }
}
