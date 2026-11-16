package com.mora.shield

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Narrow Termux bridge. Only fixed proot-distro/XFCE commands are exposed;
 * no free-form shell command is accepted from UI state or external intents.
 */
object TermuxBridge {
    const val TERMUX_PACKAGE = "com.termux"
    const val RUN_PERMISSION = "com.termux.permission.RUN_COMMAND"
    private const val ACTION = "com.termux.RUN_COMMAND"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val PREFIX = "/data/data/com.termux/files/usr"
    private const val HOME = "/data/data/com.termux/files/home"

    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun hasRunPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || context.checkSelfPermission(RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
        if (launch != null) context.startActivity(launch)
    }

    fun requestStatus(context: Context, distro: String) {
        run(context, "status:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("list", "--quiet"), true)
        run(context, "session:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", distro, "--", "bash", "-lc", "vncserver -list 2>/dev/null | grep -q ':1' && echo MORA_XFCE_RUNNING || echo MORA_XFCE_STOPPED"), true)
    }

    fun installDistro(context: Context, distro: String) {
        requireDistro(distro)
        run(context, "install:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("install", distro), true)
    }

    fun installDesktop(context: Context, distro: String) {
        requireDistro(distro)
        val script = if (distro == "archlinux") {
            "pacman -Syu --noconfirm xfce4 xfce4-goodies tigervnc dbus"
        } else {
            "apt-get update && apt-get install -y xfce4 dbus-x11 tigervnc-standalone-server"
        }
        run(context, "desktop:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", distro, "--", "bash", "-lc", script), true)
    }

    fun startDesktop(context: Context, distro: String) {
        requireDistro(distro)
        val script = "mkdir -p ~/.vnc && printf '%s\\n' '#!/bin/sh' 'unset SESSION_MANAGER' 'unset DBUS_SESSION_BUS_ADDRESS' 'exec dbus-run-session startxfce4' > ~/.vnc/xstartup && chmod 700 ~/.vnc/xstartup && (pgrep -f 'Xtigervnc.*:1' >/dev/null || vncserver :1 -localhost yes -geometry 1280x720 -depth 24)"
        run(context, "start:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", distro, "--", "bash", "-lc", script), true)
    }

    fun stopDesktop(context: Context, distro: String) {
        requireDistro(distro)
        run(context, "stop:$distro", "$PREFIX/bin/proot-distro",
            arrayOf("login", distro, "--", "bash", "-lc", "vncserver -kill :1 || true"), true)
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
