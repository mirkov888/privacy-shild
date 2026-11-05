package com.mora.shield

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.Calendar
import kotlin.concurrent.thread

data class AppItem(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap?,
    val score: Int,
    val trackers: String,
    val blocked: Boolean,
    val newApp: Boolean,
    val tor: Boolean
)

class Stats(
    val total: Int,
    val byType: List<Pair<String, Int>>,
    val topApps: List<Pair<String, Int>>,
    val hourly: IntArray,
    val levels: IntArray,
    val score: Int,
    val blockedApps: Int,
    val newApps: Int
)

val SCORE_KEYS = listOf(
    "ads", "telemetry", "mac_rand", "lock_priv", "loc_off", "adb_off", "ipv6_off", "clean_boot",
    "am_on", "nb_on", "na_on", "usb_lock", "wifi_open", "autoreboot", "dm_on", "bio_on"
)

fun Map<String, String>.on(k: String): Boolean = this[k] == "1"
fun Map<String, String>.dry(k: String): Boolean = this[k] != "0"

object Store {
    var rootOk by mutableStateOf<Boolean?>(null)
    var installed by mutableStateOf(false)
    var version by mutableStateOf("")
    var cfg by mutableStateOf<Map<String, String>>(emptyMap())
    var events by mutableStateOf<List<Ps.Ev>>(emptyList())
    var apps by mutableStateOf<List<AppItem>>(emptyList())
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var textDialogTitle by mutableStateOf("")
    var textDialogBody by mutableStateOf("")
    var textDialogOpen by mutableStateOf(false)
    var lastSync by mutableStateOf(0L)
    lateinit var appCtx: Context
    var lastRev = ""
    @Volatile private var pollGen = 0
    var kernel by mutableStateOf<List<KernelManager.Policy>>(emptyList())
    var kernelInfo by mutableStateOf("")
    var kernelBoot by mutableStateOf(false)
    var ram by mutableStateOf(-1)
    var batt by mutableStateOf(Pair(-1, -1))
    var scripts by mutableStateOf<List<String>>(emptyList())
    var tests by mutableStateOf<List<TestRow>>(emptyList())
    var testSum by mutableStateOf("")
    var sus by mutableStateOf<List<SusRow>>(emptyList())
    var susSum by mutableStateOf("")
    var susRan by mutableStateOf(false)
    var appLock by mutableStateOf(false)
    var unlocked by mutableStateOf(false)
    var lastPause = 0L

