package xyz.vanty.aba.ui.comp

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.Idioma
import xyz.vanty.aba.util.L

/** Tarjeta "Idioma" con el selector ES | EN (la misma en todos los roles). */
@Composable
fun TarjetaIdioma(vm: AppViewModel, modifier: Modifier = Modifier, alCambiar: () -> Unit = {}) {
    Tarjeta(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoTono(Icons.Rounded.Translate, T.acentoSuave, T.acento, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(L("Idioma", "Language"), style = MaterialTheme.typography.titleMedium, color = T.texto)
                Text(L("Textos, IA y notificaciones", "Texts, AI and notifications"), style = MaterialTheme.typography.bodySmall, color = T.terciario)
            }
            SelectorIdioma(Idioma.actual, { vm.cambiarIdioma(it); alCambiar() })
        }
    }
}

/** Cerrar sesión + eliminar cuenta (requisito de Google Play), con confirmación por contraseña. */
@Composable
fun ZonaCuenta(e: Estado, vm: AppViewModel, modifier: Modifier = Modifier) {
    var dialogo by remember { mutableStateOf(false) }
    Column(modifier) {
        BotonGrande(
            L("CERRAR SESIÓN", "SIGN OUT"), vm::salir, Modifier.fillMaxWidth(),
            color = T.tarjeta, labio = T.borde, textoColor = T.texto, icono = Icons.AutoMirrored.Rounded.Logout,
        )
        Spacer(Modifier.height(10.dp))
        TextButton({ vm.limpiarErrorEliminar(); dialogo = true }, Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.DeleteForever, null, tint = T.peligro, modifier = Modifier.padding(end = 6.dp))
            Text(L("Eliminar mi cuenta", "Delete my account"), color = T.peligro, fontWeight = FontWeight.SemiBold)
        }
    }
    if (dialogo) DialogoEliminar(e, vm) { dialogo = false }
}

@Composable
private fun DialogoEliminar(e: Estado, vm: AppViewModel, onCerrar: () -> Unit) {
    var clave by remember { mutableStateOf("") }
    val familia = e.perfil?.role == "padre"
    AlertDialog(
        onDismissRequest = { if (!e.eliminando) onCerrar() },
        icon = { AriaFlotando(Aria.NEUTRAL, 96.dp) },
        title = { Text(L("¿Eliminar tu cuenta?", "Delete your account?"), textAlign = TextAlign.Center) },
        text = {
            Column {
                Text(
                    if (familia) L("Se borran tu acceso, tu perfil y tus archivos. La historia clínica de tus hijos queda en el centro, como exige la ley. Esto no se puede deshacer.",
                        "Your access, profile and files will be deleted. Your children's clinical record stays with the center, as required by law. This can't be undone.")
                    else L("Se borran tu acceso, tu perfil y tus archivos. Los registros clínicos que hiciste quedan en el centro. Esto no se puede deshacer.",
                        "Your access, profile and files will be deleted. The clinical records you made stay with the center. This can't be undone."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    clave, { clave = it }, label = { Text(L("Tu contraseña", "Your password")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
                e.errorEliminar?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = T.peligro, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton({ vm.eliminarCuenta(clave) }, enabled = !e.eliminando) {
                if (e.eliminando) CircularProgressIndicator(Modifier.padding(end = 8.dp).width(18.dp).height(18.dp), strokeWidth = 2.dp, color = T.peligro)
                Text(L("Eliminar definitivamente", "Delete permanently"), color = T.peligro, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onCerrar, enabled = !e.eliminando) { Text(L("Cancelar", "Cancel")) } },
    )
}

/** Festejo a pantalla completa reutilizable: ARIA, título grande, confeti (o monedas) y vibración. */
@Composable
fun FestejoPantalla(
    titulo: String, subtitulo: String, @DrawableRes pose: Int, onCerrar: () -> Unit,
    acento: Color = Color(0xFF5AC8FA), boton: Color = Color(0xFF0A8CF0), labio: Color = Color(0xFF0B5C9E),
    monedas: Boolean = false, grande: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        entrada.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 230f))
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xF20B1B33)).clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Confeti(monedas = monedas)
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AriaFlotando(pose, 190.dp, Modifier.graphicsLayer { scaleX = entrada.value; scaleY = entrada.value })
            Spacer(Modifier.height(10.dp))
            if (grande != null) Text(grande, color = acento, style = MaterialTheme.typography.displaySmall.copy(fontSize = 52.sp, fontWeight = FontWeight.Black))
            Text(titulo, color = acento, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = entrada.value.coerceIn(0f, 1f) })
            Spacer(Modifier.height(8.dp))
            Text(subtitulo, color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))
            BotonGrande(L("¡CONTINUAR!", "CONTINUE!"), onCerrar, Modifier.fillMaxWidth(), color = boton, labio = labio)
        }
    }
}
