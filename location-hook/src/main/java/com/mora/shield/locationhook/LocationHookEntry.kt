package com.mora.shield.locationhook

import android.app.Application
import android.content.Context
import android.location.Location
import android.os.SystemClock
import com.mora.shield.LocationCoordinate
import com.mora.shield.LocationMode
import com.mora.shield.LocationSpoofEngine
import com.mora.shield.PerAppLocationProfile
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LocationHookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val targetPackage = lpparam.packageName
        if (targetPackage == "android" || targetPackage == "com.android.systemui" ||
            targetPackage == "com.mora.shield" || targetPackage == "com.mora.shield.locationhook") return

        XposedHelpers.findAndHookMethod(
            Application::class.java, "attach", Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val appContext = param.args[0] as? Context ?: return
                    if (appContext.packageName != targetPackage) return
                    runCatching {
                        appContext.contentResolver.call(
                            android.net.Uri.parse("content://com.mora.shield.location.profile"),
                            "backend_heartbeat", null, null
                        )
                    }
                    installLocationHooks(appContext, targetPackage)
                }
            }
        )
    }

    private fun installLocationHooks(context: Context, targetPackage: String) {
        val cacheLock = Any()
        var cachedProfile: PerAppLocationProfile? = null
        var lastReadAt = 0L
        var lastHeartbeatAt = 0L
        var cachedCoordinate: LocationCoordinate? = null

        fun profile(): PerAppLocationProfile? {
            val now = SystemClock.elapsedRealtime()
            if (lastHeartbeatAt == 0L || now - lastHeartbeatAt >= 30_000L) {
                lastHeartbeatAt = now
                runCatching {
                    context.contentResolver.call(
                        android.net.Uri.parse("content://com.mora.shield.location.profile"),
                        "backend_heartbeat", null, null
                    )
                }
            }
            synchronized(cacheLock) {
                if (now - lastReadAt < 5_000L && cachedProfile != null) return cachedProfile
                lastReadAt = now
                val bundle = runCatching {
                    context.contentResolver.call(
                        android.net.Uri.parse("content://com.mora.shield.location.profile"),
                        "get_profile", null, null
                    )
                }.getOrNull() ?: return cachedProfile
                val mode = runCatching { LocationMode.valueOf(bundle.getString("mode") ?: "REAL") }.getOrDefault(LocationMode.REAL)
                cachedProfile = PerAppLocationProfile(
                    packageName = targetPackage,
                    mode = mode,
                    latitude = bundle.getDouble("latitude"),
                    longitude = bundle.getDouble("longitude"),
                    radiusMeters = bundle.getInt("radiusMeters", 1000),
                    intervalMinutes = bundle.getInt("intervalMinutes", 15),
                    enabled = bundle.getBoolean("enabled", false)
                )
                cachedCoordinate = null
                return cachedProfile
            }
        }

        fun coordinate(): LocationCoordinate? {
            val p = profile() ?: return null
            if (!p.enabled || p.mode == LocationMode.REAL) return null
            synchronized(cacheLock) {
                val next = LocationSpoofEngine.coordinateFor(p, SystemClock.elapsedRealtime())
                if (next != null) cachedCoordinate = next
                return cachedCoordinate
            }
        }

        try {
            XposedHelpers.findAndHookMethod(Location::class.java, "getLatitude", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    coordinate()?.let { param.result = it.latitude }
                }
            })
            XposedHelpers.findAndHookMethod(Location::class.java, "getLongitude", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    coordinate()?.let { param.result = it.longitude }
                }
            })
            XposedBridge.log("Mora Location Hook active for $targetPackage")
        } catch (error: Throwable) {
            XposedBridge.log("Mora Location Hook failed for $targetPackage: $error")
        }
    }
}
