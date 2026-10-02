package xyz.vanty.aba.ui.comp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import xyz.vanty.aba.BuildConfig
import xyz.vanty.aba.data.Backend
import xyz.vanty.aba.ui.theme.T
import xyz.vanty.aba.util.L
import java.io.File

/**
 * Apartado del equipo mostrado con la pantalla real de vanty.xyz (modo app: ?embebido=1 oculta el menú
 * y la cabecera de la web). La sesión de la app se pasa como la cookie de Supabase, así no se vuelve a
 * pedir la contraseña. Se envía solo el token de acceso (renovado al abrir): la web nunca usa el token de
 * renovación de la app, así no se cierra la sesión del teléfono.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PanelWeb(panel: String, vista: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var listo by remember(panel, vista) { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var motivo by remember { mutableStateOf("") }
    var web by remember { mutableStateOf<WebView?>(null) }
    var reintentos by remember(panel, vista) { mutableStateOf(0) }
    var reintentar by remember { mutableStateOf(0) }
    val ruta = "/${Backend.idioma}/$panel?vista=$vista&embebido=1"
    // La web deja su cookie de sesión y redirige al apartado (GET /api/app/entrar con el token en el encabezado)
    val destino = "${BuildConfig.API_BASE_URL}/api/app/entrar?destino=" + java.net.URLEncoder.encode(ruta, "UTF-8")
    var token by remember { mutableStateOf("") }

    // Selector de archivos de la web (<input type="file">)
    var callbackArchivos by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uris = if (r.resultCode == Activity.RESULT_OK) {
            r.data?.clipData?.let { c -> Array(c.itemCount) { c.getItemAt(it).uri } } ?: r.data?.data?.let { arrayOf(it) }
        } else null
        callbackArchivos?.onReceiveValue(uris); callbackArchivos = null
    }
    // Micrófono para las notas de voz del chat
    var pedidoMic by remember { mutableStateOf<PermissionRequest?>(null) }
    val permisoMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pedidoMic?.let { if (ok) it.grant(it.resources) else it.deny() }; pedidoMic = null
    }

    // La sesión de la web venció: se renueva y se vuelve a abrir (máximo 2 veces seguidas)
    fun volverAEntrar() { if (reintentos < 2) reintentos++ else { motivo = "login"; error = true } }

    LaunchedEffect(panel, vista, reintentos, reintentar) {
        listo = false
        val (t, r) = tokenFresco()
        token = t.orEmpty()
        error = r != null
        if (r != null) motivo = r
        listo = true
    }

    Box(modifier.fillMaxSize().background(T.fondo)) {
        if (listo && !error) {
            AndroidView(
                factory = { c ->
                    WebView(c).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        settings.userAgentString = settings.userAgentString + " VantyApp/Android"
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        addJavascriptInterface(Puente(c), "VantyApp")
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                                val u = req.url
                                val propio = u.host == Uri.parse(BuildConfig.API_BASE_URL).host
                                // Si la web manda al login (la sesión venció), se renueva y se vuelve a abrir el apartado
                                if (propio && u.path.orEmpty().contains("/login")) { volverAEntrar(); return true }
                                if (propio && u.scheme == "https") return false
                                runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                return true
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { cargando = true }
                            // Navegación interna de la web (router) hacia el login
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                if (url?.contains("/login") == true) volverAEntrar()
                            }
                            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: android.webkit.WebResourceError) {
                                if (req.isForMainFrame) { motivo = "red ${err.errorCode}"; error = true }
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                cargando = false
                                // Se quedó en /api/app/entrar: la web no aceptó el token
                                if (url?.contains("/api/app/entrar") == true) { motivo = "token"; error = true; return }
                                view.evaluateJavascript(SCRIPT_DESCARGAS, null)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                                callbackArchivos?.onReceiveValue(null)
                                callbackArchivos = cb
                                return runCatching { elegir.launch(params.createIntent().putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)); true }
                                    .getOrElse { callbackArchivos = null; false }
                            }
                            override fun onPermissionRequest(request: PermissionRequest) {
                                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources) {
                                    if (ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) request.grant(request.resources)
                                    else { pedidoMic = request; permisoMic.launch(Manifest.permission.RECORD_AUDIO) }
                                } else request.deny()
                            }
                        }
                        // Descargas normales (enlaces firmados): las abre el navegador o la app que corresponda
                        setDownloadListener { url, _, _, _, _ -> if (!url.startsWith("blob:")) abrirEnlace(c, url) }
                        loadUrl(destino, mapOf("Authorization" to "Bearer $token"))
                        web = this
                    }
                },
                update = { w -> if (w.url == null) w.loadUrl(destino, mapOf("Authorization" to "Bearer $token")) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (error) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                AriaFlotando(Aria.PREOCUPADA, 120.dp)
                Spacer(Modifier.height(12.dp))
                Text(L("No se pudo abrir este apartado. Revisa tu conexión.", "Couldn't open this section. Check your connection."),
                    style = MaterialTheme.typography.bodyMedium, color = T.secundario, textAlign = TextAlign.Center)
                Text(motivo, style = MaterialTheme.typography.labelSmall, color = T.terciario)
                Spacer(Modifier.height(12.dp))
                BotonGrande(L("REINTENTAR", "RETRY"), { error = false; reintentos = 0; reintentar++ })
            }
        }
        if (!listo || cargando) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(CircleShape).align(Alignment.TopCenter), color = T.acento, trackColor = T.relleno)
        }
    }
    // Atrás dentro de la web (p. ej. de la ficha de un paciente a la lista) antes de salir del apartado
    BackHandler(enabled = web?.canGoBack() == true) { web?.goBack() }
}

/** Token de acceso recién renovado (la web lo convierte en su cookie de sesión). Devuelve (token, motivo de error). */
private suspend fun tokenFresco(): Pair<String?, String?> = runCatching {
    val auth = Backend.supabase.auth
    runCatching { auth.refreshCurrentSession() }
    val s = auth.currentSessionOrNull() ?: return null to "sin_sesion"
    val cm = CookieManager.getInstance()
    cm.setCookie(BuildConfig.API_BASE_URL, "vanty_locale=${Backend.idioma}; Path=/; Max-Age=31536000")
    cm.flush()
    s.accessToken to null
}.getOrElse { null to "error: ${it.javaClass.simpleName} ${it.message?.take(80).orEmpty()}" }

