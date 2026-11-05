package com.mora.shield.core

import android.content.Context
import androidx.room.Room
import com.mora.shield.Ps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mora Core is the on-device control plane for the Mora ecosystem.
 * It owns the registry, policies, durable activity timeline and command audit.
 * No arbitrary shell command API is exposed here.
 */
object MoraCore {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initializationStarted = AtomicBoolean(false)

    @Volatile private var database: MoraCoreDatabase? = null
    @Volatile private var registry: AppRegistry? = null
    @Volatile private var eventHub: EventHub? = null
    @Volatile private var policyEngine: PolicyEngine? = null
    @Volatile private var commandRouter: CommandRouter? = null

    fun initialize(context: Context) {
        if (database != null || !initializationStarted.compareAndSet(false, true)) return
        synchronized(lock) {
            if (database != null) return
            val appContext = context.applicationContext
            val db = Room.databaseBuilder(
                appContext,
                MoraCoreDatabase::class.java,
                "mora_core.db"
            ).build()
            database = db
            registry = AppRegistry(appContext, db.coreDao())
            eventHub = EventHub(db.coreDao())
            policyEngine = PolicyEngine(db.coreDao())
            commandRouter = CommandRouter(db.coreDao(), policyEngine!!)
        }
        scope.launch {
            runCatching { registryOrThrow().seedKnownApps() }
        }
    }

    fun isInitialized(): Boolean = database != null

    private fun registryOrThrow(): AppRegistry = registry
        ?: error("Mora Core belum diinisialisasi")

    private fun eventHubOrThrow(): EventHub = eventHub
        ?: error("Mora Core belum diinisialisasi")

    private fun policyOrThrow(): PolicyEngine = policyEngine
        ?: error("Mora Core belum diinisialisasi")

    private fun routerOrThrow(): CommandRouter = commandRouter
        ?: error("Mora Core belum diinisialisasi")

    suspend fun overview(): CoreOverview {
        val dao = database?.coreDao() ?: return CoreOverview()
        return CoreOverview(
            registeredApps = dao.countApps(),
            connectedApps = dao.countConnectedApps(),
            storedEvents = dao.countEvents(),
            pendingCommands = dao.countPendingCommands()
        )
    }

    suspend fun registeredApps(): List<RegisteredAppEntity> = registryOrThrow().apps()

    suspend fun refreshRegistry() = registryOrThrow().seedKnownApps()

    suspend fun recentEvents(limit: Int = 100): List<ActivityEventEntity> =
        eventHubOrThrow().recent(limit)

    suspend fun recentCommandAudit(limit: Int = 100): List<CommandAuditEntity> =
        (database ?: error("Mora Core belum diinisialisasi")).coreDao().recentCommandAudit(limit.coerceIn(1, 1000))

    fun observeEvents(limit: Int = 100) = eventHubOrThrow().observe(limit)

    suspend fun appPolicy(packageName: String): AppPolicy? = policyOrThrow().get(packageName)

    /** Update central policy. Commands remain disabled unless explicitly enabled and allowlisted. */
    suspend fun updateAppPolicy(policy: AppPolicy): Boolean = policyOrThrow().update(policy)

    /** Internal hook for the upcoming authenticated Connector IPC service. */
    internal suspend fun markConnectorConnected(
        principal: ConnectorPrincipal,
        apiVersion: Int,
        capabilities: Set<String>
    ): Boolean = registryOrThrow().markConnected(principal, apiVersion, capabilities)

    /** Internal hook for connector heartbeat. */
    internal suspend fun connectorHeartbeat(principal: ConnectorPrincipal): Boolean =
        registryOrThrow().heartbeat(principal)

    /** Internal hook for connector events after package/signature authentication. */
    internal suspend fun acceptConnectorEvent(principal: ConnectorPrincipal, event: MoraEvent): Boolean {
        val app = registryOrThrow().verifiedApp(principal) ?: return false
        if (app.connectionState != "CONNECTED") return false
        val advertised = app.capabilities.split('\n').filter { it.isNotBlank() }.toSet()
        if ("events.publish" !in advertised) return false
        val policy = appPolicy(principal.packageName)
        if (policy?.enabled != true || !policy.monitoringEnabled) return false
        return eventHubOrThrow().publish(event.copy(sourcePackage = principal.packageName))
    }

    /** Register a narrowly scoped app-owned handler. There is deliberately no shell executor. */
    internal fun registerCommandHandler(packageName: String, commandId: String, handler: MoraCommandHandler): Boolean =
        routerOrThrow().registerHandler(packageName, commandId, handler)

    /** Command invocation is denied by default and requires an authenticated principal + policy allowlist. */
    internal suspend fun executeCommand(
        principal: ConnectorPrincipal,
        request: MoraCommandRequest,
        approvedByUser: Boolean = false
    ): MoraCommandResult = routerOrThrow().execute(principal, request, approvedByUser)

    /** Authenticated connector policy snapshot. */
    internal suspend fun connectorPolicy(principal: ConnectorPrincipal): AppPolicy? {
        val app = registryOrThrow().verifiedApp(principal) ?: return null
        if (app.connectionState != "CONNECTED") return null
        return appPolicy(principal.packageName)
    }

    internal suspend fun markConnectorDisconnected(packageName: String) =
        registryOrThrow().markDisconnected(packageName)

    /** Start an app-owned remote command only after the Core policy gate approves it. */
    internal suspend fun authorizeRemoteCommand(
        principal: ConnectorPrincipal,
        request: MoraCommandRequest,
        approvedByUser: Boolean = false
    ): MoraCommandResult = routerOrThrow().authorizeRemote(principal, request, approvedByUser)

    /** Persist a connector result only for the matching in-flight package/capability/request tuple. */
    internal suspend fun completeRemoteCommand(
        principal: ConnectorPrincipal,
        requestId: String,
        commandId: String,
        success: Boolean,
        summary: String
    ): Boolean = routerOrThrow().completeRemote(principal, requestId, commandId, success, summary)

    fun publishShieldEvent(event: Ps.Ev) {
        if (!isInitialized()) return
        scope.launch {
            val severity = when (event.type.uppercase()) {
                "MIC", "CAM", "LOC", "CONTACTS", "SMS", "CALLLOG" -> EventSeverity.MEDIUM
                "NETBIG" -> EventSeverity.LOW
                else -> EventSeverity.INFO
            }
            val stableId = listOf(
                "shield",
                event.ts.toString(),
                event.type,
                event.pkg,
                event.detail.hashCode().toString()
            ).joinToString(":")
            eventHubOrThrow().publish(
                MoraEvent(
                    eventId = stableId,
                    sourcePackage = "com.mora.shield",
                    eventType = event.type.ifBlank { "SHIELD_EVENT" },
                    title = "Aktivitas " + event.type.ifBlank { "Shield" },
                    affectedPackage = event.pkg,
                    detail = event.detail,
                    severity = severity,
                    occurredAtMs = event.ts * 1000L
                )
            )
        }
    }

    fun ingestShieldEvents(events: List<Ps.Ev>) {
        if (!isInitialized()) return
        // Ps.events() returns newest-first; reverse to keep insert order deterministic.
        events.asReversed().forEach(::publishShieldEvent)
    }
}
