package com.mora.shield.core

import java.util.concurrent.ConcurrentHashMap

internal class CommandRouter(
    private val dao: CoreDao,
    private val policies: PolicyEngine
) {
    private val handlers = ConcurrentHashMap<String, MoraCommandHandler>()

    fun registerHandler(packageName: String, commandId: String, handler: MoraCommandHandler): Boolean {
        if (!CoreSanitizer.validPackage(packageName) || !CoreSanitizer.validCommandId(commandId)) return false
        val key = "$packageName|$commandId"
        return handlers.putIfAbsent(key, handler) == null
    }

    suspend fun execute(
        principal: ConnectorPrincipal,
        request: MoraCommandRequest,
        approvedByUser: Boolean = false
    ): MoraCommandResult {
        val now = System.currentTimeMillis()
        if (request.packageName != principal.packageName || !CoreSanitizer.validPackage(request.packageName)) {
            return record(request, "DENIED", "Identitas package tidak cocok", now)
        }
        if (request.requestId.isBlank() || request.requestId.length > 120 || !CoreSanitizer.validCommandId(request.commandId)) {
            return record(request, "DENIED", "Request tidak valid", now)
        }
        if (request.payload.length > 4096) return record(request, "DENIED", "Payload melebihi 4 KB", now)

        when (val decision = policies.evaluate(principal, request.commandId, approvedByUser)) {
            is PolicyDecision.Denied -> return record(request, "DENIED", decision.reason, now)
            PolicyDecision.ApprovalRequired -> return record(request, "PENDING_APPROVAL", "Menunggu persetujuan pengguna", now)
            PolicyDecision.Allowed -> Unit
        }

        val handler = handlers["${request.packageName}|${request.commandId}"]
            ?: return record(request, "FAILED", "Handler capability belum tersedia", now)
        dao.upsertCommandAudit(
            CommandAuditEntity(request.requestId, request.packageName, request.commandId, "RUNNING", "Perintah diterima", now, 0L)
        )
        return try {
            val result = handler.execute(request.payload)
            record(request, if (result.success) "SUCCEEDED" else "FAILED", CoreSanitizer.cleanText(result.summary, 300), now)
        } catch (error: Exception) {
            record(request, "FAILED", CoreSanitizer.cleanText(error.message ?: "Eksekusi gagal", 300), now)
        }
    }

    /** Authorize a command that will be executed by the remote companion APK, not in Shield. */
    suspend fun authorizeRemote(
        principal: ConnectorPrincipal,
        request: MoraCommandRequest,
        approvedByUser: Boolean = false
    ): MoraCommandResult {
        val now = System.currentTimeMillis()
        if (request.packageName != principal.packageName || !CoreSanitizer.validPackage(request.packageName)) {
            return record(request, "DENIED", "Identitas package tidak cocok", now)
        }
        if (request.requestId.isBlank() || request.requestId.length > 120 || !CoreSanitizer.validCommandId(request.commandId)) {
            return record(request, "DENIED", "Request tidak valid", now)
        }
        if (request.payload.length > 4096) return record(request, "DENIED", "Payload melebihi 4 KB", now)

        val app = dao.getApp(principal.packageName)
            ?: return record(request, "DENIED", "Aplikasi belum terdaftar", now)
        val advertised = app.capabilities.split('\n').filter { it.isNotBlank() }.toSet()
        if (request.commandId !in advertised) {
            return record(request, "DENIED", "Capability tidak diumumkan connector", now)
        }

        when (val decision = policies.evaluate(principal, request.commandId, approvedByUser)) {
            is PolicyDecision.Denied -> return record(request, "DENIED", decision.reason, now)
            PolicyDecision.ApprovalRequired -> return record(request, "PENDING_APPROVAL", "Menunggu persetujuan pengguna", now)
            PolicyDecision.Allowed -> Unit
        }

        dao.upsertCommandAudit(
            CommandAuditEntity(request.requestId, request.packageName, request.commandId, "RUNNING", "Dikirim ke connector", now, 0L)
        )
        return MoraCommandResult(request.requestId, "RUNNING", "Perintah dikirim ke connector")
    }

    /** Complete only a matching in-flight request. A connector cannot complete another app's audit row. */
    suspend fun completeRemote(
        principal: ConnectorPrincipal,
        requestId: String,
        commandId: String,
        success: Boolean,
        summary: String
    ): Boolean {
        if (requestId.isBlank() || requestId.length > 120 || !CoreSanitizer.validCommandId(commandId)) return false
        val existing = dao.getCommandAudit(requestId) ?: return false
        if (existing.packageName != principal.packageName || existing.commandId != commandId || existing.status != "RUNNING") return false
        dao.upsertCommandAudit(
            existing.copy(
                status = if (success) "SUCCEEDED" else "FAILED",
                resultSummary = CoreSanitizer.cleanText(summary, 300),
                completedAtMs = System.currentTimeMillis()
            )
        )
        return true
    }

    private suspend fun record(request: MoraCommandRequest, status: String, summary: String, createdAt: Long): MoraCommandResult {
        val safeSummary = CoreSanitizer.cleanText(summary, 300)
        dao.upsertCommandAudit(
            CommandAuditEntity(
                requestId = request.requestId.take(120),
                packageName = request.packageName.take(255),
                commandId = request.commandId.take(100),
                status = status,
                resultSummary = safeSummary,
                createdAtMs = createdAt,
                completedAtMs = if (status == "PENDING_APPROVAL" || status == "RUNNING") 0L else System.currentTimeMillis()
            )
        )
        return MoraCommandResult(request.requestId, status, safeSummary)
    }
}
