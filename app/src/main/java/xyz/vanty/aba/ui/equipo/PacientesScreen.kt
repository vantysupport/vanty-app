package xyz.vanty.aba.ui.equipo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import io.github.jan.supabase.postgrest.from
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import xyz.vanty.aba.data.Paciente
import xyz.vanty.aba.data.Programa
import xyz.vanty.aba.ui.comp.Aria
import xyz.vanty.aba.ui.comp.Etiqueta
import xyz.vanty.aba.ui.comp.Tarjeta
import xyz.vanty.aba.ui.comp.Vacio
import xyz.vanty.aba.ui.comp.aparecer
import xyz.vanty.aba.ui.comp.presionable
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import xyz.vanty.aba.util.calcularEdad

// Un degradado por paciente (según su nombre) para que cada avatar sea reconocible
private val PALETAS = listOf(
    listOf(Color(0xFF01ABFC), Color(0xFF0063D8)), listOf(Color(0xFF38BDF8), Color(0xFF0369A1)),
    listOf(Color(0xFF60A5FA), Color(0xFF2563EB)), listOf(Color(0xFF3B82F6), Color(0xFF1E40AF)),
    listOf(Color(0xFF7DD3FC), Color(0xFF0EA5E9)), listOf(Color(0xFF0EA5E9), Color(0xFF1D4ED8)),
)

