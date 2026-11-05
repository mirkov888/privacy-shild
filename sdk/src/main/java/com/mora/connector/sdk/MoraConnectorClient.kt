package com.mora.connector.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Source-distributed Android client for Mora Connector API v1.
 * Copy this package into the companion APK's app/src/main/java folder.
 */
class MoraConnectorClient(
    context: Context,
    private val connectorId: String,
    private val listener: MoraConnectorListener? = null
) : Closeable {
    private val appContext = context.applicationContext
    private val commandHandlers = ConcurrentHashMap<String, MoraConnectorCommandHandler>()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val incomingMessenger = Messenger(IncomingHandler())

    @Volatile private var remote: Messenger? = null
    @Volatile private var bound = false
    @Volatile private var registered = false
    @Volatile private var sessionToken: String? = null
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!registered) return
            send(MoraConnectorContract.MSG_HEARTBEAT, baseBundle())
            mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null) {
                notifyError("Mora Shield mengembalikan Binder kosong")
                return
            }
            remote = Messenger(service)
            sendRegistration()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            registered = false
            sessionToken = null
            mainHandler.removeCallbacks(heartbeatRunnable)
            remote = null
            listener?.onDisconnected()
        }

        override fun onBindingDied(name: ComponentName?) {
            registered = false
            sessionToken = null
            mainHandler.removeCallbacks(heartbeatRunnable)
            remote = null
            if (bound) runCatching { appContext.unbindService(this) }
            bound = false
            listener?.onDisconnected()
        }

        override fun onNullBinding(name: ComponentName?) {
            registered = false
            sessionToken = null
            mainHandler.removeCallbacks(heartbeatRunnable)
            remote = null
            notifyError("Service Mora Shield tidak menerima binding")
        }
    }

    val isConnected: Boolean get() = registered

    init {
        require(connectorId.matches(Regex("^[a-z][a-z0-9_-]{1,39}$"))) { "connectorId tidak valid" }
    }

    /** Register a local capability handler before connect() so it is advertised at handshake. */
    fun registerCommandHandler(commandId: String, handler: MoraConnectorCommandHandler): Boolean {
        if (!validCapability(commandId)) return false
        if (!commandHandlers.containsKey(commandId) && commandHandlers.size >= MAX_COMMAND_HANDLERS) return false
        commandHandlers[commandId] = handler
        if (registered) sendRegistration()
        return true
    }

    fun removeCommandHandler(commandId: String) {
        commandHandlers.remove(commandId)
        if (registered) sendRegistration()
    }

    /** Bind explicitly to Mora Shield. Install Shield first and sign both APKs with the same key. */
    fun connect(): Boolean {
        if (bound) return true
        val intent = Intent(MoraConnectorContract.ACTION_BIND).setComponent(
            ComponentName(MoraConnectorContract.HOST_PACKAGE, MoraConnectorContract.HOST_SERVICE_CLASS)
        )
        return try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE).also { bound = it }
        } catch (error: SecurityException) {
            notifyError("Izin Connector ditolak. Periksa signing certificate kedua APK.")
            false
        } catch (error: Exception) {
            notifyError(error.message ?: "Gagal menghubungkan ke Mora Shield")
            false
        }
    }

    fun publishEvent(event: MoraConnectorEvent): Boolean {
        if (!registered || !validEventType(event.eventType) || event.title.isBlank()) return false
        if (event.affectedPackage.isNotBlank() && !validPackage(event.affectedPackage)) return false
        val data = Bundle().apply {
            putInt(MoraConnectorContract.KEY_API_VERSION, MoraConnectorContract.API_VERSION)
            putString(MoraConnectorContract.KEY_EVENT_ID, event.eventId.take(160))
            putString(MoraConnectorContract.KEY_EVENT_TYPE, event.eventType)
            putString(MoraConnectorContract.KEY_TITLE, event.title.take(120))
            putString(MoraConnectorContract.KEY_AFFECTED_PACKAGE, event.affectedPackage.take(255))
            putString(MoraConnectorContract.KEY_DETAIL, event.detail.take(700))
            putString(MoraConnectorContract.KEY_SEVERITY, normalizeSeverity(event.severity))
            putLong(MoraConnectorContract.KEY_OCCURRED_AT_MS, event.occurredAtMs)
        }
        return send(MoraConnectorContract.MSG_PUBLISH_EVENT, data)
    }

    fun heartbeat(): Boolean {
        if (!registered) return false
        return send(MoraConnectorContract.MSG_HEARTBEAT, baseBundle())
    }

    fun requestPolicy(): Boolean {
        if (!registered) return false
        return send(MoraConnectorContract.MSG_GET_POLICY, baseBundle())
    }

    fun disconnect() {
        if (registered) send(MoraConnectorContract.MSG_DISCONNECT, baseBundle())
        registered = false
        sessionToken = null
        mainHandler.removeCallbacks(heartbeatRunnable)
        remote = null
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
        listener?.onDisconnected()
    }

    override fun close() {
        disconnect()
        executor.shutdownNow()
    }

    private fun sendRegistration() {
        val capabilities = ArrayList<String>().apply {
            add("events.publish")
            add("policy.read")
            addAll(commandHandlers.keys.sorted())
        }
        val data = baseBundle().apply {
            putString("connector_id", connectorId)
            putStringArrayList(MoraConnectorContract.KEY_CAPABILITIES, capabilities)
        }
        if (!send(MoraConnectorContract.MSG_REGISTER, data, allowBeforeRegistration = true)) {
            notifyError("Gagal mengirim handshake ke Mora Shield")
        }
    }

    private fun send(what: Int, data: Bundle, allowBeforeRegistration: Boolean = false): Boolean {
        val target = remote ?: return false
        if (!allowBeforeRegistration && !registered) return false
        return try {
            val message = Message.obtain(null, what).apply {
                this.data = data
                replyTo = incomingMessenger
            }
            target.send(message)
            true
        } catch (_: RemoteException) {
            false
        }
    }

    private fun baseBundle() = Bundle().apply {
        putInt(MoraConnectorContract.KEY_API_VERSION, MoraConnectorContract.API_VERSION)
        sessionToken?.let { putString(MoraConnectorContract.KEY_SESSION_TOKEN, it) }
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (!isShieldCaller(message.sendingUid)) {
                notifyError("Pesan dari identitas yang bukan Mora Shield ditolak")
                return
            }
            val data = message.data ?: Bundle()
            when (message.what) {
                MoraConnectorContract.MSG_REGISTERED -> {
                    val token = data.getString(MoraConnectorContract.KEY_SESSION_TOKEN).orEmpty()
                    val ok = data.getBoolean(MoraConnectorContract.KEY_OK, false) && token.length in 32..64
                    sessionToken = token.takeIf { ok }
                    registered = ok
                    if (ok) {
                        listener?.onConnected()
                        mainHandler.removeCallbacks(heartbeatRunnable)
                        mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
                    } else notifyError(data.getString(MoraConnectorContract.KEY_SUMMARY) ?: "Registrasi connector ditolak")
                }
                MoraConnectorContract.MSG_HEARTBEAT_ACK -> {
                    if (!data.getBoolean(MoraConnectorContract.KEY_OK, false)) {
                        registered = false
                        sessionToken = null
                        mainHandler.removeCallbacks(heartbeatRunnable)
                        notifyError("Heartbeat ditolak oleh Mora Shield")
                    }
                }
                MoraConnectorContract.MSG_EVENT_ACK -> {
                    listener?.onEventAccepted(
                        data.getString(MoraConnectorContract.KEY_EVENT_ID).orEmpty(),
                        data.getBoolean(MoraConnectorContract.KEY_OK, false),
                        data.getString(MoraConnectorContract.KEY_SUMMARY).orEmpty()
                    )
                }
                MoraConnectorContract.MSG_COMMAND_RESULT_ACK -> {
                    listener?.onCommandSent(MoraConnectorResponse(
                        data.getBoolean(MoraConnectorContract.KEY_OK, false),
                        data.getString(MoraConnectorContract.KEY_SUMMARY).orEmpty()
                    ))
                }
                MoraConnectorContract.MSG_POLICY -> {
                    val policy = if (data.getBoolean(MoraConnectorContract.KEY_OK, false)) {
                        MoraConnectorPolicy(
                            enabled = data.getBoolean(MoraConnectorContract.KEY_POLICY_ENABLED, false),
                            monitoringEnabled = data.getBoolean(MoraConnectorContract.KEY_MONITORING_ENABLED, false),
                            networkMode = data.getString(MoraConnectorContract.KEY_NETWORK_MODE).orEmpty(),
                            backgroundMode = data.getString(MoraConnectorContract.KEY_BACKGROUND_MODE).orEmpty(),
                            updatedAtMs = data.getLong(MoraConnectorContract.KEY_POLICY_UPDATED_AT, 0L)
                        )
                    } else null
                    listener?.onPolicyReceived(policy)
                }
                MoraConnectorContract.MSG_COMMAND -> {
                    val incomingToken = data.getString(MoraConnectorContract.KEY_SESSION_TOKEN).orEmpty()
                    if (registered && incomingToken.isNotBlank() && incomingToken == sessionToken) handleCommand(data)
                    else notifyError("Command berasal dari sesi Mora Shield yang tidak aktif")
                }
                MoraConnectorContract.MSG_ERROR -> {
                    registered = false
                    sessionToken = null
                    mainHandler.removeCallbacks(heartbeatRunnable)
                    notifyError(data.getString(MoraConnectorContract.KEY_SUMMARY) ?: "Kesalahan Connector")
                }
                MoraConnectorContract.MSG_DISCONNECTED -> {
                    registered = false
                    sessionToken = null
                    mainHandler.removeCallbacks(heartbeatRunnable)
                    listener?.onDisconnected()
                }
                else -> super.handleMessage(message)
            }
        }
    }

    private fun handleCommand(data: Bundle) {
        if (data.getInt(MoraConnectorContract.KEY_API_VERSION, -1) != MoraConnectorContract.API_VERSION) {
            notifyError("Versi command dari Mora Shield tidak didukung")
            return
        }
        val requestId = data.getString(MoraConnectorContract.KEY_REQUEST_ID).orEmpty()
        val commandId = data.getString(MoraConnectorContract.KEY_COMMAND_ID).orEmpty()
        val payload = data.getString(MoraConnectorContract.KEY_PAYLOAD).orEmpty()
        if (requestId.isBlank() || !validCapability(commandId) || payload.length > 4096) {
            sendCommandResult(requestId, commandId, false, "Format command tidak valid")
            return
        }
        val handler = commandHandlers[commandId]
        if (handler == null) {
            sendCommandResult(requestId, commandId, false, "Capability tidak memiliki handler")
            return
        }
        executor.execute {
            val result = try {
                handler.execute(MoraConnectorCommand(requestId, commandId, payload))
            } catch (error: Exception) {
                MoraConnectorCommandResult(requestId, commandId, false, error.message ?: "Handler gagal")
            }
            sendCommandResult(
                requestId,
                commandId,
                result.success,
                result.summary.take(300)
            )
        }
    }

    private fun sendCommandResult(requestId: String, commandId: String, success: Boolean, summary: String) {
        val data = baseBundle().apply {
            putString(MoraConnectorContract.KEY_REQUEST_ID, requestId.take(120))
            putString(MoraConnectorContract.KEY_COMMAND_ID, commandId.take(100))
            putBoolean(MoraConnectorContract.KEY_SUCCESS, success)
            putString(MoraConnectorContract.KEY_SUMMARY, summary.take(300))
        }
        send(MoraConnectorContract.MSG_COMMAND_RESULT, data)
    }

    private fun isShieldCaller(uid: Int): Boolean {
        if (uid < 0) return false
        val packages = appContext.packageManager.getPackagesForUid(uid)?.distinct().orEmpty()
        return packages.size == 1 && packages.single() == MoraConnectorContract.HOST_PACKAGE
    }

    private fun notifyError(message: String) {
        mainHandler.post { listener?.onConnectionError(message) }
    }

    private fun validCapability(value: String): Boolean = CAPABILITY.matches(value)
    private fun validEventType(value: String): Boolean = EVENT_TYPE.matches(value)
    private fun validPackage(value: String): Boolean = PACKAGE.matches(value)

    private fun normalizeSeverity(value: String): String =
        value.lowercase().takeIf { it in setOf("info", "low", "medium", "high", "critical") } ?: "info"

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val MAX_COMMAND_HANDLERS = 62
        private val CAPABILITY = Regex("^[a-z][a-z0-9]*(\\.[a-z0-9_-]+){1,4}$")
        private val EVENT_TYPE = Regex("^[A-Za-z][A-Za-z0-9_.-]{0,63}$")
        private val PACKAGE = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
    }
}
