package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HotspotDao {

    // Connected Devices
    @Query("SELECT * FROM connected_devices ORDER BY isOnline DESC, lastSeenEpochMs DESC")
    fun observeAllDevices(): Flow<List<ConnectedDeviceEntity>>

    @Query("SELECT * FROM connected_devices")
    suspend fun getAllDevicesOnce(): List<ConnectedDeviceEntity>

    @Query("SELECT * FROM connected_devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDeviceById(deviceId: String): ConnectedDeviceEntity?

    @Query("SELECT * FROM connected_devices WHERE ipAddress = :ipAddress LIMIT 1")
    suspend fun getDeviceByIp(ipAddress: String): ConnectedDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(device: ConnectedDeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevices(devices: List<ConnectedDeviceEntity>)

    @Update
    suspend fun updateDevice(device: ConnectedDeviceEntity)

    @Query("UPDATE connected_devices SET isOnline = 0, currentDownloadSpeedBps = 0, currentUploadSpeedBps = 0 WHERE deviceId NOT IN (:onlineIds)")
    suspend fun markDevicesOfflineExcept(onlineIds: List<String>)

    @Query("UPDATE connected_devices SET isOnline = 0, currentDownloadSpeedBps = 0, currentUploadSpeedBps = 0")
    suspend fun markAllDevicesOffline()

    @Query("DELETE FROM connected_devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)

    // Device Profiles
    @Query("SELECT * FROM device_profiles ORDER BY name ASC")
    fun observeProfiles(): Flow<List<DeviceProfileEntity>>

    @Query("SELECT * FROM device_profiles")
    suspend fun getProfilesOnce(): List<DeviceProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: DeviceProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(profiles: List<DeviceProfileEntity>)

    // Automation Rules
    @Query("SELECT * FROM automation_rules ORDER BY ruleId ASC")
    fun observeAutomationRules(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules")
    suspend fun getAutomationRulesOnce(): List<AutomationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationRule(rule: AutomationRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutomationRules(rules: List<AutomationRuleEntity>)

    @Query("DELETE FROM automation_rules WHERE ruleId = :ruleId")
    suspend fun deleteAutomationRule(ruleId: Int)

    // Traffic Samples
    @Query("SELECT * FROM traffic_samples WHERE deviceId = 'GLOBAL_HOTSPOT' ORDER BY timestampEpochMs DESC LIMIT 60")
    fun observeRecentGlobalSamples(): Flow<List<TrafficSampleEntity>>

    @Query("SELECT * FROM traffic_samples ORDER BY timestampEpochMs DESC LIMIT 500")
    fun observeAllTrafficSamples(): Flow<List<TrafficSampleEntity>>

    @Query("SELECT * FROM traffic_samples ORDER BY timestampEpochMs DESC")
    suspend fun getAllTrafficSamplesOnce(): List<TrafficSampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrafficSample(sample: TrafficSampleEntity)

    @Query("DELETE FROM traffic_samples WHERE timestampEpochMs < :cutoffEpochMs")
    suspend fun pruneOldTrafficSamples(cutoffEpochMs: Long)

    // Action Audit Logs
    @Query("SELECT * FROM action_logs ORDER BY timestampEpochMs DESC LIMIT 150")
    fun observeActionLogs(): Flow<List<ActionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(log: ActionLogEntity)

    @Query("DELETE FROM action_logs")
    suspend fun clearActionLogs()
}