/** Recibe los archivos que la web genera en el navegador (informes Word/PDF, Excel, recibos) y los abre. */
private class Puente(private val ctx: Context) {
    @JavascriptInterface
    fun guardar(base64: String, mime: String?, nombre: String?) {
        runCatching {
            val dir = File(ctx.cacheDir, "informes").apply { mkdirs() }
            val limpio = (nombre?.takeIf { it.isNotBlank() } ?: "archivo").replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val f = File(dir, limpio)
            f.writeBytes(Base64.decode(base64, Base64.DEFAULT))
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (!abrirArchivoLocal(ctx, f, mime?.takeIf { it.isNotBlank() }))
                    Toast.makeText(ctx, L("Instala una app para abrir este archivo.", "Install an app to open this file."), Toast.LENGTH_LONG).show()
            }
        }
    }
}

// Las descargas de la web se hacen con enlaces blob: que el WebView no puede bajar: se intercepta el clic
// (y URL.createObjectURL) para mandar el archivo a la app.
private const val SCRIPT_DESCARGAS = """
(function(){if(window.__vantyApp)return;window.__vantyApp=1;
var blobs={};var oc=URL.createObjectURL;
URL.createObjectURL=function(o){var u=oc.call(URL,o);try{if(o instanceof Blob)blobs[u]=o}catch(e){}return u};
function enviar(b,n){var fr=new FileReader();fr.onloadend=function(){VantyApp.guardar(String(fr.result).split(',')[1],b.type||'application/octet-stream',n||'archivo')};fr.readAsDataURL(b)}
var ac=HTMLAnchorElement.prototype.click;
HTMLAnchorElement.prototype.click=function(){var h=this.href;if(h&&h.indexOf('blob:')===0&&blobs[h]){enviar(blobs[h],this.download);return}return ac.call(this)};
document.addEventListener('click',function(e){var a=e.target&&e.target.closest&&e.target.closest('a[href^="blob:"]');if(a&&blobs[a.href]){e.preventDefault();enviar(blobs[a.href],a.download)}},true);
})();
"""
