package com.mora.shield

/** Jembatan ke mesin modul Mora Privacy Shield (ps.sh). */
object Ps {
    const val MOD = "/data/adb/modules/privacy_shield"
    const val DATA = "/data/adb/privacyshield"

    private val SAFE = Regex("^[A-Za-z0-9._]+$")

    fun safe(s: String): Boolean = SAFE.matches(s)

    fun run(args: String): Root.Result = Root.sh("sh $MOD/bin/ps.sh $args")

    fun installed(): Boolean = Root.sh("[ -f $MOD/bin/ps.sh ] && echo yes").out.contains("yes")

    fun version(): String {
        val r = Root.sh("sed -n 's/^version=//p' $MOD/module.prop")
        return if (r.ok) r.out else "?"
    }

    fun status(): Map<String, String> {
        val r = run("status")
        val m = HashMap<String, String>()
        for (line in r.out.lines()) {
            val i = line.indexOf('=')
            if (i > 0) m[line.substring(0, i)] = line.substring(i + 1)
        }
        return m
    }

    fun set(key: String, value: String, pin: String = ""): Root.Result {
        if (!safe(key) || !Regex("^[A-Za-z0-9._,:/-]*$").matches(value)) return Root.Result(2, "nilai tidak valid")
        val v = if (value.isEmpty()) "\"\"" else value
        if (pin.isNotEmpty() && !Regex("^[0-9]{1,16}$").matches(pin)) return Root.Result(2, "PIN tidak valid")
        val r = Root.sh("sh $MOD/bin/ps.sh set $key $v" + (if (pin.isEmpty()) "" else " $pin"))
        if (!r.ok) return r
        Root.sh("sh $MOD/bin/ps.sh apply")
        return r
    }

    /** Versi modul sebagai angka mayor, mis. "v3.0.0" -> 3. Mengembalikan 0 bila tidak terbaca. */
    fun major(ver: String): Int {
        val m = Regex("(\\d+)").find(ver) ?: return 0
        return m.value.toIntOrNull() ?: 0
    }

    data class Ev(val ts: Long, val type: String, val pkg: String, val detail: String)

    fun parseEv(line: String): Ev? {
        val p = line.trim().split("|", limit = 4)
        if (p.size < 4) return null
        val ts = p[0].toLongOrNull() ?: return null
        return Ev(ts, p[1], p[2], p[3])
    }

    fun events(n: Int): List<Ev> {
        val r = run("events $n")
        return r.out.lines().mapNotNull { parseEv(it) }.reversed()
    }

    /** pkg -> (skor, daftar nama) dari hasil pemindaian pelacak terakhir. */
    fun trackers(): Map<String, Pair<Int, String>> {
        val r = run("trkres")
        val m = HashMap<String, Pair<Int, String>>()
        for (line in r.out.lines()) {
            if (!line.startsWith("R|")) continue
            val p = line.split("|", limit = 4)
            if (p.size < 3) continue
            m[p[1]] = Pair(p[2].toIntOrNull() ?: 0, if (p.size > 3) p[3] else "")
        }
        return m
    }

    fun blockedPkgs(st: Map<String, String>): Set<String> =
        (st["fw_pkgs"] ?: "").split(",").filter { it.isNotBlank() }.toSet()

    fun block(pkg: String): Boolean = safe(pkg) && run("fwblock $pkg").out.contains("ok")
    fun unblock(pkg: String): Boolean = safe(pkg) && run("fwunblock $pkg").out.contains("ok")
    fun allowNew(pkg: String): Boolean = safe(pkg) && run("naallow $pkg").out.contains("ok")

    fun ultimate(args: String): Root.Result {
        if (!Regex("^(status|log|sensor (enable|disable)|lockdown (on|off))$").matches(args)) {
            return Root.Result(2, "perintah UltimatePrivacy tidak valid")
        }
        return Root.sh("sh $MOD/bin/ultimateprivacy.sh $args")
    }

    /** Sidik jari konfigurasi + jumlah kejadian (modul v3.2+). Kosong bila modul lama. */
    fun rev(): String {
        val r = run("rev")
        return if (r.ok && Regex("^[0-9]+-[0-9]+$").matches(r.out)) r.out else ""
    }

    /** Status lengkap: modul v3.2+ memakai `sync`, modul lama jatuh ke `status`. */
    fun sync(): Map<String, String> {
        val r = run("sync")
        val m = HashMap<String, String>()
        if (r.ok) for (line in r.out.lines()) {
            val i = line.indexOf('=')
            if (i > 0) m[line.substring(0, i)] = line.substring(i + 1)
        }
        return if (m.containsKey("rev")) m else status()
    }
}
