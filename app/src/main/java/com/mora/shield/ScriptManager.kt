package com.mora.shield

import android.util.Base64

object ScriptManager {
    private const val DIR="/data/adb/privacyshield/scripts"
    private val nameRule=Regex("^[A-Za-z0-9_-]{1,40}\\.sh$")
    fun list():List<String> {
        val r=Root.sh("mkdir -p '$DIR'; for f in '$DIR'/*.sh; do [ -f \"\$f\" ] && basename \"\$f\"; done")
        return if(r.ok) r.out.lines().filter { nameRule.matches(it) } else emptyList()
    }
    fun save(name:String, body:String):String {
        if(!nameRule.matches(name)) return "Nama script tidak valid"
        if(body.length>20000) return "Script terlalu besar (maksimal 20 KB)"
        val b64=Base64.encodeToString(body.toByteArray(Charsets.UTF_8),Base64.NO_WRAP)
        val r=Root.sh("mkdir -p '$DIR' && printf '%s' '$b64' | base64 -d > '$DIR/$name' && chmod 700 '$DIR/$name'")
        return if(r.ok) "Script disimpan: $name" else "Gagal: ${r.out}"
    }
    fun run(name:String):String {
        if(!nameRule.matches(name) || name !in list()) return "Script tidak ditemukan"
        val r=Root.sh("sh '$DIR/$name'")
        return "exit=${r.code}\\n${r.out}"
    }
    fun delete(name:String):String {
        if(!nameRule.matches(name)) return "Nama script tidak valid"
        val r=Root.sh("rm -f '$DIR/$name'")
        return if(r.ok) "Dihapus: $name" else "Gagal: ${r.out}"
    }
}
