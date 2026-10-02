package xyz.vanty.aba.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.vanty.aba.data.Comun
import xyz.vanty.aba.data.Documento
import xyz.vanty.aba.data.Recurso
import xyz.vanty.aba.data.RepoFamilia
import xyz.vanty.aba.ui.AppViewModel
import xyz.vanty.aba.ui.Estado
import xyz.vanty.aba.ui.Pestana
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.Baldosa
import xyz.vanty.aba.ui.comp.CabeceraSub
import xyz.vanty.aba.ui.comp.IconoTono
import xyz.vanty.aba.ui.comp.Mosaico
import xyz.vanty.aba.ui.comp.Pintura
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.Vacio
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.fechaCorta

/** "Más" de las familias: todo lo que no cabe en la barra, en tarjetas grandes de colores. */
@Composable
fun MasFamiliaScreen(e: Estado, vm: AppViewModel, pad: PaddingValues) {
    var pendientes by remember { mutableStateOf(0) }
    var estadoEval by remember { mutableStateOf<String?>("cargando") }
    LaunchedEffect(e.hijo?.id) {
        val h = e.hijo; val p = e.perfil
        estadoEval = if (h != null && p != null) runCatching { xyz.vanty.aba.data.EvaluacionInicial.obtener(h.id, p.id)?.estado ?: "pendiente_intake" }.getOrNull() else null
    }
    val evalPendiente = estadoEval != null && estadoEval !in setOf("cargando", "revisado", "completado", "terapia_seleccionada")
    LaunchedEffect(e.perfil?.id) {
        val hoy = xyz.vanty.aba.util.hoyIso()
        pendientes = e.perfil?.id?.let { id -> runCatching { xyz.vanty.aba.data.Formularios.deFamilia(id) }.getOrDefault(emptyList())
            .count { it.status in setOf("pending", "assigned", "enviado") && (it.deadline == null || it.deadline >= hoy) } } ?: 0
    }
    val baldosas = listOfNotNull(
        Baldosa("evaluacion", L("Evaluación inicial", "Initial evaluation"), L("Complétala paso a paso", "Complete it step by step"),
            Icons.Rounded.Checklist, Pintura.turquesa, insignia = 1).takeIf { evalPendiente },
        Baldosa("practicar", L("Practicar", "Practice"), L("Plan semanal y programas", "Weekly plan & programs"), Icons.Rounded.SportsScore, Pintura.naranja),
        Baldosa("recursos", L("Recursos", "Resources"), L("Guías y materiales", "Guides and materials"), Icons.AutoMirrored.Rounded.MenuBook, Pintura.verde),
        Baldosa("formularios", L("Formularios", "Forms"), if (pendientes > 0) L("$pendientes por completar", "$pendientes to complete") else L("Al día ✓", "All done ✓"),
            Icons.AutoMirrored.Rounded.Assignment, Pintura.rosa, insignia = pendientes),
        Baldosa("documentos", L("Documentos", "Documents"), L("Informes y archivos", "Reports and files"), Icons.Rounded.Folder, Pintura.morado),
        Baldosa("perfil", L("Mi perfil", "My profile"), L("Idioma, avisos y cuenta", "Language, alerts, account"), Icons.Rounded.Person, Pintura.azul),
    )
    LazyColumn(contentPadding = pad) {
        item {
            Text(L("Más para ti", "More for you"), style = MaterialTheme.typography.headlineSmall, color = T.texto, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            Mosaico(baldosas) { b ->
                vm.irA(when (b.clave) {
                    "practicar" -> Pestana.Practicar; "recursos" -> Pestana.Recursos
                    "documentos" -> Pestana.Documentos; "formularios" -> Pestana.Formularios; "evaluacion" -> Pestana.Evaluacion; else -> Pestana.Perfil
                })
            }
        }
    }
}

@Composable
fun RecursosScreen(e: Estado, vm: AppViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    var lista by remember { mutableStateOf<List<Recurso>?>(null) }
    LaunchedEffect(e.perfil?.id) {
        val p = e.perfil ?: return@LaunchedEffect
        lista = runCatching { RepoFamilia.recursos(p.id, e.hijos.map { it.id }) }.getOrDefault(emptyList())
    }
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { CabeceraSub(L("Recursos", "Resources")) { vm.irA(Pestana.Mas) } }
        if (lista?.isEmpty() == true) item { Tarjeta { Vacio(Aria.SENTADA, L("Aún no hay recursos", "No resources yet"), L("Tu centro compartirá aquí guías, videos y materiales.", "Your center will share guides, videos and materials here.")) } }
        itemsIndexed(lista.orEmpty(), key = { _, r -> r.id }) { i, r ->
            val (icono, tono) = when (r.tipo) {
                "video" -> Icons.Rounded.PlayCircle to T.peligro
                "audio" -> Icons.Rounded.Headphones to T.acento
                "pdf" -> Icons.Rounded.PictureAsPdf to T.peligro
                "image" -> Icons.Rounded.Image to T.exito
                "link" -> Icons.Rounded.Link to T.acento
                else -> Icons.Rounded.Description to T.aviso
            }
            Tarjeta(Modifier.aparecer(i), onClick = {
                r.url?.let { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconoTono(icono, tono.copy(alpha = 0.14f), tono)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto)
                        r.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 2) }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentosScreen(e: Estado, vm: AppViewModel, pad: PaddingValues) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<Documento>?>(null) }
    LaunchedEffect(e.hijo?.id) {
        val h = e.hijo ?: return@LaunchedEffect
        lista = runCatching { RepoFamilia.documentos(h.id) }.getOrDefault(emptyList())
    }
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { CabeceraSub(L("Documentos", "Documents")) { vm.irA(Pestana.Mas) } }
        if (lista?.isEmpty() == true) item { Tarjeta { Vacio(Aria.SENTADA, L("Sin documentos", "No documents"), L("Aquí verás los informes y archivos de ${e.hijo?.primerNombre ?: "tu peque"}.", "${e.hijo?.primerNombre ?: "Your child"}'s reports and files will appear here.")) } }
        itemsIndexed(lista.orEmpty(), key = { _, d -> d.id }) { i, d ->
            Row(
                Modifier.aparecer(i).fillMaxWidth().background(T.tarjeta, RoundedCornerShape(18.dp))
                    .presionable { alcance.launch { Comun.abrirArchivo(d.url)?.let { u -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } } } }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pdf = d.tipo?.contains("pdf") == true || d.nombre?.endsWith(".pdf", true) == true
                IconoTono(if (pdf) Icons.Rounded.PictureAsPdf else Icons.Rounded.Description, T.acentoSuave, T.acento)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.nombre ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto, maxLines = 2)
                    Text(listOfNotNull(d.subidoPor, d.fecha?.let { val (dd, m) = fechaCorta(it); "$dd $m" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = T.secundario)
                }
            }
        }
    }
}
