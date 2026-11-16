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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mora.shield.identity.IdentityProfile

/** Local-only preview; network loads, file access and content access are disabled. */
@Composable
fun IdentityWebViewPreview(profile: IdentityProfile) {
    Text("Preview WebView Mora • " + profile.model, style = MaterialTheme.typography.titleSmall)
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    blockNetworkLoads = true
                    blockNetworkImageLoads = true
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    userAgentString = profile.userAgent
                    if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
                loadDataWithBaseURL("https://mora.local/", PREVIEW_HTML, "text/html", "UTF-8", null)
            }
        }
    )
}
private const val PREVIEW_HTML = """
<!doctype html><html lang="en"><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>body{font:14px sans-serif;margin:14px;word-break:break-word;color:#222}h3{margin:0 0 12px}.label{font-weight:600;margin-top:10px}pre{white-space:pre-wrap;background:#eee;padding:10px;border-radius:8px}</style>
</head><body><h3>Mora Identity Preview</h3>
<div class="label">navigator.userAgent</div><pre id="ua">Checking…</pre>
<div class="label">navigator.platform</div><pre id="platform"></pre>
<div class="label">Viewport</div><pre id="viewport"></pre>
<script>
document.getElementById('ua').textContent=navigator.userAgent;
document.getElementById('platform').textContent=navigator.platform||'not exposed';
document.getElementById('viewport').textContent=window.innerWidth+' × '+window.innerHeight;
</script></body></html>
"""
