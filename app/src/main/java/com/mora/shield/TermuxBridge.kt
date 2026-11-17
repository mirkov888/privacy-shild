package com.mora.shield

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Fixed-command bridge to Termux/proot-distro. Shell input is never taken
 * from arbitrary UI text.
 */
object TermuxBridge {
    const val TERMUX_PACKAGE = "com.termux"
    const val X11_PACKAGE = "com.termux.x11"
    const val RUN_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val ACTION = "com.termux.RUN_COMMAND"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val PREFIX = "/data/data/com.termux/files/usr"
    private const val HOME = "/data/data/com.termux/files/home"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun x11AppInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(X11_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun openX11(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(X11_PACKAGE)
        if (launch != null) context.startActivity(launch)
    }

    fun installX11Package(context: Context) {
        run(context, "x11_install", "$PREFIX/bin/bash",
            arrayOf("-lc", "pkg update -y && pkg install -y x11-repo && pkg update -y && pkg install -y termux-x11-nightly"), true)
    }

    fun hasRunPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            context.checkSelfPermission(RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
        if (launch != null) context.startActivity(launch)
    }

    fun installManager(context: Context) {
        run(context, "bootstrap", "$PREFIX/bin/bash",
            arrayOf("-lc", "pkg update -y && pkg install -y proot-distro"), true)
    }

    fun requestStatus(context: Context, distro: String) {
        requireDistro(distro)
        val rootfs = "$PREFIX/var/lib/proot-distro/containers/$distro/rootfs"
        val container = "$PREFIX/var/lib/proot-distro/containers/$distro"
        val script = """
            if [ ! -d "$container" ]; then
                echo MORA_DISTRO_NOT_INSTALLED
                exit 0
            fi
            echo MORA_DISTRO_INSTALLED
            if [ ! -x "$rootfs/bin/sh" ] || [ ! -f "$rootfs/etc/os-release" ]; then
                echo MORA_ROOTFS_BROKEN
                exit 0
            fi
            echo MORA_ROOTFS_OK
            "$PREFIX/bin/proot-distro" login "$distro" -- bash -lc '
                if command -v xfce4-session >/dev/null 2>&1 && command -v dbus-run-session >/dev/null 2>&1; then
                    echo MORA_DESKTOP_READY
                else
                    echo MORA_DESKTOP_MISSING
                fi
                if pgrep -x xfce4-session >/dev/null 2>&1; then
                    echo MORA_XFCE_RUNNING
                else
                    echo MORA_XFCE_STOPPED
                fi
            '
            if command -v termux-x11 >/dev/null 2>&1; then
                echo MORA_X11_PACKAGE_READY
            else
                echo MORA_X11_PACKAGE_MISSING
            fi
        """.trimIndent()
        run(context, "status:$distro", "$PREFIX/bin/bash", arrayOf("-lc", script), true)
    }

    fun installDistro(context: Context, distro: String) {
        requireDistro(distro)
        run(context, "install:$distro", "$PREFIX/bin/bash",
            arrayOf("-lc", installScript(distro, removeFirst = false)), true)
    }

    /** Destructive repair is only called after an explicit UI warning. */
    fun repairDistro(context: Context, distro: String) {
        requireDistro(distro)
        run(context, "repair:$distro", "$PREFIX/bin/bash",
            arrayOf("-lc", installScript(distro, removeFirst = true)), true)
    }

    private fun installScript(distro: String, removeFirst: Boolean): String {
        val remove = if (removeFirst) {
            "\"$PREFIX/bin/proot-distro\" remove \"$distro\" >/dev/null 2>&1 || true"
        } else ":"
        return """
            set +e
            $remove
            for attempt in 1 2 3; do
                "$PREFIX/bin/proot-distro" install "$distro" && exit 0
                echo MORA_INSTALL_RETRY
                "$PREFIX/bin/proot-distro" remove "$distro" >/dev/null 2>&1 || true
                sleep 5
            done
            echo MORA_INSTALL_FAILED
            exit 1
        """.trimIndent()
    }

    fun installDesktop(context: Context, distro: String) {
        requireDistro(distro)
        val script = if (distro == "archlinux") {
            "pacman -Syu --noconfirm xfce4 xfce4-goodies dbus"
        } else {
            "apt-get update && apt-get install -y xfce4 xfce4-goodies dbus-x11"
        }
        run(context, "desktop:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", distro, "--", "bash", "-lc", script), true)
    }

