package com.mora.shield.connector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger

/** Client kecil untuk aplikasi Mora lain yang ingin mengirim event ke Mora Shield. */
class MoraConnectorClient(
    context: Context,
    private val onConnectionChanged: ((Boolean) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    @Volatile private var remote: Messenger? = null
    @Volatile private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = service?.let { Messenger(it) }
            onConnectionChanged?.invoke(remote != null)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            onConnectionChanged?.invoke(false)
        }

        override fun onBindingDied(name: ComponentName?) {
            remote = null
            onConnectionChanged?.invoke(false)
        }

        override fun onNullBinding(name: ComponentName?) {
            remote = null
            onConnectionChanged?.invoke(false)
        }
    }

    fun connect(): Boolean {
        if (bound) return true
        val intent = Intent(MoraConnectorContract.ACTION_BIND)
            .setPackage(MoraConnectorContract.HOST_PACKAGE)
        return try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE).also { bound = it }
        } catch (_: Exception) {
            false
        }
    }

    /** true berarti pesan berhasil dimasukkan ke Messenger, bukan bukti event sudah diproses. */
    fun publishEvent(
        connectorId: String,
        eventType: String,
        title: String,
        packageName: String = "",
        detail: String = "",
        severity: String = "info",
        occurredAtMs: Long = System.currentTimeMillis()
    ): Boolean {
        val target = remote ?: return false
        if (!connectorId.matches(Regex("^[A-Za-z0-9._-]{1,80}$"))) return false
        if (!eventType.matches(Regex("^[A-Za-z0-9._-]{1,48}$")) || title.isBlank()) return false

        val data = Bundle().apply {
            putInt(MoraConnectorContract.KEY_API_VERSION, MoraConnectorContract.API_VERSION)
            putString(MoraConnectorContract.KEY_CONNECTOR_ID, connectorId)
            putString(MoraConnectorContract.KEY_EVENT_TYPE, eventType)
            putString(MoraConnectorContract.KEY_TITLE, title.take(100))
            putString(MoraConnectorContract.KEY_PACKAGE_NAME, packageName.take(255))
            putString(MoraConnectorContract.KEY_DETAIL, detail.take(500))
            putString(MoraConnectorContract.KEY_SEVERITY, severity)
            putLong(MoraConnectorContract.KEY_OCCURRED_AT, occurredAtMs)
        }
        val message = Message.obtain(null, MoraConnectorContract.MSG_PUBLISH_EVENT).apply { this.data = data }
        return try {
            target.send(message)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun disconnect() {
        remote = null
        if (bound) {
            try {
                appContext.unbindService(connection)
            } catch (_: IllegalArgumentException) {
                // Sudah tidak terikat.
            }
        }
        bound = false
        onConnectionChanged?.invoke(false)
    }
}
