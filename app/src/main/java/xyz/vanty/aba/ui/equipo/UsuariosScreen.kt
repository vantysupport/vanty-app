package xyz.vanty.aba.ui.equipo

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.vanty.aba.data.Invitacion
import xyz.vanty.aba.data.Paciente
import xyz.vanty.aba.data.RepoAdmin
import xyz.vanty.aba.data.Usuario
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.CabeceraSub
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.fechaCorta

private fun nombreRolWeb(r: String) = when (r) {
    "jefe", "admin" -> L("Administración", "Admin"); "especialista", "terapeuta" -> L("Especialista", "Specialist")
    "secretaria" -> L("Secretaría", "Front desk"); "padre" -> L("Familia", "Family"); else -> r
}

/** Usuarios del centro e invitaciones (solo dirección). Cupos según el plan, sin ofrecer compras. */
@Composable
fun UsuariosScreen(e: EstadoEquipo, vm: EquipoViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var seccion by rememberSaveable { mutableStateOf(0) }
    var usuarios by remember { mutableStateOf<List<Usuario>?>(null) }
    var invitaciones by remember { mutableStateOf<List<Invitacion>?>(null) }
    var invitar by remember { mutableStateOf(false) }
    suspend fun cargar() {
        usuarios = runCatching { RepoAdmin.usuarios().first }.getOrDefault(emptyList())
        invitaciones = runCatching { RepoAdmin.invitaciones() }.getOrDefault(emptyList())
    }
    LaunchedEffect(Unit) { cargar() }

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item { CabeceraSub(L("Usuarios", "Users")) { vm.irA(TabEquipo.Mas) } }
        item {
            BotonGrande(L("INVITAR A ALGUIEN", "INVITE SOMEONE"), { invitar = true }, Modifier.fillMaxWidth(), icono = Icons.Rounded.PersonAdd)
        }
        item {
            Row(Modifier.fillMaxWidth().background(T.relleno, RoundedCornerShape(16.dp)).padding(4.dp)) {
                listOf(L("Equipo", "Team"), L("Familias", "Families"), L("Invitaciones", "Invites")).forEachIndexed { i, t ->
                    val activo = i == seccion
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (activo) T.tarjeta else Color.Transparent)
                        .presionable { seccion = i }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(t, style = MaterialTheme.typography.titleSmall, color = if (activo) T.acento else T.secundario)
                    }
                }
            }
        }
        if (seccion < 2) {
            val lista = usuarios.orEmpty().filter { if (seccion == 0) it.rol != "padre" else it.rol == "padre" }
            itemsIndexed(lista, key = { _, u -> u.id }) { i, u ->
                Row(Modifier.aparecer(i.coerceAtMost(12)).fillMaxWidth().background(T.tarjeta, RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u.nombre, 42.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.nombre, style = MaterialTheme.typography.titleSmall, color = if (u.activo) T.texto else T.terciario, maxLines = 1)
                        Text(u.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Etiqueta(nombreRolWeb(u.rol), T.acentoSuave, T.acento)
                            if (u.principal) Etiqueta(L("Principal", "Owner"), T.aviso.copy(alpha = 0.15f), T.aviso)
                        }
                    }
                    if (!u.principal) {
                        IconButton({
                            alcance.launch {
                                val r = u.email?.let { RepoAdmin.enviarCambioClave(it) } ?: "error"
                                vm.aviso(if (r == null) L("Enviamos a ${u.email} un correo para cambiar la contraseña.", "We emailed ${u.email} a password reset link.") else L("No se pudo enviar el correo.", "Couldn't send the email."))
                            }
                        }) { Icon(Icons.Rounded.Key, L("Cambiar contraseña", "Reset password"), tint = T.secundario) }
                        Switch(u.activo, {
                            alcance.launch {
                                when (RepoAdmin.alternarActivo(u.id)) {
                                    null -> cargar()
                                    "seat_limit" -> vm.aviso(L("No hay cupo disponible en el plan del centro. Desactiva a otra persona primero.", "No seats left in the center's plan. Deactivate someone else first."))
                                    "solo_admin_principal" -> vm.aviso(L("Solo el administrador principal puede hacer este cambio.", "Only the main administrator can do this."))
                                    else -> vm.aviso(L("No se pudo actualizar.", "Couldn't update."))
                                }
                            }
                        }, colors = SwitchDefaults.colors(checkedTrackColor = T.exito))
                    }
                }
            }
        } else {
            val activas = invitaciones.orEmpty()
            if (activas.isEmpty()) item { Tarjeta { Text(L("No hay invitaciones. Invita a tu equipo o a una familia con el botón de arriba.", "No invites yet. Invite your team or a family with the button above."), color = T.secundario) } }
            itemsIndexed(activas, key = { _, v -> v.id }) { i, v ->
                Tarjeta(Modifier.aparecer(i)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.email ?: v.paciente?.let { L("Familia de $it", "$it's family") } ?: L("Enlace de invitación", "Invite link"), style = MaterialTheme.typography.titleSmall, color = T.texto)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Etiqueta(nombreRolWeb(v.role.orEmpty()), T.acentoSuave, T.acento)
                                val (t, c) = when (v.estado) {
                                    "activa" -> L("Activa", "Active") to T.exito; "usada" -> L("Usada", "Used") to T.acento
                                    "vencida" -> L("Vencida", "Expired") to T.terciario; else -> L("Revocada", "Revoked") to T.terciario
                                }
                                Etiqueta(t, c.copy(alpha = 0.14f), c)
                                v.vence?.let { val (d, m) = fechaCorta(it); Text(L("vence $d $m", "expires $d $m"), style = MaterialTheme.typography.labelSmall, color = T.terciario) }
                            }
                        }
                        if (v.estado == "activa" && v.link != null) IconButton({ compartir(ctx, v.link) }) { Icon(Icons.Rounded.Share, L("Compartir", "Share"), tint = T.acento) }
                    }
                    if (v.estado == "activa") TextButton({ alcance.launch { if (RepoAdmin.revocar(v.id)) cargar() } }) { Text(L("Revocar", "Revoke"), color = T.peligro) }
                }
            }
        }
    }
    if (invitar) HojaInvitar(e.pacientes, vm, { alcance.launch { cargar() }; seccion = 2 }) { invitar = false }
}

