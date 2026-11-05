package com.mora.shield

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Menerima tautan yang diketuk (YouTube/X/Reddit), membersihkan, mengalihkan, lalu menutup diri tanpa tampilan. */
class RedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = intent?.dataString
        if (raw != null && (raw.startsWith("https://") || raw.startsWith("http://"))) {
            val cleaned = Linker.clean(raw).url
            val red = Linker.redirect(this, cleaned)
            val target = red?.url ?: cleaned
            val pkg = red?.targetPackage
            if (!(pkg != null && open(target, pkg))) open(target, null)
        }
        finish()
    }

    private fun open(url: String, pkg: String?): Boolean {
        return try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            if (pkg != null) {
                i.setPackage(pkg)
            } else {
                // Pakai peramban bawaan secara eksplisit agar tidak kembali ke aplikasi ini (hindari putaran).
                val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
                val ri = packageManager.resolveActivity(probe, 0)
                val bp = ri?.activityInfo?.packageName
                if (bp != null && bp != packageName && bp != "android") i.setPackage(bp)
            }
            startActivity(i)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
