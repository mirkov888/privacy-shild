#!/usr/bin/env python3
# Install the complete Mora Shield Phase 3 patch. Idempotent and conservative.
from pathlib import Path
import argparse
import shutil
import sys

PATCH = Path(__file__).resolve().parent
SRC = PATCH / "app/src/main/java/com/mora/shield"
JAVA = Path("app/src/main/java/com/mora/shield")

def require(ok, message):
    if not ok:
        raise RuntimeError(message)

def edit(project, rel, transform):
    path = project / rel
    old = path.read_text(encoding="utf-8")
    new = transform(old)
    if new == old:
        print("sudah terintegrasi:", rel)
        return
    backup = path.with_suffix(path.suffix + ".phase3.bak")
    if not backup.exists():
        shutil.copy2(path, backup)
    path.write_text(new, encoding="utf-8")
    print("diubah:", rel)

def patch_main(text):
    old_titles = '    val titles = listOf(\n        "Beranda",\n        "Monitor",\n        "Aplikasi",\n        "Pengaturan",\n        "Alat"\n    )'
    new_titles = '    val titles = listOf(\n        "Beranda",\n        "Monitor",\n        "Aplikasi",\n        "Pengaturan",\n        "Mora Core",\n        "Alat"\n    )'
    if old_titles in text:
        text = text.replace(old_titles, new_titles, 1)
    else:
        require(new_titles in text, "Daftar tab MainActivity berbeda; hentikan patch.")
    old_icons = '    val icons = listOf(\n        Icons.Filled.Home,\n        Icons.Filled.Notifications,\n        Icons.Filled.List,\n        Icons.Filled.Settings,\n        Icons.Filled.BuildIcon\n    )'
    new_icons = '    val icons = listOf(\n        Icons.Filled.Home,\n        Icons.Filled.Notifications,\n        Icons.Filled.List,\n        Icons.Filled.Settings,\n        Icons.Filled.BuildIcon,\n        Icons.Filled.BuildIcon\n    )'
    if old_icons in text:
        text = text.replace(old_icons, new_icons, 1)
    else:
        require(new_icons in text, "Daftar ikon MainActivity berbeda; hentikan patch.")
    old_screen = '                3 -> SettingsScreen()\n\n                4 -> ToolsScreen()'
    new_screen = '                3 -> SettingsScreen()\n\n                4 -> MoraCoreScreen()\n\n                5 -> ToolsScreen()'
    if old_screen in text:
        text = text.replace(old_screen, new_screen, 1)
    else:
        require(new_screen in text, "Pemetaan tab MainActivity berbeda; hentikan patch.")
    return text

def patch_tools(text):
    anchor = "        LinkCard()\n"
    require(anchor in text, "Anchor LinkCard() tidak ditemukan di ScreenTools.")
    add = ""
    if "ScreenTimeCard()" not in text:
        add += "        ScreenTimeCard()\n"
    if "TerminalCard()" not in text:
        add += "        TerminalCard()\n"
    return text.replace(anchor, anchor + add, 1) if add else text

def patch_service(text):
    if "ScreenTimeTracker" in text:
        return text
    anchor = "import android.content.Intent\n"
    require(anchor in text, "Import Intent tidak ditemukan di EventService.")
    text = text.replace(anchor, anchor + "import android.content.IntentFilter\nimport android.os.PowerManager\nimport androidx.core.content.ContextCompat\n", 1)
    anchor = "    private val lastShown = HashMap<String, Long>()\n"
    fields = '''    private val lastShown = HashMap<String, Long>()
    private var screenReceiverRegistered = false
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> ScreenTimeTracker.setInteractive(context, true)
                Intent.ACTION_SCREEN_OFF -> ScreenTimeTracker.setInteractive(context, false)
            }
        }
    }
'''
    require(anchor in text, "Field lastShown tidak ditemukan.")
    text = text.replace(anchor, fields, 1)
    anchor = "        if (!running) { startReader(); startStats() }\n"
    require(anchor in text, "Anchor startReader/startStats tidak ditemukan.")
    text = text.replace(anchor, anchor + "        registerScreenReceiver()\n", 1)
    anchor = "    private fun startReader() {\n"
    method = '''    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        try {
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiverRegistered = true
            ScreenTimeTracker.setInteractive(this, getSystemService(PowerManager::class.java).isInteractive)
        } catch (_: Exception) {
            // Screen Time card still samples while the UI is open.
        }
    }

'''
    require(anchor in text, "Method startReader tidak ditemukan.")
    text = text.replace(anchor, method + anchor, 1)
    old_stats = '''                        val st = Notif.statsText(this)
                        getSystemService(android.app.NotificationManager::class.java).notify(1, Notif.foreground(this, st.first, st.second))'''
    new_stats = '''                        val st = Notif.statsText(this)
                        val power = getSystemService(PowerManager::class.java)
                        val screen = ScreenTimeTracker.tick(this, power.isInteractive)
                        val screenLine = "Layar ${ScreenTimeTracker.format(screen.screenOnMs)} · standby ${ScreenTimeTracker.format(screen.standbyMs)}"
                        getSystemService(android.app.NotificationManager::class.java).notify(
                            1,
                            Notif.foreground(this, st.first, st.second + "\\n" + screenLine)
                        )'''
    require(old_stats in text, "Anchor notifikasi statistik tidak ditemukan.")
    text = text.replace(old_stats, new_stats, 1)
    old_destroy = '''        running = false
        proc?.destroy()
        super.onDestroy()'''
    new_destroy = '''        running = false
        proc?.destroy()
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        super.onDestroy()'''
    require(old_destroy in text, "Anchor onDestroy tidak ditemukan.")
    return text.replace(old_destroy, new_destroy, 1)

