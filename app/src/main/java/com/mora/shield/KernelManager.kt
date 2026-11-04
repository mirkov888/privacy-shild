package com.mora.shield

/** Kernel sysfs discovery. Only known CPU governor nodes are writable. */
object KernelManager {
    fun read(path:String):String {
        if(!path.startsWith("/sys/") || path.contains("..") || path.contains("'")) return ""
        val r=Root.sh("[ -r '$path' ] && cat '$path' 2>/dev/null")
        return if(r.ok) r.out.trim() else ""
    }
    fun policies():List<Pair<String,String>> = (0..15).map { "/sys/devices/system/cpu/cpufreq/policy$it" }
        .filter { read("$it/scaling_governor").isNotBlank() }
        .map { p -> p to listOf("governor" to "scaling_governor","current" to "scaling_cur_freq","min" to "scaling_min_freq","max" to "scaling_max_freq").joinToString("\n") { (n,f)-> "$n: ${read("$p/$f").ifBlank { "—" }}" } }
    fun setGovernor(policy:String,value:String):String {
        if(policy !in (0..15).map { "/sys/devices/system/cpu/cpufreq/policy$it" }) return "CPU policy tidak valid"
        if(!value.matches(Regex("[A-Za-z0-9_-]{1,32}"))) return "Nilai tidak valid"
        val available=read("$policy/scaling_available_governors").split(Regex("\\s+"))
        if(value !in available) return "Governor tidak didukung kernel"
        val r=Root.sh("printf '%s' '$value' > '$policy/scaling_governor'")
        return if(r.ok) "Governor berhasil diubah" else "Gagal: ${r.out}"
    }
    fun setProfile(name:String):String {
        if (name !in setOf("performance","balanced","battery")) return "Profil tidak valid"
        val policies=(0..15).map { "/sys/devices/system/cpu/cpufreq/policy$it" }
            .filter { read("$it/scaling_governor").isNotBlank() }
        if (policies.isEmpty()) return "CPU cpufreq tidak ditemukan"
        val results=policies.map { p ->
            val avail=read("$p/scaling_available_governors").split(Regex("\\s+"))
            val preferred=when(name) {
                "performance" -> listOf("performance","schedutil","ondemand")
                "battery" -> listOf("powersave","schedutil","ondemand")
                else -> listOf("schedutil","ondemand","interactive")
            }
            val gov=preferred.firstOrNull { it in avail }
            if (gov==null) "Tidak ada governor cocok pada ${p.substringAfterLast('/')}"
            else setGovernor(p,gov)
        }
        return results.joinToString("\n")
    }

    fun saveGovernorsForBoot(enabled:Boolean):String {
        val path="/data/adb/privacyshield/kernel_governors.conf"
        if (!enabled) {
            val r=Root.sh("rm -f '$path'")
            return if(r.ok) "Apply on Boot dimatikan" else "Gagal menghapus konfigurasi: ${r.out}"
        }
        val lines=policies().mapNotNull { (p, _) ->
            val g=read("$p/scaling_governor")
            if(g.isBlank() || !g.matches(Regex("[A-Za-z0-9_-]+"))) null
            else "${p.substringAfterLast('/')}=$g"
        }
        if(lines.isEmpty()) return "Tidak ada governor yang bisa disimpan"
        val data=android.util.Base64.encodeToString(lines.joinToString("\n", postfix="\n").toByteArray(), android.util.Base64.NO_WRAP)
        val r=Root.sh("mkdir -p /data/adb/privacyshield && printf '%s' '$data' | base64 -d > '$path' && chmod 600 '$path'")
        return if(r.ok) "Apply on Boot disimpan untuk ${lines.size} policy CPU" else "Gagal menyimpan: ${r.out}"
    }

    fun applyOnBootEnabled():Boolean = Root.sh("[ -s /data/adb/privacyshield/kernel_governors.conf ]").ok

    fun summary():String = "CPU cores: ${Root.sh("getconf _NPROCESSORS_ONLN").out.ifBlank { "?" }}\nUptime: ${Root.sh("cat /proc/uptime").out.substringBefore(' ').ifBlank { "?" }} s\nLoad: ${Root.sh("cat /proc/loadavg").out.ifBlank { "?" }}"
    fun gpu():String {
        val kgsl="/sys/class/kgsl/kgsl-3d0"
        val keys=listOf("gpuclk","max_gpuclk","min_gpuclk","gpu_available_frequencies")
        val lines=keys.mapNotNull { k->read("$kgsl/$k").takeIf { it.isNotBlank() }?.let { "$k: $it" } }
        val df=Root.sh("for d in /sys/class/devfreq/*; do [ -d \"\$d\" ] && echo \"\${d##*/}\"; done").out
        return (lines+if(df.isNotBlank()) listOf("devfreq devices: $df") else emptyList()).joinToString("\n")
    }
    fun memory():String {
        val a=Root.sh("head -n 8 /proc/meminfo").out
        val z=Root.sh("cat /sys/block/zram*/mm_stat 2>/dev/null").out
        val io=Root.sh("for f in /sys/block/*/queue/scheduler; do [ -r \"\$f\" ] && echo \"\$f: $(cat \"\$f\")\"; done").out
        return "RAM:\n${a.ifBlank { "Tidak tersedia" }}\nZRAM:\n${z.ifBlank { "Tidak tersedia" }}\nI/O scheduler:\n${io.ifBlank { "Tidak tersedia" }}"
    }
    fun thermal():String {
        val t=Root.sh("for z in /sys/class/thermal/thermal_zone*; do [ -r \"\$z/temp\" ] && echo \"$(cat \"\$z/type\" 2>/dev/null)=$(cat \"\$z/temp\")\"; done").out
        val b=Root.sh("for f in capacity temp status current_now voltage_now; do p=/sys/class/power_supply/battery/\$f; [ -r \"\$p\" ] && echo \"\$f=$(cat \"\$p\")\"; done").out
        return "Thermal zones:\n${t.ifBlank { "Tidak tersedia" }}\nBattery:\n${b.ifBlank { "Tidak tersedia" }}"
    }
}
