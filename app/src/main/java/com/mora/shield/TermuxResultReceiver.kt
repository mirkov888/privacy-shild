package com.mora.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val operation = intent.getStringExtra(EXTRA_OPERATION) ?: "unknown"
        val result = intent.getBundleExtra("result")
        val output = result?.getString("stdout").orEmpty()
        val error = result?.getString("stderr").orEmpty()
        val exitCode = result?.getInt("exitCode", -1) ?: -1
        val internalError = result?.getString("errmsg").orEmpty()
        val prefs = context.getSharedPreferences("mora_linux", Context.MODE_PRIVATE)
        val distro = operation.substringAfter(':', "")
        val ok = exitCode == 0 && internalError.isBlank()
        val editor = prefs.edit()
            .putString("last_operation", operation)
            .putString("last_output", (output + if (error.isNotBlank()) "\n$error" else "").takeLast(6000))
            .putString("last_error", (internalError.ifBlank { error }).take(3000))
            .putInt("last_exit_code", exitCode)
            .putLong("last_updated", System.currentTimeMillis())
        if (operation == "distros") {
            val found = Regex("MORA_INSTALLED_DISTRO:([a-z0-9_-]+)")
                .findAll(output).map { it.groupValues[1] }.toSet()
            editor.putStringSet("detected_distros", found)
            found.forEach { name ->
                editor.putBoolean("installed_$name", true)
                    .putBoolean("rootfs_broken_$name", false)
            }
        } else if (operation.startsWith("x11_server:")) {
            if (ok && output.contains("MORA_X11_SERVER_READY")) {
                try {
                    TermuxBridge.openX11(context)
                    TermuxBridge.startDesktopSession(context, distro)
                } catch (e: Exception) {
                    editor.putString("last_error", e.message ?: "Gagal membuka Termux:X11")
                }
            } else {
                editor.putString("last_error", "Server Termux:X11 gagal dijalankan. Periksa paket termux-x11-nightly.")
            }
        } else if (operation == "bootstrap") {
            editor.putBoolean("manager_ready", ok)
            if (!ok && internalError.isBlank() && error.isBlank()) {
                editor.putString("last_error", "Instalasi proot-distro gagal (exit code $exitCode). Periksa output Termux.")
            }
        } else if (operation.startsWith("status:")) {
            val installed = output.contains("MORA_DISTRO_INSTALLED")
            val rootfsOk = output.contains("MORA_ROOTFS_OK")
            editor.putBoolean("installed_$distro", installed)
                .putBoolean("rootfs_broken_$distro", installed && !rootfsOk)
                .putString("status_$distro", when {
                    !installed -> "not_installed"
                    rootfsOk -> "installed"
                    else -> "rootfs_broken"
                })
                .putString("session_$distro", when {
                    output.contains("MORA_XFCE_RUNNING") -> "running"
                    output.contains("MORA_XFCE_STOPPED") -> "stopped"
                    else -> "unknown"
                })
                .putBoolean("x11_package_ready", output.contains("MORA_X11_PACKAGE_READY"))
            if (output.contains("MORA_DESKTOP_READY")) {
                editor.putBoolean("desktop_ready_$distro", true)
            } else if (output.contains("MORA_DESKTOP_MISSING")) {
                editor.putBoolean("desktop_ready_$distro", false)
            }
        } else if (operation == "x11_install" && ok) {
            editor.putBoolean("x11_package_ready", true)
        } else if (operation.startsWith("session:")) {
            editor.putString("session_$distro", when {
                output.contains("MORA_XFCE_RUNNING") -> "running"
                output.contains("MORA_XFCE_STOPPED") -> "stopped"
                else -> "unknown"
            })
        } else if ((operation.startsWith("install:") || operation.startsWith("repair:")) && ok) {
            editor.putBoolean("installed_$distro", true)
                .putBoolean("rootfs_broken_$distro", false)
                .putString("status_$distro", "installed")
        } else if (operation.startsWith("desktop:") && ok) {
            editor.putBoolean("desktop_ready_$distro", true)
        } else if (operation.startsWith("start:") && ok) {
            editor.putString("session_$distro", "start_requested")
        } else if (operation.startsWith("stop:") && ok) {
            editor.putString("session_$distro", "stopped")
        }
        editor.apply()
        context.sendBroadcast(Intent(ACTION_RESULT).setPackage(context.packageName))
    }

    companion object {
        const val EXTRA_OPERATION = "mora_operation"
        const val ACTION_RESULT = "com.mora.shield.LINUX_STATUS_CHANGED"
    }
}