def update_readme(project):
    path = project / "README.md"
    old = path.read_text(encoding="utf-8") if path.exists() else "# Mora Shield\n"
    marker = "## Mora Shield Phase 3 Integrated"
    if marker in old:
        return
    section = '''
## Mora Shield Phase 3 Integrated

- Dedicated Mora Core tab with the three-app registry and exact connection states.
- Per-app policy master switch, activity monitoring, connector command access and capability allowlist.
- Central activity timeline, connector overview and command audit.
- Existing Downloader controls and SystemManager policy UI.
- Daily screen-on/standby tracking and local terminal in Tools.
- See `docs/MORA_SHIELD_APP_MODULE_ROADMAP.md` for future app and module updates.

Connector commands remain deny-by-default and require user approval. Terminal is local to Shield, not exposed through Connector IPC. Network/background policies are not OS-enforced unless the worker implements the corresponding handler.
'''
    path.write_text(old.rstrip() + "\n\n" + section, encoding="utf-8")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("project", nargs="?", type=Path, default=Path.cwd())
    project = parser.parse_args().project.expanduser().resolve()
    require(project.is_dir(), f"Folder tidak ditemukan: {project}")
    required = [
        JAVA / "MainActivity.kt", JAVA / "ScreenTools.kt", JAVA / "EventService.kt",
        JAVA / "MoraCoreCard.kt", JAVA / "MoraDownloaderControlCard.kt",
        JAVA / "MoraSystemManagerPolicyCard.kt", JAVA / "core/MoraCore.kt",
        JAVA / "core/AppRegistry.kt", Path("app/src/main/AndroidManifest.xml"),
        Path("app/build.gradle.kts")
    ]
    missing = [str(p) for p in required if not (project / p).is_file()]
    require(not missing, "File wajib tidak ditemukan:\n" + "\n".join(missing))
    registry = (project / JAVA / "core/AppRegistry.kt").read_text(encoding="utf-8")
    core = (project / JAVA / "core/MoraCore.kt").read_text(encoding="utf-8")
    require(all(x in registry for x in ["com.mora.shield", "com.mora.nsfwdownloader", "com.mora.systemmanager"]),
            "Registry belum berisi ketiga aplikasi Mora.")
    require("recentCommandAudit" in core and "updateAppPolicy" in core, "API Mora Core tidak cocok.")

    for name in ["MoraCoreScreen.kt", "ScreenTimeTracker.kt", "ScreenTimeCard.kt", "TerminalCard.kt"]:
        source = SRC / name
        target = project / JAVA / name
        if target.exists():
            require(target.read_bytes() == source.read_bytes(),
                    f"{target.relative_to(project)} sudah ada dan berbeda; merge manual diperlukan.")
            print("sudah ada:", target.relative_to(project))
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            print("ditambahkan:", target.relative_to(project))

    edit(project, JAVA / "MainActivity.kt", patch_main)
    edit(project, JAVA / "ScreenTools.kt", patch_tools)
    edit(project, JAVA / "EventService.kt", patch_service)

    road_src = PATCH / "docs/MORA_SHIELD_APP_MODULE_ROADMAP.md"
    road_dst = project / "docs/MORA_SHIELD_APP_MODULE_ROADMAP.md"
    road_dst.parent.mkdir(parents=True, exist_ok=True)
    if not road_dst.exists():
        shutil.copy2(road_src, road_dst)
        print("ditambahkan:", road_dst.relative_to(project))
    else:
        print("roadmap sudah ada; tidak ditimpa.")
    update_readme(project)
    print("\nPatch terpasang. Build belum dijalankan oleh installer.")
    print("Jalankan: git diff --check")
    print("Lalu: bash ./gradlew :app:assembleDebug --no-daemon")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("ERROR:", exc, file=sys.stderr)
        raise SystemExit(2)
