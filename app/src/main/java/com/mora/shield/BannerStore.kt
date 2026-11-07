package com.mora.shield

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.zip.ZipInputStream

object BannerStore {
    var selectedIndex by mutableIntStateOf(0)
        private set

    private fun directory(context: Context) = File(context.filesDir, "mora_banners").apply { mkdirs() }
    fun load(context: Context) {
        selectedIndex = context.getSharedPreferences("mora_banner", Context.MODE_PRIVATE).getInt("selected", 0)
    }
    fun select(context: Context, index: Int) {
        selectedIndex = index
        context.getSharedPreferences("mora_banner", Context.MODE_PRIVATE).edit().putInt("selected", index).apply()
    }
    fun files(context: Context): List<File> = directory(context).listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
        ?.sortedBy { it.name } ?: emptyList()
    fun bitmap(context: Context): Bitmap? {
        if (selectedIndex <= 0) return null
        return files(context).getOrNull(selectedIndex - 1)?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    fun importZip(context: Context, uri: Uri): Int {
        val dir = directory(context)
        dir.listFiles()?.forEach { it.delete() }
        var count = 0
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (!entry.isDirectory && ext in setOf("jpg", "jpeg", "png", "webp") &&
                        name.matches(Regex("[A-Za-z0-9._-]{1,120}"))) {
                        val target = File(dir, "%02d_%s".format(count + 1, name))
                        var written = 0L
                        target.outputStream().use { out ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = zip.read(buffer)
                                if (n < 0) break
                                written += n
                                total += n
                                require(written <= 12L * 1024 * 1024 && total <= 48L * 1024 * 1024) {
                                    "Ukuran gambar ZIP terlalu besar"
                                }
                                out.write(buffer, 0, n)
                            }
                        }
                        count++
                    }
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalArgumentException("File ZIP tidak bisa dibuka")
        select(context, 0)
        return count
    }
}
