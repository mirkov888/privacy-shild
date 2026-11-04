package com.mora.shield

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var content: FrameLayout
    private var tab = 0

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun bg(work: () -> Unit) {
        Thread {
            try {
                work()
            } catch (e: Exception) {
                ui.post { toast("Error: " + (e.message ?: "?")) }
            }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(24), dp(12), dp(8))

        val title = TextView(this)
        title.text = "Mora Shield"
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        root.addView(title)

        status = TextView(this)
        status.textSize = 13f
        status.text = "Memeriksa modul…"
        status.setPadding(0, dp(4), 0, dp(8))
        root.addView(status)

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        val names = arrayOf("Aktivitas", "Aplikasi", "Kendali", "Kernel", "Scripts", "Info")
        for ((i, n) in names.withIndex()) {
            val b = Button(this)
            b.text = n
            b.textSize = 12f
            b.isAllCaps = false
            b.setOnClickListener { show(i) }
            tabs.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(tabs)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val prefs = getSharedPreferences("mora", Context.MODE_PRIVATE)
        if (prefs.getBoolean("service", true)) startMonitor()
        checkModule()
        show(0)
    }

    private fun startMonitor() {
        try {
            startForegroundService(Intent(this, EventService::class.java))
        } catch (e: Exception) {
            toast("Pemantau gagal dimulai: " + (e.message ?: "?"))
        }
    }

    private fun checkModule() {
        bg {
            val rootOk = Root.available()
            val inst = rootOk && Ps.installed()
            val ver = if (inst) Ps.version() else ""
            ui.post {
                status.text = when {
                    !rootOk -> "Akses root belum diberikan. Buka KernelSU Manager > Superuser dan izinkan Mora Shield."
                    !inst -> "Modul Mora Privacy Shield tidak ditemukan. Pasang modulnya dulu (v3.0 atau lebih baru)."
                    Ps.major(ver) < 3 -> "Modul terpasang versi $ver terlalu lama. Pasang v3.0.0 atau lebih baru lalu REBOOT."
                    else -> "Modul terdeteksi: $ver"
                }
            }
        }
    }

    private fun show(i: Int) {
        tab = i
        content.removeAllViews()
        when (i) {
            0 -> showEvents()
            1 -> showApps()
            2 -> showControl()
            3 -> showKernel()
            4 -> showScripts()
            else -> showInfo()
        }
    }

    // ------------------------------------------------------------ Aktivitas

    private fun typeName(t: String): String = when (t) {
        "MIC" -> "mikrofon"
        "CAM" -> "kamera"
        "LOC" -> "lokasi"
        "CONTACTS" -> "kontak"
        "SMS" -> "SMS"
        "CALLLOG" -> "log panggilan"
        "NETBIG" -> "kirim data besar"
        "NEWAPP" -> "aplikasi baru"
        "ACTION" -> "aksi darurat"
        else -> t
    }

    private fun showEvents() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val refresh = Button(this)
        refresh.text = "Segarkan"
        refresh.isAllCaps = false
        box.addView(refresh)
        val list = ListView(this)
        box.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(box)

        val fmt = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
        fun load() {
            bg {
                val evs = Ps.events(100)
                ui.post {
                    list.adapter = object : BaseAdapter() {
                        override fun getCount(): Int = if (evs.isEmpty()) 1 else evs.size
                        override fun getItem(position: Int): Any = position
                        override fun getItemId(position: Int): Long = position.toLong()
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                            val tv = TextView(this@MainActivity)
                            tv.setPadding(dp(8), dp(8), dp(8), dp(8))
                            if (evs.isEmpty()) {
                                tv.text = "Belum ada kejadian. Nyalakan pemantauan di tab Kendali."
                                return tv
                            }
                            val e = evs[position]
                            val who = if (e.pkg.isEmpty()) "—" else Notif.label(this@MainActivity, e.pkg)
                            tv.text = who + " — " + typeName(e.type) + "\n" +
                                fmt.format(Date(e.ts * 1000)) + (if (e.detail.isEmpty()) "" else " · " + e.detail)
                            return tv
                        }
                    }
                }
            }
        }
        refresh.setOnClickListener { load() }
        load()
    }

    // ------------------------------------------------------------ Aplikasi

    private data class AppRow(
        val pkg: String,
        val label: String,
        val icon: Drawable?,
        var score: Int,
        var names: String,
        var blocked: Boolean
    )

    private fun showApps() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val info = TextView(this)
        info.text = "Memuat aplikasi…"
        info.setPadding(dp(8), dp(8), dp(8), dp(8))
        box.addView(info)
        val list = ListView(this)
        box.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(box)

        bg {
            val pm = packageManager
            val rows = ArrayList<AppRow>()
            for (ai in pm.getInstalledApplications(0)) {
                if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                if (ai.packageName == packageName) continue
                val label = pm.getApplicationLabel(ai).toString()
                rows.add(AppRow(ai.packageName, label, try { pm.getApplicationIcon(ai) } catch (e: Exception) { null }, 0, "", false))
            }
            val trk = Ps.trackers()
            val blocked = Ps.blockedPkgs(Ps.status())
            for (r in rows) {
                val t = trk[r.pkg]
                if (t != null) {
                    r.score = t.first
                    r.names = t.second
                }
                r.blocked = blocked.contains(r.pkg)
            }
            rows.sortWith(compareByDescending<AppRow> { it.score }.thenBy { it.label.lowercase() })
            ui.post {
                info.text = if (trk.isEmpty()) "Belum ada hasil pemindaian pelacak. Jalankan di tab Kendali." else "${rows.size} aplikasi. Ketuk untuk opsi."
                list.adapter = object : BaseAdapter() {
                    override fun getCount(): Int = rows.size
                    override fun getItem(position: Int): Any = rows[position]
                    override fun getItemId(position: Int): Long = position.toLong()
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                        val r = rows[position]
                        val row = LinearLayout(this@MainActivity)
                        row.orientation = LinearLayout.HORIZONTAL
                        row.gravity = Gravity.CENTER_VERTICAL
                        row.setPadding(dp(8), dp(6), dp(8), dp(6))
                        val iv = ImageView(this@MainActivity)
                        iv.setImageDrawable(r.icon)
                        row.addView(iv, LinearLayout.LayoutParams(dp(40), dp(40)))
                        val tv = TextView(this@MainActivity)
                        tv.setPadding(dp(12), 0, 0, 0)
                        val lvl = if (r.score == 0) "bersih" else if (r.score <= 3) "rendah" else if (r.score <= 8) "sedang" else "tinggi"
                        tv.text = r.label + "\npelacak: skor ${r.score} ($lvl) · " + (if (r.blocked) "internet DIBLOKIR" else "online")
                        row.addView(tv)
                        return row
                    }
                }
                list.setOnItemClickListener { _, _, position, _ -> appDialog(rows[position], list) }
            }
        }
    }

    private fun appDialog(r: AppRow, list: ListView) {
        val items = arrayOf(
            if (r.blocked) "Buka blokir internet" else "Blokir internet",
            "Izin dan info aplikasi (pengaturan sistem)",
            "Lihat pelacak yang terdeteksi"
        )
        AlertDialog.Builder(this)
            .setTitle(r.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> bg {
                        val ok = if (r.blocked) Ps.unblock(r.pkg) else Ps.block(r.pkg)
                        ui.post {
                            if (ok) {
                                r.blocked = !r.blocked
                                (list.adapter as BaseAdapter).notifyDataSetChanged()
                                toast(if (r.blocked) "Diblokir" else "Blokir dibuka")
                            } else toast("Gagal. Pastikan modul terpasang dan root diizinkan.")
                        }
                    }
                    1 -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + r.pkg))
                    )
                    else -> AlertDialog.Builder(this)
                        .setTitle(r.label)
                        .setMessage(if (r.names.isEmpty()) "Tidak ada SDK pelacak yang dikenal terdeteksi (atau belum dipindai)." else r.names.split(",").joinToString("\n• ", "• "))
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ------------------------------------------------------------ Kendali

    private fun section(parent: LinearLayout, text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 14f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setPadding(0, dp(16), 0, dp(6))
        parent.addView(t)
    }

    private fun btn(parent: LinearLayout, text: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        parent.addView(b)
    }

    private fun confirm(title: String, msg: String, ok: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(ok) { _, _ -> action() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun moduleSwitch(parent: LinearLayout, key: String, text: String, st: Map<String, String>) {
        val sw = Switch(this)
        sw.text = text
        sw.isChecked = st[key] == "1"
        sw.setOnCheckedChangeListener { v, on ->
            if (!v.isPressed) return@setOnCheckedChangeListener
            bg {
                val r = Ps.set(key, if (on) "1" else "0")
                ui.post { toast(if (r.ok) "Disimpan" else "Gagal: " + r.out.take(140)) }
            }
        }
        parent.addView(sw)
    }

    private fun showControl() {
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(4), 0, dp(4), dp(16))
        sv.addView(col)
        content.addView(sv)

        section(col, "Pemantau di aplikasi")
        val prefs = getSharedPreferences("mora", Context.MODE_PRIVATE)
        val svc = Switch(this)
        svc.text = "Notifikasi kejadian (layanan latar)"
        svc.isChecked = prefs.getBoolean("service", true)
        svc.setOnCheckedChangeListener { v, on ->
            if (!v.isPressed) return@setOnCheckedChangeListener
            prefs.edit().putBoolean("service", on).apply()
            if (on) startMonitor() else stopService(Intent(this, EventService::class.java))
        }
        col.addView(svc)
        btn(col, "Hapus daftar aplikasi yang diabaikan") {
            prefs.edit().remove("ignored").apply()
            toast("Daftar diabaikan dikosongkan")
        }

        section(col, "Pemantauan di modul")
        val holder = LinearLayout(this)
        holder.orientation = LinearLayout.VERTICAL
        col.addView(holder)
        bg {
            val st = Ps.status()
            ui.post {
                if (st.isEmpty()) {
                    val t = TextView(this)
                    t.text = "Tidak bisa membaca modul. Cek status di atas."
                    holder.addView(t)
                } else {
                    moduleSwitch(holder, "am_on", "Pantau akses mikrofon/kamera/lokasi", st)
                    moduleSwitch(holder, "nb_on", "Pantau data keluar saat layar mati", st)
                    moduleSwitch(holder, "na_on", "Blokir internet untuk aplikasi baru", st)
                }
            }
        }

        section(col, "Pemindaian")
        btn(col, "Pindai SDK pelacak di semua aplikasi") {
            bg {
                val r = Ps.run("trkstart")
                ui.post { toast(if (r.out.contains("started")) "Pemindaian dimulai. Hasil di tab Aplikasi dalam beberapa menit." else r.out) }
            }
        }

        section(col, "UltimatePrivacy (ekstensi terintegrasi)")
        btn(col, "Status UltimatePrivacy") {
            bg { val r=Ps.ultimate("status"); ui.post { AlertDialog.Builder(this).setTitle("UltimatePrivacy").setMessage(r.out).setPositiveButton("Tutup",null).show() } }
        }
        btn(col, "Sensor privacy: aktifkan semua sensor") {
            confirm("Aktifkan sensor privacy?", "Perintah global sensor privacy bergantung pada dukungan ROM. Verifikasi kembali melalui Pengaturan > Privasi.", "Aktifkan") {
                bg { val r=Ps.ultimate("sensor enable"); ui.post { toast(r.out.take(180)) } }
            }
        }
        btn(col, "Sensor privacy: nonaktifkan semua sensor") {
            confirm("Nonaktifkan sensor privacy?", "Akses kamera dan mikrofon akan dipulihkan sesuai dukungan ROM.", "Nonaktifkan") {
                bg { val r=Ps.ultimate("sensor disable"); ui.post { toast(r.out.take(180)) } }
            }
        }
        btn(col, "UltimatePrivacy lockdown marker ON/OFF") {
            confirm("Ubah marker lockdown?", "Marker ini hanya penanda; bukan pemblokiran sistem menyeluruh.", "ON") {
                bg { val r=Ps.ultimate("lockdown on"); ui.post { toast(r.out.take(180)) } }
            }
        }

        section(col, "Darurat")
        btn(col, "Lockdown sekarang (hanya PIN)") {
            confirm("Lockdown sekarang?", "Sidik jari/wajah dinonaktifkan dan layar dikunci.", "Lockdown") {
                bg { Ps.run("lockdown"); ui.post { toast("Perintah dikirim") } }
            }
        }
        btn(col, "Reboot terkunci sekarang") {
            confirm("Reboot sekarang?", "HP akan reboot. Data tetap ada tetapi terkunci sampai PIN diketik.", "Reboot") {
                bg { Root.sh("svc power reboot || reboot") }
            }
        }
        btn(col, "PANIC: mode pesawat dan kunci") {
            confirm("Jalankan PANIC?", "Mode pesawat, Bluetooth dan NFC mati, cache dibersihkan, layar dikunci.", "Jalankan") {
                bg { Ps.run("panic"); ui.post { toast("Selesai") } }
            }
        }
        btn(col, "Perbaiki internet (darurat)") {
            confirm("Perbaiki internet?", "Mematikan kill switch, firewall, DNS pribadi, dan beberapa proteksi jaringan lalu menyambung ulang Wi-Fi.", "Perbaiki") {
                bg { Ps.run("netfix"); ui.post { toast("Selesai") } }
            }
        }
    }

    // ------------------------------------------------------------ Info

    // Kernel Manager: discovery-first, safe CPU governor controls.
    private fun showKernel() {
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)) }
        scroll.addView(body); content.addView(scroll)
        val stats = TextView(this).apply { textSize = 14f }
        val cpu = TextView(this).apply { textSize = 13f }
        val gpu = TextView(this).apply { textSize = 13f }
        val mem = TextView(this).apply { textSize = 13f }
        val thermal = TextView(this).apply { textSize = 13f }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        btn(body, "Refresh statistik") { loadKernel(stats,cpu,gpu,mem,thermal,controls) }
        section(body,"Preset performa")
        val presets=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("performance" to "Performance","balanced" to "Balanced","battery" to "Battery Saver").forEach { (id,label) ->
            btn(presets,label) { confirm("Terapkan preset", "Preset $label hanya memilih CPU governor yang tersedia; tidak mengubah voltase atau batas thermal.", "Terapkan") {
                bg { val out=KernelManager.setProfile(id); ui.post { toast(out.take(220)); loadKernel(stats,cpu,gpu,mem,thermal,controls) } }
            } }
        }
        body.addView(presets)
        section(body,"Apply on Boot")
        val boot=Switch(this).apply {
            text="Pulihkan CPU governor setelah boot"
            isChecked=KernelManager.applyOnBootEnabled()
            setOnCheckedChangeListener { v,on ->
                if(!v.isPressed) return@setOnCheckedChangeListener
                bg { val out=KernelManager.saveGovernorsForBoot(on); ui.post { toast(out) ; isChecked=KernelManager.applyOnBootEnabled() } }
            }
        }
        body.addView(boot)
        section(body,"Ringkasan sistem"); body.addView(stats)
        section(body,"CPU policy & governor"); body.addView(cpu); body.addView(controls)
        section(body,"GPU / devfreq"); body.addView(gpu)
        section(body,"RAM / ZRAM / I/O"); body.addView(mem)
        section(body,"Thermal / baterai"); body.addView(thermal)
        section(body,"Catatan keamanan")
        body.addView(TextView(this).apply { textSize=12f; text="Kontrol hanya muncul jika node sysfs tersedia. Versi ini hanya mengubah CPU governor yang didukung kernel. Tidak mengubah thermal limit, tegangan, charging current, atau frekuensi GPU." })
        loadKernel(stats,cpu,gpu,mem,thermal,controls)
    }

    private fun loadKernel(stats: TextView,cpu: TextView,gpu: TextView,mem: TextView,thermal: TextView,controls: LinearLayout) {
        bg {
            val summary=KernelManager.summary(); val policies=KernelManager.policies()
            val gpuText=KernelManager.gpu(); val memText=KernelManager.memory(); val thermalText=KernelManager.thermal()
            ui.post {
                stats.text=summary
                cpu.text=policies.joinToString("\n\n") { it.second }.ifBlank { "CPU cpufreq tidak ditemukan." }
                gpu.text=gpuText.ifBlank { "Interface GPU/devfreq tidak ditemukan." }
                mem.text=memText; thermal.text=thermalText
                controls.removeAllViews()
                for ((path,_) in policies) {
                    val governors=KernelManager.read("$path/scaling_available_governors").split(Regex("\\s+")).filter { it.matches(Regex("[A-Za-z0-9_-]+")) }
                    if(governors.isNotEmpty()) {
                        section(controls,"Pilih governor · ${path.substringAfterLast('/')}")
                        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
                        governors.forEach { g -> btn(row,g) { confirm("CPU governor","Terapkan $g pada ${path.substringAfterLast('/')}?", "Terapkan") { bg { val msg=KernelManager.setGovernor(path,g); ui.post { toast(msg); loadKernel(stats,cpu,gpu,mem,thermal,controls) } } } } }
                        controls.addView(row)
                    }
                }
            }
        }
    }


    // ------------------------------------------------------------ Script Manager
    private fun showScripts() {
        val col=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8)) }
        val sc=ScrollView(this); sc.addView(col); content.addView(sc)
        val info=TextView(this).apply { text="Script dijalankan sebagai root. Hanya jalankan script yang kamu pahami."; textSize=13f }
        col.addView(info)
        btn(col,"Buat script baru") {
            val name=android.widget.EditText(this).apply { hint="nama-script.sh" }
            val body=android.widget.EditText(this).apply { hint="#!/system/bin/sh\\necho Hello"; minLines=8; gravity=Gravity.TOP }
            val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),0); addView(name); addView(body) }
            AlertDialog.Builder(this).setTitle("Script root baru").setView(layout)
                .setPositiveButton("Simpan") { _,_-> bg { val r=ScriptManager.save(name.text.toString(),body.text.toString()); ui.post { toast(r) ; show(4) } } }
                .setNegativeButton("Batal",null).show()
        }
        btn(col,"Refresh daftar script") { show(4) }
        bg {
            val files=ScriptManager.list()
            ui.post {
                if(files.isEmpty()) col.addView(TextView(this).apply { text="Belum ada script. Buat script pertama." })
                files.forEach { file ->
                    val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
                    val label=TextView(this).apply { text=file; textSize=14f }
                    row.addView(label,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
                    val run=Button(this).apply { text="Jalankan"; isAllCaps=false; setOnClickListener {
                        confirm("Jalankan script root?", "Script $file akan berjalan dengan hak root. Pastikan kamu mempercayai isinya.", "Jalankan") {
                            bg { val out=ScriptManager.run(file); ui.post { AlertDialog.Builder(this@MainActivity).setTitle(file).setMessage(out).setPositiveButton("Tutup",null).show() } }
                        }
                    } }
                    val del=Button(this).apply { text="Hapus"; isAllCaps=false; setOnClickListener {
                        confirm("Hapus script?",file,"Hapus"){bg{val out=ScriptManager.delete(file);ui.post{toast(out);show(4)}}}
                    } }
                    row.addView(run); row.addView(del); col.addView(row)
                }
            }
        }
    }

    private fun showInfo() {
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(8), dp(8), dp(8), dp(16))
        sv.addView(col)
        val tv = TextView(this)
        tv.text = "Mora Shield adalah pendamping modul Mora Privacy Shield.\n\n" +
            "• Semua kerja berat dilakukan modul lewat akses root. Aplikasi ini hanya menampilkan kejadian dan mengirim perintah.\n" +
            "• Aplikasi tidak memakai internet dan tidak mengirim data apa pun keluar.\n" +
            "• Kejadian berasal dari pemeriksaan berkala (AppOps dan penghitung iptables). Akses yang sangat singkat bisa terlewat, dan isi data tidak bisa dilihat.\n" +
            "• Notifikasi memiliki tombol Blokir internet, Izinkan, dan Abaikan.\n" +
            "• Tile Lockdown bisa ditambahkan ke panel pengaturan cepat.\n\n" +
            "Bila notifikasi tidak muncul: izinkan notifikasi untuk aplikasi ini dan matikan optimasi baterai untuknya.\n" +
            "Bila saklar di tab Kendali gagal: pastikan modul v3.0.0 atau lebih baru dan HP sudah di-reboot setelah memasangnya."
        col.addView(tv)
        btn(col, "Diagnostik (kirim hasilnya bila ada masalah)") {
            bg {
                val rootId = Root.sh("id")
                val inst = Root.sh("ls -la ${Ps.MOD} 2>&1 | head -n 15")
                val ver = Ps.version()
                val st = Ps.run("status").out.lines().take(25).joinToString("\n")
                val test = Ps.run("set am_sec 20")
                val txt = "root: " + rootId.out + "\nversi modul: " + ver + "\n\n" + inst.out +
                    "\n\nuji set: kode=" + test.code + " " + test.out + "\n\nstatus (25 baris):\n" + st
                ui.post {
                    val t = TextView(this)
                    t.text = txt
                    t.textSize = 11f
                    t.setTextIsSelectable(true)
                    t.setPadding(dp(12), dp(8), dp(12), dp(8))
                    val s2 = ScrollView(this)
                    s2.addView(t)
                    AlertDialog.Builder(this).setTitle("Diagnostik").setView(s2).setPositiveButton("Tutup", null).show()
                }
            }
        }
        content.addView(sv)
    }
}
