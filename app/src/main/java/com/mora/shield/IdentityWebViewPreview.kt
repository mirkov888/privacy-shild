package com.mora.shield

import android.webkit.WebChromeClient
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.mora.shield.identity.FingerprintProtectionEngine
import com.mora.shield.identity.IdentityProfile
import com.mora.shield.identity.IdentityProtectionSettings
import com.mora.shield.identity.IdentityDiagnosticsStore
import com.mora.shield.identity.IdentityDiagnostics
import org.json.JSONTokener

/**
 * Isolated, offline preview for testing one selected browser profile.
 * This does not configure Chrome, Firefox, or arbitrary third-party WebViews.
 */
@Composable
fun IdentityWebViewPreview(
    profile: IdentityProfile,
    protection: IdentityProtectionSettings,
    sessionSeed: String
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val diagnostics = remember(context) { IdentityDiagnosticsStore(context) }
    var previewWebView by remember(profile.id, protection, sessionSeed) { mutableStateOf<WebView?>(null) }
    var diagnosticMessage by remember(profile.id, protection, sessionSeed) { mutableStateOf("") }
    fun captureReport(onCaptured: (String) -> Unit) {
        val current = previewWebView
        if (current == null) {
            diagnosticMessage = "WebView belum siap. Buka ulang preview lalu coba lagi."
            return
        }
        current.evaluateJavascript("JSON.stringify(window.moraCollectDiagnostics())") { encoded ->
            val report = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
            if (report.isNullOrBlank() || !IdentityDiagnostics.isValidReport(report)) {
                diagnosticMessage = "Laporan belum tersedia atau formatnya tidak valid."
            } else {
                runCatching { diagnostics.saveLatest(report) }
                    .onSuccess { onCaptured(report) }
                    .onFailure { diagnosticMessage = it.message ?: "Gagal menyimpan laporan" }
            }
        }
    }

    val html = remember(profile, protection, sessionSeed) {
        val script = FingerprintProtectionEngine.buildDocumentStartScript(protection, sessionSeed)
        PREVIEW_HTML.replace("/*MORA_PROTECTION_SCRIPT*/", script)
    }

    Text("Preview WebView Mora • " + profile.model, style = MaterialTheme.typography.titleSmall)
    Text(
        "Sesi terisolasi • jaringan diblokir • DOM Storage/cache nonaktif",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text("Fingerprint diagnostics lokal", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                captureReport { report ->
                    runCatching { diagnostics.saveBaseline(report) }
                        .onSuccess { diagnosticMessage = "Baseline tersimpan. Jalankan lagi setelah proteksi diubah untuk membandingkan." }
                        .onFailure { diagnosticMessage = it.message ?: "Gagal menyimpan baseline" }
                }
            },
            modifier = Modifier.weight(1f)
        ) { Text("Simpan baseline") }
        OutlinedButton(
            onClick = {
                captureReport { report ->
                    val changes = runCatching { diagnostics.compareWithBaseline(report) }
                        .getOrElse { listOf(it.message ?: "Perbandingan gagal") }
                    diagnosticMessage = if (changes.isEmpty()) {
                        "Tidak ada perubahan pada field diagnostik yang dibandingkan."
                    } else {
                        changes.joinToString("\n")
                    }
                    clipboard.setText(AnnotatedString(report))
                }
            },
            modifier = Modifier.weight(1f)
        ) { Text("Bandingkan") }
    }
    OutlinedButton(
        onClick = {
            captureReport { report ->
                clipboard.setText(AnnotatedString(report))
                diagnosticMessage = "Laporan JSON terbaru disalin ke clipboard."
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Ambil & salin laporan JSON") }
    if (diagnosticMessage.isNotBlank()) {
        Text(diagnosticMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    key(profile.id, protection, sessionSeed) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(310.dp),
            factory = { context ->
                WebView(context).apply {
                    previewWebView = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = false
                        databaseEnabled = false
                        allowFileAccess = false
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        blockNetworkLoads = true
                        blockNetworkImage = true
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        mediaPlaybackRequiresUserGesture = true
                        userAgentString = profile.userAgent
                        if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
                    }
                    if (protection.syncClientHints &&
                        WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)
                    ) {
                        runCatching {
                            FingerprintProtectionEngine.clientHintsFor(profile)?.let { metadata ->
                                WebSettingsCompat.setUserAgentMetadata(settings, metadata)
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            // The preview never grants camera or microphone capture.
                            request.deny()
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                    }
                    loadDataWithBaseURL("https://mora.local/", html, "text/html", "UTF-8", null)
                }
            },
            onRelease = { view ->
                view.stopLoading()
                view.loadUrl("about:blank")
                view.clearHistory()
                view.clearCache(true)
                view.removeAllViews()
                view.destroy()
                if (previewWebView === view) previewWebView = null
            }
        )
    }
}

private const val PREVIEW_HTML = """
<!doctype html><html lang="en"><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{font:13px sans-serif;margin:14px;word-break:break-word;color:#222}
h3{margin:0 0 12px}.label{font-weight:600;margin-top:9px}
pre{white-space:pre-wrap;background:#eee;padding:8px;border-radius:8px;margin:4px 0}
</style>
<script>/*MORA_PROTECTION_SCRIPT*/</script>
</head><body>
<h3>Mora Identity & Fingerprint Preview</h3>
<div class="label">navigator.userAgent</div><pre id="ua">Checking…</pre>
<div class="label">User-Agent Client Hints</div><pre id="hints">Checking…</pre>
<div class="label">Canvas output (session-stable)</div><pre id="canvas">Checking…</pre>
<div class="label">WebGL renderer</div><pre id="webgl">Checking…</pre>
<div class="label">Viewport / locale / timezone</div><pre id="environment">Checking…</pre>
<div class="label">WebRTC availability</div><pre id="webrtc">Checking…</pre>
<script>
(() => {
  const put = (id, value) => { document.getElementById(id).textContent = value; };
  window.moraCollectDiagnostics = function() {
    const report = {
      collectedAt: new Date().toISOString(),
      userAgent: navigator.userAgent,
      clientHints: navigator.userAgentData ? {
        brands: navigator.userAgentData.brands,
        mobile: navigator.userAgentData.mobile,
        platform: navigator.userAgentData.platform
      } : null,
      platform: navigator.platform || "unavailable",
      locale: { language: navigator.language || null, languages: navigator.languages || [] },
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "unavailable",
      screen: {
        innerWidth: window.innerWidth, innerHeight: window.innerHeight,
        width: screen.width, height: screen.height,
        colorDepth: screen.colorDepth, dpr: window.devicePixelRatio
      },
      canvas: null,
      webgl: null,
      webRtc: {
        peerConnection: typeof RTCPeerConnection !== "undefined",
        mediaDevices: !!navigator.mediaDevices,
        getUserMedia: !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
      }
    };
    try {
      const canvas = document.createElement("canvas");
      canvas.width = 160; canvas.height = 48;
      const ctx = canvas.getContext("2d");
      ctx.fillStyle = "#7655ff"; ctx.fillRect(0, 0, 160, 48);
      ctx.fillStyle = "#ffffff"; ctx.font = "16px sans-serif";
      ctx.fillText("Mora privacy test", 8, 29);
      const data = canvas.toDataURL();
      let hash = 2166136261;
      for (let i = 0; i < data.length; i++) {
        hash ^= data.charCodeAt(i);
        hash = Math.imul(hash, 16777619);
      }
      report.canvas = { available: true, hash: (hash >>> 0).toString(16), dataUrlLength: data.length };
    } catch (error) {
      report.canvas = { available: false, error: String(error) };
    }
    try {
      const canvas = document.createElement("canvas");
      const gl = canvas.getContext("webgl") || canvas.getContext("webgl2");
      if (!gl) report.webgl = { available: false };
      else {
        const debug = gl.getExtension("WEBGL_debug_renderer_info");
        report.webgl = {
          available: true,
          vendor: debug ? gl.getParameter(debug.UNMASKED_VENDOR_WEBGL) : gl.getParameter(gl.VENDOR),
          renderer: debug ? gl.getParameter(debug.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER),
          debugExtension: !!debug
        };
      }
    } catch (error) {
      report.webgl = { available: false, error: String(error) };
    }
    return report;
  };
  const report = window.moraCollectDiagnostics();
  put("ua", report.userAgent);
  put("hints", JSON.stringify(report.clientHints, null, 2));
  put("canvas", JSON.stringify(report.canvas));
  put("webgl", JSON.stringify(report.webgl));
  put("webrtc", JSON.stringify(report.webRtc));
  put("environment", [
    report.screen.innerWidth + " × " + report.screen.innerHeight,
    report.locale.language,
    report.timezone
  ].join(" · "));
})();;
</script>
</body></html>
"""
