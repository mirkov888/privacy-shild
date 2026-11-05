package com.mora.shield.core

enum class ConnectorState(val label: String) {
    READY("Aktif"),
    DEGRADED("Terbatas"),
    UNAVAILABLE("Tidak tersedia"),
    ERROR("Error")
}

data class ConnectorStatus(
    val id: String,
    val displayName: String,
    val state: ConnectorState,
    val message: String,
    val checkedAtMs: Long = System.currentTimeMillis()
)

interface MoraConnector {
    val id: String
    val displayName: String
    val capabilities: Set<String>

    /** Pemeriksaan hanya-baca. Connector tidak boleh menjalankan aksi destruktif di sini. */
    fun inspect(): ConnectorStatus
}

enum class MoraSeverity(val wireValue: String) {
    INFO("info"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    companion object {
        fun fromWire(value: String): MoraSeverity =
            values().firstOrNull { it.wireValue == value.lowercase() } ?: INFO
    }
}

data class MoraEvent(
    val sourceId: String,
    val type: String,
    val title: String,
    val packageName: String = "",
    val detail: String = "",
    val severity: MoraSeverity = MoraSeverity.INFO,
    val occurredAtMs: Long = System.currentTimeMillis(),
    val id: String = java.util.UUID.randomUUID().toString()
)