    fun startDesktop(context: Context, distro: String) {
        requireDistro(distro)
        val script = """
            set -e
            if ! command -v termux-x11 >/dev/null 2>&1; then
                echo "Komponen termux-x11 belum terpasang di Termux."
                exit 20
            fi

            # Termux:X11 creates its Unix socket under Termux TMPDIR.
            # Keep the server and PRoot guest on the exact same shared /tmp.
            export TMPDIR="$PREFIX/tmp"
            export XDG_RUNTIME_DIR="${'$'}TMPDIR"
            mkdir -p "${'$'}XDG_RUNTIME_DIR"
            X11_SOCKET="${'$'}TMPDIR/.X11-unix/X0"

            if [ ! -S "${'$'}X11_SOCKET" ]; then
                # A stale server process without its socket cannot serve clients.
                pkill -x termux-x11 2>/dev/null || true
                for attempt in 1 2 3 4 5; do
                    pgrep -x termux-x11 >/dev/null 2>&1 || break
                    sleep 1
                done
                termux-x11 :0 >/dev/null 2>&1 &
                for attempt in 1 2 3 4 5 6 7 8 9 10; do
                    [ -S "${'$'}X11_SOCKET" ] && break
                    sleep 1
                done
            fi

            if [ ! -S "${'$'}X11_SOCKET" ]; then
                echo "MORA_X11_SOCKET_MISSING"
                echo "Socket X11 tidak terbentuk di ${'$'}X11_SOCKET. Buka Termux:X11 dan periksa instalasi termux-x11-nightly."
                exit 21
            fi

            /system/bin/am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null 2>&1 || true
            sleep 1
            "$PREFIX/bin/proot-distro" login --shared-tmp "$distro" -- bash -lc '
                export DISPLAY=:0
                export XDG_RUNTIME_DIR=/tmp
                if [ ! -S /tmp/.X11-unix/X0 ]; then
                    echo MORA_X11_SOCKET_NOT_SHARED
                    echo "Socket X11 host tidak terlihat di Debian. Shared /tmp gagal."
                    exit 22
                fi
                if command -v xdpyinfo >/dev/null 2>&1; then
                    if ! xdpyinfo -display "${'$'}DISPLAY" >/dev/null 2>&1; then
                        echo MORA_X11_CONNECTION_FAILED
                        echo "Socket terlihat, tetapi Debian tidak dapat terhubung ke display :0."
                        exit 23
                    fi
                fi
                if pgrep -x xfce4-session >/dev/null 2>&1; then
                    echo MORA_XFCE_ALREADY_RUNNING
                    exit 0
                fi
                exec dbus-run-session -- xfce4-session
            '
        """.trimIndent()
        run(context, "start:$distro", "$PREFIX/bin/bash",
            arrayOf("-lc", script), true)
    }

    fun stopDesktop(context: Context, distro: String) {
        requireDistro(distro)
        val script = """
            "$PREFIX/bin/proot-distro" login --shared-tmp "$distro" -- bash -lc '
                pkill -TERM -x xfce4-session 2>/dev/null || true
                pkill -TERM -x xfwm4 2>/dev/null || true
                pkill -TERM -x xfce4-panel 2>/dev/null || true
                echo MORA_XFCE_STOPPED
            '
        """.trimIndent()
        run(context, "stop:$distro", "$PREFIX/bin/bash", arrayOf("-lc", script), true)
    }

    private fun requireDistro(distro: String) {
        require(distro in setOf("debian", "ubuntu", "archlinux")) { "Unsupported distro" }
    }

    private fun run(context: Context, operation: String, path: String, args: Array<String>, background: Boolean) {
        check(installed(context)) { "Termux belum terpasang" }
        check(hasRunPermission(context)) { "Izin Run commands in Termux belum diberikan" }
        val callback = Intent(context, TermuxResultReceiver::class.java).apply {
            action = "com.mora.shield.TERMUX_RESULT"
            putExtra(TermuxResultReceiver.EXTRA_OPERATION, operation)
        }
        val flags = PendingIntent.FLAG_ONE_SHOT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val pending = PendingIntent.getBroadcast(context, operation.hashCode(), callback, flags)
        val intent = Intent(ACTION).apply {
            setClassName(TERMUX_PACKAGE, SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", path)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
            putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending)
        }
        context.startService(intent)
    }
}
