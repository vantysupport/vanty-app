package xyz.vanty.aba.ui.equipo

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import xyz.vanty.aba.data.Paciente
import xyz.vanty.aba.data.Pedido
import xyz.vanty.aba.data.Producto
import xyz.vanty.aba.data.Recurso
import xyz.vanty.aba.data.RepoAdmin
import xyz.vanty.aba.data.Terapia
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.BotonGrande
import xyz.vanty.aba.ui.comp.CabeceraSub
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.IconoTono
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.Vacio
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.fechaCorta
import xyz.vanty.aba.util.monedaFmt

/**
 * Recursos adicionales del equipo: materiales para familias, pedidos de la tienda del centro y catálogo de
 * terapias. Cada pestaña aparece según el plan (recursos_recursos / recursos_tienda / recursos_terapias).
 * La edición completa del catálogo y de los productos queda en la PC.
 */
@Composable
fun RecursosEquipoScreen(e: EstadoEquipo, app: Estado, vm: EquipoViewModel, pad: PaddingValues) {
    val pestanas = listOfNotNull(
        ("recursos" to L("Recursos", "Resources")).takeIf { app.on("recursos_recursos") },
        ("tienda" to L("Tienda", "Store")).takeIf { app.on("recursos_tienda") },
        ("terapias" to L("Terapias", "Therapies")).takeIf { app.on("recursos_terapias") },
    )
    var sel by rememberSaveable { mutableStateOf(pestanas.firstOrNull()?.first ?: "recursos") }
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var recursos by remember { mutableStateOf<List<Recurso>?>(null) }
    var pedidos by remember { mutableStateOf<List<Pedido>?>(null) }
    var productos by remember { mutableStateOf<List<Producto>?>(null) }
    var terapias by remember { mutableStateOf<List<Terapia>?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    LaunchedEffect(sel) {
        when (sel) {
            "recursos" -> recursos = runCatching { RepoAdmin.recursosCentro() }.getOrDefault(emptyList())
            "tienda" -> { pedidos = runCatching { RepoAdmin.pedidos() }.getOrDefault(emptyList()); productos = runCatching { RepoAdmin.productos() }.getOrDefault(emptyList()) }
            "terapias" -> terapias = runCatching { RepoAdmin.terapias() }.getOrDefault(emptyList())
        }
    }

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item { CabeceraSub(L("Recursos adicionales", "Additional resources")) { vm.irA(TabEquipo.Mas) } }
        if (pestanas.size > 1) item {
            Row(Modifier.fillMaxWidth().background(T.relleno, RoundedCornerShape(16.dp)).padding(4.dp)) {
                pestanas.forEach { (clave, t) ->
                    val activo = clave == sel
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (activo) T.tarjeta else Color.Transparent)
                        .presionable { sel = clave }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(t, style = MaterialTheme.typography.titleSmall, color = if (activo) T.acento else T.secundario)
                    }
                }
            }
        }
        when (sel) {
            "recursos" -> {
                item { BotonGrande(L("COMPARTIR UN RECURSO", "SHARE A RESOURCE"), { nuevo = true }, Modifier.fillMaxWidth(), icono = Icons.Rounded.Add) }
                if (recursos?.isEmpty() == true) item { Tarjeta { Vacio(Aria.SENTADA, L("Sin recursos", "No resources"), L("Comparte guías, videos o enlaces con las familias.", "Share guides, videos or links with families.")) } }
                itemsIndexed(recursos.orEmpty(), key = { _, r -> r.id }) { i, r ->
                    Tarjeta(Modifier.aparecer(i.coerceAtMost(12)), onClick = { r.url?.let { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } } }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconoTono(Icons.AutoMirrored.Rounded.MenuBook, T.exito.copy(alpha = 0.14f), T.exito)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto)
                                r.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 2) }
                            }
                            Etiqueta(if (r.global == true) L("Todas", "All") else L("Una familia", "One family"), T.acentoSuave, T.acento)
                        }
                    }
                }
            }
            "tienda" -> {
                item { Seccion(L("Pedidos", "Orders"), "${pedidos?.size ?: 0}") }
                if (pedidos?.isEmpty() == true) item { Tarjeta { Text(L("Todavía no hay pedidos.", "No orders yet."), color = T.secundario) } }
                itemsIndexed(pedidos.orEmpty(), key = { _, p -> p.id }) { i, p ->
                    Tarjeta(Modifier.aparecer(i.coerceAtMost(12))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.familia ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto)
                                Text(p.items.joinToString(" · ") { "${it.cantidad}× ${it.producto}" }, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 2)
                                p.fecha?.let { val (d, m) = fechaCorta(it); Text("$d $m", style = MaterialTheme.typography.labelSmall, color = T.terciario) }
                            }
                            Text(monedaFmt(p.total, e.moneda), style = MaterialTheme.typography.titleMedium, color = T.texto)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("pendiente" to L("Pendiente", "Pending"), "confirmado" to L("Confirmado", "Confirmed"), "listo" to L("Listo", "Ready"),
                                "entregado" to L("Entregado", "Delivered"), "cancelado" to L("Cancelado", "Cancelled")).forEach { (k, t) ->
                                val activo = p.estado == k
                                Text(t, style = MaterialTheme.typography.labelMedium, color = if (activo) Color.White else T.secundario,
                                    modifier = Modifier.presionable {
                                        alcance.launch {
                                            runCatching { RepoAdmin.estadoPedido(p.id, k) }
                                            pedidos = pedidos?.map { if (it.id == p.id) it.copy(estado = k) else it }
                                        }
                                    }.background(if (activo) T.acento else T.relleno, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
                item { Seccion(L("Productos", "Products"), "${productos?.size ?: 0}") }
                itemsIndexed(productos.orEmpty(), key = { _, p -> "p" + p.id }) { i, p ->
                    Row(Modifier.aparecer(i.coerceAtMost(12)).fillMaxWidth().background(T.tarjeta, RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.nombre ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto)
                            Text(listOfNotNull(monedaFmt(p.precio, e.moneda), p.stock?.let { L("stock $it", "stock $it") }, p.categoria).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = T.secundario)
                        }
                        Switch(p.activo != false, { on ->
                            alcance.launch {
                                runCatching { RepoAdmin.activarProducto(p.id, on) }
                                productos = productos?.map { if (it.id == p.id) it.copy(activo = on) else it }
                            }
                        }, colors = SwitchDefaults.colors(checkedTrackColor = T.exito))
                    }
                }
            }
            else -> {
                if (terapias?.isEmpty() == true) item { Tarjeta { Text(L("El catálogo de terapias se arma desde la PC.", "The therapy catalog is set up on the desktop."), color = T.secundario) } }
                itemsIndexed(terapias.orEmpty(), key = { _, t -> t.id }) { i, t ->
                    Tarjeta(Modifier.aparecer(i.coerceAtMost(12))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconoTono(Icons.Rounded.Spa, T.acentoSuave, T.acento)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.nombre ?: "—", style = MaterialTheme.typography.titleSmall, color = if (t.activo == false) T.terciario else T.texto)
                                Text(listOfNotNull(t.duracion, t.modalidad, t.precio?.let { monedaFmt(it, t.moneda ?: e.moneda) }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = T.secundario)
                            }
                            if (t.activo == false) Etiqueta(L("Oculta", "Hidden"), T.relleno, T.terciario)
                        }
                        t.descripcion?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 3, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
            }
        }
    }
    if (nuevo) HojaRecurso(e.pacientes, { titulo, desc, url, tipo, childId ->
        alcance.launch {
            val ok = RepoAdmin.crearRecurso(titulo, desc, url, tipo, childId)
            vm.aviso(if (ok) L("¡Recurso compartido!", "Resource shared!") else L("No se pudo compartir.", "Couldn't share."))
            if (ok) { recursos = RepoAdmin.recursosCentro(); nuevo = false }
        }
    }) { nuevo = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaRecurso(pacientes: List<Paciente>, onGuardar: (String, String?, String, String, String?) -> Unit, onCerrar: () -> Unit) {
    var titulo by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("link") }
    var para by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.tarjeta) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(L("Compartir recurso", "Share resource"), style = MaterialTheme.typography.headlineSmall, color = T.texto)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(titulo, { titulo = it }, label = { Text(L("Título", "Title")) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(desc, { desc = it }, label = { Text(L("Descripción", "Description")) }, maxLines = 3, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(url, { url = it }, label = { Text(L("Enlace (video, PDF, página)", "Link (video, PDF, page)")) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("link" to L("Enlace", "Link"), "video" to "Video", "pdf" to "PDF", "audio" to "Audio", "document" to L("Documento", "Document")).forEach { (k, t) ->
                    Text(t, style = MaterialTheme.typography.labelLarge, color = if (k == tipo) Color.White else T.secundario,
                        modifier = Modifier.presionable { tipo = k }.background(if (k == tipo) T.acento else T.relleno, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(L("¿Para quién?", "For whom?"), style = MaterialTheme.typography.labelMedium, color = T.secundario)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf<Pair<String?, String>>(null to L("Todas las familias", "All families")) + pacientes.map { it.id to it.nombre }).forEach { (id, t) ->
                    Text(t, style = MaterialTheme.typography.labelLarge, color = if (id == para) Color.White else T.secundario,
                        modifier = Modifier.presionable { para = id }.background(if (id == para) T.acento else T.relleno, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            BotonGrande(L("COMPARTIR", "SHARE"), { onGuardar(titulo, desc, url, tipo, para) }, Modifier.fillMaxWidth(),
                habilitado = titulo.isNotBlank() && url.isNotBlank(), icono = Icons.Rounded.Add)
        }
    }
}
