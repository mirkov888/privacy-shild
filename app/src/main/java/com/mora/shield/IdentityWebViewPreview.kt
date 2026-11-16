package com.mora.shield

import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.mora.shield.identity.FingerprintProtectionEngine
import com.mora.shield.identity.IdentityProfile
import com.mora.shield.identity.IdentityProtectionSettings

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
    val html = remember(profile, protection, sessionSeed) {
        val script = FingerprintProtectionEngine.buildDocumentStartScript(protection, sessionSeed)
        PREVIEW_HTML.replace("/*MORA_PROTECTION_SCRIPT*/", script)
    }

    Text("Preview WebView Mora • " + profile.model, style = MaterialTheme.typography.titleSmall)
    Text(
        "Sesi terisolasi • jaringan diblokir • penyimpanan web nonaktif",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    key(profile.id, protection, sessionSeed) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(310.dp),
            factory = { context ->
                WebView(context).apply {
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
                    webChromeClient = WebChromeClient()
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
<script>
(() => {
  const put = (id, value) => { document.getElementById(id).textContent = value; };
  put('ua', navigator.userAgent);
  put('environment', [
    window.innerWidth + ' × ' + window.innerHeight,
    navigator.language || 'locale unavailable',
    Intl.DateTimeFormat().resolvedOptions().timeZone || 'timezone unavailable'
  ].join(' · '));

  try {
    const canvas = document.createElement('canvas');
    canvas.width = 160;
    canvas.height = 48;
    const context = canvas.getContext('2d');
    context.fillStyle = '#6b4eff';
    context.fillRect(0, 0, 160, 48);
    context.fillStyle = '#ffffff';
    context.font = '16px sans-serif';
    context.fillText('Mora privacy test', 8, 29);
    const data = canvas.toDataURL();
    let hash = 2166136261;
    for (let i = 0; i < data.length; i++) {
      hash ^= data.charCodeAt(i);
      hash = Math.imul(hash, 16777619);
    }
    put('canvas', 'hash-' + (hash >>> 0).toString(16));
  } catch (error) {
    put('canvas', 'Unavailable: ' + String(error));
  }

  try {
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl') || canvas.getContext('webgl2');
    if (!gl) {
      put('webgl', 'WebGL tidak tersedia');
    } else {
      const debug = gl.getExtension('WEBGL_debug_renderer_info');
      const vendor = debug ? gl.getParameter(debug.UNMASKED_VENDOR_WEBGL) : gl.getParameter(gl.VENDOR);
      const renderer = debug ? gl.getParameter(debug.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER);
      put('webgl', vendor + ' · ' + renderer + (debug ? ' · debug extension visible' : ' · debug extension hidden'));
    }
  } catch (error) {
    put('webgl', 'Unavailable: ' + String(error));
  }

  const uaData = navigator.userAgentData;
  if (!uaData) {
    put('hints', 'User-Agent Client Hints tidak didukung oleh WebView ini atau profil browser ini.');
  } else {
    put('hints', JSON.stringify({
      brands: uaData.brands,
      mobile: uaData.mobile,
      platform: uaData.platform
    }, null, 2));
    if (typeof uaData.getHighEntropyValues === 'function') {
      uaData.getHighEntropyValues(['platformVersion', 'model', 'fullVersionList'])
        .then(values => put('hints', JSON.stringify(values, null, 2)))
        .catch(() => {});
    }
  }
})();
</script>
</body></html>
"""
