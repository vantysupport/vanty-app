package xyz.vanty.aba.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import xyz.vanty.aba.ui.comp.presionable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.vanty.aba.R
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.AriaFlotando
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.theme.MarcaDegradado
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.ui.comp.SelectorIdioma
import xyz.vanty.aba.util.Idioma
import xyz.vanty.aba.util.L

@Composable
fun LoginScreen(e: Estado, vm: AppViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    var ver by remember { mutableStateOf(false) }
    val foco = LocalFocusManager.current

    Box(Modifier.fillMaxSize().background(T.fondo)) {
        // Cabecera azul con ARIA saludando
        Box(
            Modifier.fillMaxWidth().height(330.dp)
                .background(MarcaDegradado, RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)),
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.vanty_mark_white), "Vanty", Modifier.height(30.dp))
                SelectorIdioma(Idioma.actual, vm::cambiarIdioma, Modifier.align(Alignment.CenterEnd), sobreAzul = true)
            }
            Spacer(Modifier.height(10.dp))
            AriaFlotando(Aria.SALUDO, 170.dp)
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier.aparecer(1).fillMaxWidth()
                    .background(T.tarjeta, RoundedCornerShape(28.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(L("¡Hola! Soy ARIA", "Hi! I'm ARIA"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
                Text(
                    L("Entra con tu cuenta de Vanty: la misma que usas en la web.",
                        "Sign in with your Vanty account: the same one you use on the web."),
                    style = MaterialTheme.typography.bodyMedium, color = T.secundario,
                )

                val colores = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = T.acento, unfocusedBorderColor = T.borde,
                    focusedContainerColor = T.relleno, unfocusedContainerColor = T.relleno,
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text(L("Correo", "Email")) },
                    leadingIcon = { Icon(Icons.Rounded.Email, null) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), colors = colores,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = clave, onValueChange = { clave = it },
                    label = { Text(L("Contraseña", "Password")) },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                    trailingIcon = {
                        IconButton({ ver = !ver }) { Icon(if (ver) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, L("Mostrar", "Show")) }
                    },
                    visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true, shape = RoundedCornerShape(16.dp), colors = colores,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { foco.clearFocus(); vm.entrar(email, clave) }),
                    modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(e.aviso != null && e.errorLogin == null, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
                    Text(
                        e.aviso.orEmpty(), color = T.exito, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().background(T.exito.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(10.dp),
                    )
                }
                AnimatedVisibility(e.errorLogin != null, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
                    Text(
                        e.errorLogin.orEmpty(), color = T.peligro, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().background(T.peligro.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(10.dp),
                    )
                }

                BotonGrande(
                    L("ENTRAR", "SIGN IN"), { foco.clearFocus(); vm.entrar(email, clave) },
                    Modifier.fillMaxWidth(), cargando = e.entrando,
                )
                TextButton({ vm.recuperarClave(email) }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text(L("¿Olvidaste tu contraseña?", "Forgot your password?"), color = T.acento)
                }
                if (e.conGoogle || e.conMicrosoft) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.dp).background(T.borde))
                        Text(L("  o continúa con  ", "  or continue with  "), style = MaterialTheme.typography.labelMedium, color = T.terciario)
                        Box(Modifier.weight(1f).height(1.dp).background(T.borde))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (e.conGoogle) BotonProveedor("Google", R.drawable.ic_google, !e.entrando, Modifier.weight(1f)) { vm.entrarCon("google") }
                        if (e.conMicrosoft) BotonProveedor("Microsoft", R.drawable.ic_microsoft, !e.entrando, Modifier.weight(1f)) { vm.entrarCon("azure") }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                L("¿Aún no tienes cuenta? Pídele a tu centro una invitación.", "No account yet? Ask your center for an invitation."),
                style = MaterialTheme.typography.bodySmall, color = T.terciario, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BotonProveedor(nombre: String, icono: Int, habilitado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(50.dp).clip(CircleShape).background(T.tarjeta).border(1.5.dp, T.borde, CircleShape)
            .presionable(habilitado, onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icono), null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(nombre, style = MaterialTheme.typography.titleSmall, color = T.texto)
    }
}

/** Para cuentas del equipo (admin, especialista, secretaria): la app es solo para familias por ahora. */
@Composable
fun SoloFamiliasScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().background(T.fondo).statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        AriaFlotando(Aria.NEUTRAL, 160.dp)
        Spacer(Modifier.height(18.dp))
        Text(L("Esta cuenta se usa desde la web", "This account is used on the web"), style = MaterialTheme.typography.headlineSmall, color = T.texto, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            L("La app es para familias, especialistas, secretaría y administración del centro. Las cuentas de la consola de la plataforma se usan desde la web.",
                "The app is for families, specialists, front desk and center administrators. Platform console accounts are used on the web."),
            style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        BotonGrande(L("USAR OTRA CUENTA", "USE ANOTHER ACCOUNT"), vm::salir, Modifier.fillMaxWidth(), color = T.relleno, labio = T.borde, textoColor = T.texto)
    }
}

@Composable
fun CargandoScreen() {
    Box(Modifier.fillMaxSize().background(MarcaDegradado), contentAlignment = Alignment.Center) {
        AriaFlotando(Aria.SALUDO, 150.dp)
    }
}

