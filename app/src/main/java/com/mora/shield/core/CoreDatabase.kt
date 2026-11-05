package com.mora.shield.core

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "registered_apps")
data class RegisteredAppEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val versionName: String,
    val apiVersion: Int,
    val connectionState: String,
    val capabilities: String,
    val certificateSha256: String,
    val lastSeenAtMs: Long,
    val updatedAtMs: Long
)

@Entity(
    tableName = "activity_events",
    indices = [Index(value = ["occurredAtMs"]), Index(value = ["sourcePackage"]), Index(value = ["affectedPackage"])]
)
data class ActivityEventEntity(
    @PrimaryKey val eventId: String,
    val sourcePackage: String,
    val eventType: String,
    val title: String,
    val affectedPackage: String,
    val detail: String,
    val severity: String,
    val occurredAtMs: Long,
    val receivedAtMs: Long
)

@Entity(tableName = "app_policies")
data class AppPolicyEntity(
    @PrimaryKey val packageName: String,
    val enabled: Boolean = true,
    val monitoringEnabled: Boolean = true,
    val commandAccessEnabled: Boolean = false,
    /** Newline-separated capability IDs. Empty means deny all commands. */
    val allowedCommands: String = "",
    /** Newline-separated IDs; '*' means every allowed command needs approval. */
    val approvalRequiredCommands: String = "*",
    val networkMode: String = "inherit",
    val backgroundMode: String = "normal",
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_audit", indices = [Index(value = ["createdAtMs"]), Index(value = ["packageName"])])
data class CommandAuditEntity(
    @PrimaryKey val requestId: String,
    val packageName: String,
    val commandId: String,
    val status: String,
    val resultSummary: String,
    val createdAtMs: Long,
    val completedAtMs: Long
)

@Dao
interface CoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: RegisteredAppEntity)

    @Query("SELECT * FROM registered_apps ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun getApps(): List<RegisteredAppEntity>

    @Query("SELECT * FROM registered_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getApp(packageName: String): RegisteredAppEntity?

    @Query("SELECT COUNT(*) FROM registered_apps")
    suspend fun countApps(): Int

    @Query("SELECT COUNT(*) FROM registered_apps WHERE connectionState = 'CONNECTED'")
    suspend fun countConnectedApps(): Int

    @Query("UPDATE registered_apps SET connectionState = :state, lastSeenAtMs = :seenAt, updatedAtMs = :seenAt WHERE packageName = :packageName")
    suspend fun updateConnection(packageName: String, state: String, seenAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPolicy(policy: AppPolicyEntity)

    @Query("SELECT * FROM app_policies WHERE packageName = :packageName LIMIT 1")
    suspend fun getPolicy(packageName: String): AppPolicyEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: ActivityEventEntity): Long

    @Query("SELECT * FROM activity_events ORDER BY occurredAtMs DESC LIMIT :limit")
    suspend fun recentEvents(limit: Int): List<ActivityEventEntity>

    @Query("SELECT * FROM activity_events ORDER BY occurredAtMs DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int): Flow<List<ActivityEventEntity>>

    @Query("SELECT COUNT(*) FROM activity_events")
    suspend fun countEvents(): Int

    @Query("DELETE FROM activity_events WHERE eventId NOT IN (SELECT eventId FROM activity_events ORDER BY occurredAtMs DESC LIMIT :keep)")
    suspend fun trimEvents(keep: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommandAudit(audit: CommandAuditEntity)

    @Query("SELECT COUNT(*) FROM command_audit WHERE status IN ('PENDING_APPROVAL', 'RUNNING')")
    suspend fun countPendingCommands(): Int

    @Query("SELECT * FROM command_audit ORDER BY createdAtMs DESC LIMIT :limit")
    suspend fun recentCommandAudit(limit: Int): List<CommandAuditEntity>
}

@Database(
    entities = [
        RegisteredAppEntity::class,
        ActivityEventEntity::class,
        AppPolicyEntity::class,
        CommandAuditEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MoraCoreDatabase : RoomDatabase() {
    abstract fun coreDao(): CoreDao
}
