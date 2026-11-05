package com.mora.shield.core

import com.mora.shield.Ps
import com.mora.shield.connector.DeviceTelemetryConnector
import com.mora.shield.connector.ShieldModuleConnector
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pusat event dan registry connector Mora Shield.
 *
 * Core menyimpan event secara lokal di memori. Core tidak mengirim data ke internet
 * dan tidak mengeksekusi perintah shell yang dikirim oleh connector eksternal.
 */
object MoraCore {
    private const val MAX_EVENTS = 500
    private val lock = Any()
    private val connectors = LinkedHashMap<String, MoraConnector>()
    private val history = ArrayDeque<MoraEvent>()
    private val listeners = CopyOnWriteArrayList<(MoraEvent) -> Unit>()

    @Volatile
    private var initialized = false

    fun initialize() {
        synchronized(lock) {
            if (initialized) return
            registerLocked(ShieldModuleConnector())
            registerLocked(DeviceTelemetryConnector())
            initialized = true
        }
    }

    fun isInitialized(): Boolean = initialized

    fun registerConnector(connector: MoraConnector): Boolean {
        val id = connector.id.trim()
        if (!id.matches(Regex("^[A-Za-z0-9._-]{1,80}$"))) return false
        synchronized(lock) {
            if (connectors.containsKey(id)) return false
            connectors[id] = connector
            return true
        }
    }

    fun unregisterConnector(id: String): Boolean {
        synchronized(lock) {
            return connectors.remove(id) != null
        }
    }

    private fun registerLocked(connector: MoraConnector) {
        connectors[connector.id] = connector
    }

    fun inspectConnectors(): List<ConnectorStatus> {
        val snapshot = synchronized(lock) { connectors.values.toList() }
        return snapshot.map { connector ->
            try {
                connector.inspect()
            } catch (error: Exception) {
                ConnectorStatus(
                    id = connector.id,
                    displayName = connector.displayName,
                    state = ConnectorState.ERROR,
                    message = error.message ?: "Pemeriksaan connector gagal"
                )
            }
        }
    }

    fun publish(event: MoraEvent) {
        val source = event.sourceId.trim()
        val type = event.type.trim()
        val title = event.title.trim().take(100)
        if (!source.matches(Regex("^[A-Za-z0-9._:-]{1,120}$"))) return
        if (!type.matches(Regex("^[A-Za-z0-9._-]{1,48}$")) || title.isBlank()) return

        val normalized = event.copy(
            sourceId = source,
            type = type,
            title = title,
            packageName = event.packageName.trim().take(255),
            detail = event.detail.trim().take(500),
            id = event.id.take(160)
        )

        synchronized(lock) {
            if (history.any { it.id == normalized.id }) return
            history.addLast(normalized)
            while (history.size > MAX_EVENTS) history.removeFirst()
        }

        listeners.forEach { listener ->
            try {
                listener(normalized)
            } catch (_: Exception) {
                // Listener yang gagal tidak boleh menghentikan alur event lain.
            }
        }
    }

    /** Mengimpor riwayat modul yang sudah dibaca Store, dengan ID stabil agar tidak duplikat. */
    fun ingestShieldEvents(events: List<Ps.Ev>) {
        // Ps.events() mengembalikan urutan newest-first; masukkan terbalik supaya deque tetap oldest-first.
        events.asReversed().forEach { event -> publishShieldEvent(event) }
    }

    fun publishShieldEvent(event: Ps.Ev) {
        val severity = when (event.type.uppercase()) {
            "MIC", "CAM", "LOC", "CONTACTS", "SMS", "CALLLOG" -> MoraSeverity.MEDIUM
            "NETBIG" -> MoraSeverity.LOW
            else -> MoraSeverity.INFO
        }
        val stableId = listOf(
            "shield",
            event.ts.toString(),
            event.type,
            event.pkg,
            event.detail.hashCode().toString()
        ).joinToString(":")
        publish(
            MoraEvent(
                id = stableId,
                sourceId = "shield-module",
                type = event.type.ifBlank { "EVENT" },
                title = "Aktivitas " + event.type.ifBlank { "Shield" },
                packageName = event.pkg,
                detail = event.detail,
                severity = severity,
                occurredAtMs = event.ts * 1000L
            )
        )
    }

    fun recentEvents(limit: Int = 100): List<MoraEvent> {
        val safeLimit = limit.coerceIn(0, MAX_EVENTS)
        return synchronized(lock) {
            history.toList().takeLast(safeLimit).asReversed()
        }
    }

    fun subscribe(listener: (MoraEvent) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun connectorCount(): Int = synchronized(lock) { connectors.size }
}
