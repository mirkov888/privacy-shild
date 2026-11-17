package com.mora.shield.core

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import com.mora.rustandroid.NativeCore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** App-side adapter. Existing Room MoraCore and authenticated Connector remain the control plane. */
object MoraRustEngine {
    const val EXPECTED_API_VERSION = 9
    const val EXPECTED_CAPABILITY_MASK = 0x7ff

    data class Status(
        val apiVersion: Int,
        val schemaVersion: Int,
        val capabilityMask: Int,
        val modules: List<String>,
        val compatible: Boolean
    )

    data class SelfCheckResult(
        val passed: Int,
        val total: Int,
        val details: List<String>
    ) {
        val allPassed: Boolean get() = passed == total
    }

    fun inspect(): Status {
        val descriptor = JSONObject(NativeCore.nativeCoreDescriptor())
        val capabilities = descriptor.optJSONObject("capabilities")
            ?: error("Descriptor tidak memiliki capability")
        val mask = capabilities.optInt("mask", -1)
        val moduleArray = descriptor.optJSONArray("modules") ?: JSONArray()
        val modules = (0 until moduleArray.length()).mapNotNull { index ->
            moduleArray.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
        }
        val apiVersion = descriptor.optInt("api_version", -1)
        val schemaVersion = descriptor.optInt("schema_version", -1)
        val nativeMask = NativeCore.nativeCoreCapabilities()
        return Status(
            apiVersion,
            schemaVersion,
            mask,
            modules,
            apiVersion == EXPECTED_API_VERSION &&
                schemaVersion == 1 &&
                mask == EXPECTED_CAPABILITY_MASK &&
                nativeMask == EXPECTED_CAPABILITY_MASK &&
                modules.size == 6
        )
    }

    private data class PersistentModuleStore(
        val fileName: String,
        val responseKey: String
    )

    private fun persistentStore(module: String): PersistentModuleStore = when (module) {
        "profile_store" -> PersistentModuleStore("mora_rust_profiles.json", "store")
        "system_config" -> PersistentModuleStore("mora_rust_system_config.json", "config")
        "runtime_manager" -> PersistentModuleStore("mora_rust_runtime_services.json", "runtime")
        "event_logger" -> PersistentModuleStore("mora_rust_activity_events.json", "log")
        "network_monitor" -> PersistentModuleStore("mora_rust_network_snapshots.json", "monitor")
        else -> error("Modul tidak memiliki penyimpanan persisten: $module")
    }