private fun compartir(ctx: android.content.Context, link: String) = runCatching {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), L("Compartir invitación", "Share invite")))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaInvitar(pacientes: List<Paciente>, vm: EquipoViewModel, onCreada: () -> Unit, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var rol by remember { mutableStateOf("padre") }
    var email by remember { mutableStateOf("") }
    var paciente by remember { mutableStateOf<Paciente?>(null) }
    var dias by remember { mutableStateOf(7) }
    var enviando by remember { mutableStateOf(false) }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(L("Invitar", "Invite"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Spacer(Modifier.height(12.dp))
            Chips(listOf("padre" to L("Familia", "Family"), "especialista" to L("Especialista", "Specialist"), "secretaria" to L("Secretaría", "Front desk")), rol) { rol = it }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(email, { email = it }, label = { Text(L("Correo (opcional)", "Email (optional)")) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            Text(L("Si escribes un correo, le enviamos la invitación. Si no, compártela tú por WhatsApp.", "With an email we send the invite. Without one, share it yourself on WhatsApp."),
                style = MaterialTheme.typography.bodySmall, color = T.terciario, modifier = Modifier.padding(top = 4.dp))
            if (rol == "padre") {
                Spacer(Modifier.height(12.dp))
                Text(L("¿De qué paciente?", "For which patient?"), style = MaterialTheme.typography.labelMedium, color = T.secundario)
                Spacer(Modifier.height(6.dp))
                Chips(pacientes.map { it.id to it.nombre }, paciente?.id) { id -> paciente = pacientes.firstOrNull { it.id == id } }
            }
            Spacer(Modifier.height(12.dp))
            Text(L("Válida por", "Valid for"), style = MaterialTheme.typography.labelMedium, color = T.secundario)
            Spacer(Modifier.height(6.dp))
            Chips(listOf(1, 3, 7, 14, 30).map { "$it" to L("$it ${if (it == 1) "día" else "días"}", "$it day${if (it == 1) "" else "s"}") }, "$dias") { dias = it.toInt() }
            Spacer(Modifier.height(20.dp))
            BotonGrande(L("CREAR INVITACIÓN", "CREATE INVITE"), {
                enviando = true
                alcance.launch {
                    val (inv, enviado) = RepoAdmin.invitar(rol, email, if (rol == "padre") paciente?.id else null, dias)
                    enviando = false
                    if (inv == null) { vm.aviso(L("No se pudo crear la invitación.", "Couldn't create the invite.")); return@launch }
                    vm.aviso(if (enviado) L("¡Invitación enviada por correo! ✉️", "Invite emailed! ✉️") else L("Invitación creada. Compártela.", "Invite created. Share it."))
                    if (!enviado) inv.link?.let { compartir(ctx, it) }
                    onCreada(); onCerrar()
                }
            }, Modifier.fillMaxWidth(), cargando = enviando, icono = Icons.Rounded.PersonAdd)
        }
    }
}

@Composable
private fun Chips(opciones: List<Pair<String, String>>, sel: String?, onSel: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opciones.forEach { (clave, texto) ->
            val activo = clave == sel
            Text(texto, style = MaterialTheme.typography.labelLarge, color = if (activo) Color.White else T.secundario,
                modifier = Modifier.presionable { onSel(clave) }.background(if (activo) T.acento else T.relleno, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
}
