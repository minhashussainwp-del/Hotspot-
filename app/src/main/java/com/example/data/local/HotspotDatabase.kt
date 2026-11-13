package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConnectedDeviceEntity::class,
        DeviceProfileEntity::class,
        AutomationRuleEntity::class,
        TrafficSampleEntity::class,
        ActionLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HotspotDatabase : RoomDatabase() {
    abstract fun hotspotDao(): HotspotDao

    companion object {
        @Volatile
        private var INSTANCE: HotspotDatabase? = null

        fun getInstance(context: Context): HotspotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HotspotDatabase::class.java,
                    "hotspot_control_pro.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun defaultProfiles(): List<DeviceProfileEntity> = listOf(
            DeviceProfileEntity(
                profileId = "profile_family",
                name = "Family",
                description = "High priority, 5 MB/s download limit, 5 GB monthly quota",
                defaultDownloadLimitKbps = 5000,
                defaultUploadLimitKbps = 2000,
                defaultQuotaBytes = 5L * 1024L * 1024L * 1024L,
                quotaPeriod = QuotaPeriod.MONTHLY,
                allowInternet = true,
                priorityLevel = PriorityLevel.HIGH
            ),
            DeviceProfileEntity(
                profileId = "profile_friends",
                name = "Friends",
                description = "Normal priority, 2 MB/s download limit, 1 GB daily quota",
                defaultDownloadLimitKbps = 2000,
                defaultUploadLimitKbps = 1000,
                defaultQuotaBytes = 1024L * 1024L * 1024L,
                quotaPeriod = QuotaPeriod.DAILY,
                allowInternet = true,
                priorityLevel = PriorityLevel.NORMAL
            ),
            DeviceProfileEntity(
                profileId = "profile_guest",
                name = "Guest",
                description = "Low priority, 500 KB/s download limit, 500 MB daily quota, 24h expiry",
                defaultDownloadLimitKbps = 500,
                defaultUploadLimitKbps = 250,
                defaultQuotaBytes = 500L * 1024L * 1024L,
                quotaPeriod = QuotaPeriod.DAILY,
                allowInternet = true,
                priorityLevel = PriorityLevel.LOW,
                defaultExpiryHours = 24
            ),
            DeviceProfileEntity(
                profileId = "profile_work",
                name = "Work",
                description = "High priority, Unlimited speed, 10 GB weekly quota",
                defaultDownloadLimitKbps = 0,
                defaultUploadLimitKbps = 0,
                defaultQuotaBytes = 10L * 1024L * 1024L * 1024L,
                quotaPeriod = QuotaPeriod.WEEKLY,
                allowInternet = true,
                priorityLevel = PriorityLevel.HIGH
            ),
            DeviceProfileEntity(
                profileId = "profile_priority",
                name = "Priority",
                description = "Maximum bandwidth priority, Unlimited speed & data allocation",
                defaultDownloadLimitKbps = 0,
                defaultUploadLimitKbps = 0,
                defaultQuotaBytes = 0L,
                quotaPeriod = QuotaPeriod.UNLIMITED,
                allowInternet = true,
                priorityLevel = PriorityLevel.HIGH
            ),
            DeviceProfileEntity(
                profileId = "profile_restricted",
                name = "Restricted",
                description = "Strict 100 KB/s speed cap, 100 MB daily cap, Night hours blocked (23:00-06:00)",
                defaultDownloadLimitKbps = 100,
                defaultUploadLimitKbps = 100,
                defaultQuotaBytes = 100L * 1024L * 1024L,
                quotaPeriod = QuotaPeriod.DAILY,
                allowInternet = true,
                scheduleStartHour = 23,
                scheduleEndHour = 6,
                priorityLevel = PriorityLevel.LOW
            )
        )

        fun defaultAutomationRules(): List<AutomationRuleEntity> = listOf(
            AutomationRuleEntity(
                title = "Block Exhausted Quotas Automatically",
                description = "Immediately block client internet access when allocated data reaches zero",
                triggerType = "QUOTA_REACHED",
                targetCategory = "ALL",
                actionType = "BLOCK_INTERNET",
                isEnabled = true
            ),
            AutomationRuleEntity(
                title = "Restore Internet When Data Added",
                description = "Automatically lift quota blocks when the owner allocates additional data",
                triggerType = "DATA_ADDED",
                targetCategory = "ALL",
                actionType = "ALLOW_INTERNET",
                isEnabled = true
            ),
            AutomationRuleEntity(
                title = "Apply Returning Device Speed Profile",
                description = "Automatically re-apply saved speed & quota profile when a known client reconnects",
                triggerType = "RETURNING_DEVICE",
                targetCategory = "ALL",
                actionType = "APPLY_PROFILE",
                isEnabled = true
            ),
            AutomationRuleEntity(
                title = "Auto-Block Unknown New Clients",
                description = "Quarantine unknown MAC/IP clients until manually approved by the owner",
                triggerType = "UNKNOWN_CONNECT",
                targetCategory = "UNKNOWN",
                actionType = "BLOCK_INTERNET",
                isEnabled = false
            ),
            AutomationRuleEntity(
                title = "Scheduled Nightly Pause (23:00 - 06:00)",
                description = "Pause guest & non-trusted hotspot internet access during late-night hours",
                triggerType = "SCHEDULE_WINDOW",
                targetCategory = "UNKNOWN",
                actionType = "PAUSE_ALL",
                scheduleStartHour = 23,
                scheduleEndHour = 6,
                isEnabled = false
            )
        )
    }
}
