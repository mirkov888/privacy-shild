package com.mora.shield.identity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mora.shield.PerAppIdentityStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class IdentityRandomizationSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = 24,
    val seed: String = "",
    val platform: IdentityPlatform = IdentityPlatform.ANDROID,
    val randomizeEnabledApps: Boolean = false
)

data class IdentityRandomizationResult(
    val runIndex: Int,
    val selectedProfile: String?,
    val randomizedApps: Int
)

/** Persistent, reproducible randomization policy. It only selects compatibility profiles. */
class IdentityRandomizationStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): IdentityRandomizationSettings {
        val seed = prefs.getString(KEY_SEED, null)?.takeIf { it.isNotBlank() }
            ?: newSeed().also { prefs.edit().putString(KEY_SEED, it).apply() }
        val platform = runCatching {
            IdentityPlatform.valueOf(prefs.getString(KEY_PLATFORM, IdentityPlatform.ANDROID.name)!!)
        }.getOrDefault(IdentityPlatform.ANDROID)
        return IdentityRandomizationSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            intervalHours = prefs.getInt(KEY_INTERVAL, 24).coerceIn(6, 168),
            seed = seed,
            platform = platform,
            randomizeEnabledApps = prefs.getBoolean(KEY_APPS, false)
        )
    }

    fun save(settings: IdentityRandomizationSettings): IdentityRandomizationSettings {
        require(settings.intervalHours in 6..168) { "Interval randomisasi harus 6–168 jam" }
        val seed = settings.seed.trim().ifBlank { newSeed() }
        require(seed.length <= 128 && seed.none(Char::isISOControl)) { "Seed tidak valid (maksimal 128 karakter)" }
        val saved = settings.copy(seed = seed)
        prefs.edit()
            .putBoolean(KEY_ENABLED, saved.enabled)
            .putInt(KEY_INTERVAL, saved.intervalHours)
            .putString(KEY_SEED, saved.seed)
            .putString(KEY_PLATFORM, saved.platform.name)
            .putBoolean(KEY_APPS, saved.randomizeEnabledApps)
            .apply()
        return saved
    }

    fun nextRunIndex(): Int {
        val next = (prefs.getInt(KEY_RUN_INDEX, 0) + 1).coerceAtLeast(1)
        prefs.edit().putInt(KEY_RUN_INDEX, next).apply()
        return next
    }

    fun runIndex(): Int = prefs.getInt(KEY_RUN_INDEX, 0)

    fun restoreRunIndex(value: Int) {
        require(value >= 0) { "Nomor run randomizer tidak valid" }
        prefs.edit().putInt(KEY_RUN_INDEX, value).apply()
    }

    private fun newSeed(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFERENCES = "mora_identity_randomization"
        private const val KEY_ENABLED = "schedule_enabled"
        private const val KEY_INTERVAL = "interval_hours"
        private const val KEY_SEED = "reusable_seed"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_APPS = "randomize_enabled_apps"
        private const val KEY_RUN_INDEX = "run_index"

        /** Stable across devices/builds: the same seed, scope and run index choose the same entry. */
        fun stableIndex(seed: String, scope: String, runIndex: Int, size: Int): Int {
            require(size > 0) { "Daftar kandidat kosong" }
            val input = "$seed|$scope|$runIndex".toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(input)
            var value = 0L
            for (index in 0 until 8) value = (value shl 8) or (digest[index].toLong() and 0xffL)
            return ((value ushr 1) % size.toLong()).toInt()
        }

        /** Returns null for an empty candidate set instead of crashing a scheduled worker. */
        fun stableIndexOrNull(seed: String, scope: String, runIndex: Int, size: Int): Int? =
            if (size <= 0) null else stableIndex(seed, scope, runIndex, size)
    }
}

object IdentityRandomizationEngine {
    fun runNow(
        context: Context,
        settings: IdentityRandomizationSettings
    ): IdentityRandomizationResult {
        val app = context.applicationContext
        val store = IdentityRandomizationStore(app)
        val saved = store.save(settings)
        val runIndex = store.nextRunIndex()
        val engine = IdentityEngine(app)
        val candidates = engine.profilesForPlatform(saved.platform)
        val chosen = IdentityRandomizationStore.stableIndexOrNull(
            saved.seed, "browser:" + saved.platform.name, runIndex, candidates.size
        )?.let(candidates::get)
        if (chosen != null) engine.select(chosen.id)
        val appCount = if (saved.randomizeEnabledApps) {
            PerAppIdentityStore.randomizeEnabledCompatibilityProfiles(app, saved.seed, runIndex)
        } else 0
        return IdentityRandomizationResult(runIndex, chosen?.model, appCount)
    }
}

/** WorkManager runs while Mora is not in the foreground; Android may defer periodic work for battery. */
class IdentityRandomizationWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val store = IdentityRandomizationStore(applicationContext)
        val settings = store.load()
        if (!settings.enabled) return Result.success()
        IdentityRandomizationEngine.runNow(applicationContext, settings)
        Result.success()
    }.getOrElse { Result.retry() }
}

object IdentityRandomizationScheduler {
    private const val WORK_NAME = "mora-identity-randomization"

    fun schedule(context: Context, settings: IdentityRandomizationSettings) {
        val saved = IdentityRandomizationStore(context).save(settings.copy(enabled = true))
        val request = PeriodicWorkRequestBuilder<IdentityRandomizationWorker>(
            saved.intervalHours.toLong(),
            TimeUnit.HOURS
        ).addTag(WORK_NAME).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    fun sync(context: Context, settings: IdentityRandomizationSettings) {
        if (settings.enabled) schedule(context, settings) else cancel(context)
    }
}
