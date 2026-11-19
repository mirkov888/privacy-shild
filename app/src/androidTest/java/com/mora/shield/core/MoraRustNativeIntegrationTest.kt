package com.mora.shield.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mora.rustandroid.NativeCore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MoraRustNativeIntegrationTest {
    @Test
    fun nativeLibraryLoadsAndNegotiatesExpectedContract() {
        assertEquals(9, NativeCore.nativeCoreApiVersion())
        assertEquals(0x7ff, NativeCore.nativeCoreCapabilities())

        val descriptor = JSONObject(NativeCore.nativeCoreDescriptor())
        assertEquals(9, descriptor.getInt("api_version"))
        assertEquals(1, descriptor.getInt("schema_version"))
        assertEquals(0x7ff, descriptor.getJSONObject("capabilities").getInt("mask"))
        assertEquals(6, descriptor.getJSONArray("modules").length())
    }

    @Test
    fun allSixRustModulesDispatchThroughJni() {
        val profile = dispatch(
            "profile_store",
            "create",
            params = JSONObject().put("id", "jni_test").put("label", "JNI Test")
        )
        assertTrue(profile.toString(), profile.optBoolean("ok"))
        assertEquals(
            1,
            profile.getJSONObject("data").getJSONObject("store")
                .getJSONArray("profiles").length()
        )

        val config = dispatch("system_config", "get")
        assertTrue(config.toString(), config.optBoolean("ok"))
        assertTrue(config.getJSONObject("data").has("config"))

        val runtime = dispatch("runtime_manager", "list")
        assertTrue(runtime.toString(), runtime.optBoolean("ok"))
        assertEquals(
            3,
            runtime.getJSONObject("data").getJSONObject("runtime")
                .getJSONArray("services").length()
        )

        val event = dispatch(
            "event_logger",
            "record",
            params = JSONObject()
                .put("category", "system")
                .put("action", "jni_instrumentation_test")
                .put("detail", "Android instrumentation")
        )
        assertTrue(event.toString(), event.optBoolean("ok"))
        assertEquals(
            1,
            event.getJSONObject("data").getJSONObject("log")
                .getJSONArray("events").length()
        )

        val snapshot = JSONObject()
            .put("uid", 1000)
            .put("rx_bytes", 10L)
            .put("tx_bytes", 20L)
            .put("transport", "none")
            .put("connected", false)
            .put("validated", false)
            .put("metered", false)
        val network = dispatch(
            "network_monitor",
            "record",
            params = JSONObject().put("snapshot_json", snapshot.toString())
        )
        assertTrue(network.toString(), network.optBoolean("ok"))
        assertEquals(
            1,
            network.getJSONObject("data").getJSONObject("monitor")
                .getJSONArray("snapshots").length()
        )

        val auditInput = JSONObject()
            .put("package_name", "com.mora.shield")
            .put("target_sdk", 35)
            .put("debuggable", false)
            .put("allow_backup", false)
            .put("uses_cleartext_traffic", false)
            .put("security_patch", "")
            .put("permissions", JSONArray())
        val audit = dispatch(
            "security_auditor",
            "audit",
            params = JSONObject().put("input_json", auditInput.toString())
        )
        assertTrue(audit.toString(), audit.optBoolean("ok"))
        assertTrue(audit.getJSONObject("data").has("report"))
    }

    @Test
    fun everyLegacyJniExportRemainsCallable() {
        assertEquals(42, NativeCore.nativeAdd(20, 22))
        assertEquals("Mora Core", NativeCore.nativeNormalizeProfileLabel("  Mora Core  "))
        assertTrue(NativeCore.nativeIsValidProfileId("mora_core"))
        assertTrue(NativeCore.nativeIsSafeText("safe text", 64))
        assertFalse(NativeCore.nativeIsSafeText("unsafe\ntext", 64))

        val profile = JSONObject(
            NativeCore.nativeProfileOperation(
                """{"profiles":[]}""", "create", "native_profile", "Native Profile", 1L
            )
        )
        assertTrue(profile.toString(), profile.optBoolean("ok"))

        val config = JSONObject(
            NativeCore.nativeSystemConfigOperation("", "get", "", "", 1L)
        )
        assertTrue(config.toString(), config.optBoolean("ok"))

        val runtime = JSONObject(
            NativeCore.nativeRuntimeOperation("", "list", "", 1L)
        )
        assertTrue(runtime.toString(), runtime.optBoolean("ok"))

        val event = JSONObject(
            NativeCore.nativeEventLogOperation(
                "", "record", "system", "jni_legacy_test", "Legacy JNI test", 1L
            )
        )
        assertTrue(event.toString(), event.optBoolean("ok"))

        val snapshot = JSONObject()
            .put("uid", 1000)
            .put("rx_bytes", 10L)
            .put("tx_bytes", 20L)
            .put("transport", "none")
            .put("connected", false)
            .put("validated", false)
            .put("metered", false)
        val network = JSONObject(
            NativeCore.nativeNetworkMonitorOperation("", "record", snapshot.toString(), 1L)
        )
        assertTrue(network.toString(), network.optBoolean("ok"))

        val auditInput = JSONObject()
            .put("package_name", "com.mora.shield")
            .put("target_sdk", 35)
            .put("debuggable", false)
            .put("allow_backup", false)
            .put("uses_cleartext_traffic", false)
            .put("security_patch", "")
            .put("permissions", JSONArray())
        val audit = JSONObject(NativeCore.nativeSecurityAudit(auditInput.toString(), 1L))
        assertTrue(audit.toString(), audit.optBoolean("ok"))
    }

    @Test
    fun appAdapterPersistsAllStatefulRustModulesBetweenCalls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            "mora_rust_profiles.json",
            "mora_rust_system_config.json",
            "mora_rust_runtime_services.json",
            "mora_rust_activity_events.json",
            "mora_rust_network_snapshots.json"
        ).forEach { File(context.filesDir, it).delete() }

        val profile = MoraRustEngine.dispatchPersistent(
            context,
            "profile_store",
            "create",
            JSONObject().put("id", "persist_test").put("label", "Persist Test")
        )
        assertTrue(profile.toString(), profile.optBoolean("ok"))
        val listedProfiles = MoraRustEngine.dispatchPersistent(context, "profile_store", "list")
            .getJSONObject("data").getJSONObject("store").getJSONArray("profiles")
        assertEquals(1, listedProfiles.length())

        val changedConfig = MoraRustEngine.dispatchPersistent(
            context,
            "system_config",
            "set",
            JSONObject().put("key", "log_level").put("value", "debug")
        )
        assertTrue(changedConfig.toString(), changedConfig.optBoolean("ok"))
        val loadedConfig = MoraRustEngine.dispatchPersistent(context, "system_config", "get")
            .getJSONObject("data").getJSONObject("config")
        assertEquals("debug", loadedConfig.getString("log_level"))

        val started = MoraRustEngine.dispatchPersistent(
            context,
            "runtime_manager",
            "start",
            JSONObject().put("service_id", "diagnostics")
        )
        assertTrue(started.toString(), started.optBoolean("ok"))
        val runtime = MoraRustEngine.dispatchPersistent(context, "runtime_manager", "list")
            .getJSONObject("data").getJSONObject("runtime").getJSONArray("services")
        assertTrue((0 until runtime.length()).any {
            val service = runtime.getJSONObject(it)
            service.getString("id") == "diagnostics" && service.getBoolean("running")
        })

        val recorded = MoraRustEngine.dispatchPersistent(
            context,
            "event_logger",
            "record",
            JSONObject()
                .put("category", "system")
                .put("action", "persistence_test")
                .put("detail", "Persisted through app-private storage")
        )
        assertTrue(recorded.toString(), recorded.optBoolean("ok"))
        val events = MoraRustEngine.dispatchPersistent(context, "event_logger", "list")
            .getJSONObject("data").getJSONObject("log").getJSONArray("events")
        assertEquals(1, events.length())

        val snapshot = JSONObject()
            .put("uid", 1000)
            .put("rx_bytes", 100L)
            .put("tx_bytes", 200L)
            .put("transport", "none")
            .put("connected", false)
            .put("validated", false)
            .put("metered", false)
        val recordedNetwork = MoraRustEngine.dispatchPersistent(
            context,
            "network_monitor",
            "record",
            JSONObject().put("snapshot_json", snapshot.toString())
        )
        assertTrue(recordedNetwork.toString(), recordedNetwork.optBoolean("ok"))
        val network = MoraRustEngine.dispatchPersistent(context, "network_monitor", "list")
            .getJSONObject("data").getJSONObject("monitor").getJSONArray("snapshots")
        assertEquals(1, network.length())

        val health = MoraRustEngine.runPersistentSelfCheck(context)
        assertEquals(health.details.joinToString("\n"), 6, health.passed)
        assertTrue(health.details.joinToString("\n"), health.allPassed)
    }

    @Test
    fun productionAdapterSelfCheckPassesAgainstLoadedNativeLibrary() {
        val result = MoraRustEngine.runSelfCheck()
        // This self-check covers API compatibility plus all six Rust modules.
        assertEquals(result.details.joinToString("\n"), 7, result.passed)
        assertEquals(7, result.total)
        assertTrue(result.details.joinToString("\n"), result.allPassed)
    }

    private fun dispatch(
        module: String,
        operation: String,
        stateJson: String = "",
        params: JSONObject = JSONObject()
    ): JSONObject {
        val request = JSONObject()
            .put("schema_version", 1)
            .put("request_id", "android-jni-test")
            .put("module", module)
            .put("operation", operation)
            .put("timestamp_ms", 1L)
            .put("state_json", stateJson)
            .put("params", params)
        return JSONObject(NativeCore.nativeCoreDispatch(request.toString()))
    }
}
