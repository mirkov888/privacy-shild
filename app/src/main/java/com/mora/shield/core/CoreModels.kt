package com.mora.shield.core

import java.util.UUID

enum class EventSeverity(val wireValue: String) {
    INFO("info"), LOW("low"), MEDIUM("medium"), HIGH("high"), CRITICAL("critical");

    companion object {
        fun fromWire(value: String): EventSeverity =
            values().firstOrNull { it.wireValue == value.lowercase() } ?: INFO
    }
}

data class MoraEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val sourcePackage: String,
    val eventType: String,
    val title: String,
    val affectedPackage: String = "",
    val detail: String = "",
    val severity: EventSeverity = EventSeverity.INFO,
    val occurredAtMs: Long = System.currentTimeMillis()
)

data class CoreOverview(
    val registeredApps: Int = 0,
    val connectedApps: Int = 0,
    val storedEvents: Int = 0,
    val pendingCommands: Int = 0
)

data class AppPolicy(
    val packageName: String,
    val enabled: Boolean = true,
    val monitoringEnabled: Boolean = true,
    val commandAccessEnabled: Boolean = false,
    val allowedCommands: Set<String> = emptySet(),
    val approvalRequiredCommands: Set<String> = setOf("*"),
    val networkMode: String = "inherit",
    val backgroundMode: String = "normal"
)

data class MoraCommandRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val packageName: String,
    val commandId: String,
    val payload: String = "",
    val requestedAtMs: Long = System.currentTimeMillis()
)

data class MoraCommandResult(
    val requestId: String,
    val status: String,
    val summary: String
)

data class CommandExecutionResult(
    val success: Boolean,
    val summary: String
)

fun interface MoraCommandHandler {
    suspend fun execute(payload: String): CommandExecutionResult
}

/**
 * Created only after the host authenticates a connector. The IPC/authentication
 * implementation is introduced in the Connector SDK phase.
 */
data class ConnectorPrincipal internal constructor(
    val packageName: String,
    val certificateSha256: String
)