    /** Mengecek tiap beberapa detik apakah konfigurasi/kejadian berubah (mis. dari WebUI) lalu memuat ulang. */
    fun startPolling(ctx: Context) {
        val gen = ++pollGen
        thread {
            while (gen == pollGen) {
                Thread.sleep(6000)
                if (rootOk == true && installed && !busy) {
                    val r = Ps.rev()
                    if (r.isNotEmpty() && r != lastRev) {
                        lastRev = r
                        val sy = Ps.sync()
                        cfg = sy
                        events = Ps.events(3000)
                        lastSync = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    fun stopPolling() { pollGen++ }

    fun loadKernel() {
        thread {
            kernel = KernelManager.policies()
            kernelInfo = KernelManager.summary()
            kernelBoot = KernelManager.applyOnBootEnabled()
            ram = KernelManager.ramPercent()
            batt = KernelManager.battery()
        }
    }

    fun loadScripts() {
        thread { scripts = ScriptManager.list() }
    }

    fun showText(title: String, body: String) {
        textDialogTitle = title
        textDialogBody = body
        textDialogOpen = true
    }

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        thread {
            val ok = Root.available()
            rootOk = ok
            if (!ok) {
                installed = false
                return@thread
            }
            val inst = Ps.installed()
            installed = inst
            if (!inst) return@thread
            version = Ps.version()
            val sy = Ps.sync()
            cfg = sy
            lastRev = sy["rev"] ?: ""
            lastSync = System.currentTimeMillis()
            events = Ps.events(3000)
            loadApps(app)
        }
    }

    private fun loadApps(ctx: Context) {
        val pm = ctx.packageManager
        val trk = Ps.trackers()
        val st = cfg
        val blocked = Ps.blockedPkgs(st)
        val tor = (st["tor_apps"] ?: "").split(",").filter { it.isNotBlank() }.toSet()
        val newApps = Root.sh("cat ${Ps.DATA}/na_block 2>/dev/null").out.lines().filter { it.isNotBlank() }.toSet()
        val list = ArrayList<AppItem>()
        for (ai in pm.getInstalledApplications(0)) {
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            if (ai.packageName == ctx.packageName) continue
            val label = pm.getApplicationLabel(ai).toString()
            val icon: ImageBitmap? = try {
                pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
            val t = trk[ai.packageName]
            list.add(
                AppItem(
                    ai.packageName, label, icon, t?.first ?: 0, t?.second ?: "",
                    blocked.contains(ai.packageName), newApps.contains(ai.packageName), tor.contains(ai.packageName)
                )
            )
        }
        list.sortWith(compareByDescending<AppItem> { it.score }.thenBy { it.label.lowercase() })
        apps = list
    }

    fun set(key: String, value: String, pin: String = "", done: (Boolean) -> Unit = {}) {
        thread {
            val r = Ps.set(key, value, pin)
            val pending = r.out.startsWith("PENDING")
            if (r.ok && !pending) {
                cfg = cfg + (key to value)
            }
            message = when {
                pending -> "Penonaktifan dijadwalkan. Lihat daftar tertunda."
                r.ok -> "Disimpan"
                else -> "Gagal: " + r.out.take(140)
            }
            done(r.ok)
        }
    }

    fun run(args: String, okMsg: String, done: (String) -> Unit = {}) {
        thread {
            busy = true
            val r = Ps.run(args)
            busy = false
            message = if (r.ok) okMsg else "Gagal: " + r.out.take(140)
            done(r.out)
        }
    }

    fun toggleBlock(item: AppItem) {
        thread {
            val ok = if (item.blocked) Ps.unblock(item.pkg) else Ps.block(item.pkg)
            if (ok) {
                apps = apps.map { if (it.pkg == item.pkg) it.copy(blocked = !it.blocked) else it }
                message = if (item.blocked) "Blokir dibuka: ${item.label}" else "Internet diblokir: ${item.label}"
            } else {
                message = "Gagal. Pastikan modul v3.0+ dan root diizinkan."
            }
        }
    }

    fun stats(): Stats {
        val cutoff = System.currentTimeMillis() / 1000 - 7 * 86400
        val evs = events.filter { it.ts >= cutoff }
        val byType = evs.groupBy { it.type }.map { Pair(it.key, it.value.size) }.sortedByDescending { it.second }
        val label = HashMap<String, String>()
        for (a in apps) label[a.pkg] = a.label
        val top = evs.filter { it.pkg.isNotEmpty() && it.type != "ACTION" }
            .groupBy { it.pkg }.map { Pair(label[it.key] ?: it.key, it.value.size) }
            .sortedByDescending { it.second }.take(6)
        val hourly = IntArray(24)
        val cal = Calendar.getInstance()
        for (e in evs) {
            cal.timeInMillis = e.ts * 1000
            hourly[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        val levels = IntArray(4)
        for (a in apps) {
            val i = if (a.score == 0) 0 else if (a.score <= 3) 1 else if (a.score <= 8) 2 else 3
            levels[i]++
        }
        val onCount = SCORE_KEYS.count { cfg.on(it) }
        val score = if (cfg.isEmpty()) 0 else onCount * 100 / SCORE_KEYS.size
        return Stats(evs.size, byType, top, hourly, levels, score, apps.count { it.blocked }, apps.count { it.newApp })
    }

    fun runSelftest() {
        thread {
            busy = true
            val r = Ps.run("selftest")
            busy = false
            if (!r.ok) { message = "Uji sistem gagal: " + r.out.take(120); return@thread }
            val rows = ArrayList<TestRow>()
            var sum = ""
            for (l in r.out.lines()) {
                val p = l.split("|")
                if (p.size >= 4 && p[0] == "T") rows.add(TestRow(p[1], p[2], p.drop(3).joinToString("|")))
                if (p.size >= 4 && p[0] == "SUM") sum = "${p[1]} lulus, ${p[2]} gagal, ${p[3]} dilewati"
            }
            tests = rows
            testSum = sum
        }
    }

    fun runSus() {
        thread {
            busy = true
            val r = Ps.run("sus")
            busy = false
            if (!r.ok) { message = "Pemeriksaan gagal: " + r.out.take(120); return@thread }
            val rows = ArrayList<SusRow>()
            for (l in r.out.lines()) {
                val p = l.split("|")
                if (p.size >= 5 && p[0] == "S") rows.add(SusRow(p[1], p[2], p[3], p.drop(4).joinToString("|")))
            }
            sus = rows
            susRan = true
        }
    }

    fun fwSet(pkg: String, mode: String) {
        thread {
            val r = Ps.run("fwset $pkg $mode")
            message = if (r.ok) "Firewall diperbarui" else "Gagal: " + r.out.take(120)
            if (r.ok) refresh(appCtx)
        }
    }

    class WeekRow(val type: String, val now: Int, val prev: Int, val top: String)

    fun weekly(): List<WeekRow> {
        val nowS = System.currentTimeMillis() / 1000
        val w = 7 * 86400L
        val label = HashMap<String, String>()
        for (a in apps) label[a.pkg] = a.label
        val out = ArrayList<WeekRow>()
        for (t in listOf("MIC", "CAM", "LOC", "CONTACTS", "SMS", "CALLLOG", "NETBIG")) {
            val cur = events.filter { it.type == t && it.ts >= nowS - w }
            val prev = events.count { it.type == t && it.ts >= nowS - 2 * w && it.ts < nowS - w }
            if (cur.isEmpty() && prev == 0) continue
            val top = cur.filter { it.pkg.isNotEmpty() }.groupBy { it.pkg }.maxByOrNull { it.value.size }?.key
            out.add(WeekRow(t, cur.size, prev, if (top == null) "" else (label[top] ?: top)))
        }
        return out
    }
}
