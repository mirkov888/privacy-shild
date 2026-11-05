package com.mora.shield.connector

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.mora.shield.core.MoraCore
import com.mora.shield.core.MoraEvent
import com.mora.shield.core.MoraSeverity

/** IPC satu arah: connector eksternal hanya boleh mengirim event, bukan perintah root. */
class MoraConnectorService : Service() {
    private val messenger: Messenger by lazy { Messenger(IncomingHandler()) }

    override fun onBind(intent: Intent?): IBinder {
        MoraCore.initialize()
        return messenger.binder
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != MoraConnectorContract.MSG_PUBLISH_EVENT) {
                super.handleMessage(msg)
                return
            }
            acceptEvent(msg)
        }
    }

    private fun acceptEvent(message: Message) {
        val senderPackages = try {
            packageManager.getPackagesForUid(message.sendingUid).orEmpty()
        } catch (_: Exception) {
            emptyArray()
        }
        val senderPackage = senderPackages.firstOrNull { it != packageName } ?: return

        val data: Bundle = message.data ?: return
        if (data.getInt(MoraConnectorContract.KEY_API_VERSION, -1) != MoraConnectorContract.API_VERSION) return

        val connectorId = data.getString(MoraConnectorContract.KEY_CONNECTOR_ID).orEmpty().trim()
        val type = data.getString(MoraConnectorContract.KEY_EVENT_TYPE).orEmpty().trim()
        val title = data.getString(MoraConnectorContract.KEY_TITLE).orEmpty().trim()
        if (!connectorId.matches(Regex("^[A-Za-z0-9._-]{1,80}$"))) return
        if (!type.matches(Regex("^[A-Za-z0-9._-]{1,48}$")) || title.isBlank()) return

        val targetPackage = data.getString(MoraConnectorContract.KEY_PACKAGE_NAME).orEmpty().trim()
        if (targetPackage.isNotEmpty() && !targetPackage.matches(Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$"))) return

        val now = System.currentTimeMillis()
        val suppliedTime = data.getLong(MoraConnectorContract.KEY_OCCURRED_AT, now)
        val eventTime = suppliedTime.takeIf { it in (now - 86_400_000L)..(now + 86_400_000L) } ?: now
        val severity = MoraSeverity.fromWire(data.getString(MoraConnectorContract.KEY_SEVERITY).orEmpty())

        MoraCore.publish(
            MoraEvent(
                sourceId = senderPackage + ":" + connectorId,
                type = type,
                title = title.take(100),
                packageName = targetPackage,
                detail = data.getString(MoraConnectorContract.KEY_DETAIL).orEmpty().take(500),
                severity = severity,
                occurredAtMs = eventTime
            )
        )
    }
}
