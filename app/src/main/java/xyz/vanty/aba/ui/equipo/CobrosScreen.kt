package xyz.vanty.aba.ui.equipo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import xyz.vanty.aba.data.CitaEquipo
import xyz.vanty.aba.data.Especialista
import xyz.vanty.aba.data.NinoTutor
import xyz.vanty.aba.data.NuevoCobro
import xyz.vanty.aba.data.NuevoPaquete
import xyz.vanty.aba.data.Paciente
import xyz.vanty.aba.data.Pago
import xyz.vanty.aba.data.RepoAgenda
import xyz.vanty.aba.data.RepoPagos
import xyz.vanty.aba.data.Tarifa
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.Campo
import xyz.vanty.aba.ui.comp.CampoBoton
import xyz.vanty.aba.ui.comp.CampoFecha
import xyz.vanty.aba.ui.comp.CampoHora
import xyz.vanty.aba.ui.comp.Confirmar
import xyz.vanty.aba.ui.comp.Etiq
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.Opciones
import xyz.vanty.aba.ui.comp.Pastilla
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.Vacio
import xyz.vanty.aba.ui.comp.abrirArchivoLocal
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.MarcaDegradado
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.EN
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.fechaCorta
import xyz.vanty.aba.util.fechaLarga
import xyz.vanty.aba.util.hora12
import xyz.vanty.aba.util.monedaFmt
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Métodos de pago de la web (SecretariaPagos). */
val METODOS = listOf(
    "efectivo" to ("Efectivo" to "Cash"), "yape" to ("Yape" to "Yape"), "plin" to ("Plin" to "Plin"),
    "transferencia" to ("Transferencia" to "Transfer"), "tarjeta" to ("Tarjeta" to "Card"), "otro" to ("Otro" to "Other"),
)

private fun nombreMetodo(m: String?) = METODOS.firstOrNull { it.first == m }?.second?.let { L(it.first, it.second) } ?: (m ?: "—")

@Composable
private fun estadoPago(s: String?): Pair<String, Color> = when (s) {
    "paid" -> L("Pagado", "Paid") to T.exito
    "partial" -> L("Parcial", "Partial") to T.acento
    "cancelled" -> L("Cancelado", "Cancelled") to T.peligro
    "refunded" -> L("Devuelto", "Refunded") to T.terciario
    else -> L("Pendiente", "Pending") to T.aviso
}

private val PAQUETE = Regex("""^(.*)\s\((\d+)/(\d+)\)\s*$""")

private sealed interface Item {
    data class Uno(val p: Pago) : Item
    data class Paquete(val key: String, val pagos: List<Pago>) : Item
}

/** Registros: los cobros de un mismo paquete ("Concepto (n/m)", creados juntos) van en una sola tarjeta, como la web. */
private fun agrupar(pagos: List<Pago>): List<Item> {
    val grupos = LinkedHashMap<String, MutableList<Pago>>()
    val orden = mutableListOf<Any>()
    pagos.forEach { p ->
        val m = PAQUETE.find(p.concept.orEmpty())
        if (m == null || m.groupValues[3].toInt() < 2) { orden += Item.Uno(p); return@forEach }
        val key = "${p.childId ?: p.externo}|${p.creado}|${m.groupValues[1]}"
        if (key !in grupos) { grupos[key] = mutableListOf(); orden += key }
        grupos[key]!!.add(p)
    }
    return orden.map { if (it is String) grupos[it]!!.let { l -> if (l.size == 1) Item.Uno(l[0]) else Item.Paquete(it, l) } else it as Item }
}

