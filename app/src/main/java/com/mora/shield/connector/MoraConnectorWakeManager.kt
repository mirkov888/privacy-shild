package com.mora.shield.connector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

/**
 * Wakes signed companion connector processes only while Mora Shield's foreground
 * Control Center is visible. These are bound-only services, not permanent daemons.
 */
object MoraConnectorWakeManager {
    private const val TAG = "MoraConnectorWake"
    private val lock = Any()
    private val active = LinkedHashMap<String, ServiceConnection>()

    private data class Target(val packageName: String, val className: String)

    // MoraSystemManager wake endpoint is added in Phase 4 once its worker side exists.
    private val targets = listOf(
        Target(
            "com.mora.nsfwdownloader",
            "com.mora.nsfwdownloader.MoraDownloaderWakeService"
        )
    )

    fun bindKnownCompanions(context: Context) {
        val app = context.applicationContext
        targets.forEach { target -> bind(app, target) }
    }

    private fun bind(context: Context, target: Target) {
        val key = target.packageName + "/" + target.className
        synchronized(lock) {
            if (active.containsKey(key)) return
            val component = ComponentName(target.packageName, target.className)
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    Log.i(TAG, "Companion wake bound: ${target.packageName}")
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    Log.w(TAG, "Companion wake disconnected: ${target.packageName}")
                }

                override fun onBindingDied(name: ComponentName?) {
                    synchronized(lock) { active.remove(key) }
                    runCatching { context.unbindService(this) }
                    Log.w(TAG, "Companion binding died: ${target.packageName}")
                }

                override fun onNullBinding(name: ComponentName?) {
                    synchronized(lock) { active.remove(key) }
                    runCatching { context.unbindService(this) }
                    Log.w(TAG, "Companion returned null binding: ${target.packageName}")
                }
            }
            try {
                val intent = Intent().setComponent(component)
                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (bound) active[key] = connection
                else Log.d(TAG, "Companion not installed or service not exported: ${target.packageName}")
            } catch (error: SecurityException) {
                Log.w(TAG, "Signature permission denied for ${target.packageName}")
            } catch (error: Exception) {
                Log.w(TAG, "Could not bind ${target.packageName}: ${error.javaClass.simpleName}")
            }
        }
    }

    fun unbindAll(context: Context) {
        val app = context.applicationContext
        val snapshot = synchronized(lock) {
            active.values.toList().also { active.clear() }
        }
        snapshot.forEach { connection ->
            runCatching { app.unbindService(connection) }
        }
    }
}