@Composable
fun Avatar(nombre: String, tam: androidx.compose.ui.unit.Dp = 46.dp) {
    val p = PALETAS[(nombre.hashCode() and 0x7fffffff) % PALETAS.size]
    Box(Modifier.size(tam).background(Brush.linearGradient(p), CircleShape), contentAlignment = Alignment.Center) {
        Text(nombre.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
            color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacientesScreen(e: EstadoEquipo, vm: EquipoViewModel, pad: PaddingValues) {
    var q by rememberSaveable { mutableStateOf("") }
    var ficha by remember { mutableStateOf<Paciente?>(null) }
    val lista = e.pacientes.filter { q.isBlank() || it.nombre.contains(q.trim(), ignoreCase = true) }
    PullToRefreshBox(e.refrescando, vm::refrescar, Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            item {
                OutlinedTextField(
                    q, { q = it }, placeholder = { Text(L("Buscar paciente", "Search patient")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = T.tarjeta, focusedContainerColor = T.tarjeta, unfocusedBorderColor = T.borde, focusedBorderColor = T.acento),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Seccion(L("Pacientes activos", "Active patients"), "${lista.size}") }
            if (lista.isEmpty() && !e.cargando) item { Tarjeta { Vacio(Aria.NEUTRAL, L("Sin resultados", "No results"), L("Prueba con otro nombre.", "Try another name.")) } }
            itemsIndexed(lista, key = { _, p -> p.id }) { i, p ->
                Row(
                    Modifier.aparecer(i.coerceAtMost(12)).animateItem().fillMaxWidth().presionable { ficha = p }
                        .background(T.tarjeta, RoundedCornerShape(18.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(p.nombre)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.nombre, style = MaterialTheme.typography.titleMedium, color = T.texto)
                        Text(listOfNotNull(calcularEdad(p.nacimiento)?.let { L("$it años", "$it y/o") }, p.diagnosis).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = T.secundario, maxLines = 1)
                    }
                }
            }
        }
    }
    ficha?.let { FichaPaciente(it, vm) { ficha = null } }
}

/** Ficha del paciente con pestañas como la web: ABA (programas, gráfica y sesiones), Info, Informes y Documentos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FichaPaciente(p: Paciente, vm: EquipoViewModel, onCerrar: () -> Unit) {
    var programas by remember { mutableStateOf<List<ProgramaAba>?>(null) }
    var recargar by remember { mutableStateOf(0) }
    var pestana by rememberSaveable { mutableStateOf("aba") }
    var registrar by remember { mutableStateOf<ProgramaAba?>(null) }
    LaunchedEffect(p.id, recargar) { programas = runCatching { RepoAba.programas(p.id) }.getOrDefault(emptyList()) }
    ModalBottomSheet(onCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = T.fondo) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.nombre, 60.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(p.nombre, style = MaterialTheme.typography.headlineSmall, color = T.texto)
                    Text(listOfNotNull(calcularEdad(p.nacimiento)?.let { L("$it años", "$it years old") }, p.diagnosis).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = T.secundario)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("aba" to L("ABA", "ABA"), "info" to L("Info", "Info"), "informes" to L("Informes", "Reports"), "docs" to L("Documentos", "Documents")).forEach { (k, t) ->
                    Text(
                        t, style = MaterialTheme.typography.labelLarge, color = if (pestana == k) Color.White else T.texto,
                        modifier = Modifier.presionable { pestana = k }.background(if (pestana == k) T.acento else T.tarjeta, CircleShape).padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            when (pestana) {
                "aba" -> {
                    val lista = programas
                    val activos = lista.orEmpty().count { !it.criterioAlcanzado }
                    val logrados = lista.orEmpty().size - activos
                    if (lista != null) {
                        Text(L("$activos en curso · $logrados con criterio alcanzado", "$activos in progress · $logrados criterion met"),
                            style = MaterialTheme.typography.bodySmall, color = T.secundario)
                        Spacer(Modifier.height(8.dp))
                    }
                    when {
                        lista == null -> LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = T.acento, trackColor = T.relleno)
                        lista.isEmpty() -> Text(L("Sin programas asignados.", "No programs assigned."), color = T.secundario, style = MaterialTheme.typography.bodyMedium)
                        else -> lista.forEach { pr -> TarjetaProgramaAba(pr) { registrar = pr } }
                    }
                }
                "info" -> InfoPaciente(p)
                "informes" -> ListaInformes(p.id)
                "docs" -> ListaDocumentos(p.id)
            }
        }
    }
    registrar?.let { pr ->
        HojaRegistrarSesion(pr, p.id, { registrar = null }) {
            registrar = null; recargar++
            vm.aviso(L("Sesión registrada", "Session logged"))
        }
    }
}

@kotlinx.serialization.Serializable
private data class FichaInfo(
    val name: String? = null,
    val apodo: String? = null,
    @kotlinx.serialization.SerialName("birth_date") val nacimiento: String? = null,
    val diagnosis: String? = null,
    @kotlinx.serialization.Serializable(with = xyz.vanty.aba.data.TextoFlexible::class) val notas: String? = null,
    @kotlinx.serialization.Serializable(with = xyz.vanty.aba.data.TextoFlexible::class) val notes: String? = null,
    @kotlinx.serialization.SerialName("parent_id") val padreId: String? = null,
    @kotlinx.serialization.SerialName("created_at") val creado: String? = null,
)

@kotlinx.serialization.Serializable
private data class PadreInfo(
    @kotlinx.serialization.SerialName("full_name") val nombre: String? = null,
    val email: String? = null,
    val phone: String? = null,
)

/** Datos del paciente y de su familia (pestaña Info de la web). */
@Composable
private fun InfoPaciente(p: Paciente) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var info by remember { mutableStateOf<FichaInfo?>(null) }
    var padre by remember { mutableStateOf<PadreInfo?>(null) }
    LaunchedEffect(p.id) {
        runCatching {
            val sb = xyz.vanty.aba.data.Backend.supabase
            val f = sb.from("children").select(io.github.jan.supabase.postgrest.query.Columns.list("name", "apodo", "birth_date", "diagnosis", "notas", "notes", "parent_id", "created_at")) {
                filter { eq("id", p.id) }
            }.decodeSingleOrNull<FichaInfo>()
            info = f
            f?.padreId?.let { id ->
                padre = sb.from("profiles").select(io.github.jan.supabase.postgrest.query.Columns.list("full_name", "email", "phone")) { filter { eq("id", id) } }.decodeSingleOrNull<PadreInfo>()
            }
        }
    }
    val i = info
    Tarjeta {
        Dato(L("Nombre", "Name"), i?.name ?: p.nombre)
        i?.apodo?.takeIf { it.isNotBlank() }?.let { Dato(L("Apodo", "Nickname"), it) }
        Dato(L("Fecha de nacimiento", "Date of birth"), (i?.nacimiento ?: p.nacimiento)?.take(10) ?: "—")
        Dato(L("Diagnóstico", "Diagnosis"), i?.diagnosis ?: p.diagnosis ?: "—")
        (i?.notas ?: i?.notes)?.takeIf { it.isNotBlank() }?.let { Dato(L("Notas", "Notes"), it) }
        i?.creado?.let { Dato(L("En el centro desde", "At the center since"), it.take(10)) }
    }
    padre?.let { pa ->
        Spacer(Modifier.height(12.dp))
        Tarjeta {
            Text(L("Familia", "Family"), style = MaterialTheme.typography.titleMedium, color = T.texto)
            Dato(L("Nombre", "Name"), pa.nombre ?: "—")
            pa.email?.let { Dato(L("Correo", "Email"), it) }
            pa.phone?.let { Dato(L("Teléfono", "Phone"), it) }
            val tel = pa.phone?.filter { it.isDigit() }.orEmpty()
            if (tel.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    L("Escribir por WhatsApp", "Message on WhatsApp"), style = MaterialTheme.typography.labelLarge, color = T.acento,
                    modifier = Modifier.presionable {
                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$tel")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }.background(T.acentoSuave, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Dato(titulo: String, valor: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelSmall, color = T.terciario)
        Text(valor, style = MaterialTheme.typography.bodyMedium, color = T.texto)
    }
}

/** Informes generados (Word) de un paciente o, con `childId` null, los últimos del centro. */
@Composable
fun ListaInformes(childId: String?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    var informes by remember { mutableStateOf<List<xyz.vanty.aba.data.Informe>?>(null) }
    var abriendo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(childId) { informes = runCatching { xyz.vanty.aba.data.RepoEquipo.informes(childId) }.getOrDefault(emptyList()) }
    Spacer(Modifier.height(14.dp))
    Text(L("Informes y evaluaciones", "Reports and evaluations"), style = MaterialTheme.typography.titleMedium, color = T.texto)
    Spacer(Modifier.height(8.dp))
    if (informes?.isEmpty() == true) Text(L("Sin informes todavía.", "No reports yet."), color = T.secundario, style = MaterialTheme.typography.bodyMedium)
    informes.orEmpty().forEach { inf ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).background(T.tarjeta, RoundedCornerShape(16.dp))
                .presionable(habilitado = abriendo == null) {
                    abriendo = inf.id
                    alcance.launch {
                        val f = runCatching { xyz.vanty.aba.data.RepoEquipo.archivoInforme(ctx, inf.id) }.getOrNull()
                        abriendo = null
                        if (f == null || !xyz.vanty.aba.ui.comp.abrirArchivoLocal(ctx, f, inf.mime))
                            android.widget.Toast.makeText(ctx, L("Instala una app para abrir documentos Word.", "Install an app to open Word documents."), android.widget.Toast.LENGTH_LONG).show()
                    }
                }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            xyz.vanty.aba.ui.comp.IconoTono(androidx.compose.material.icons.Icons.Rounded.Description, T.acentoSuave, T.acento, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(inf.titulo ?: inf.archivo ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto, maxLines = 2)
                Text(listOfNotNull(if (childId == null) inf.children?.name else null, inf.fecha?.let { xyz.vanty.aba.util.fechaCorta(it).let { (d, m) -> "$d $m" } }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = T.secundario)
            }
            if (abriendo == inf.id) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = T.acento)
        }
    }
}

/** Documentos subidos al expediente del paciente (se abren con un enlace temporal firmado). */
@Composable
private fun ListaDocumentos(childId: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val alcance = androidx.compose.runtime.rememberCoroutineScope()
    var docs by remember { mutableStateOf<List<xyz.vanty.aba.data.Documento>?>(null) }
    LaunchedEffect(childId) { docs = runCatching { xyz.vanty.aba.data.RepoFamilia.documentos(childId) }.getOrDefault(emptyList()) }
    if (docs.isNullOrEmpty()) return
    Spacer(Modifier.height(14.dp))
    Text(L("Documentos", "Documents"), style = MaterialTheme.typography.titleMedium, color = T.texto)
    Spacer(Modifier.height(8.dp))
    docs.orEmpty().forEach { d ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).background(T.tarjeta, RoundedCornerShape(16.dp))
                .presionable { alcance.launch { xyz.vanty.aba.data.Comun.abrirArchivo(d.url)?.let { xyz.vanty.aba.ui.comp.abrirEnlace(ctx, it) } } }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            xyz.vanty.aba.ui.comp.IconoTono(androidx.compose.material.icons.Icons.Rounded.Folder, T.aviso.copy(alpha = 0.15f), T.aviso, 36.dp)
            Spacer(Modifier.width(10.dp))
            Text(d.nombre ?: "—", style = MaterialTheme.typography.titleSmall, color = T.texto, modifier = Modifier.weight(1f), maxLines = 2)
        }
    }
}
