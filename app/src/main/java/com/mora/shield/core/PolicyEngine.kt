package com.mora.shield.core

internal sealed class PolicyDecision {
    object Allowed : PolicyDecision()
    object ApprovalRequired : PolicyDecision()
    data class Denied(val reason: String) : PolicyDecision()
}

internal class PolicyEngine(private val dao: CoreDao) {
    suspend fun get(packageName: String): AppPolicy? {
        val p = dao.getPolicy(packageName) ?: return null
        return AppPolicy(
            packageName = p.packageName,
            enabled = p.enabled,
            monitoringEnabled = p.monitoringEnabled,
            commandAccessEnabled = p.commandAccessEnabled,
            allowedCommands = p.allowedCommands.split('\n').filter { it.isNotBlank() }.toSet(),
            approvalRequiredCommands = p.approvalRequiredCommands.split('\n').filter { it.isNotBlank() }.toSet(),
            networkMode = p.networkMode,
            backgroundMode = p.backgroundMode
        )
    }

    suspend fun update(policy: AppPolicy): Boolean {
        if (!CoreSanitizer.validPackage(policy.packageName)) return false
        if (policy.allowedCommands.any { !CoreSanitizer.validCommandId(it) }) return false
        if (policy.approvalRequiredCommands.any { it != "*" && !CoreSanitizer.validCommandId(it) }) return false
        if (policy.networkMode !in setOf("inherit", "allow", "block", "wifi_only", "mobile_only")) return false
        if (policy.backgroundMode !in setOf("normal", "restricted", "allowed")) return false
        if (dao.getApp(policy.packageName) == null) return false
        dao.upsertPolicy(
            AppPolicyEntity(
                packageName = policy.packageName,
                enabled = policy.enabled,
                monitoringEnabled = policy.monitoringEnabled,
                commandAccessEnabled = policy.commandAccessEnabled,
                allowedCommands = policy.allowedCommands.sorted().joinToString("\n"),
                approvalRequiredCommands = policy.approvalRequiredCommands.sorted().joinToString("\n"),
                networkMode = policy.networkMode,
                backgroundMode = policy.backgroundMode,
                updatedAtMs = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun evaluate(principal: ConnectorPrincipal, commandId: String, approvedByUser: Boolean): PolicyDecision {
        if (!CoreSanitizer.validCommandId(commandId)) return PolicyDecision.Denied("Command ID tidak valid")
        val app = dao.getApp(principal.packageName) ?: return PolicyDecision.Denied("Aplikasi belum terdaftar")
        if (app.certificateSha256.isNotBlank() && !app.certificateSha256.equals(principal.certificateSha256, true)) {
            return PolicyDecision.Denied("Identitas connector tidak cocok")
        }
        if (app.connectionState != "CONNECTED") return PolicyDecision.Denied("Connector belum terhubung")
        val policy = dao.getPolicy(principal.packageName) ?: return PolicyDecision.Denied("Kebijakan aplikasi tidak ditemukan")
        if (!policy.enabled) return PolicyDecision.Denied("Aplikasi dinonaktifkan oleh kebijakan Mora Shield")
        if (!policy.commandAccessEnabled) return PolicyDecision.Denied("Akses perintah belum diaktifkan")
        val allowed = policy.allowedCommands.split('\n').filter { it.isNotBlank() }.toSet()
        if (commandId !in allowed) return PolicyDecision.Denied("Capability belum diizinkan")
        val approval = policy.approvalRequiredCommands.split('\n').filter { it.isNotBlank() }.toSet()
        val needsApproval = "*" in approval || commandId in approval
        if (needsApproval && !approvedByUser) return PolicyDecision.ApprovalRequired
        return PolicyDecision.Allowed
    }
}
