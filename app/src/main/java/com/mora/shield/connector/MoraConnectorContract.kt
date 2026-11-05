package com.mora.shield.connector

/** Kontrak IPC berversi untuk aplikasi pendamping Mora. */
object MoraConnectorContract {
    const val API_VERSION = 1

    const val HOST_PACKAGE = "com.mora.shield"
    const val CONNECTOR_PERMISSION = "com.mora.shield.permission.CONNECTOR"
    const val ACTION_BIND = "com.mora.shield.action.CONNECTOR"

    const val MSG_PUBLISH_EVENT = 1

    const val KEY_API_VERSION = "api_version"
    const val KEY_CONNECTOR_ID = "connector_id"
    const val KEY_EVENT_TYPE = "event_type"
    const val KEY_TITLE = "title"
    const val KEY_PACKAGE_NAME = "package_name"
    const val KEY_DETAIL = "detail"
    const val KEY_SEVERITY = "severity"
    const val KEY_OCCURRED_AT = "occurred_at_ms"
}
