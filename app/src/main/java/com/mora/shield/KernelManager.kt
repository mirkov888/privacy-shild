package com.mora.shield

/** Penemuan sysfs kernel. Hanya node governor CPU yang dikenal yang boleh ditulis. */
object KernelManager {
    data class Policy(
        val path: String, val name: String, val governor: String, val cur: String,
        val min: String, val max: String, val available: List<String>
    )

    private val POLICY_PATHS = (0..15).map { "/sys/devices/system/cpu/cpufreq/policy$it" }

    fun read(path: String): String {
        if (!path.startsWith("/sys/") || path.contains("..") || path.contains("'")) return ""
        val r = Root.sh("[ -r '$path' ] && cat '$path' 2>/dev/null")
        return if (r.ok) r.out.trim() else ""
    }

    private fun mhz(khz: String): String {
        val v = khz.toLongOrNull() ?: return "—"
        return (v / 1000).toString() + " MHz"
    }

    fun policies(): List<Policy> = POLICY_PATHS.mapNotNull { p ->
        val g = read("$p/scaling_governor")
        if (g.isBlank()) null
        else Policy(
            p, p.substringAfterLast('/'), g,
            mhz(read("$p/scaling_cur_freq")), mhz(read("$p/scaling_min_freq")), mhz(read("$p/scaling_max_freq")),
            read("$p/scaling_available_governors").split(Regex("\\s+")).filter { it.matches(Regex("[A-Za-z0-9_-]+")) }
        )
    }

    fun setGovernor(policy: String, value: String): String {
        if (policy !in POLICY_PATHS) return "CPU policy tidak valid"
        if (!value.matches(Regex("[A-Za-z0-9_-]{1,32}"))) return "Nilai tidak valid"
        val available = read("$policy/scaling_available_governors").split(Regex("\\s+"))
        if (value !in available) return "Governor tidak didukung kernel"
        val r = Root.sh("printf '%s' '$value' > '$policy/scaling_governor'")
        return if (r.ok) "Governor diubah ke $value" else "Gagal: ${r.out}"
    }

    fun setProfile(name: String): String {
        if (name !in setOf("performance", "balanced", "battery")) return "Profil tidak valid"
        val ps = policies()
        if (ps.isEmpty()) return "CPU cpufreq tidak ditemukan"
        val preferred = when (name) {
            "performance" -> listOf("performance", "schedutil", "ondemand")
            "battery" -> listOf("powersave", "schedutil", "ondemand")
            else -> listOf("schedutil", "ondemand", "interactive")
        }
        return ps.joinToString("\n") { p ->
            val gov = preferred.firstOrNull { it in p.available }
            if (gov == null) "Tidak ada governor cocok pada ${p.name}" else p.name + ": " + setGovernor(p.path, gov)
        }
    }

    fun saveGovernorsForBoot(enabled: Boolean): String {
        val path = "${Ps.DATA}/kernel_governors.conf"
        if (!enabled) {
            val r = Root.sh("rm -f '$path'")
            return if (r.ok) "Pulihkan saat boot dimatikan" else "Gagal menghapus konfigurasi: ${r.out}"
        }
        val lines = policies().filter { it.governor.matches(Regex("[A-Za-z0-9_-]+")) }.map { "${it.name}=${it.governor}" }
        if (lines.isEmpty()) return "Tidak ada governor yang bisa disimpan"
        val data = android.util.Base64.encodeToString(
            lines.joinToString("\n", postfix = "\n").toByteArray(), android.util.Base64.NO_WRAP
        )
        val r = Root.sh("mkdir -p ${Ps.DATA} && printf '%s' '$data' | base64 -d > '$path' && chmod 600 '$path'")
        return if (r.ok) "Disimpan untuk ${lines.size} policy CPU" else "Gagal menyimpan: ${r.out}"
    }

    fun applyOnBootEnabled(): Boolean = Root.sh("[ -s ${Ps.DATA}/kernel_governors.conf ]").ok

    fun summary(): String {
        val cores = Root.sh("getconf _NPROCESSORS_ONLN").out.ifBlank { "?" }
        val up = Root.sh("cat /proc/uptime").out.substringBefore(' ').toDoubleOrNull()
        val load = Root.sh("cat /proc/loadavg").out.ifBlank { "?" }
        val upText = if (up == null) "?" else "${(up / 3600).toInt()} j ${((up % 3600) / 60).toInt()} m"
        return "Inti CPU: $cores\nNyala sejak: $upText\nBeban: $load"
    }

    fun gpu(): String {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        val keys = listOf("gpuclk", "max_gpuclk", "min_gpuclk", "gpu_available_frequencies")
        val lines = keys.mapNotNull { k -> read("$kgsl/$k").takeIf { it.isNotBlank() }?.let { "$k: $it" } }
        val df = Root.sh("for d in /sys/class/devfreq/*; do [ -d \"\$d\" ] && echo \"\${d##*/}\"; done").out
        return (lines + if (df.isNotBlank()) listOf("devfreq: " + df.lines().joinToString(", ")) else emptyList()).joinToString("\n")
    }

    /** Persen RAM terpakai atau -1. */
    fun ramPercent(): Int {
        val m = Root.sh("grep -E '^(MemTotal|MemAvailable):' /proc/meminfo").out.lines()
        val total = m.firstOrNull { it.startsWith("MemTotal") }?.filter { it.isDigit() }?.toLongOrNull() ?: return -1
        val avail = m.firstOrNull { it.startsWith("MemAvailable") }?.filter { it.isDigit() }?.toLongOrNull() ?: return -1
        if (total <= 0) return -1
        return (100 - avail * 100 / total).toInt()
    }

    /** Suhu baterai (derajat C) dan persen baterai; -1 bila tidak terbaca. */
    fun battery(): Pair<Int, Int> {
        val cap = read("/sys/class/power_supply/battery/capacity").toIntOrNull() ?: -1
        val t = read("/sys/class/power_supply/battery/temp").toIntOrNull()
        return Pair(cap, if (t == null) -1 else t / 10)
    }

    fun thermal(): String {
        val t = Root.sh("for z in /sys/class/thermal/thermal_zone*; do [ -r \"\$z/temp\" ] && echo \"\$(cat \"\$z/type\" 2>/dev/null)=\$(cat \"\$z/temp\")\"; done").out
        return t.ifBlank { "Tidak tersedia" }
    }
}