/**
 * Pagos (SecretariaPagos de la web) para jefe, admin y secretaría: Dashboard · Registros · Deudas · Por paciente · Tarifas,
 * nuevo cobro y paquete de sesiones vinculados a la Agenda (se agenda la sesión y va a Google / Outlook),
 * cambiar estado, abonos, recibo PDF, enviar recibo por correo y eliminar (también la sesión agendada).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CobrosScreen(e: EstadoEquipo, vm: EquipoViewModel, pad: PaddingValues) {
    val alcance = rememberCoroutineScope()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("dashboard") }
    var periodo by rememberSaveable { mutableStateOf("mes") }
    var pagos by remember { mutableStateOf<List<Pago>?>(null) }
    var deudas by remember { mutableStateOf<List<Pago>>(emptyList()) }
    var ninos by remember { mutableStateOf<List<NinoTutor>>(emptyList()) }
    var tarifas by remember { mutableStateOf<List<Tarifa>>(emptyList()) }
    var especialistas by remember { mutableStateOf<List<Especialista>>(emptyList()) }
    var refrescando by remember { mutableStateOf(false) }
    var buscar by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf("all") }
    var nuevo by remember { mutableStateOf(false) }
    var paquete by remember { mutableStateOf(false) }
    var abonar by remember { mutableStateOf<Pago?>(null) }
    var borrar by remember { mutableStateOf<List<Pago>?>(null) }
    var enviar by remember { mutableStateOf<List<Pago>?>(null) }
    var tarifa by remember { mutableStateOf<Tarifa?>(null) }
    var nuevaTarifa by remember { mutableStateOf(false) }
    var borrarTarifa by remember { mutableStateOf<Tarifa?>(null) }

    suspend fun cargar() = coroutineScope {
        val p = async { runCatching { RepoPagos.pagos(periodo) }.getOrNull() }
        val d = async { runCatching { RepoPagos.deudas() }.getOrNull() }
        val t = async { runCatching { RepoPagos.tarifas() }.getOrNull() }
        p.await()?.let { pagos = it } ?: run { if (pagos == null) pagos = emptyList(); vm.aviso(L("Sin conexión. Desliza hacia abajo para reintentar.", "No connection. Pull down to retry.")) }
        d.await()?.let { deudas = it }
        t.await()?.let { tarifas = it }
    }
    LaunchedEffect(periodo) { cargar() }
    LaunchedEffect(Unit) {
        launch { ninos = runCatching { RepoPagos.ninos() }.getOrDefault(emptyList()) }
        launch { especialistas = runCatching { RepoAgenda.especialistas() }.getOrDefault(emptyList()) }
    }
    fun recargar() { alcance.launch { cargar(); vm.refrescar() } }

    val lista = pagos.orEmpty()
    val tabs = listOf("dashboard" to "Dashboard", "registros" to L("Registros", "Records"), "deudas" to L("Deudas", "Debts"),
        "agrupado" to L("Por paciente", "By patient"), "tarifas" to L("Tarifas", "Rates"))

    PullToRefreshBox(refrescando, { alcance.launch { refrescando = true; cargar(); refrescando = false } }, Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Column(Modifier.aparecer(0).fillMaxWidth().background(MarcaDegradado, RoundedCornerShape(26.dp)).padding(20.dp)) {
                    Text(L("Pagos", "Payments"), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(L("Por cobrar: ${monedaFmt(deudas.sumOf { it.saldo }, e.moneda)}", "To collect: ${monedaFmt(deudas.sumOf { it.saldo }, e.moneda)}"),
                        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonBlanco("+ " + L("Nuevo cobro", "New payment"), Modifier.weight(1f)) { nuevo = true }
                        BotonBlanco("📦 " + L("Paquete", "Package"), Modifier.weight(1f)) { paquete = true }
                    }
                }
            }
            item { Opciones(tabs, tab, Modifier.aparecer(1)) { tab = it } }
            if (tab == "dashboard" || tab == "registros" || tab == "agrupado") item {
                Opciones(listOf("semana" to L("Semana", "Week"), "mes" to L("Mes", "Month"), "anio" to L("Año", "Year")), periodo) { periodo = it }
            }
            when (tab) {
                "dashboard" -> item { Dashboard(lista, e.moneda) }
                "registros" -> {
                    item {
                        Campo(buscar, { buscar = it }, L("Buscar paciente o concepto…", "Search patient or concept…"))
                        Spacer(Modifier.height(8.dp))
                        Opciones(listOf("all" to L("Todos", "All")) + RepoPagos.ESTADOS.map { it to estadoPago(it).first }, filtro, punto = { if (it == "all") null else estadoPago(it).second }) { filtro = it }
                    }
                    val q = buscar.trim().lowercase()
                    val filtrados = lista.filter { p -> (filtro == "all" || p.status == filtro) && (q.isEmpty() || p.paciente.lowercase().contains(q) || p.concept.orEmpty().lowercase().contains(q)) }
                    if (pagos != null && filtrados.isEmpty()) item { Tarjeta { Vacio(Aria.SENTADA, L("Sin registros", "No records"), L("No hay cobros en este período.", "No payments in this period.")) } }
                    items(agrupar(filtrados), key = { if (it is Item.Uno) it.p.id else (it as Item.Paquete).key }) { it ->
                        when (it) {
                            is Item.Uno -> FilaPago(it.p, e.moneda,
                                onEstado = { k -> if (k == "partial") abonar = it.p else alcance.launch { runCatching { RepoPagos.cambiarEstado(it.p, k) }.onSuccess { vm.aviso(L("Actualizado a ${estadoTexto(k)}", "Updated to ${estadoTexto(k)}")); cargar() }.onFailure { vm.aviso(L("No se pudo actualizar.", "Couldn't update.")) } } },
                                onRecibo = { alcance.launch { abrirRecibo(ctx, listOf(it.p.id), vm) } },
                                onEnviar = { enviar = listOf(it.p) }, onBorrar = { borrar = listOf(it.p) }, onAbonar = { abonar = it.p })
                            is Item.Paquete -> TarjetaPaquete(it.pagos, e.moneda,
                                onRecibo = { alcance.launch { abrirRecibo(ctx, it.pagos.map { p -> p.id }, vm) } },
                                onEnviar = { enviar = it.pagos }, onBorrar = { borrar = it.pagos }, onAbonar = { p -> abonar = p })
                        }
                    }
                }
                "deudas" -> {
                    val grupos = deudas.groupBy { it.childId ?: ("ext:" + it.externo) }.values.sortedByDescending { g -> g.sumOf { it.saldo } }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Mini(Modifier.weight(1f), L("Deuda total", "Total debt"), monedaFmt(deudas.sumOf { it.saldo }, e.moneda))
                            Mini(Modifier.weight(1f), L("Pacientes", "Patients"), "${grupos.size}")
                        }
                    }
                    if (grupos.isEmpty()) item { Tarjeta { Vacio(Aria.CELEBRA, L("¡Todo cobrado!", "All collected!"), L("No hay deudas pendientes. 🎉", "No pending debts. 🎉")) } }
                    items(grupos, key = { it.first().id }) { g ->
                        Tarjeta {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(g.first().paciente, 38.dp); Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(g.first().paciente, style = MaterialTheme.typography.titleSmall, color = T.texto)
                                    Text(L("${g.size} cobros", "${g.size} charges"), style = MaterialTheme.typography.bodySmall, color = T.terciario)
                                }
                                Text(monedaFmt(g.sumOf { it.saldo }, e.moneda), style = MaterialTheme.typography.titleMedium, color = T.aviso)
                            }
                            g.forEach { p ->
                                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(p.concept.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = T.texto, maxLines = 1)
                                        Text(fechaTxt(p) + " · " + L("saldo ", "balance ") + monedaFmt(p.saldo, e.moneda), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                                    }
                                    Text(L("Abonar", "Pay"), style = MaterialTheme.typography.labelLarge, color = Color.White,
                                        modifier = Modifier.clip(CircleShape).background(T.acento).presionable { abonar = p }.padding(horizontal = 14.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                }
                "agrupado" -> {
                    val porPaciente = lista.groupBy { it.paciente }.toList().sortedBy { it.first }
                    if (pagos != null && porPaciente.isEmpty()) item { Tarjeta { Vacio(Aria.SENTADA, L("Sin registros", "No records"), L("No hay cobros en este período.", "No payments in this period.")) } }
                    items(porPaciente, key = { "g" + it.first }) { (nombre, ps) ->
                        Tarjeta {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(nombre, 38.dp); Spacer(Modifier.width(10.dp))
                                Text(nombre, style = MaterialTheme.typography.titleSmall, color = T.texto, modifier = Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(monedaFmt(ps.sumOf { it.cobrado }, e.moneda), style = MaterialTheme.typography.titleSmall, color = T.exito)
                                    if (ps.sumOf { it.saldo } > 0) Text(L("debe ", "owes ") + monedaFmt(ps.sumOf { it.saldo }, e.moneda), style = MaterialTheme.typography.labelSmall, color = T.aviso)
                                }
                            }
                            ps.groupBy { it.fecha.take(7) }.toSortedMap(compareByDescending { it }).forEach { (mes, pm) ->
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text(mesTexto(mes), style = MaterialTheme.typography.bodyMedium, color = T.secundario, modifier = Modifier.weight(1f))
                                    Text(L("${pm.size} cobros · ", "${pm.size} charges · ") + monedaFmt(pm.filter { it.status != "cancelled" && it.status != "refunded" }.sumOf { it.amount }, e.moneda),
                                        style = MaterialTheme.typography.bodyMedium, color = T.texto)
                                }
                            }
                        }
                    }
                }
                "tarifas" -> {
                    item {
                        BotonGrande(L("NUEVA TARIFA", "NEW RATE"), { nuevaTarifa = true }, Modifier.fillMaxWidth(), icono = Icons.Rounded.Add)
                    }
                    if (tarifas.isEmpty()) item { Tarjeta { Vacio(Aria.SENTADA, L("Sin tarifas", "No rates"), L("Crea las tarifas de tus servicios para cobrar más rápido.", "Create your service rates to charge faster.")) } }
                    items(tarifas, key = { "t" + it.id }) { t ->
                        Tarjeta {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.name.orEmpty(), style = MaterialTheme.typography.titleSmall, color = T.texto)
                                    Text(listOfNotNull(t.description, t.duracion?.let { "$it min" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                                }
                                Text(monedaFmt(t.amount, e.moneda), style = MaterialTheme.typography.titleMedium, color = T.texto)
                                IconButton({ tarifa = t }) { Icon(Icons.Rounded.Edit, L("Editar", "Edit"), tint = T.secundario) }
                                IconButton({ borrarTarifa = t }) { Icon(Icons.Rounded.DeleteOutline, L("Eliminar", "Delete"), tint = T.peligro) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (nuevo) HojaNuevoCobroWeb(ninos, tarifas, especialistas, e, onCerrar = { nuevo = false }) { ok -> nuevo = false; if (ok) { vm.aviso(L("Pago registrado", "Payment recorded")); recargar() } }
    if (paquete) HojaPaquete(ninos, tarifas, especialistas, e, onCerrar = { paquete = false }) { ok, n -> paquete = false; if (ok) { vm.aviso(L("$n pagos creados", "$n payments created")); recargar() } }
    abonar?.let { p ->
        HojaCobro(p, e.moneda, { monto, metodo ->
            abonar = null
            if (monto <= 0 || monto > (if (p.status == "partial") p.saldo else p.amount) + 0.001) vm.aviso(L("Ingresá un monto válido (hasta el saldo).", "Enter a valid amount (up to the balance)."))
            else { vm.registrarAbono(p, monto, metodo); alcance.launch { kotlinx.coroutines.delay(700); cargar() } }
        }) { abonar = null }
    }
    borrar?.let { ps -> DialogoBorrar(ps, e.moneda, { borrar = null }) { conSesiones ->
        borrar = null
        alcance.launch {
            val ok = runCatching { RepoPagos.eliminar(ps.map { it.id }) }.isSuccess
            if (!ok) { vm.aviso(L("No se pudo eliminar.", "Couldn't delete.")); return@launch }
            val fallidas = if (conSesiones) RepoPagos.eliminarSesiones(ps.map { it.citaId }) else 0
            vm.aviso(when {
                fallidas > 0 -> L("No se pudieron eliminar $fallidas sesión(es) de la agenda", "$fallidas session(s) could not be removed from the schedule")
                ps.size > 1 -> L("Paquete eliminado (${ps.size} pagos)", "Package deleted (${ps.size} payments)")
                else -> L("Pago eliminado", "Payment deleted")
            })
            recargar()
        }
    } }
    enviar?.let { ps -> DialogoEnviar(ps, { enviar = null }) { email ->
        alcance.launch {
            when (val r = RepoPagos.enviarRecibo(ps.map { it.id }, email)) {
                is RepoPagos.Envio.Ok -> { enviar = null; vm.aviso(L("Recibo enviado a ${r.email}", "Receipt sent to ${r.email}")) }
                RepoPagos.Envio.SinCorreo -> vm.aviso(L("Este paciente no tiene correo de familia. Escribe un correo para enviarlo.", "This patient has no family email. Type an email to send it."))
                RepoPagos.Envio.Error -> vm.aviso(L("No se pudo enviar el recibo. Inténtalo de nuevo.", "Could not send the receipt. Try again."))
            }
        }
    } }
    if (nuevaTarifa || tarifa != null) HojaTarifa(tarifa, { nuevaTarifa = false; tarifa = null }) { nombre, desc, monto, min ->
        val id = tarifa?.id
        nuevaTarifa = false; tarifa = null
        alcance.launch {
            runCatching { RepoPagos.guardarTarifa(id, nombre, desc, monto, min) }
                .onSuccess { vm.aviso(if (id != null) L("Tarifa actualizada", "Rate updated") else L("Tarifa creada", "Rate created")); cargar() }
                .onFailure { vm.aviso(L("No se pudo guardar.", "Couldn't save.")) }
        }
    }
    borrarTarifa?.let { t ->
        Confirmar(L("¿Eliminar tarifa?", "Delete rate?"), t.name.orEmpty(), L("Eliminar", "Delete"), onSi = {
            borrarTarifa = null
            alcance.launch { runCatching { RepoPagos.borrarTarifa(t.id) }; vm.aviso(L("Tarifa eliminada", "Rate deleted")); cargar() }
        }, onNo = { borrarTarifa = null })
    }
}

private fun estadoTexto(k: String) = when (k) {
    "paid" -> L("Pagado", "Paid"); "partial" -> L("Parcial", "Partial"); "cancelled" -> L("Cancelado", "Cancelled")
    "refunded" -> L("Devuelto", "Refunded"); else -> L("Pendiente", "Pending")
}

private fun fechaTxt(p: Pago) = p.fecha.takeIf { it.isNotBlank() }?.let { val (d, m) = fechaCorta(it); "$d $m" } ?: "—"

private fun mesTexto(yyyyMm: String): String = runCatching {
    val ym = YearMonth.parse(yyyyMm)
    val loc = if (EN) Locale.ENGLISH else Locale.forLanguageTag("es")
    ym.month.getDisplayName(TextStyle.FULL, loc).replaceFirstChar { it.titlecase(loc) } + " " + ym.year
}.getOrDefault(yyyyMm)

private suspend fun abrirRecibo(ctx: android.content.Context, ids: List<String>, vm: EquipoViewModel) {
    val f = RepoPagos.recibo(ctx, ids)
    if (f == null || !abrirArchivoLocal(ctx, f, "application/pdf")) vm.aviso(L("No se pudo abrir el recibo.", "Couldn't open the receipt."))
}

@Composable
private fun BotonBlanco(texto: String, modifier: Modifier, onClick: () -> Unit) {
    Text(texto, style = MaterialTheme.typography.labelLarge, color = T.acento, textAlign = TextAlign.Center,
        modifier = modifier.clip(CircleShape).background(Color.White).presionable(onClick = onClick).padding(vertical = 11.dp))
}

@Composable
private fun Mini(modifier: Modifier, titulo: String, valor: String) {
    Column(modifier.background(T.tarjeta, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = T.secundario)
        Text(valor, style = MaterialTheme.typography.titleLarge, color = T.texto, maxLines = 1)
    }
}

/** Dashboard de la web: cobrado, cobros, por cobrar, cancelados, últimos 6 meses y por método. */
@Composable
private fun Dashboard(pagos: List<Pago>, moneda: String) {
    val conCobro = pagos.filter { it.cobrado > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Mini(Modifier.weight(1f), L("Cobrado", "Collected"), monedaFmt(conCobro.sumOf { it.cobrado }, moneda))
            Mini(Modifier.weight(1f), L("Cobros", "Payments"), "${conCobro.size}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Mini(Modifier.weight(1f), L("Por cobrar", "To collect"), monedaFmt(pagos.sumOf { it.saldo }, moneda))
            Mini(Modifier.weight(1f), L("Cancelados", "Cancelled"), "${pagos.count { it.status == "cancelled" }}")
        }
        Tarjeta {
            Text(L("Ingresos por mes", "Income by month"), style = MaterialTheme.typography.titleMedium, color = T.texto)
            Spacer(Modifier.height(10.dp))
            val hoy = LocalDate.now()
            val meses = (5 downTo 0).map { YearMonth.from(hoy).minusMonths(it.toLong()) }
            val totales = meses.map { m -> conCobro.filter { (it.pagadoEl ?: it.fecha).startsWith(m.toString()) }.sumOf { it.cobrado } }
            val max = (totales.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val loc = if (EN) Locale.ENGLISH else Locale.forLanguageTag("es")
            Row(Modifier.fillMaxWidth().height(130.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meses.zip(totales).forEach { (m, t) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth(0.7f).height((100 * t / max).dp.coerceAtLeast(4.dp)).clip(RoundedCornerShape(8.dp)).background(MarcaDegradado))
                        Text(m.month.getDisplayName(TextStyle.SHORT, loc).take(3), style = MaterialTheme.typography.labelSmall, color = T.terciario)
                    }
                }
            }
        }
        Tarjeta {
            Text(L("Por método", "By method"), style = MaterialTheme.typography.titleMedium, color = T.texto)
            val total = conCobro.sumOf { it.cobrado }.coerceAtLeast(0.01)
            METODOS.map { (k, _) -> k to conCobro.filter { it.metodo == k }.sumOf { it.cobrado } }.filter { it.second > 0 }.forEach { (k, v) ->
                Column(Modifier.padding(top = 10.dp)) {
                    Row { Text(nombreMetodo(k), style = MaterialTheme.typography.bodyMedium, color = T.texto, modifier = Modifier.weight(1f)); Text(monedaFmt(v, moneda), style = MaterialTheme.typography.bodyMedium, color = T.texto) }
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(CircleShape).background(T.relleno)) {
                        Box(Modifier.fillMaxWidth((v / total).toFloat()).height(6.dp).clip(CircleShape).background(MarcaDegradado))
                    }
                }
            }
            if (conCobro.isEmpty()) Text(L("Sin cobros en este período.", "No payments in this period."), style = MaterialTheme.typography.bodySmall, color = T.terciario, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Línea de la sesión vinculada: fecha, hora y asistencia (como asistenciaDe de la web). */
@Composable
private fun SesionVinculada(p: Pago) {
    val f = p.appointments?.fecha ?: return
    val (t, c) = when {
        p.appointments.status == "completed" -> L("Realizada", "Attended") to T.exito
        p.appointments.status == "cancelled" -> L("Cancelada", "Cancelled") to T.peligro
        f < LocalDate.now().toString() -> L("Asistencia sin marcar", "Attendance not marked") to T.aviso
        else -> L("Agendada", "Scheduled") to T.acento
    }
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("📅 " + L("Sesión ", "Session ") + fechaLarga(f) + " · " + hora12(p.appointments.hora), style = MaterialTheme.typography.bodySmall, color = T.secundario)
        Etiqueta(t, c.copy(alpha = 0.14f), c)
        p.especialista?.nombre?.let { Text("· $it", style = MaterialTheme.typography.bodySmall, color = T.terciario) }
    }
}

@Composable
private fun FilaPago(
    p: Pago, moneda: String, enPaquete: Boolean = false,
    onEstado: (String) -> Unit, onRecibo: () -> Unit, onEnviar: () -> Unit, onBorrar: () -> Unit, onAbonar: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val (et, ec) = estadoPago(p.status)
    Column(if (enPaquete) Modifier.fillMaxWidth().padding(top = 10.dp) else Modifier.fillMaxWidth().background(T.tarjeta, RoundedCornerShape(20.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            if (!enPaquete) { Avatar(p.paciente, 40.dp); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (enPaquete) p.concept.orEmpty() else p.paciente, style = MaterialTheme.typography.titleSmall, color = T.texto, maxLines = 1, modifier = Modifier.weight(1f, false))
                    if (p.childId == null && p.externo != null && !enPaquete) { Spacer(Modifier.width(6.dp)); Etiqueta(L("Sin inscribir", "Not enrolled"), T.relleno, T.terciario) }
                }
                Text((if (enPaquete) "" else p.concept.orEmpty() + " · ") + fechaTxt(p), style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 1)
                SesionVinculada(p)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(monedaFmt(p.amount, moneda), style = MaterialTheme.typography.titleSmall, color = T.texto)
                if (p.status == "partial") Text(L("debe ", "owes ") + monedaFmt(p.saldo, moneda), style = MaterialTheme.typography.labelSmall, color = T.aviso)
                Text(nombreMetodo(p.metodo), style = MaterialTheme.typography.labelSmall, color = T.terciario)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(Modifier.clip(CircleShape).background(ec.copy(alpha = 0.14f)).presionable { menu = true }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(et, style = MaterialTheme.typography.labelMedium, color = ec)
                    Icon(Icons.Rounded.ExpandMore, null, tint = ec, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(menu, { menu = false }, containerColor = T.tarjeta) {
                    RepoPagos.ESTADOS.forEach { k ->
                        val (t, c) = estadoPago(k)
                        DropdownMenuItem(text = { Text(t, color = T.texto) }, onClick = { menu = false; if (k != p.status) onEstado(k) },
                            leadingIcon = { Box(Modifier.size(8.dp).background(c, CircleShape)) })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (p.saldo > 0 && p.status != "cancelled" && p.status != "refunded" && p.status != "paid")
                Text(L("Abonar", "Pay"), style = MaterialTheme.typography.labelLarge, color = T.acento, modifier = Modifier.clip(CircleShape).presionable(onClick = onAbonar).padding(horizontal = 10.dp, vertical = 6.dp))
            IconButton(onRecibo, Modifier.size(36.dp)) { Icon(Icons.Rounded.PictureAsPdf, L("Ver recibo", "View receipt"), tint = T.secundario, modifier = Modifier.size(20.dp)) }
            IconButton(onEnviar, Modifier.size(36.dp)) { Icon(Icons.Rounded.MailOutline, L("Enviar recibo por correo", "Email receipt"), tint = T.secundario, modifier = Modifier.size(20.dp)) }
            IconButton(onBorrar, Modifier.size(36.dp)) { Icon(Icons.Rounded.DeleteOutline, L("Eliminar pago", "Delete payment"), tint = T.peligro, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun TarjetaPaquete(pagos: List<Pago>, moneda: String, onRecibo: () -> Unit, onEnviar: () -> Unit, onBorrar: () -> Unit, onAbonar: (Pago) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val ps = pagos.sortedBy { it.appointments?.fecha ?: it.fecha }
    val p0 = ps.first()
    val concepto = p0.concept.orEmpty().replace(Regex("""\s*\(\d+/\d+\)\s*$"""), "")
    val vigentes = ps.filter { it.status != "cancelled" && it.status != "refunded" }
    Column(Modifier.fillMaxWidth().background(T.tarjeta, RoundedCornerShape(20.dp)).padding(14.dp).animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.presionable { abierto = !abierto }) {
            Box(Modifier.size(40.dp).background(T.acentoSuave, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Inventory2, null, tint = T.acento) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p0.paciente, style = MaterialTheme.typography.titleSmall, color = T.texto, maxLines = 1)
                Text(L("Paquete · ", "Package · ") + concepto, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 1)
                Text(L("${ps.count { it.status == "paid" }}/${ps.size} pagadas", "${ps.count { it.status == "paid" }}/${ps.size} paid"), style = MaterialTheme.typography.labelSmall, color = T.terciario)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(monedaFmt(vigentes.sumOf { it.amount }, moneda), style = MaterialTheme.typography.titleSmall, color = T.texto)
                val pend = ps.sumOf { it.saldo }
                if (pend > 0) Text(L("pendiente ", "pending ") + monedaFmt(pend, moneda), style = MaterialTheme.typography.labelSmall, color = T.aviso)
            }
            Icon(if (abierto) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = T.terciario)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(onRecibo, Modifier.size(36.dp)) { Icon(Icons.Rounded.PictureAsPdf, L("Recibo del paquete", "Package receipt"), tint = T.secundario, modifier = Modifier.size(20.dp)) }
            IconButton(onEnviar, Modifier.size(36.dp)) { Icon(Icons.Rounded.MailOutline, L("Enviar recibo del paquete", "Email package receipt"), tint = T.secundario, modifier = Modifier.size(20.dp)) }
            IconButton(onBorrar, Modifier.size(36.dp)) { Icon(Icons.Rounded.DeleteOutline, L("Eliminar paquete", "Delete package"), tint = T.peligro, modifier = Modifier.size(20.dp)) }
        }
        AnimatedVisibility(abierto) {
            Column {
                ps.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.concept.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = T.texto, maxLines = 1)
                            Text(fechaTxt(p), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                            SesionVinculada(p)
                        }
                        val (t, c) = estadoPago(p.status)
                        Etiqueta(t, c.copy(alpha = 0.14f), c)
                        if (p.saldo > 0 && p.status != "cancelled" && p.status != "refunded" && p.status != "paid")
                            Text(L("Abonar", "Pay"), style = MaterialTheme.typography.labelLarge, color = T.acento, modifier = Modifier.clip(CircleShape).presionable { onAbonar(p) }.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogoBorrar(ps: List<Pago>, moneda: String, onNo: () -> Unit, onSi: (Boolean) -> Unit) {
    var sesiones by remember { mutableStateOf(false) }
    val conSesion = ps.count { it.citaId != null }
    AlertDialog(
        onDismissRequest = onNo, containerColor = T.tarjeta,
        title = { Text(if (ps.size > 1) L("¿Eliminar el paquete?", "Delete the package?") else L("¿Eliminar este pago?", "Delete this payment?"), color = T.texto) },
        text = {
            Column {
                Text("${ps.first().paciente} · ${ps.first().concept.orEmpty()} · ${monedaFmt(ps.sumOf { it.amount }, moneda)}", color = T.secundario)
                if (conSesion > 0) Row(Modifier.padding(top = 10.dp).presionable { sesiones = !sesiones }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(sesiones, { sesiones = it }, colors = CheckboxDefaults.colors(checkedColor = T.peligro))
                    Text(if (conSesion == 1) L("Eliminar también la sesión agendada (agenda y calendarios)", "Also delete the scheduled session (schedule and calendars)")
                        else L("Eliminar también las $conSesion sesiones agendadas (agenda y calendarios)", "Also delete the $conSesion scheduled sessions (schedule and calendars)"),
                        style = MaterialTheme.typography.bodySmall, color = T.texto)
                }
            }
        },
        confirmButton = { TextButton({ onSi(sesiones) }) { Text(L("Eliminar", "Delete"), color = T.peligro) } },
        dismissButton = { TextButton(onNo) { Text(L("Cancelar", "Cancel"), color = T.secundario) } },
    )
}

@Composable
private fun DialogoEnviar(ps: List<Pago>, onNo: () -> Unit, onEnviar: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onNo, containerColor = T.tarjeta,
        title = { Text(L("Enviar recibo", "Send receipt"), color = T.texto) },
        text = {
            Column {
                Text((if (ps.size > 1) L("Paquete · ", "Package · ") else "") + ps.first().concept.orEmpty().replace(Regex("""\s*\(\d+/\d+\)\s*$"""), ""), color = T.secundario)
                Spacer(Modifier.height(10.dp))
                Campo(email, { email = it }, L("Correo (vacío = el de la familia registrada)", "Email (empty = the registered family's)"))
            }
        },
        confirmButton = { TextButton({ onEnviar(email) }) { Text(L("Enviar", "Send"), color = T.acento) } },
        dismissButton = { TextButton(onNo) { Text(L("Cancelar", "Cancel"), color = T.secundario) } },
    )
}

/** Abono / cobrar el saldo (registrarAbono de la web). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaCobro(p: Pago, moneda: String, onCobrar: (Double, String) -> Unit, onCerrar: () -> Unit) {
    val saldo = if (p.status == "partial") p.saldo else p.amount
    var monto by remember { mutableStateOf("") }
    var metodo by remember { mutableStateOf(p.metodo ?: "efectivo") }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(if (p.status == "partial") L("Registrar abono", "Record payment") else L("¿Cuánto pagó?", "How much was paid?"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Text("${p.paciente} · ${L("saldo", "balance")} ${monedaFmt(saldo, moneda)}", style = MaterialTheme.typography.bodyMedium, color = T.secundario)
            Etiq(L("Monto", "Amount"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Campo(monto, { monto = it.replace(',', '.') }, "0.00", Modifier.weight(1f), numerico = true)
                Spacer(Modifier.width(8.dp))
                Pastilla(L("Todo", "All"), false) { monto = String.format(Locale.US, "%.2f", saldo) }
            }
            Etiq(L("Método", "Method"))
            Opciones(METODOS.map { (c, n) -> c to L(n.first, n.second) }, metodo) { metodo = it }
            Spacer(Modifier.height(20.dp))
            BotonGrande(L("REGISTRAR", "RECORD"), { onCobrar(monto.toDoubleOrNull() ?: 0.0, metodo) }, Modifier.fillMaxWidth(), icono = Icons.Rounded.Payments)
        }
    }
}

/** Paciente registrado (buscador) o "sin inscribir" (nombre libre), como la web. */
@Composable
private fun ElegirPaciente(ninos: List<NinoTutor>, externo: Boolean, onExterno: (Boolean) -> Unit, childId: String?, onChild: (String) -> Unit, nombreExt: String, onNombreExt: (String) -> Unit) {
    var lista by remember { mutableStateOf(false) }
    Opciones(listOf(false to "👥 " + L("Registrado", "Registered"), true to "➕ " + L("Sin inscribir", "Not enrolled")), externo) { onExterno(it) }
    Spacer(Modifier.height(8.dp))
    if (externo) Campo(nombreExt, onNombreExt, L("Nombre del niño/a", "Child's name"))
    else CampoBoton(ninos.firstOrNull { it.id == childId }?.nombre ?: L("Elegí un paciente", "Choose a patient"), Icons.Rounded.Person, vacio = childId == null) { lista = true }
    if (lista) ElegirPacientes(ninos.map { Paciente(it.id, it.name) }, listOfNotNull(childId), multiple = false, onCerrar = { lista = false }) { it.firstOrNull()?.let(onChild); lista = false }
}

/** Concepto con las tarifas del centro: al elegir una se completa el monto. */
@Composable
private fun Concepto(concepto: String, onConcepto: (String) -> Unit, tarifas: List<Tarifa>, moneda: String, onMonto: (String) -> Unit) {
    Campo(concepto, { v -> onConcepto(v); tarifas.firstOrNull { it.name.equals(v, true) }?.let { onMonto(it.amount.toString()) } }, L("Ej: Sesión de terapia ABA", "E.g. ABA therapy session"))
    if (tarifas.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tarifas.filter { it.activa != false }.forEach { t -> Pastilla("${t.name} · ${monedaFmt(t.amount, moneda)}", concepto == t.name) { onConcepto(t.name.orEmpty()); onMonto(t.amount.toString()) } }
        }
    }
}

@Composable
private fun ElegirEspecialista(lista: List<Especialista>, sel: String?, onSel: (String?) -> Unit) {
    Opciones(listOf<Pair<String?, String>>(null to L("Sin asignar", "Unassigned")) + lista.map { it.id to it.nombre.orEmpty() }, sel) { onSel(it) }
}

/** "Nuevo cobro" de la web, con "Vincular con la agenda" (sesión ya agendada o una nueva). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaNuevoCobroWeb(ninos: List<NinoTutor>, tarifas: List<Tarifa>, especialistas: List<Especialista>, e: EstadoEquipo, onCerrar: () -> Unit, onListo: (Boolean) -> Unit) {
    val alcance = rememberCoroutineScope()
    var externo by remember { mutableStateOf(false) }
    var childId by remember { mutableStateOf<String?>(null) }
    var nombreExt by remember { mutableStateOf("") }
    var concepto by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var adelanto by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("paid") }
    var metodo by remember { mutableStateOf("efectivo") }
    var fecha by remember { mutableStateOf(LocalDate.now()) }
    var esp by remember { mutableStateOf<String?>(null) }
    var responsable by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var agenda by remember { mutableStateOf(false) }
    var modoAgenda by remember { mutableStateOf("existente") }
    var cita by remember { mutableStateOf<CitaEquipo?>(null) }
    var hora by remember { mutableStateOf("09:00") }
    var citas by remember { mutableStateOf<List<CitaEquipo>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(agenda, childId, externo) {
        citas = if (agenda && !externo && childId != null) runCatching { RepoPagos.citasPaciente(childId!!) }.getOrDefault(emptyList()) else emptyList()
        if (agenda && citas.isEmpty()) modoAgenda = "nueva"
    }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(L("Nuevo cobro", "New payment"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Etiq(L("Paciente", "Patient"))
            ElegirPaciente(ninos, externo, { externo = it }, childId, { childId = it }, nombreExt) { nombreExt = it }
            Etiq(L("Concepto", "Concept")); Concepto(concepto, { concepto = it }, tarifas, e.moneda) { monto = it }
            Etiq(L("Monto", "Amount")); Campo(monto, { monto = it.replace(',', '.') }, "0.00", numerico = true)
            Etiq(L("Estado", "Status"))
            Opciones(RepoPagos.ESTADOS.map { it to estadoTexto(it) }, estado, punto = { estadoPago(it).second }) { estado = it }
            if (estado == "partial") { Etiq(L("Adelanto", "Down payment")); Campo(adelanto, { adelanto = it.replace(',', '.') }, "0.00", numerico = true) }
            Etiq(L("Método", "Method")); Opciones(METODOS.map { (c, n) -> c to L(n.first, n.second) }, metodo) { metodo = it }
            Etiq(L("Fecha", "Date")); CampoFecha(fecha, { fecha = it })
            if (especialistas.isNotEmpty()) { Etiq(L("Especialista", "Specialist")); ElegirEspecialista(especialistas, esp) { esp = it } }
            Etiq(L("Responsable de pago", "Payer"))
            Campo(responsable, { responsable = it }, ninos.firstOrNull { it.id == childId }?.tutor?.nombre ?: L("Nombre de quien paga", "Name of who pays"))
            Etiq(L("Notas", "Notes")); Campo(notas, { notas = it }, L("Opcional", "Optional"), lineas = 2)

            if (!externo) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp).background(T.acentoSuave, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = T.acento); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L("Vincular con la agenda", "Link to the schedule"), style = MaterialTheme.typography.titleSmall, color = T.texto)
                        Text(L("Una sesión ya agendada o una nueva (va a los calendarios conectados).", "An already scheduled session or a new one (goes to connected calendars)."), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                    }
                    Switch(agenda, { agenda = it }, colors = SwitchDefaults.colors(checkedTrackColor = T.acento, uncheckedTrackColor = T.relleno, uncheckedThumbColor = T.terciario, uncheckedBorderColor = T.borde))
                }
                if (agenda) {
                    Spacer(Modifier.height(8.dp))
                    Opciones(listOf("existente" to L("Sesión agendada", "Scheduled session"), "nueva" to L("Agendar nueva", "Schedule new")), modoAgenda) { modoAgenda = it }
                    if (modoAgenda == "existente") {
                        if (childId == null) Text(L("Elegí primero el paciente.", "Choose the patient first."), style = MaterialTheme.typography.bodySmall, color = T.terciario, modifier = Modifier.padding(top = 8.dp))
                        else if (citas.isEmpty()) Text(L("Este paciente no tiene sesiones recientes.", "This patient has no recent sessions."), style = MaterialTheme.typography.bodySmall, color = T.terciario, modifier = Modifier.padding(top = 8.dp))
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            citas.take(12).forEach { c ->
                                val on = cita?.id == c.id
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) T.acento else T.relleno).presionable { cita = c }.padding(12.dp)) {
                                    Text(fechaLarga(c.fecha) + " · " + hora12(c.hora), style = MaterialTheme.typography.bodyMedium, color = if (on) Color.White else T.texto, modifier = Modifier.weight(1f))
                                    Text(c.servicio.orEmpty(), style = MaterialTheme.typography.bodySmall, color = if (on) Color.White else T.secundario, maxLines = 1)
                                }
                            }
                        }
                    } else {
                        Etiq(L("Hora de la sesión (fecha: la del cobro)", "Session time (date: the payment's)")); CampoHora(hora, { hora = it })
                    }
                }
            }

            error?.let { Text(it, color = T.peligro, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(18.dp))
            BotonGrande(L("REGISTRAR PAGO", "RECORD PAYMENT"), {
                val m = monto.toDoubleOrNull(); val a = adelanto.toDoubleOrNull() ?: 0.0
                error = when {
                    externo && nombreExt.isBlank() -> L("Escribe el nombre del niño/a.", "Enter the child's name.")
                    !externo && childId == null -> L("Selecciona un paciente.", "Select a patient.")
                    m == null || m <= 0 -> L("Ingresa un monto válido.", "Enter a valid amount.")
                    concepto.isBlank() -> L("Escribe el concepto.", "Enter the concept.")
                    estado == "partial" && (a <= 0 || a >= m) -> L("El adelanto debe ser mayor a 0 y menor que el total.", "The down payment must be greater than 0 and less than the total.")
                    agenda && !externo && modoAgenda == "existente" && cita == null -> L("Elige la sesión de la agenda.", "Choose the session from the schedule.")
                    else -> null
                }
                if (error != null) return@BotonGrande
                guardando = true
                val n = NuevoCobro(if (externo) null else childId, if (externo) nombreExt else null, m!!, a, concepto, metodo, estado, notas, fecha, responsable, esp,
                    if (agenda && !externo) modoAgenda else null, cita?.id, hora, cita?.fecha?.take(10)?.let { LocalDate.parse(it) })
                alcance.launch {
                    val r = runCatching { RepoPagos.crearCobro(n, ninos.firstOrNull { it.id == childId }?.tutor?.nombre, e.perfil?.id) }
                    guardando = false
                    if (r.isSuccess) onListo(true)
                    else error = if (r.exceptionOrNull()?.message == "agenda") L("No se pudo agendar la sesión.", "Could not add the session to the schedule.") else L("No se pudo guardar. Intenta de nuevo.", "Couldn't save. Try again.")
                }
            }, Modifier.fillMaxWidth(), icono = Icons.Rounded.Payments, cargando = guardando)
        }
    }
}

/** "Paquete de sesiones": días elegidos en el calendario, un cobro por día y (opcional) cada sesión en la agenda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaPaquete(ninos: List<NinoTutor>, tarifas: List<Tarifa>, especialistas: List<Especialista>, e: EstadoEquipo, onCerrar: () -> Unit, onListo: (Boolean, Int) -> Unit) {
    val alcance = rememberCoroutineScope()
    var externo by remember { mutableStateOf(false) }
    var childId by remember { mutableStateOf<String?>(null) }
    var nombreExt by remember { mutableStateOf("") }
    var concepto by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf("paid") }
    var metodo by remember { mutableStateOf("efectivo") }
    var esp by remember { mutableStateOf<String?>(null) }
    var responsable by remember { mutableStateOf("") }
    var mes by remember { mutableStateOf(YearMonth.now()) }
    var dias by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var agendar by remember { mutableStateOf(false) }
    var hora by remember { mutableStateOf("09:00") }
    var horas by remember { mutableStateOf<Map<LocalDate, String>>(emptyMap()) }
    var conSesion by remember { mutableStateOf<Set<String>>(emptySet()) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(agendar, childId, externo, mes) {
        conSesion = if (agendar && !externo && childId != null)
            runCatching { RepoPagos.citasEnDias(childId!!, (1..mes.lengthOfMonth()).map { mes.atDay(it).toString() }).keys }.getOrDefault(emptySet()) else emptySet()
    }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(L("Paquete de sesiones", "Session package"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Etiq(L("Paciente", "Patient"))
            ElegirPaciente(ninos, externo, { externo = it }, childId, { childId = it }, nombreExt) { nombreExt = it }
            Etiq(L("Concepto", "Concept")); Concepto(concepto, { concepto = it }, tarifas, e.moneda) { monto = it }
            Etiq(L("Monto por sesión", "Amount per session")); Campo(monto, { monto = it.replace(',', '.') }, "0.00", numerico = true)
            Etiq(L("Estado", "Status"))
            Opciones(RepoPagos.ESTADOS.filter { it != "partial" }.map { it to estadoTexto(it) }, estado, punto = { estadoPago(it).second }) { estado = it }
            Etiq(L("Método", "Method")); Opciones(METODOS.map { (c, n) -> c to L(n.first, n.second) }, metodo) { metodo = it }
            if (especialistas.isNotEmpty()) { Etiq(L("Especialista", "Specialist")); ElegirEspecialista(especialistas, esp) { esp = it } }
            Etiq(L("Responsable de pago", "Payer"))
            Campo(responsable, { responsable = it }, ninos.firstOrNull { it.id == childId }?.tutor?.nombre ?: L("Nombre de quien paga", "Name of who pays"))

            Etiq(L("Días de las sesiones (${dias.size})", "Session days (${dias.size})"))
            CalendarioMulti(mes, { mes = it }, dias, conSesion) { d -> dias = if (d in dias) dias - d else dias + d }
            val m = monto.toDoubleOrNull() ?: 0.0
            if (dias.isNotEmpty()) Text(L("${dias.size} sesiones · total ${monedaFmt(m * dias.size, e.moneda)}", "${dias.size} sessions · total ${monedaFmt(m * dias.size, e.moneda)}"),
                style = MaterialTheme.typography.titleSmall, color = T.acento, modifier = Modifier.padding(top = 8.dp))

            if (!externo) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp).background(T.acentoSuave, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = T.acento); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L("Agendar las sesiones", "Schedule the sessions"), style = MaterialTheme.typography.titleSmall, color = T.texto)
                        Text(L("Los días que ya tienen sesión (con punto) se vinculan a ella. Las nuevas van a los calendarios conectados.",
                            "Days that already have a session (dot) are linked to it. New ones go to connected calendars."), style = MaterialTheme.typography.bodySmall, color = T.secundario)
                    }
                    Switch(agendar, { agendar = it }, colors = SwitchDefaults.colors(checkedTrackColor = T.acento, uncheckedTrackColor = T.relleno, uncheckedThumbColor = T.terciario, uncheckedBorderColor = T.borde))
                }
                if (agendar) {
                    Etiq(L("Hora de las sesiones", "Sessions time")); CampoHora(hora, { hora = it })
                    dias.sorted().filter { it.toString() !in conSesion }.forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(fechaLarga(d.toString()), style = MaterialTheme.typography.bodyMedium, color = T.texto, modifier = Modifier.weight(1f))
                            CampoHora(horas[d] ?: hora, { horas = horas + (d to it) }, Modifier.width(140.dp))
                        }
                    }
                }
            }

            error?.let { Text(it, color = T.peligro, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(18.dp))
            BotonGrande(L("CREAR PAQUETE", "CREATE PACKAGE"), {
                error = when {
                    externo && nombreExt.isBlank() -> L("Escribe el nombre del niño/a.", "Enter the child's name.")
                    !externo && childId == null -> L("Selecciona un paciente.", "Select a patient.")
                    m <= 0 -> L("Ingresa el monto por sesión.", "Enter the amount per session.")
                    concepto.isBlank() -> L("Escribe el concepto.", "Enter the concept.")
                    dias.isEmpty() -> L("Selecciona al menos un día.", "Select at least one day.")
                    else -> null
                }
                if (error != null) return@BotonGrande
                guardando = true
                val n = NuevoPaquete(if (externo) null else childId, if (externo) nombreExt else null, m, concepto, metodo, estado, responsable, esp,
                    dias.toList(), agendar && !externo, hora, horas)
                alcance.launch {
                    val r = runCatching { RepoPagos.crearPaquete(n, ninos.firstOrNull { it.id == childId }?.tutor?.nombre, e.perfil?.id, L("Paquete de ${dias.size} sesiones", "Package of ${dias.size} sessions")) }
                    guardando = false
                    if (r.isSuccess) onListo(true, dias.size)
                    else error = if (r.exceptionOrNull()?.message == "agenda") L("No se pudieron agendar las sesiones.", "Could not add the sessions to the schedule.") else L("No se pudo guardar. Intenta de nuevo.", "Couldn't save. Try again.")
                }
            }, Modifier.fillMaxWidth(), icono = Icons.Rounded.Inventory2, cargando = guardando)
        }
    }
}

/** Calendario del mes para elegir varios días (los que ya tienen sesión llevan un punto). */
@Composable
private fun CalendarioMulti(mes: YearMonth, onMes: (YearMonth) -> Unit, sel: Set<LocalDate>, conSesion: Set<String>, onDia: (LocalDate) -> Unit) {
    val loc = if (EN) Locale.ENGLISH else Locale.forLanguageTag("es")
    Column(Modifier.fillMaxWidth().background(T.relleno, RoundedCornerShape(20.dp)).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onMes(mes.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = T.secundario) }
            Text(mes.month.getDisplayName(TextStyle.FULL, loc).replaceFirstChar { it.titlecase(loc) } + " " + mes.year,
                style = MaterialTheme.typography.titleSmall, color = T.texto, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton({ onMes(mes.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = T.secundario) }
        }
        val vacios = mes.atDay(1).dayOfWeek.value % 7
        (0 until (vacios + mes.lengthOfMonth() + 6) / 7).forEach { s ->
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { c ->
                    val n = s * 7 + c - vacios + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (n in 1..mes.lengthOfMonth()) {
                            val d = mes.atDay(n)
                            val on = d in sel
                            Column(Modifier.fillMaxSize().clip(CircleShape).then(if (on) Modifier.background(MarcaDegradado) else Modifier).presionable { onDia(d) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("$n", style = MaterialTheme.typography.bodyMedium, color = if (on) Color.White else T.texto)
                                if (d.toString() in conSesion) Box(Modifier.size(4.dp).background(if (on) Color.White else T.acento, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaTarifa(t: Tarifa?, onCerrar: () -> Unit, onGuardar: (String, String, Double, Int) -> Unit) {
    var nombre by remember { mutableStateOf(t?.name.orEmpty()) }
    var desc by remember { mutableStateOf(t?.description.orEmpty()) }
    var monto by remember { mutableStateOf(t?.amount?.toString().orEmpty()) }
    var min by remember { mutableStateOf((t?.duracion ?: 60).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (t == null) L("Nueva tarifa", "New rate") else L("Editar tarifa", "Edit rate"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Etiq(L("Servicio", "Service")); Campo(nombre, { nombre = it }, L("Ej: Sesión ABA", "E.g. ABA session"))
            Etiq(L("Descripción", "Description")); Campo(desc, { desc = it }, L("Opcional", "Optional"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { Etiq(L("Monto", "Amount")); Campo(monto, { monto = it.replace(',', '.') }, "0.00", numerico = true) }
                Column(Modifier.weight(1f)) { Etiq(L("Duración (min)", "Duration (min)")); Campo(min, { min = it }, "60", numerico = true) }
            }
            error?.let { Text(it, color = T.peligro, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(18.dp))
            BotonGrande(L("GUARDAR", "SAVE"), {
                val m = monto.toDoubleOrNull()
                error = when { nombre.isBlank() -> L("Escribe el nombre del servicio.", "Enter the service name."); m == null -> L("Ingresa un monto válido.", "Enter a valid amount."); else -> null }
                if (error == null) onGuardar(nombre, desc, m!!, min.toIntOrNull() ?: 60)
            }, Modifier.fillMaxWidth())
        }
    }
}
