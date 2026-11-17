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

            export TMPDIR="$PREFIX/tmp"
            export XDG_RUNTIME_DIR="${'$'}TMPDIR"
            mkdir -p "${'$'}XDG_RUNTIME_DIR"
            X11_SOCKET="${'$'}TMPDIR/.X11-unix/X0"

            if [ ! -S "${'$'}X11_SOCKET" ]; then
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
                echo MORA_X11_SOCKET_MISSING
                echo "Socket X11 tidak terbentuk. Periksa aplikasi Termux:X11."
                exit 21
            fi

            /system/bin/am start --user 0 -n com.termux.x11/com.termux.x11.MainActivity >/dev/null 2>&1 || true
            sleep 1

            "$PREFIX/bin/proot-distro" login --shared-tmp "$distro" -- bash -lc '
                set -e
                export XDG_RUNTIME_DIR=/tmp

                if [ ! -S /tmp/.X11-unix/X0 ]; then
                    echo MORA_X11_SOCKET_NOT_SHARED
                    echo "Socket X11 host tidak terlihat melalui shared /tmp."
                    exit 22
                fi

                if ! command -v python3 >/dev/null 2>&1; then
                    echo MORA_X11_PROXY_PYTHON_MISSING
                    echo "Python 3 diperlukan untuk menjalankan proxy X11."
                    exit 24
                fi

                mkdir -p /tmp/.X11-unix
                pkill -TERM -f "[m]ora-x11-proxy.py" 2>/dev/null || true
                rm -f /tmp/.X11-unix/X99

                cat > /tmp/mora-x11-proxy.py <<PY
            import os
            import socket
            import threading
            import select

            TARGET = chr(0) + "$PREFIX/tmp/.X11-unix/X0"
            LOCAL = "/tmp/.X11-unix/X99"

            def relay(client, upstream):
                try:
                    while True:
                        ready, _, _ = select.select([client, upstream], [], [])
                        for source in ready:
                            data = source.recv(65536)
                            if not data:
                                return
                            destination = upstream if source is client else client
                            destination.sendall(data)
                except (OSError, ValueError):
                    pass
                finally:
                    client.close()
                    upstream.close()

            def handle(client):
                upstream = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
                try:
                    upstream.connect(TARGET)
                    relay(client, upstream)
                except OSError:
                    client.close()
                    upstream.close()

            server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            server.bind(LOCAL)
            server.listen(16)
            print("PROXY_READY", flush=True)
            while True:
                client, _ = server.accept()
                threading.Thread(target=handle, args=(client,), daemon=True).start()
            PY

                nohup python3 -u /tmp/mora-x11-proxy.py >/tmp/mora-x11-proxy.log 2>&1 </dev/null &
                PROXY_PID=$!

                for attempt in 1 2 3 4 5; do
                    [ -S /tmp/.X11-unix/X99 ] && break
                    sleep 1
                done

                if [ ! -S /tmp/.X11-unix/X99 ]; then
                    echo MORA_X11_PROXY_FAILED
                    cat /tmp/mora-x11-proxy.log 2>/dev/null || true
                    exit 25
                fi

                export DISPLAY=:99
                if command -v xdpyinfo >/dev/null 2>&1; then
                    if ! timeout 8 xdpyinfo -display "${'$'}DISPLAY" >/dev/null 2>&1; then
                        echo MORA_X11_CONNECTION_FAILED
                        cat /tmp/mora-x11-proxy.log 2>/dev/null || true
                        exit 23
                    fi
                fi

                if pgrep -x xfce4-session >/dev/null 2>&1; then
                    echo MORA_XFCE_ALREADY_RUNNING
                    exit 0
                fi

                exec dbus-run-session -- startxfce4
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
                pkill -TERM -x xfdesktop 2>/dev/null || true
                pkill -TERM -f "[m]ora-x11-proxy.py" 2>/dev/null || true
                rm -f /tmp/.X11-unix/X99 /tmp/mora-x11-proxy.py
                echo MORA_XFCE_STOPPED
                echo MORA_X11_PROXY_STOPPED
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
