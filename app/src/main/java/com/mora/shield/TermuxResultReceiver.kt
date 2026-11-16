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
            .putString("last_error", internalError.take(1500))
            .putInt("last_exit_code", exitCode)
            .putLong("last_updated", System.currentTimeMillis())
        if (operation == "bootstrap") {
            editor.putBoolean("manager_ready", ok)
            if (!ok && internalError.isBlank() && error.isBlank()) {
                editor.putString("last_error", "Instalasi proot-distro gagal (exit code $exitCode). Periksa output Termux.")
            }
        } else if (operation.startsWith("status:")) {
            val installed = output.contains("($distro)") ||
                output.lines().any { line ->
                    val normalized = line.trim().removePrefix("*").trim().lowercase()
                    normalized == distro || normalized.startsWith("$distro ") || normalized.endsWith(" $distro")
                }
            editor.putBoolean("installed_$distro", installed)
                .putString("status_$distro", if (installed) "installed" else "not_installed")
        } else if (operation.startsWith("session:")) {
            editor.putString("session_$distro", when {
                output.contains("MORA_XFCE_RUNNING") -> "running"
                output.contains("MORA_XFCE_STOPPED") -> "stopped"
                else -> "unknown"
            })
        } else if (operation.startsWith("desktop:") && ok) {
            editor.putBoolean("desktop_ready_$distro", true)
        } else if (operation.startsWith("install:") && ok) {
            editor.putBoolean("installed_$distro", true).putString("status_$distro", "installed")
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
