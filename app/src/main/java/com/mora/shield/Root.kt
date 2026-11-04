package com.mora.shield

/** Menjalankan perintah lewat `su` (KernelSU / Magisk). */
object Root {
    data class Result(val code: Int, val out: String) {
        val ok: Boolean get() = code == 0
    }

    fun sh(script: String): Result = try {
        val p = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        Result(p.waitFor(), out)
    } catch (e: Exception) {
        Result(-1, e.message ?: "su tidak tersedia")
    }

    fun available(): Boolean {
        val r = sh("id -u")
        return r.ok && r.out.endsWith("0")
    }
}
