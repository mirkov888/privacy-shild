package com.mora.shield

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Tujuan menu Bagikan: tampilkan tautan yang sudah bersih. */
class LinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val r = Linker.clean(text.take(4000))
        val red = Linker.redirect(this, r.url.trim())
        setContent {
            MoraTheme {
                Surface(Modifier.fillMaxWidth()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                        Text("Tautan bersih", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (r.removed.isEmpty() && !r.unwrapped) "Tidak ada pelacak yang ditemukan."
                            else "Dibuang ${r.removed.size} parameter" + (if (r.unwrapped) " dan pengalihan dibongkar" else "") +
                                (if (r.removed.isNotEmpty()) ": " + r.removed.distinct().joinToString(", ") else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        SelectionContainer { Text(r.url, style = MaterialTheme.typography.bodyMedium) }
                        if (red != null && red.url != r.url) {
                            Spacer(Modifier.height(12.dp))
                            Text("Versi privat:", style = MaterialTheme.typography.labelMedium)
                            SelectionContainer { Text(red.url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row {
                            Button(onClick = { copyText(this@LinkActivity, red?.url ?: r.url); finish() }) { Text("Salin") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { share(red?.url ?: r.url); finish() }) { Text("Bagikan") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { finish() }) { Text("Tutup") }
                        }
                    }
                }
            }
        }
    }

    private fun share(s: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, s) }
        startActivity(Intent.createChooser(i, "Bagikan tautan bersih"))
    }
}

fun copyText(ctx: Context, s: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("tautan", s))
}
