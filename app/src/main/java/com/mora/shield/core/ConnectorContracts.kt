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
