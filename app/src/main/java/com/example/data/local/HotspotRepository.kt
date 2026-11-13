package com.example.data.local

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HotspotRepository(
    private val dao: HotspotDao,
    val preferences: HotspotPreferences
) {
    val allDevices: Flow<List<ConnectedDeviceEntity>> = dao.observeAllDevices()
    val allProfiles: Flow<List<DeviceProfileEntity>> = dao.observeProfiles()
    val automationRules: Flow<List<AutomationRuleEntity>> = dao.observeAutomationRules()
    val recentGlobalSamples: Flow<List<TrafficSampleEntity>> = dao.observeRecentGlobalSamples()
    val allTrafficSamples: Flow<List<TrafficSampleEntity>> = dao.observeAllTrafficSamples()
    val actionLogs: Flow<List<ActionLogEntity>> = dao.observeActionLogs()

    suspend fun ensureSeeded() {
        if (dao.getProfilesOnce().isEmpty()) {
            dao.upsertProfiles(HotspotDatabase.defaultProfiles())
        }
        if (dao.getAutomationRulesOnce().isEmpty()) {
            dao.upsertAutomationRules(HotspotDatabase.defaultAutomationRules())
        }
    }

    suspend fun getAllDevicesOnce(): List<ConnectedDeviceEntity> = dao.getAllDevicesOnce()
    suspend fun getDeviceById(id: String): ConnectedDeviceEntity? = dao.getDeviceById(id)
    suspend fun getDeviceByIp(ip: String): ConnectedDeviceEntity? = dao.getDeviceByIp(ip)
    suspend fun upsertDevice(device: ConnectedDeviceEntity) = dao.upsertDevice(device)
    suspend fun upsertDevices(devices: List<ConnectedDeviceEntity>) = dao.upsertDevices(devices)
    suspend fun deleteDevice(deviceId: String) = dao.deleteDevice(deviceId)

    suspend fun markDevicesOfflineExcept(onlineIds: List<String>) {
        if (onlineIds.isEmpty()) {
            dao.markAllDevicesOffline()
        } else {
            dao.markDevicesOfflineExcept(onlineIds)
        }
    }

    suspend fun getProfilesOnce(): List<DeviceProfileEntity> = dao.getProfilesOnce()
    suspend fun upsertProfile(profile: DeviceProfileEntity) = dao.upsertProfile(profile)

    suspend fun getAutomationRulesOnce(): List<AutomationRuleEntity> = dao.getAutomationRulesOnce()
    suspend fun upsertAutomationRule(rule: AutomationRuleEntity) = dao.upsertAutomationRule(rule)
    suspend fun deleteAutomationRule(ruleId: Int) = dao.deleteAutomationRule(ruleId)

    suspend fun recordTrafficSample(
        deviceId: String,
        deviceName: String,
        upstreamSource: String,
        downloadSpeedBps: Long,
        uploadSpeedBps: Long,
        deltaDownloadBytes: Long,
        deltaUploadBytes: Long
    ) {
        val now = System.currentTimeMillis()
        val dayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        dao.insertTrafficSample(
            TrafficSampleEntity(
                timestampEpochMs = now,
                dayBucket = dayStr,
                deviceId = deviceId,
                deviceName = deviceName,
                upstreamSource = upstreamSource,
                downloadSpeedBps = downloadSpeedBps,
                uploadSpeedBps = uploadSpeedBps,
                deltaDownloadBytes = deltaDownloadBytes,
                deltaUploadBytes = deltaUploadBytes
            )
        )
    }

    suspend fun logAction(
        category: String,
        title: String,
        detail: String,
        deviceId: String = "",
        adapter: String = "UNROOTED_GATEWAY",
        verified: Boolean = true
    ) {
        dao.insertActionLog(
            ActionLogEntity(
                actionCategory = category,
                title = title,
                detail = detail,
                targetDeviceId = deviceId,
                enforcementAdapter = adapter,
                verifiedEnforcement = verified
            )
        )
    }

    suspend fun clearLogs() = dao.clearActionLogs()

    suspend fun exportAnalyticsCsv(): String {
        val devices = dao.getAllDevicesOnce()
        val samples = dao.getAllTrafficSamplesOnce()
        val sb = StringBuilder()
        sb.appendLine("Section,Device ID,Device Name,IP Address,MAC Address,Category,Status,Download Bytes,Upload Bytes,Total Bytes,Quota Bytes,Download Limit KBps,Upload Limit KBps,Priority")
        for (d in devices) {
            sb.appendLine(
                "DEVICE_SUMMARY,\"${d.deviceId}\",\"${d.customName.replace("\"", "\"\"")}\",\"${d.ipAddress}\",\"${d.macAddress}\",${d.category},${d.accessState},${d.downloadedBytes},${d.uploadedBytes},${d.totalBytes},${d.allocatedCombinedBytes},${d.downloadLimitKbps},${d.uploadLimitKbps},${d.priorityLevel}"
            )
        }
        sb.appendLine()
        sb.appendLine("Sample Timestamp,Day Bucket,Device ID,Device Name,Upstream Source,Download Speed Bps,Upload Speed Bps,Delta Download Bytes,Delta Upload Bytes")
        for (s in samples.take(300)) {
            sb.appendLine(
                "${s.timestampEpochMs},${s.dayBucket},\"${s.deviceId}\",\"${s.deviceName.replace("\"", "\"\"")}\",${s.upstreamSource},${s.downloadSpeedBps},${s.uploadSpeedBps},${s.deltaDownloadBytes},${s.deltaUploadBytes}"
            )
        }
        return sb.toString()
    }
}
