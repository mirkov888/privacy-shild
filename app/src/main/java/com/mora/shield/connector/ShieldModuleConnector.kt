package com.mora.shield.connector

import com.mora.shield.KernelManager
import com.mora.shield.Ps
import com.mora.shield.Root
import com.mora.shield.core.ConnectorState
import com.mora.shield.core.ConnectorStatus
import com.mora.shield.core.MoraConnector

/** Adapter hanya-baca untuk modul Mora Privacy Shield yang sudah terpasang. */
class ShieldModuleConnector : MoraConnector {
    override val id: String = "shield-module"
    override val displayName: String = "Mora Privacy Shield"
    override val capabilities: Set<String> = setOf(
        "module.status.read",
        "module.events.read",
        "module.config.read"
    )

    override fun inspect(): ConnectorStatus {
        val now = System.currentTimeMillis()
        if (!Root.available()) {
            return ConnectorStatus(id, displayName, ConnectorState.UNAVAILABLE, "Akses root belum diberikan", now)
        }
        if (!Ps.installed()) {
            return ConnectorStatus(id, displayName, ConnectorState.UNAVAILABLE, "Modul privacy_shield belum terpasang", now)
        }
        val version = Ps.version().ifBlank { "versi tidak diketahui" }
        val revision = Ps.rev()
        val suffix = if (revision.isBlank()) "" else " · rev " + revision
        return ConnectorStatus(
            id,
            displayName,
            ConnectorState.READY,
            "Terhubung · " + version + suffix,
            now
        )
    }
}

/** Adapter telemetry perangkat. Metrik dibaca lokal melalui node sistem yang dikenal. */
class DeviceTelemetryConnector : MoraConnector {
    override val id: String = "device-telemetry"
    override val displayName: String = "Device Telemetry"
    override val capabilities: Set<String> = setOf(
        "device.ram.read",
        "device.battery.read",
        "device.thermal.read"
    )

    override fun inspect(): ConnectorStatus {
        val now = System.currentTimeMillis()
        if (!Root.available()) {
            return ConnectorStatus(id, displayName, ConnectorState.UNAVAILABLE, "Memerlukan akses root untuk pembacaan telemetry", now)
        }

        val ram = KernelManager.ramPercent()
        val battery = KernelManager.battery()
        val parts = ArrayList<String>()
        if (ram >= 0) parts.add("RAM " + ram + "%")
        if (battery.first >= 0) parts.add("Baterai " + battery.first + "%")
        if (battery.second >= 0) parts.add("Suhu " + battery.second + "°C")

        val state = if (parts.isEmpty()) ConnectorState.DEGRADED else ConnectorState.READY
        val message = if (parts.isEmpty()) "Telemetry belum tersedia" else parts.joinToString(" · ")
        return ConnectorStatus(id, displayName, state, message, now)
    }
}