    private fun readAtomic(file: File): String {
        val backup = File(file.path + ".bak")
        if (!file.exists() && !backup.exists()) return ""
        return AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun writeAtomic(file: File, value: String) {
        val atomic = AtomicFile(file)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(value.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (failure: Throwable) {
            stream?.let { atomic.failWrite(it) }
            throw failure
        }
    }

    /**
     * Runs a Rust operation using an app-private durable state document.
     * Each module's state is committed atomically only after Rust returns a valid success response.
     */
    fun dispatchPersistent(
        context: Context,
        module: String,
        operation: String,
        params: JSONObject = JSONObject()
    ): JSONObject {
        val store = persistentStore(module)
        val file = File(context.applicationContext.filesDir, store.fileName)
        val response = dispatch(module, operation, readAtomic(file), params = params)
        val state = response.getJSONObject("data").getJSONObject(store.responseKey)
        writeAtomic(file, state.toString())
        return response
    }

    /** Dispatch one module operation through the common versioned JSON contract. */
    fun dispatch(
        module: String,
        operation: String,
        stateJson: String = "",
        params: JSONObject = JSONObject()
    ): JSONObject {
        val request = JSONObject().apply {
            put("schema_version", 1)
            put("request_id", UUID.randomUUID().toString())
            put("module", module)
            put("operation", operation)
            put("timestamp_ms", System.currentTimeMillis().coerceAtLeast(0L))
            put("state_json", stateJson)
            put("params", params)
        }
        val response = JSONObject(NativeCore.nativeCoreDispatch(request.toString()))
        if (!response.optBoolean("ok")) {
            error("${response.optString("module", module)}: ${response.optString("error", "native_operation_failed")}")
        }
        return response
    }

    /** Exercises all six Rust modules with temporary in-memory inputs only. */
    fun runSelfCheck(): SelfCheckResult {
        val checks = mutableListOf<Pair<String, Boolean>>()
        val status = inspect()
        checks += "API descriptor and capability negotiation" to status.compatible

        val profile = dispatch(
            "profile_store", "create", """{"profiles":[]}""",
            JSONObject().put("id", "mora_phase11").put("label", "Mora Shield")
        ).getJSONObject("data").getJSONObject("store")
        checks += "Profile Store" to (profile.getJSONArray("profiles").length() == 1)

        val config = dispatch("system_config", "get")
            .getJSONObject("data").getJSONObject("config")
        checks += "System Config" to (config.optString("log_level") == "info")

        val runtime = dispatch("runtime_manager", "list")
            .getJSONObject("data").getJSONObject("runtime")
        checks += "Runtime Manager" to (runtime.getJSONArray("services").length() == 3)

        val log = dispatch(
            "event_logger", "record",
            params = JSONObject()
                .put("category", "system")
                .put("action", "phase11_self_check")
                .put("detail", "Mora Shield Rust integration smoke test")
        ).getJSONObject("data").getJSONObject("log")
        checks += "Event Logger" to (log.getJSONArray("events").length() == 1)

        val sample = JSONObject()
            .put("uid", Process.myUid().coerceAtLeast(1))
            .put("rx_bytes", 1000L)
            .put("tx_bytes", 2000L)
            .put("transport", "none")
            .put("connected", false)
            .put("validated", false)
            .put("metered", false)
        val network = dispatch(
            "network_monitor", "record",
            params = JSONObject().put("snapshot_json", sample.toString())
        ).getJSONObject("data").getJSONObject("monitor")
        checks += "Network Monitor" to (network.getJSONArray("snapshots").length() == 1)

        val auditInput = JSONObject()
            .put("package_name", "com.mora.shield")
            .put("target_sdk", 35)
            .put("debuggable", false)
            .put("allow_backup", false)
            .put("uses_cleartext_traffic", false)
            .put("security_patch", "")
            .put("permissions", JSONArray())
        val audit = dispatch(
            "security_auditor", "audit",
            params = JSONObject().put("input_json", auditInput.toString())
        ).getJSONObject("data").getJSONObject("report")
        checks += "Security Auditor" to (audit.optString("risk_level") == "good")

        val details = checks.map { (name, passed) ->
            "${if (passed) "PASS" else "FAIL"} · $name"
        }
        return SelfCheckResult(checks.count { it.second }, checks.size, details)
    }

    /** Audit only this installed app's package metadata and requested permissions. */
    @Suppress("DEPRECATION")
    fun auditOwnApp(context: Context): JSONObject {
        val appContext = context.applicationContext
        val packageInfo = appContext.packageManager.getPackageInfo(
            appContext.packageName, PackageManager.GET_PERMISSIONS
        )
        val permissions = JSONArray()
        (packageInfo.requestedPermissions ?: emptyArray()).forEach { permission ->
            permissions.put(
                JSONObject()
                    .put("name", permission)
                    .put(
                        "granted",
                        appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                    )
            )
        }
        val appInfo = appContext.applicationInfo
        val input = JSONObject()
            .put("package_name", appContext.packageName)
            .put("target_sdk", appInfo.targetSdkVersion)
            .put(
                "debuggable",
                appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            )
            .put("allow_backup", appInfo.flags and 0x00008000 != 0)
            .put("uses_cleartext_traffic", appInfo.flags and 0x08000000 != 0)
            .put("security_patch", Build.VERSION.SECURITY_PATCH ?: "")
            .put("permissions", permissions)

        val report = dispatch(
            "security_auditor", "audit",
            params = JSONObject().put("input_json", input.toString())
        ).getJSONObject("data").getJSONObject("report")

        writeAtomic(
            File(appContext.filesDir, "mora_rust_security_audit.json"),
            report.toString(2)
        )
        return report
    }

    /** Capture this app UID's TrafficStats counters; this is not a device-wide traffic monitor. */
    fun captureOwnNetworkSnapshot(context: Context): JSONObject {
        val appContext = context.applicationContext
        val uid = Process.myUid().coerceAtLeast(1)
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivity.activeNetwork
        val capabilities = activeNetwork?.let(connectivity::getNetworkCapabilities)
        val transport = when {
            capabilities == null -> "none"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val rxBytes = TrafficStats.getUidRxBytes(uid)
        val txBytes = TrafficStats.getUidTxBytes(uid)
        val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val metered = capabilities != null && connectivity.isActiveNetworkMetered
        val snapshot = JSONObject()
            .put("uid", uid)
            .put("rx_bytes", if (rxBytes >= 0) rxBytes else JSONObject.NULL)
            .put("tx_bytes", if (txBytes >= 0) txBytes else JSONObject.NULL)
            .put("transport", transport)
            .put("connected", connected)
            .put("validated", validated)
            .put("metered", metered)

        val data = dispatchPersistent(
            appContext,
            "network_monitor",
            "record",
            JSONObject().put("snapshot_json", snapshot.toString())
        ).getJSONObject("data")
        return data.getJSONObject("summary")
    }
}
