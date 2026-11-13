package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

object DeviceCategory {
    const val TRUSTED = "TRUSTED"
    const val UNKNOWN = "UNKNOWN"
    const val BLOCKED = "BLOCKED"
}

object AccessState {
    const val ALLOWED = "ALLOWED"
    const val BLOCKED_PERMANENT = "BLOCKED_PERMANENT"
    const val BLOCKED_TEMPORARY = "BLOCKED_TEMPORARY"
    const val PAUSED = "PAUSED"
    const val QUOTA_EXHAUSTED = "QUOTA_EXHAUSTED"
    const val SCHEDULE_BLOCKED = "SCHEDULE_BLOCKED"
}

object PriorityLevel {
    const val HIGH = "HIGH"
    const val NORMAL = "NORMAL"
    const val LOW = "LOW"
}

object QuotaPeriod {
    const val UNLIMITED = "UNLIMITED"
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val CUSTOM = "CUSTOM"
}

@Entity(tableName = "connected_devices")
data class ConnectedDeviceEntity(
    @PrimaryKey val deviceId: String, // Stable key (MAC if non-randomized or IP + Hostname fingerprint)
    val customName: String,
    val detectedHostname: String,
    val ipAddress: String,
    val ipv6Address: String = "",
    val macAddress: String = "UNAVAILABLE (OS PRIVACY)",
    val vendorName: String = "Android / Wi-Fi Client",
    val interfaceName: String = "ap0",
    val category: String = DeviceCategory.UNKNOWN, // TRUSTED, UNKNOWN, BLOCKED
    val isOnline: Boolean = true,
    val firstSeenEpochMs: Long = System.currentTimeMillis(),
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val connectedSinceEpochMs: Long = System.currentTimeMillis(),
    val accumulatedDurationSec: Long = 0L,
    val currentDownloadSpeedBps: Long = 0L,
    val currentUploadSpeedBps: Long = 0L,
    val downloadedBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val profileId: String = "profile_guest",
    val accessState: String = AccessState.ALLOWED,
    val tempBlockUntilEpochMs: Long = 0L,
    val scheduledBlockStartHour: Int = -1,
    val scheduledBlockEndHour: Int = -1,
    val downloadLimitKbps: Int = 0, // 0 = Unlimited
    val uploadLimitKbps: Int = 0,   // 0 = Unlimited
    val priorityLevel: String = PriorityLevel.NORMAL,
    val quotaPeriod: String = QuotaPeriod.UNLIMITED,
    val allocatedDownloadBytes: Long = 0L, // 0 = Unlimited
    val allocatedUploadBytes: Long = 0L,   // 0 = Unlimited
    val allocatedCombinedBytes: Long = 0L, // 0 = Unlimited
    val quotaBaselineBytes: Long = 0L,     // Baseline total bytes when quota was reset/allocated
    val quotaExpiryEpochMs: Long = 0L,     // 0 = No expiry
    val autoBlockOnQuota: Boolean = true,
    val enforcementStatusLabel: String = "Unrooted Gateway Ready"
) {
    val totalBytes: Long
        get() = downloadedBytes + uploadedBytes

    val quotaUsedBytes: Long
        get() = (totalBytes - quotaBaselineBytes).coerceAtLeast(0L)

    val quotaRemainingBytes: Long
        get() = if (allocatedCombinedBytes <= 0L) Long.MAX_VALUE
        else (allocatedCombinedBytes - quotaUsedBytes).coerceAtLeast(0L)

    val isBlockedOrPaused: Boolean
        get() = accessState != AccessState.ALLOWED
}

@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey val profileId: String,
    val name: String,
    val description: String,
    val defaultDownloadLimitKbps: Int, // 0 = Unlimited
    val defaultUploadLimitKbps: Int,   // 0 = Unlimited
    val defaultQuotaBytes: Long,       // 0 = Unlimited
    val quotaPeriod: String,           // DAILY, WEEKLY, MONTHLY, CUSTOM, UNLIMITED
    val allowInternet: Boolean,
    val scheduleStartHour: Int = -1,   // -1 = No schedule restriction
    val scheduleEndHour: Int = -1,
    val priorityLevel: String = PriorityLevel.NORMAL,
    val defaultExpiryHours: Int = 0    // 0 = No expiry
)

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true) val ruleId: Int = 0,
    val title: String,
    val description: String,
    val triggerType: String, // UNKNOWN_CONNECT, RETURNING_DEVICE, QUOTA_REACHED, SCHEDULE_WINDOW, DATA_ADDED, CLIENT_DISCONNECT
    val targetCategory: String, // ALL, UNKNOWN, TRUSTED, PROFILE
    val actionType: String, // BLOCK_INTERNET, APPLY_PROFILE, PAUSE_ALL, ALLOW_INTERNET, NOTIFY_ONLY
    val scheduleStartHour: Int = 23,
    val scheduleEndHour: Int = 6,
    val daysOfWeekCsv: String = "1,2,3,4,5,6,7",
    val targetProfileId: String = "profile_guest",
    val isEnabled: Boolean = true,
    val lastTriggeredEpochMs: Long = 0L,
    val triggerCount: Int = 0
)

@Entity(tableName = "traffic_samples")
data class TrafficSampleEntity(
    @PrimaryKey(autoGenerate = true) val sampleId: Long = 0L,
    val timestampEpochMs: Long,
    val dayBucket: String, // YYYY-MM-DD
    val deviceId: String,  // "GLOBAL_HOTSPOT" or specific deviceId
    val deviceName: String,
    val upstreamSource: String, // MOBILE_DATA or WIFI_SHARING
    val downloadSpeedBps: Long,
    val uploadSpeedBps: Long,
    val deltaDownloadBytes: Long,
    val deltaUploadBytes: Long
)

@Entity(tableName = "action_logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0L,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val actionCategory: String, // BLOCK, UNBLOCK, SPEED_LIMIT, QUOTA, AUTOMATION, DISCOVERY, SYSTEM
    val title: String,
    val detail: String,
    val targetDeviceId: String = "",
    val enforcementAdapter: String = "UNROOTED_GATEWAY",
    val verifiedEnforcement: Boolean = true
)
