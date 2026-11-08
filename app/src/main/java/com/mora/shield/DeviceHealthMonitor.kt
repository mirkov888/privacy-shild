package com.mora.shield

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.ActivityManager
import android.os.BatteryManager
import android.os.SystemClock
import android.os.StatFs
import kotlinx.coroutines.delay
import java.io.File

/** Device metrics collected locally from Android framework and procfs. */
data class DeviceHealthSnapshot(
    val cpuPercent: Int? = null,
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val storageAvailableBytes: Long = 0L,
    val storageTotalBytes: Long = 0L,
    val batteryPercent: Int? = null,
    val batteryTemperatureC: Float? = null,
    val uptimeMs: Long = 0L
) {
    val ramPercent: Int?
        get() = if (ramTotalBytes > 0L) ((ramTotalBytes - ramUsedBytes).coerceAtLeast(0L) * 100L / ramTotalBytes).toInt() else null
}

object DeviceHealthMonitor {
    private data class CpuCounters(val idle: Long, val total: Long)

    private fun readCpuCounters(): CpuCounters? = runCatching {
        val fields = File("/proc/stat").useLines { lines ->
            lines.firstOrNull { it.startsWith("cpu ") }?.trim()?.split(Regex("\\s+"))?.drop(1)
        } ?: return null
        val values = fields.mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        val idle = values[3] + (values.getOrNull(4) ?: 0L)
        CpuCounters(idle, values.sum())
    }.getOrNull()

    suspend fun capture(context: Context): DeviceHealthSnapshot {
        val first = readCpuCounters()
        delay(300)
        val second = readCpuCounters()
        val cpu = if (first != null && second != null) {
            val total = second.total - first.total
            val idle = second.idle - first.idle
            if (total > 0L) (((total - idle).coerceAtLeast(0L) * 100L) / total).toInt().coerceIn(0, 100) else null
        } else null

        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
        val storage = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
        val rawTemp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1

        return DeviceHealthSnapshot(
            cpuPercent = cpu,
            ramUsedBytes = (memory.totalMem - memory.availMem).coerceAtLeast(0L),
            ramTotalBytes = memory.totalMem,
            storageAvailableBytes = storage?.availableBytes ?: 0L,
            storageTotalBytes = storage?.totalBytes ?: 0L,
            batteryPercent = batteryPercent,
            batteryTemperatureC = if (rawTemp >= 0) rawTemp / 10f else null,
            uptimeMs = SystemClock.elapsedRealtime()
        )
    }
}
