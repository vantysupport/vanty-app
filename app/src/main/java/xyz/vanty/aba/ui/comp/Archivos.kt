package xyz.vanty.aba.ui.comp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Abre un archivo descargado (informe Word/PDF) con la app de documentos del teléfono. */
fun abrirArchivoLocal(ctx: Context, f: File, mime: String?): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", f)
    val tipo = mime ?: when (f.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "*/*"
    }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, tipo).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name))
    true
}.getOrDefault(false)

/** Abre un enlace (URL firmada de un archivo o recurso web) con la app adecuada. */
fun abrirEnlace(ctx: Context, url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
