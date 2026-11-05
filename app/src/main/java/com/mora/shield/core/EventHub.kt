package com.mora.shield.core

import kotlinx.coroutines.flow.Flow

internal class EventHub(private val dao: CoreDao) {
    suspend fun publish(event: MoraEvent): Boolean {
        if (!CoreSanitizer.validPackage(event.sourcePackage)) return false
        if (!CoreSanitizer.validEventType(event.eventType)) return false
        if (event.eventId.isBlank() || event.eventId.length > 160) return false

        val safe = ActivityEventEntity(
            eventId = event.eventId,
            sourcePackage = event.sourcePackage,
            eventType = event.eventType,
            title = CoreSanitizer.cleanText(event.title, 120),
            affectedPackage = if (CoreSanitizer.validPackage(event.affectedPackage)) event.affectedPackage else "",
            detail = CoreSanitizer.cleanText(event.detail, 700),
            severity = event.severity.wireValue,
            occurredAtMs = event.occurredAtMs.coerceAtLeast(0L),
            receivedAtMs = System.currentTimeMillis()
        )
        val inserted = dao.insertEvent(safe) != -1L
        dao.trimEvents(5000)
        return inserted
    }

    suspend fun recent(limit: Int): List<ActivityEventEntity> = dao.recentEvents(limit.coerceIn(1, 5000))

    fun observe(limit: Int): Flow<List<ActivityEventEntity>> = dao.observeRecentEvents(limit.coerceIn(1, 5000))
}
