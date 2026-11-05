package com.mora.shield.connector

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.mora.connector.sdk.MoraConnectorContract
import com.mora.shield.core.ConnectorPrincipal
import com.mora.shield.core.EventSeverity
import com.mora.shield.core.MoraCommandRequest
import com.mora.shield.core.MoraCommandResult
import com.mora.shield.core.MoraCore
import com.mora.shield.core.MoraEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Exported IPC endpoint for signed Mora companion APKs.
 * Binding is additionally guarded by the signature permission in AndroidManifest.xml.
 */
class MoraConnectorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incomingMessenger: Messenger by lazy {
        Messenger(IncomingHandler())
    }

    override fun onCreate() {
        super.onCreate()
        MoraCore.initialize(applicationContext)
        serviceScope.launch {
            while (true) {
                delay(30_000L)
                val cutoff = System.currentTimeMillis() - SESSION_TIMEOUT_MS
                sessions.values.filter { it.lastHeartbeatAtMs < cutoff }.forEach { stale ->
                    if (sessions.remove(stale.packageName, stale)) {
                        stale.unlinkDeathRecipient()
                        runCatching { MoraCore.markConnectorDisconnected(stale.packageName) }
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action != MoraConnectorContract.ACTION_BIND) return null
        return incomingMessenger.binder
    }

    override fun onDestroy() {
        sessions.values.toList().forEach { session ->
            if (sessions.remove(session.packageName, session)) {
                session.unlinkDeathRecipient()
                commandScope.launch { runCatching { MoraCore.markConnectorDisconnected(session.packageName) } }
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            // Messenger fills sendingUid from the kernel Binder caller. Never trust a claimed UID in Bundle.
            val senderUid = message.sendingUid
            val what = message.what
            val replyTo = message.replyTo
            val data = try {
                Bundle(message.data ?: Bundle())
            } catch (_: RuntimeException) {
                reply(replyTo, MoraConnectorContract.MSG_ERROR, errorBundle("Bundle IPC tidak valid"))
                return
            }
            serviceScope.launch {
                try {
                    processIncoming(what, senderUid, data, replyTo)
                } catch (_: Exception) {
                    reply(replyTo, MoraConnectorContract.MSG_ERROR, errorBundle("Permintaan Connector gagal diproses"))
                }
            }
        }
    }

    private suspend fun processIncoming(what: Int, senderUid: Int, data: Bundle, replyTo: Messenger?) {
        val apiVersion = data.getInt(MoraConnectorContract.KEY_API_VERSION, -1)
        if (apiVersion != MoraConnectorContract.API_VERSION) {
            reply(replyTo, MoraConnectorContract.MSG_ERROR, errorBundle("API version tidak didukung"))
            return
        }

        if (what == MoraConnectorContract.MSG_REGISTER) {
            registerConnector(senderUid, data, replyTo)
            return
        }

        val principal = ConnectorSecurity.principalForUid(this, senderUid)
        val sessionToken = data.getString(MoraConnectorContract.KEY_SESSION_TOKEN).orEmpty()
        if (principal == null || !isCurrentSession(principal, sessionToken)) {
            reply(replyTo, MoraConnectorContract.MSG_ERROR, errorBundle("Identitas atau sesi connector tidak valid"))
            return
        }

        when (what) {
            MoraConnectorContract.MSG_HEARTBEAT -> {
                val ok = MoraCore.connectorHeartbeat(principal)
                if (ok) sessions[principal.packageName]?.lastHeartbeatAtMs = System.currentTimeMillis()
                reply(replyTo, MoraConnectorContract.MSG_HEARTBEAT_ACK, Bundle().apply {
                    putBoolean(MoraConnectorContract.KEY_OK, ok)
                    putLong(MoraConnectorContract.KEY_SERVER_TIME_MS, System.currentTimeMillis())
                })
            }
            MoraConnectorContract.MSG_PUBLISH_EVENT -> receiveEvent(principal, data, replyTo)
            MoraConnectorContract.MSG_GET_POLICY -> sendPolicy(principal, replyTo)
            MoraConnectorContract.MSG_COMMAND_RESULT -> receiveCommandResult(principal, data, replyTo)
            MoraConnectorContract.MSG_DISCONNECT -> disconnect(principal, sessionToken, replyTo)
            else -> reply(replyTo, MoraConnectorContract.MSG_ERROR, errorBundle("Pesan tidak dikenal"))
        }
    }

    private suspend fun registerConnector(senderUid: Int, data: Bundle, replyTo: Messenger?) {
        if (replyTo == null) {
            reply(null, MoraConnectorContract.MSG_ERROR, errorBundle("Reply Messenger wajib disediakan"))
            return
        }
        // Refresh the installed signer snapshot before accepting a new session.
        runCatching { MoraCore.refreshRegistry() }
        val principal = ConnectorSecurity.principalForUid(this, senderUid)
        if (principal == null) {
            reply(replyTo, MoraConnectorContract.MSG_REGISTERED, registeredBundle(false, "Aplikasi tidak ada di registry Mora"))
            return
        }

        val connectorId = data.getString("connector_id").orEmpty()
        if (!connectorId.matches(Regex("^[a-z][a-z0-9_-]{1,39}$"))) {
            reply(replyTo, MoraConnectorContract.MSG_REGISTERED, registeredBundle(false, "Connector ID tidak valid"))
            return
        }
        val announcedCapabilities = data.getStringArrayList(MoraConnectorContract.KEY_CAPABILITIES).orEmpty()
        if (announcedCapabilities.size > 64 || announcedCapabilities.any { !it.matches(CAPABILITY_PATTERN) }) {
            reply(replyTo, MoraConnectorContract.MSG_REGISTERED, registeredBundle(false, "Daftar capability tidak valid"))
            return
        }
        val capabilities = announcedCapabilities.toSet()
        if ("events.publish" !in capabilities || "policy.read" !in capabilities) {
            reply(replyTo, MoraConnectorContract.MSG_REGISTERED, registeredBundle(false, "Connector wajib mendeklarasikan events.publish dan policy.read"))
            return
        }

        val accepted = MoraCore.markConnectorConnected(principal, apiVersion = apiVersion(data), capabilities = capabilities)
        if (!accepted) {
            reply(replyTo, MoraConnectorContract.MSG_REGISTERED, registeredBundle(false, "Package atau sertifikat penanda tangan tidak cocok"))
            return
        }

        val old = sessions.remove(principal.packageName)
        old?.unlinkDeathRecipient()
        val sessionToken = UUID.randomUUID().toString()
        val newSession = ConnectorSession(principal, replyTo, sessionToken, System.currentTimeMillis())
        // Publish the session before linking death so an immediate binderDied callback can remove it.
        sessions[principal.packageName] = newSession
        try {
            newSession.linkDeathRecipient {
                serviceScope.launch {
                    if (sessions.remove(principal.packageName, newSession)) {
                        runCatching { MoraCore.markConnectorDisconnected(principal.packageName) }
                    }
                }
            }
        } catch (_: RemoteException) {
            sessions.remove(principal.packageName, newSession)
            MoraCore.markConnectorDisconnected(principal.packageName)
            reply(replyTo, MoraConnectorContract.MSG_REGISTERED, registeredBundle(false, "Binder client sudah terputus"))
            return
        }
        reply(replyTo, MoraConnectorContract.MSG_REGISTERED, registeredBundle(true, "Connector terautentikasi", sessionToken))
    }

    private fun apiVersion(data: Bundle): Int = data.getInt(MoraConnectorContract.KEY_API_VERSION, -1)

    private suspend fun isCurrentSession(principal: ConnectorPrincipal, sessionToken: String): Boolean {
        if (sessionToken.length !in 32..64) return false
        val session = sessions[principal.packageName] ?: return false
        return session.principal.certificateSha256.equals(principal.certificateSha256, true) &&
            session.sessionToken == sessionToken
    }

    private suspend fun receiveEvent(principal: ConnectorPrincipal, data: Bundle, replyTo: Messenger?) {
        val type = data.getString(MoraConnectorContract.KEY_EVENT_TYPE).orEmpty().trim()
        val title = data.getString(MoraConnectorContract.KEY_TITLE).orEmpty().trim()
        val affectedPackage = data.getString(MoraConnectorContract.KEY_AFFECTED_PACKAGE).orEmpty().trim()
        if (!type.matches(EVENT_PATTERN) || title.isBlank()) {
            reply(replyTo, MoraConnectorContract.MSG_EVENT_ACK, resultBundle(false, "Format event tidak valid"))
            return
        }
        if (affectedPackage.isNotEmpty() && !affectedPackage.matches(PACKAGE_PATTERN)) {
            reply(replyTo, MoraConnectorContract.MSG_EVENT_ACK, resultBundle(false, "Package terkait tidak valid"))
            return
        }
        if (!allowEventRate(principal.packageName)) {
            reply(replyTo, MoraConnectorContract.MSG_EVENT_ACK, resultBundle(false, "Batas event connector tercapai; kurangi frekuensi laporan"))
            return
        }
        val now = System.currentTimeMillis()
        val suppliedTime = data.getLong(MoraConnectorContract.KEY_OCCURRED_AT_MS, now)
        val eventTime = suppliedTime.takeIf { it in (now - MAX_EVENT_AGE_MS)..(now + MAX_FUTURE_SKEW_MS) } ?: now
        val event = MoraEvent(
            eventId = data.getString(MoraConnectorContract.KEY_EVENT_ID)?.takeIf { it.length in 1..160 } ?: UUID.randomUUID().toString(),
            sourcePackage = principal.packageName,
            eventType = type,
            title = title.take(120),
            affectedPackage = affectedPackage,
            detail = data.getString(MoraConnectorContract.KEY_DETAIL).orEmpty().take(700),
            severity = EventSeverity.fromWire(data.getString(MoraConnectorContract.KEY_SEVERITY).orEmpty()),
            occurredAtMs = eventTime
        )
        val accepted = MoraCore.acceptConnectorEvent(principal, event)
        reply(replyTo, MoraConnectorContract.MSG_EVENT_ACK, resultBundle(accepted, if (accepted) "Event diterima" else "Event ditolak oleh policy").apply {
            putString(MoraConnectorContract.KEY_EVENT_ID, event.eventId)
        })
    }

    private fun allowEventRate(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(eventRateWindows) {
            val window = eventRateWindows[packageName]
            if (window == null || now - window.windowStartedAtMs >= EVENT_RATE_WINDOW_MS) {
                eventRateWindows[packageName] = EventRateWindow(now, 1)
                return true
            }
            if (window.count >= MAX_EVENTS_PER_WINDOW) return false
            window.count += 1
            return true
        }
    }

    private suspend fun sendPolicy(principal: ConnectorPrincipal, replyTo: Messenger?) {
        val policy = MoraCore.connectorPolicy(principal)
        if (policy == null) {
            reply(replyTo, MoraConnectorContract.MSG_POLICY, errorBundle("Policy tidak tersedia"))
            return
        }
        reply(replyTo, MoraConnectorContract.MSG_POLICY, Bundle().apply {
            putBoolean(MoraConnectorContract.KEY_OK, true)
            putBoolean(MoraConnectorContract.KEY_POLICY_ENABLED, policy.enabled)
            putBoolean(MoraConnectorContract.KEY_MONITORING_ENABLED, policy.monitoringEnabled)
            putString(MoraConnectorContract.KEY_NETWORK_MODE, policy.networkMode)
            putString(MoraConnectorContract.KEY_BACKGROUND_MODE, policy.backgroundMode)
            putLong(MoraConnectorContract.KEY_POLICY_UPDATED_AT, System.currentTimeMillis())
        })
    }

    private suspend fun receiveCommandResult(principal: ConnectorPrincipal, data: Bundle, replyTo: Messenger?) {
        val requestId = data.getString(MoraConnectorContract.KEY_REQUEST_ID).orEmpty()
        val commandId = data.getString(MoraConnectorContract.KEY_COMMAND_ID).orEmpty()
        if (requestId.isBlank() || !commandId.matches(COMMAND_PATTERN)) {
            reply(replyTo, MoraConnectorContract.MSG_COMMAND_RESULT_ACK, resultBundle(false, "Hasil perintah tidak valid"))
            return
        }
        val success = data.getBoolean(MoraConnectorContract.KEY_SUCCESS, false)
        val summary = data.getString(MoraConnectorContract.KEY_SUMMARY).orEmpty().take(300)
        val pending = pendingCommands[requestId]
        if (pending == null || pending.packageName != principal.packageName || pending.commandId != commandId) {
            reply(replyTo, MoraConnectorContract.MSG_COMMAND_RESULT_ACK, resultBundle(false, "Request tidak ditemukan atau tidak cocok"))
            return
        }
        val completed = MoraCore.completeRemoteCommand(
            principal = principal,
            requestId = requestId,
            commandId = commandId,
            success = success,
            summary = summary
        )
        if (completed) {
            val result = MoraCommandResult(requestId, if (success) "SUCCEEDED" else "FAILED", summary)
            pendingCommands.remove(requestId, pending)
            pending.result.complete(result)
        }
        reply(replyTo, MoraConnectorContract.MSG_COMMAND_RESULT_ACK, resultBundle(completed, if (completed) "Hasil dicatat" else "Hasil ditolak"))
    }

    private suspend fun disconnect(principal: ConnectorPrincipal, sessionToken: String, replyTo: Messenger?) {
        val session = sessions[principal.packageName]
        val removed = session != null && session.sessionToken == sessionToken &&
            sessions.remove(principal.packageName, session)
        if (removed) {
            session?.unlinkDeathRecipient()
            MoraCore.markConnectorDisconnected(principal.packageName)
        }
        reply(replyTo, MoraConnectorContract.MSG_DISCONNECTED, resultBundle(removed, if (removed) "Connector dilepas" else "Sesi sudah berubah"))
    }

    private fun reply(target: Messenger?, what: Int, data: Bundle) {
        if (target == null) return
        val message = Message.obtain(null, what).apply {
            this.data = data
        }
        runCatching { target.send(message) }
    }

    private fun registeredBundle(ok: Boolean, summary: String, sessionToken: String = "") = Bundle().apply {
        putBoolean(MoraConnectorContract.KEY_OK, ok)
        putString(MoraConnectorContract.KEY_SUMMARY, summary)
        putInt(MoraConnectorContract.KEY_API_VERSION, MoraConnectorContract.API_VERSION)
        putString(MoraConnectorContract.KEY_HOST_PACKAGE, packageName)
        if (sessionToken.isNotBlank()) putString(MoraConnectorContract.KEY_SESSION_TOKEN, sessionToken)
    }

    private fun resultBundle(ok: Boolean, summary: String) = Bundle().apply {
        putBoolean(MoraConnectorContract.KEY_OK, ok)
        putString(MoraConnectorContract.KEY_SUMMARY, summary)
    }

    private fun errorBundle(summary: String) = resultBundle(false, summary)

    private data class EventRateWindow(var windowStartedAtMs: Long, var count: Int)

    private data class PendingRemoteCommand(
        val packageName: String,
        val commandId: String,
        val result: CompletableDeferred<MoraCommandResult>
    )

    companion object {
        private const val SESSION_TIMEOUT_MS = 120_000L
        private const val COMMAND_TIMEOUT_MS = 30_000L
        private const val MAX_EVENT_AGE_MS = 24 * 60 * 60 * 1000L
        private const val MAX_FUTURE_SKEW_MS = 5 * 60 * 1000L
        private const val EVENT_RATE_WINDOW_MS = 60_000L
        private const val MAX_EVENTS_PER_WINDOW = 120
        private val eventRateWindows = HashMap<String, EventRateWindow>()
        private val EVENT_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.-]{0,63}$")
        private val PACKAGE_PATTERN = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
        private val CAPABILITY_PATTERN = Regex("^[a-z][a-z0-9]*(\\.[a-z0-9_-]+){1,4}$")
        private val COMMAND_PATTERN = CAPABILITY_PATTERN
        private val sessions = ConcurrentHashMap<String, ConnectorSession>()
        private val pendingCommands = ConcurrentHashMap<String, PendingRemoteCommand>()
        private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Called only by Mora Shield's own UI after any required user confirmation. */
        internal suspend fun dispatchCommand(
            packageName: String,
            commandId: String,
            payload: String = "",
            approvedByUser: Boolean = false
        ): MoraCommandResult = withContext(Dispatchers.IO) {
            if (!packageName.matches(PACKAGE_PATTERN) || !commandId.matches(COMMAND_PATTERN) || payload.length > 4096) {
                return@withContext MoraCommandResult("", "DENIED", "Request tidak valid")
            }
            val session = sessions[packageName]
                ?: return@withContext MoraCommandResult("", "UNAVAILABLE", "Connector belum terhubung")
            val request = MoraCommandRequest(packageName = packageName, commandId = commandId, payload = payload)
            val gate = MoraCore.authorizeRemoteCommand(session.principal, request, approvedByUser)
            if (gate.status != "RUNNING") return@withContext gate
            if (sessions[packageName] !== session) {
                MoraCore.completeRemoteCommand(session.principal, request.requestId, commandId, false, "Sesi connector berubah sebelum pengiriman")
                return@withContext MoraCommandResult(request.requestId, "FAILED", "Sesi connector berubah sebelum pengiriman")
            }

            val deferred = CompletableDeferred<MoraCommandResult>()
            val pending = PendingRemoteCommand(packageName, commandId, deferred)
            pendingCommands[request.requestId] = pending
            val message = Message.obtain(null, MoraConnectorContract.MSG_COMMAND).apply {
                data = Bundle().apply {
                    putInt(MoraConnectorContract.KEY_API_VERSION, MoraConnectorContract.API_VERSION)
                    putString(MoraConnectorContract.KEY_SESSION_TOKEN, session.sessionToken)
                    putString(MoraConnectorContract.KEY_REQUEST_ID, request.requestId)
                    putString(MoraConnectorContract.KEY_COMMAND_ID, commandId)
                    putString(MoraConnectorContract.KEY_PAYLOAD, payload)
                }
            }
            try {
                session.callbackMessenger.send(message)
            } catch (_: RemoteException) {
                pendingCommands.remove(request.requestId, pending)
                MoraCore.completeRemoteCommand(session.principal, request.requestId, commandId, false, "Gagal mengirim perintah ke connector")
                return@withContext MoraCommandResult(request.requestId, "FAILED", "Gagal mengirim perintah ke connector")
            }

            val response = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { deferred.await() }
            if (response != null) return@withContext response
            if (pendingCommands.remove(request.requestId, pending)) {
                MoraCore.completeRemoteCommand(session.principal, request.requestId, commandId, false, "Timeout menunggu connector")
                return@withContext MoraCommandResult(request.requestId, "TIMEOUT", "Timeout menunggu connector")
            }
            deferred.await()
        }
    }
}

private class ConnectorSession(
    val principal: ConnectorPrincipal,
    val callbackMessenger: Messenger,
    val sessionToken: String,
    @Volatile var lastHeartbeatAtMs: Long
) {
    val packageName: String get() = principal.packageName
    private var deathRecipient: IBinderDeath? = null

    fun linkDeathRecipient(onDeath: () -> Unit) {
        val binder = callbackMessenger.binder
        val recipient = IBinderDeath(onDeath)
        binder.linkToDeath(recipient, 0)
        deathRecipient = recipient
    }

    fun unlinkDeathRecipient() {
        val recipient = deathRecipient ?: return
        runCatching { callbackMessenger.binder.unlinkToDeath(recipient, 0) }
        deathRecipient = null
    }
}

private class IBinderDeath(private val action: () -> Unit) : android.os.IBinder.DeathRecipient {
    override fun binderDied() = action()
}
