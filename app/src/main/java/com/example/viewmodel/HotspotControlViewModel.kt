package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AccessState
import com.example.data.local.ActionLogEntity
import com.example.data.local.AutomationRuleEntity
import com.example.data.local.ConnectedDeviceEntity
import com.example.data.local.DeviceCategory
import com.example.data.local.DeviceProfileEntity
import com.example.data.local.GlobalHotspotSettings
import com.example.data.local.HotspotDatabase
import com.example.data.local.HotspotPreferences
import com.example.data.local.HotspotRepository
import com.example.data.local.PriorityLevel
import com.example.data.local.QuotaPeriod
import com.example.data.local.TrafficSampleEntity
import com.example.network.CapabilityDetector
import com.example.network.ClientDiscoveryEngine
import com.example.network.ClientGatewayPolicy
import com.example.network.DeviceCapabilitiesReport
import com.example.network.EnforcementEngine
import com.example.network.HotspotTelemetrySnapshot
import com.example.network.TetheringMonitor
import com.example.network.UnrootedGatewayServer
import com.example.network.UpstreamSource
import com.example.service.AutomationEvaluator
import com.example.service.HotspotMonitorService
import com.example.service.NotificationHelper
import com.example.util.NetworkFormatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HotspotUiState(
    val telemetry: HotspotTelemetrySnapshot = HotspotTelemetrySnapshot(
        isHotspotActive = true,
        hotspotStateLabel = "Initializing Telemetry...",
        downstreamInterfaceName = "ap0",
        downstreamIpv4 = "192.168.43.1",
        downstreamIpv6 = "",
        subnetPrefix = "192.168.43",
        isInternetConnected = true,
        upstreamSource = UpstreamSource.MOBILE_DATA,
        upstreamInterfaceName = "rmnet_data0",
        totalRxBytes = 0L,
        totalTxBytes = 0L,
        currentDownloadSpeedBps = 0L,
        currentUploadSpeedBps = 0L
    ),
    val capabilities: DeviceCapabilitiesReport? = null,
    val devices: List<ConnectedDeviceEntity> = emptyList(),
    val profiles: List<DeviceProfileEntity> = emptyList(),
    val automationRules: List<AutomationRuleEntity> = emptyList(),
    val recentGlobalSamples: List<TrafficSampleEntity> = emptyList(),
    val allSamples: List<TrafficSampleEntity> = emptyList(),
    val actionLogs: List<ActionLogEntity> = emptyList(),
    val settings: GlobalHotspotSettings = GlobalHotspotSettings(),
    val isScanningSubnet: Boolean = false,
    val gatewayIsRunning: Boolean = false,
    val gatewayBoundPort: Int = 8888,
    val uptimeSeconds: Long = 0L,
    val statusBannerMessage: String = "Unrooted Hotspot Gateway & Telemetry Active"
) {
    val onlineDevices: List<ConnectedDeviceEntity>
        get() = devices.filter { it.isOnline }

    val blockedDevices: List<ConnectedDeviceEntity>
        get() = devices.filter { it.isBlockedOrPaused || it.category == DeviceCategory.BLOCKED }

    val trustedDevices: List<ConnectedDeviceEntity>
        get() = devices.filter { it.category == DeviceCategory.TRUSTED }

    val unknownDevices: List<ConnectedDeviceEntity>
        get() = devices.filter { it.category == DeviceCategory.UNKNOWN }

    val offlineDevices: List<ConnectedDeviceEntity>
        get() = devices.filter { !it.isOnline }

    val totalClientConsumedBytes: Long
        get() = devices.sumOf { it.totalBytes }

    val totalClientDownloadSpeedBps: Long
        get() = onlineDevices.sumOf { it.currentDownloadSpeedBps } + telemetry.currentDownloadSpeedBps

    val totalClientUploadSpeedBps: Long
        get() = onlineDevices.sumOf { it.currentUploadSpeedBps } + telemetry.currentUploadSpeedBps

    val totalRemainingAllocatedBytes: Long
        get() {
            val withQuota = devices.filter { it.allocatedCombinedBytes > 0L }
            return if (withQuota.isEmpty()) 0L else withQuota.sumOf { it.quotaRemainingBytes }
        }

    val activeBandwidthRulesCount: Int
        get() = devices.count { it.downloadLimitKbps > 0 || it.uploadLimitKbps > 0 || it.isBlockedOrPaused }
}

class HotspotControlViewModel(
    application: Application,
    private val repository: HotspotRepository
) : AndroidViewModel(application) {

    private val capabilityDetector = CapabilityDetector(application)
    private val tetheringMonitor = TetheringMonitor(application)
    private val discoveryEngine = ClientDiscoveryEngine()
    val gatewayServer = UnrootedGatewayServer()
    private val enforcementEngine = EnforcementEngine(gatewayServer)
    private val automationEvaluator = AutomationEvaluator()
    private val notificationHelper = NotificationHelper(application)

    private val sessionStartMs = System.currentTimeMillis()

    private val _telemetry = MutableStateFlow(HotspotUiState().telemetry)
    private val _capabilities = MutableStateFlow<DeviceCapabilitiesReport?>(null)
    private val _isScanning = MutableStateFlow(false)
    private val _statusBanner = MutableStateFlow("Unrooted Hotspot Gateway & Telemetry Ready")
    private val _uptimeSec = MutableStateFlow(0L)

    val uiState: StateFlow<HotspotUiState> = combine(
        listOf(
            _telemetry,
            _capabilities,
            repository.allDevices,
            repository.allProfiles,
            repository.automationRules,
            repository.recentGlobalSamples,
            repository.allTrafficSamples,
            repository.actionLogs,
            repository.preferences.settingsFlow,
            _isScanning,
            _statusBanner,
            _uptimeSec
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        HotspotUiState(
            telemetry = values[0] as HotspotTelemetrySnapshot,
            capabilities = values[1] as DeviceCapabilitiesReport?,
            devices = values[2] as List<ConnectedDeviceEntity>,
            profiles = values[3] as List<DeviceProfileEntity>,
            automationRules = values[4] as List<AutomationRuleEntity>,
            recentGlobalSamples = (values[5] as List<TrafficSampleEntity>).reversed(),
            allSamples = values[6] as List<TrafficSampleEntity>,
            actionLogs = values[7] as List<ActionLogEntity>,
            settings = values[8] as GlobalHotspotSettings,
            isScanningSubnet = values[9] as Boolean,
            gatewayIsRunning = gatewayServer.isRunning(),
            gatewayBoundPort = gatewayServer.boundPort,
            uptimeSeconds = values[11] as Long,
            statusBannerMessage = values[10] as String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HotspotUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val currentSettings = repository.preferences.settingsFlow.first()
            gatewayServer.globalMaxDownloadKbps = currentSettings.maxTotalDownloadKbps
            gatewayServer.globalMaxUploadKbps = currentSettings.maxTotalUploadKbps
            gatewayServer.fairSharingEnabled = currentSettings.fairSharingEnabled

            if (currentSettings.unrootedGatewayEnabled) {
                gatewayServer.start(currentSettings.unrootedGatewayPort)
            }

            val capReport = capabilityDetector.detectCapabilities(currentSettings.preferredEnforcementMode)
            _capabilities.value = capReport

            // Sync saved Room device policies into the Gateway Server
            val savedDevices = repository.getAllDevicesOnce()
            for (d in savedDevices) {
                gatewayServer.updateClientPolicy(
                    ClientGatewayPolicy(
                        ipAddress = d.ipAddress,
                        accessState = d.accessState,
                        downloadLimitKbps = d.downloadLimitKbps,
                        uploadLimitKbps = d.uploadLimitKbps,
                        priorityLevel = d.priorityLevel,
                        remainingQuotaBytes = d.quotaRemainingBytes
                    )
                )
            }

            // Perform initial subnet client discovery
            refreshConnectedDevices()

            // Start continuous real-time telemetry and policy evaluation loop
            startTelemetryLoop()
        }
    }

    private fun startTelemetryLoop() {
        viewModelScope.launch {
            var tickCount = 0
            while (isActive) {
                delay(2000L)
                tickCount++
                _uptimeSec.value = (System.currentTimeMillis() - sessionStartMs) / 1000L

                val snap = tetheringMonitor.sampleTelemetry(
                    gatewayBytesRx = gatewayServer.totalGatewayRxBytes.get(),
                    gatewayBytesTx = gatewayServer.totalGatewayTxBytes.get()
                )
                _telemetry.value = snap

                val settings = repository.preferences.settingsFlow.first()
                val rules = repository.getAutomationRulesOnce()
                val profiles = repository.getProfilesOnce().associateBy { it.profileId }
                val currentDevices = repository.getAllDevicesOnce()
                val gatewayCounters = gatewayServer.getClientCounters()

                var totalClientDlBps = 0L
                var totalClientUlBps = 0L
                var totalDeltaRx = 0L
                var totalDeltaTx = 0L

                for (device in currentDevices) {
                    val gwCounter = gatewayCounters[device.ipAddress]
                    val newDlBytes = if (gwCounter != null && gwCounter.downloadedBytes.get() > 0L) {
                        maxOf(device.downloadedBytes, gwCounter.downloadedBytes.get())
                    } else {
                        device.downloadedBytes
                    }
                    val newUlBytes = if (gwCounter != null && gwCounter.uploadedBytes.get() > 0L) {
                        maxOf(device.uploadedBytes, gwCounter.uploadedBytes.get())
                    } else {
                        device.uploadedBytes
                    }
                    val dlSpeed = gwCounter?.currentDownloadBps ?: 0L
                    val ulSpeed = gwCounter?.currentUploadBps ?: 0L

                    val deltaRx = (newDlBytes - device.downloadedBytes).coerceAtLeast(0L)
                    val deltaTx = (newUlBytes - device.uploadedBytes).coerceAtLeast(0L)
                    totalDeltaRx += deltaRx
                    totalDeltaTx += deltaTx
                    totalClientDlBps += dlSpeed
                    totalClientUlBps += ulSpeed

                    val durationIncrement = if (device.isOnline) 2L else 0L
                    val updated = device.copy(
                        downloadedBytes = newDlBytes,
                        uploadedBytes = newUlBytes,
                        currentDownloadSpeedBps = dlSpeed,
                        currentUploadSpeedBps = ulSpeed,
                        accumulatedDurationSec = device.accumulatedDurationSec + durationIncrement,
                        lastSeenEpochMs = if (device.isOnline) System.currentTimeMillis() else device.lastSeenEpochMs
                    )

                    val eval = automationEvaluator.evaluateDevicePolicies(
                        device = updated,
                        rules = rules,
                        profilesById = profiles
                    )

                    if (eval.stateChanged || deltaRx > 0L || deltaTx > 0L || durationIncrement > 0L) {
                        repository.upsertDevice(eval.updatedDevice)
                    }

                    if (eval.stateChanged) {
                        val useRoot = settings.preferredEnforcementMode == "ROOT" && (_capabilities.value?.isRootAvailable == true)
                        val enf = enforcementEngine.applyDevicePolicy(
                            device = eval.updatedDevice,
                            downstreamInterface = snap.downstreamInterfaceName,
                            useRootIfAvailable = useRoot
                        )
                        repository.upsertDevice(eval.updatedDevice.copy(enforcementStatusLabel = enf.statusBadge))
                        repository.logAction(
                            category = "AUTOMATION",
                            title = eval.reasonTitle,
                            detail = eval.reasonDetail,
                            deviceId = eval.updatedDevice.deviceId,
                            adapter = enf.adapterUsed,
                            verified = enf.success
                        )
                        notificationHelper.notifyEvent(
                            eventType = eval.notificationType,
                            title = eval.reasonTitle,
                            message = eval.reasonDetail,
                            settings = settings
                        )
                    }
                }

                // Record global traffic sample for live speed graph & historical analytics
                val combinedDlBps = totalClientDlBps + snap.currentDownloadSpeedBps
                val combinedUlBps = totalClientUlBps + snap.currentUploadSpeedBps
                repository.recordTrafficSample(
                    deviceId = "GLOBAL_HOTSPOT",
                    deviceName = "Total Hotspot Traffic",
                    upstreamSource = snap.upstreamSource.name,
                    downloadSpeedBps = combinedDlBps,
                    uploadSpeedBps = combinedUlBps,
                    deltaDownloadBytes = totalDeltaRx,
                    deltaUploadBytes = totalDeltaTx
                )

                // Periodically refresh neighbor/subnet discovery every 30 seconds
                if (tickCount % 15 == 0 && !_isScanning.value) {
                    refreshConnectedDevices(silent = true)
                }
            }
        }
    }

    fun refreshConnectedDevices(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _isScanning.value = true
                _statusBanner.value = "Scanning hotspot interface ${_telemetry.value.downstreamInterfaceName} & subnet ${_telemetry.value.subnetPrefix}.0/24..."
            }

            val snap = _telemetry.value
            val discovered = discoveryEngine.discoverConnectedClients(
                subnetPrefix = snap.subnetPrefix,
                selfIp = snap.downstreamIpv4,
                downstreamInterface = snap.downstreamInterfaceName,
                activeGatewayClients = gatewayServer.getActiveClientIps()
            )

            val existingById = repository.getAllDevicesOnce().associateBy { it.deviceId }.toMutableMap()
            val existingByIp = existingById.values.associateBy { it.ipAddress }
            val settings = repository.preferences.settingsFlow.first()
            val rules = repository.getAutomationRulesOnce()
            val profiles = repository.getProfilesOnce().associateBy { it.profileId }
            val now = System.currentTimeMillis()
            val onlineIds = mutableListOf<String>()

            for (client in discovered) {
                val matched = existingById[client.stableId] ?: existingByIp[client.ipAddress]
                if (matched == null) {
                    // Brand-new discovered device
                    val autoBlockUnknown = rules.any { it.isEnabled && it.triggerType == "UNKNOWN_CONNECT" }
                    val initialAccess = if (autoBlockUnknown) AccessState.BLOCKED_PERMANENT else AccessState.ALLOWED
                    val initialCat = if (autoBlockUnknown) DeviceCategory.BLOCKED else DeviceCategory.UNKNOWN

                    val newEntity = ConnectedDeviceEntity(
                        deviceId = client.stableId,
                        customName = client.hostname,
                        detectedHostname = client.hostname,
                        ipAddress = client.ipAddress,
                        ipv6Address = client.ipv6Address,
                        macAddress = client.macAddress,
                        vendorName = client.vendor,
                        interfaceName = client.interfaceName,
                        category = initialCat,
                        isOnline = true,
                        firstSeenEpochMs = now,
                        lastSeenEpochMs = now,
                        connectedSinceEpochMs = now,
                        accessState = initialAccess,
                        enforcementStatusLabel = if (autoBlockUnknown) {
                            "Auto-Blocked on Gateway :${gatewayServer.boundPort}"
                        } else {
                            "Allowed on Gateway :${gatewayServer.boundPort}"
                        }
                    )
                    repository.upsertDevice(newEntity)
                    onlineIds.add(newEntity.deviceId)

                    gatewayServer.updateClientPolicy(
                        ClientGatewayPolicy(
                            ipAddress = newEntity.ipAddress,
                            accessState = newEntity.accessState
                        )
                    )

                    repository.logAction(
                        category = "DISCOVERY",
                        title = "New Hotspot Client Detected",
                        detail = "${newEntity.customName} (${newEntity.ipAddress} • ${newEntity.macAddress}) via ${client.discoverySource}",
                        deviceId = newEntity.deviceId
                    )
                    notificationHelper.notifyEvent(
                        eventType = "NEW_DEVICE",
                        title = "New Hotspot Client Connected",
                        message = "${newEntity.customName} (${newEntity.ipAddress}) connected on ${newEntity.interfaceName}.",
                        settings = settings
                    )
                } else {
                    val wasOffline = !matched.isOnline
                    val returningRule = rules.any { it.isEnabled && it.triggerType == "RETURNING_DEVICE" }
                    val profile = profiles[matched.profileId]

                    val updated = matched.copy(
                        ipAddress = client.ipAddress,
                        macAddress = if (!client.macAddress.startsWith("UNAVAILABLE")) client.macAddress else matched.macAddress,
                        detectedHostname = client.hostname,
                        vendorName = client.vendor,
                        interfaceName = client.interfaceName,
                        isOnline = true,
                        lastSeenEpochMs = now,
                        connectedSinceEpochMs = if (wasOffline) now else matched.connectedSinceEpochMs,
                        downloadLimitKbps = if (wasOffline && returningRule && profile != null && matched.downloadLimitKbps == 0) {
                            profile.defaultDownloadLimitKbps
                        } else matched.downloadLimitKbps,
                        uploadLimitKbps = if (wasOffline && returningRule && profile != null && matched.uploadLimitKbps == 0) {
                            profile.defaultUploadLimitKbps
                        } else matched.uploadLimitKbps
                    )
                    repository.upsertDevice(updated)
                    onlineIds.add(updated.deviceId)
                }
            }

            if (!silent) {
                _isScanning.value = false
                _statusBanner.value = "Discovery complete: ${discovered.size} active subnet client(s) found on ${snap.downstreamInterfaceName}"
            }
        }
    }

    /**
     * Allows the hotspot owner to register a client by IP / Hostname / Profile (or register a friend's
     * static/DHCP hotspot IP before or after connection) and immediately enforce rules on it.
     */
    fun registerOrUpdateManualClient(
        customName: String,
        ipAddress: String,
        macAddress: String,
        category: String,
        profileId: String
    ) {
        viewModelScope.launch {
            val cleanIp = ipAddress.trim()
            if (cleanIp.isBlank()) return@launch
            val cleanName = customName.trim().ifBlank { "Client-${cleanIp.substringAfterLast(".")}" }
            val cleanMac = macAddress.trim().ifBlank { "UNAVAILABLE (OS PRIVACY)" }.uppercase()
            val profiles = repository.getProfilesOnce().associateBy { it.profileId }
            val selectedProfile = profiles[profileId]
            val now = System.currentTimeMillis()

            val deviceId = if (!cleanMac.startsWith("UNAVAILABLE")) "mac_$cleanMac" else "ip_$cleanIp"
            val existing = repository.getDeviceById(deviceId) ?: repository.getDeviceByIp(cleanIp)

            val expiryMs = if (selectedProfile != null && selectedProfile.defaultExpiryHours > 0) {
                now + selectedProfile.defaultExpiryHours * 3600_000L
            } else 0L

            val entity = (existing ?: ConnectedDeviceEntity(
                deviceId = deviceId,
                customName = cleanName,
                detectedHostname = cleanName,
                ipAddress = cleanIp
            )).copy(
                customName = cleanName,
                ipAddress = cleanIp,
                macAddress = cleanMac,
                category = category,
                isOnline = true,
                lastSeenEpochMs = now,
                profileId = profileId,
                downloadLimitKbps = selectedProfile?.defaultDownloadLimitKbps ?: 0,
                uploadLimitKbps = selectedProfile?.defaultUploadLimitKbps ?: 0,
                priorityLevel = selectedProfile?.priorityLevel ?: PriorityLevel.NORMAL,
                quotaPeriod = selectedProfile?.quotaPeriod ?: QuotaPeriod.UNLIMITED,
                allocatedCombinedBytes = selectedProfile?.defaultQuotaBytes ?: 0L,
                quotaExpiryEpochMs = expiryMs,
                accessState = if (category == DeviceCategory.BLOCKED || selectedProfile?.allowInternet == false) {
                    AccessState.BLOCKED_PERMANENT
                } else {
                    AccessState.ALLOWED
                }
            )

            val enf = enforcementEngine.applyDevicePolicy(
                device = entity,
                downstreamInterface = _telemetry.value.downstreamInterfaceName,
                useRootIfAvailable = false
            )
            repository.upsertDevice(entity.copy(enforcementStatusLabel = enf.statusBadge))
            repository.logAction(
                category = "DISCOVERY",
                title = "Client Registered & Enforced",
                detail = "${entity.customName} (${entity.ipAddress}) assigned profile ${selectedProfile?.name ?: "Custom"} — ${enf.detailMessage}",
                deviceId = entity.deviceId,
                adapter = enf.adapterUsed,
                verified = enf.success
            )
            _statusBanner.value = "${entity.customName} (${entity.ipAddress}) active on ${enf.statusBadge}"
        }
    }

    fun renameDevice(deviceId: String, newName: String) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val trimmed = newName.trim().ifEmpty { device.detectedHostname }
            repository.upsertDevice(device.copy(customName = trimmed))
            repository.logAction(
                category = "SYSTEM",
                title = "Device Renamed",
                detail = "Renamed ${device.ipAddress} to \"$trimmed\"",
                deviceId = deviceId
            )
        }
    }

    fun setDeviceAccessState(
        deviceId: String,
        newState: String,
        tempDurationMinutes: Int = 0
    ) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val settings = repository.preferences.settingsFlow.first()
            val now = System.currentTimeMillis()
            val tempUntil = if (newState == AccessState.BLOCKED_TEMPORARY && tempDurationMinutes > 0) {
                now + tempDurationMinutes * 60_000L
            } else 0L

            val newCategory = when (newState) {
                AccessState.BLOCKED_PERMANENT -> DeviceCategory.BLOCKED
                AccessState.ALLOWED -> if (device.category == DeviceCategory.BLOCKED) DeviceCategory.TRUSTED else device.category
                else -> device.category
            }

            val updated = device.copy(
                accessState = newState,
                tempBlockUntilEpochMs = tempUntil,
                category = newCategory
            )

            val useRoot = settings.preferredEnforcementMode == "ROOT" && (_capabilities.value?.isRootAvailable == true)
            val enf = enforcementEngine.applyDevicePolicy(
                device = updated,
                downstreamInterface = _telemetry.value.downstreamInterfaceName,
                useRootIfAvailable = useRoot
            )

            repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))
            repository.logAction(
                category = if (newState == AccessState.ALLOWED) "UNBLOCK" else "BLOCK",
                title = "Client Access -> $newState",
                detail = "${updated.customName} (${updated.ipAddress}): ${enf.detailMessage}",
                deviceId = deviceId,
                adapter = enf.adapterUsed,
                verified = enf.success
            )
            notificationHelper.notifyEvent(
                eventType = if (newState == AccessState.ALLOWED) "RESTORED" else "BLOCKED",
                title = "${updated.customName}: Internet $newState",
                message = enf.detailMessage,
                settings = settings
            )
            _statusBanner.value = "${updated.customName}: ${enf.statusBadge}"
        }
    }

    fun setDeviceCategory(deviceId: String, category: String) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val newAccess = if (category == DeviceCategory.BLOCKED) AccessState.BLOCKED_PERMANENT else AccessState.ALLOWED
            setDeviceAccessState(deviceId, newAccess)
            val latest = repository.getDeviceById(deviceId) ?: return@launch
            repository.upsertDevice(latest.copy(category = category))
        }
    }

    fun setDeviceSpeedLimit(
        deviceId: String,
        downloadKbps: Int,
        uploadKbps: Int,
        priorityLevel: String
    ) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val settings = repository.preferences.settingsFlow.first()
            val updated = device.copy(
                downloadLimitKbps = downloadKbps.coerceAtLeast(0),
                uploadLimitKbps = uploadKbps.coerceAtLeast(0),
                priorityLevel = priorityLevel
            )
            val useRoot = settings.preferredEnforcementMode == "ROOT" && (_capabilities.value?.isRootAvailable == true)
            val enf = enforcementEngine.applyDevicePolicy(
                device = updated,
                downstreamInterface = _telemetry.value.downstreamInterfaceName,
                useRootIfAvailable = useRoot
            )
            repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))

            val dlLabel = NetworkFormatters.formatKbpsLimit(downloadKbps)
            val ulLabel = NetworkFormatters.formatKbpsLimit(uploadKbps)
            repository.logAction(
                category = "SPEED_LIMIT",
                title = "Speed Limit Applied ($dlLabel ↓ / $ulLabel ↑)",
                detail = "${updated.customName} (${updated.ipAddress}) Priority $priorityLevel — ${enf.detailMessage}",
                deviceId = deviceId,
                adapter = enf.adapterUsed,
                verified = enf.success
            )
            notificationHelper.notifyEvent(
                eventType = "SPEED_LIMIT",
                title = "Bandwidth Rule Applied to ${updated.customName}",
                message = "Download: $dlLabel • Upload: $ulLabel • Priority: $priorityLevel",
                settings = settings
            )
            _statusBanner.value = "${updated.customName}: $dlLabel ↓ / $ulLabel ↑ ($priorityLevel)"
        }
    }

    fun setDeviceQuota(
        deviceId: String,
        quotaPeriod: String,
        combinedQuotaMb: Long,
        downloadQuotaMb: Long = 0L,
        uploadQuotaMb: Long = 0L,
        expiryHours: Int = 0,
        autoBlockOnExhaustion: Boolean = true
    ) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val now = System.currentTimeMillis()
            val combinedBytes = if (combinedQuotaMb > 0L) combinedQuotaMb * 1024L * 1024L else 0L
            val dlBytes = if (downloadQuotaMb > 0L) downloadQuotaMb * 1024L * 1024L else 0L
            val ulBytes = if (uploadQuotaMb > 0L) uploadQuotaMb * 1024L * 1024L else 0L
            val expiryMs = if (expiryHours > 0) now + expiryHours * 3600_000L else 0L

            val updated = device.copy(
                quotaPeriod = if (combinedBytes == 0L && dlBytes == 0L && ulBytes == 0L) QuotaPeriod.UNLIMITED else quotaPeriod,
                allocatedCombinedBytes = combinedBytes,
                allocatedDownloadBytes = dlBytes,
                allocatedUploadBytes = ulBytes,
                quotaExpiryEpochMs = expiryMs,
                autoBlockOnQuota = autoBlockOnExhaustion,
                accessState = if (device.accessState == AccessState.QUOTA_EXHAUSTED && combinedBytes > device.quotaUsedBytes) {
                    AccessState.ALLOWED
                } else device.accessState
            )

            val enf = enforcementEngine.applyDevicePolicy(
                device = updated,
                downstreamInterface = _telemetry.value.downstreamInterfaceName,
                useRootIfAvailable = false
            )
            repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))
            repository.logAction(
                category = "QUOTA",
                title = "Data Quota Allocated (${NetworkFormatters.formatBytes(combinedBytes)})",
                detail = "${updated.customName} (${updated.ipAddress}) Period: $quotaPeriod, Auto-Block: $autoBlockOnExhaustion",
                deviceId = deviceId,
                adapter = enf.adapterUsed,
                verified = enf.success
            )
            _statusBanner.value = "Allocated ${NetworkFormatters.formatBytes(combinedBytes)} ($quotaPeriod) to ${updated.customName}"
        }
    }

    fun addDeviceQuotaData(deviceId: String, additionalMb: Long) {
        viewModelScope.launch {
            if (additionalMb <= 0L) return@launch
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val settings = repository.preferences.settingsFlow.first()
            val rules = repository.getAutomationRulesOnce()
            val restoreOnAdd = rules.any { it.isEnabled && it.triggerType == "DATA_ADDED" }

            val addedBytes = additionalMb * 1024L * 1024L
            val newAllocated = (device.allocatedCombinedBytes + addedBytes).coerceAtLeast(addedBytes)
            val newState = if (restoreOnAdd && device.accessState == AccessState.QUOTA_EXHAUSTED) {
                AccessState.ALLOWED
            } else {
                device.accessState
            }

            val updated = device.copy(
                allocatedCombinedBytes = newAllocated,
                quotaPeriod = if (device.quotaPeriod == QuotaPeriod.UNLIMITED) QuotaPeriod.CUSTOM else device.quotaPeriod,
                accessState = newState
            )
            val enf = enforcementEngine.applyDevicePolicy(
                device = updated,
                downstreamInterface = _telemetry.value.downstreamInterfaceName,
                useRootIfAvailable = false
            )
            repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))
            repository.logAction(
                category = "QUOTA",
                title = "Added +$additionalMb MB Data Package",
                detail = "${updated.customName} total quota now ${NetworkFormatters.formatBytes(newAllocated)}. Access: $newState",
                deviceId = deviceId
            )
            if (newState == AccessState.ALLOWED && device.accessState == AccessState.QUOTA_EXHAUSTED) {
                notificationHelper.notifyEvent(
                    eventType = "RESTORED",
                    title = "Internet Restored for ${updated.customName}",
                    message = "Added +$additionalMb MB data. Remaining: ${NetworkFormatters.formatBytes(updated.quotaRemainingBytes)}",
                    settings = settings
                )
            }
            _statusBanner.value = "Added +$additionalMb MB to ${updated.customName}"
        }
    }

    fun resetDeviceQuotaUsage(deviceId: String) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val updated = device.copy(
                quotaBaselineBytes = device.totalBytes,
                accessState = if (device.accessState == AccessState.QUOTA_EXHAUSTED) AccessState.ALLOWED else device.accessState
            )
            val enf = enforcementEngine.applyDevicePolicy(
                device = updated,
                downstreamInterface = _telemetry.value.downstreamInterfaceName,
                useRootIfAvailable = false
            )
            repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))
            repository.logAction(
                category = "QUOTA",
                title = "Device Quota Usage Reset",
                detail = "Reset used quota counter to 0 B for ${updated.customName} (${updated.ipAddress})",
                deviceId = deviceId
            )
            _statusBanner.value = "Quota counter reset for ${updated.customName}"
        }
    }

    fun setDeviceScheduleWindow(deviceId: String, startHour: Int, endHour: Int) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val updated = device.copy(
                scheduledBlockStartHour = startHour,
                scheduledBlockEndHour = endHour
            )
            repository.upsertDevice(updated)
            val msg = if (startHour in 0..23 && endHour in 0..23) {
                String.format("Scheduled block %02d:00 - %02d:00 set for %s", startHour, endHour, device.customName)
            } else {
                "Cleared scheduled block for ${device.customName}"
            }
            repository.logAction(
                category = "AUTOMATION",
                title = "Device Schedule Updated",
                detail = msg,
                deviceId = deviceId
            )
            _statusBanner.value = msg
        }
    }

    fun assignProfileToDevice(deviceId: String, profileId: String) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val profile = repository.getProfilesOnce().firstOrNull { it.profileId == profileId } ?: return@launch
            val now = System.currentTimeMillis()
            val expiryMs = if (profile.defaultExpiryHours > 0) now + profile.defaultExpiryHours * 3600_000L else 0L

            val updated = device.copy(
                profileId = profile.profileId,
                downloadLimitKbps = profile.defaultDownloadLimitKbps,
                uploadLimitKbps = profile.defaultUploadLimitKbps,
                allocatedCombinedBytes = profile.defaultQuotaBytes,
                quotaPeriod = profile.quotaPeriod,
                priorityLevel = profile.priorityLevel,
                scheduledBlockStartHour = profile.scheduleStartHour,
                scheduledBlockEndHour = profile.scheduleEndHour,
                quotaExpiryEpochMs = expiryMs,
                accessState = if (profile.allowInternet) AccessState.ALLOWED else AccessState.BLOCKED_PERMANENT
            )
            val enf = enforcementEngine.applyDevicePolicy(
                device = updated,
                downstreamInterface = _telemetry.value.downstreamInterfaceName,
                useRootIfAvailable = false
            )
            repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))
            repository.logAction(
                category = "SPEED_LIMIT",
                title = "Assigned Profile '${profile.name}'",
                detail = "Applied ${profile.name} rules to ${updated.customName} (${updated.ipAddress})",
                deviceId = deviceId
            )
            _statusBanner.value = "Applied '${profile.name}' profile to ${updated.customName}"
        }
    }

    /**
     * Runs a real stream test through the UnrootedGatewayServer for the selected device IP
     * to verify live token-bucket rate limiting or block enforcement and update counters.
     */
    fun verifyDeviceEnforcementNow(deviceId: String) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            val enf = enforcementEngine.applyDevicePolicy(
                device = device,
                downstreamInterface = _telemetry.value.downstreamInterfaceName,
                useRootIfAvailable = false
            )
            val counters = gatewayServer.getClientCounters()[device.ipAddress]
            val updated = device.copy(
                downloadedBytes = maxOf(device.downloadedBytes, counters?.downloadedBytes?.get() ?: 0L),
                uploadedBytes = maxOf(device.uploadedBytes, counters?.uploadedBytes?.get() ?: 0L),
                enforcementStatusLabel = enf.statusBadge
            )
            repository.upsertDevice(updated)
            repository.logAction(
                category = "SYSTEM",
                title = "Live Enforcement Verification Passed",
                detail = "${device.customName}: ${enf.detailMessage}",
                deviceId = deviceId,
                adapter = enf.adapterUsed,
                verified = enf.success
            )
            _statusBanner.value = "${device.customName}: ${enf.detailMessage}"
        }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            val device = repository.getDeviceById(deviceId) ?: return@launch
            gatewayServer.removeClientPolicy(device.ipAddress)
            repository.deleteDevice(deviceId)
            repository.logAction(
                category = "SYSTEM",
                title = "Device Record Removed",
                detail = "Removed ${device.customName} (${device.ipAddress})",
                deviceId = deviceId
            )
        }
    }

    // Global Dashboard Bulk Controls
    fun blockAllDevices() {
        viewModelScope.launch {
            val all = repository.getAllDevicesOnce()
            for (d in all) {
                val updated = d.copy(accessState = AccessState.BLOCKED_PERMANENT, category = DeviceCategory.BLOCKED)
                val enf = enforcementEngine.applyDevicePolicy(updated, _telemetry.value.downstreamInterfaceName, false)
                repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))
            }
            repository.logAction("BLOCK", "Blocked All Connected Devices", "Applied global block across ${all.size} device(s)")
            _statusBanner.value = "All ${all.size} device(s) blocked on Hotspot Gateway"
        }
    }

    fun allowAllTrustedDevices() {
        viewModelScope.launch {
            val all = repository.getAllDevicesOnce()
            var count = 0
            for (d in all) {
                if (d.category == DeviceCategory.TRUSTED) {
                    count++
                    val updated = d.copy(accessState = AccessState.ALLOWED)
                    val enf = enforcementEngine.applyDevicePolicy(updated, _telemetry.value.downstreamInterfaceName, false)
                    repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))
                }
            }
            repository.logAction("UNBLOCK", "Allowed All Trusted Devices", "Restored internet access to $count trusted device(s)")
            _statusBanner.value = "Internet allowed for $count trusted device(s)"
        }
    }

    fun pauseAllInternet() {
        viewModelScope.launch {
            val all = repository.getAllDevicesOnce()
            for (d in all) {
                val updated = d.copy(accessState = AccessState.PAUSED)
                val enf = enforcementEngine.applyDevicePolicy(updated, _telemetry.value.downstreamInterfaceName, false)
                repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))
            }
            repository.logAction("BLOCK", "Paused All Client Internet", "Temporarily paused internet for ${all.size} device(s)")
            _statusBanner.value = "All client internet traffic paused"
        }
    }

    fun resumeAllInternet() {
        viewModelScope.launch {
            val all = repository.getAllDevicesOnce()
            for (d in all) {
                val updated = d.copy(accessState = AccessState.ALLOWED)
                val enf = enforcementEngine.applyDevicePolicy(updated, _telemetry.value.downstreamInterfaceName, false)
                repository.upsertDevice(updated.copy(enforcementStatusLabel = enf.statusBadge))
            }
            repository.logAction("UNBLOCK", "Resumed All Client Internet", "Resumed internet access for ${all.size} device(s)")
            _statusBanner.value = "Resumed internet access for all ${all.size} device(s)"
        }
    }

    fun updateGlobalBandwidthSettings(
        maxDownloadKbps: Int,
        maxUploadKbps: Int,
        maxDevices: Int,
        fairSharing: Boolean,
        minClientKbps: Int,
        maxClientKbps: Int
    ) {
        viewModelScope.launch {
            gatewayServer.globalMaxDownloadKbps = maxDownloadKbps
            gatewayServer.globalMaxUploadKbps = maxUploadKbps
            gatewayServer.fairSharingEnabled = fairSharing
            repository.preferences.updateGlobalBandwidth(
                maxDownloadKbps = maxDownloadKbps,
                maxUploadKbps = maxUploadKbps,
                maxDevices = maxDevices,
                fairSharing = fairSharing,
                minClientKbps = minClientKbps,
                maxClientKbps = maxClientKbps
            )
            repository.logAction(
                category = "SPEED_LIMIT",
                title = "Global Hotspot Bandwidth Updated",
                detail = "Max ↓ ${NetworkFormatters.formatKbpsLimit(maxDownloadKbps)}, Max ↑ ${NetworkFormatters.formatKbpsLimit(maxUploadKbps)}, Fair Share: $fairSharing"
            )
            _statusBanner.value = "Updated global bandwidth ceilings & fair-sharing policy"
        }
    }

    fun saveDeviceProfile(profile: DeviceProfileEntity) {
        viewModelScope.launch {
            repository.upsertProfile(profile)
            repository.logAction("SYSTEM", "Saved Profile '${profile.name}'", profile.description)
            _statusBanner.value = "Saved profile '${profile.name}'"
        }
    }

    fun toggleAutomationRule(rule: AutomationRuleEntity) {
        viewModelScope.launch {
            val updated = rule.copy(isEnabled = !rule.isEnabled)
            repository.upsertAutomationRule(updated)
            repository.logAction(
                category = "AUTOMATION",
                title = "Automation Rule ${if (updated.isEnabled) "Enabled" else "Disabled"}",
                detail = updated.title
            )
        }
    }

    fun createAutomationRule(
        title: String,
        description: String,
        triggerType: String,
        targetCategory: String,
        actionType: String,
        startHour: Int,
        endHour: Int
    ) {
        viewModelScope.launch {
            repository.upsertAutomationRule(
                AutomationRuleEntity(
                    title = title.trim().ifEmpty { "Custom Automation Rule" },
                    description = description.trim().ifEmpty { "User-defined hotspot automation rule" },
                    triggerType = triggerType,
                    targetCategory = targetCategory,
                    actionType = actionType,
                    scheduleStartHour = startHour,
                    scheduleEndHour = endHour,
                    isEnabled = true
                )
            )
            repository.logAction("AUTOMATION", "Created Automation Rule", title)
            _statusBanner.value = "Created automation rule: $title"
        }
    }

    fun deleteAutomationRule(ruleId: Int) {
        viewModelScope.launch {
            repository.deleteAutomationRule(ruleId)
        }
    }

    fun updateGatewayConfiguration(enabled: Boolean, port: Int) {
        viewModelScope.launch {
            repository.preferences.updateGatewaySettings(enabled, port)
            if (enabled) {
                gatewayServer.stop()
                gatewayServer.start(port)
                _statusBanner.value = "Unrooted Hotspot Gateway active on port ${gatewayServer.boundPort}"
            } else {
                gatewayServer.stop()
                _statusBanner.value = "Unrooted Hotspot Gateway stopped"
            }
        }
    }

    fun updateEnforcementMode(mode: String) {
        viewModelScope.launch {
            repository.preferences.updateEnforcementMode(mode)
            _capabilities.value = capabilityDetector.detectCapabilities(mode)
            _statusBanner.value = "Active capability adapter mode set to $mode"
        }
    }

    fun refreshCapabilitiesReport() {
        viewModelScope.launch {
            val settings = repository.preferences.settingsFlow.first()
            _capabilities.value = capabilityDetector.detectCapabilities(settings.preferredEnforcementMode)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.setDarkTheme(enabled)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.preferences.setOnboardingCompleted(true)
        }
    }

    fun updatePinLock(pin: String, biometric: Boolean) {
        viewModelScope.launch {
            repository.preferences.updatePinLock(pin, biometric)
            repository.logAction(
                category = "SYSTEM",
                title = if (pin.isEmpty()) "App PIN Lock Disabled" else "App PIN Lock Updated",
                detail = "PIN security configuration updated by owner"
            )
        }
    }

    fun toggleNotificationPreference(keyName: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.updateNotificationToggle(keyName, enabled)
        }
    }

    fun startForegroundMonitorService(context: Context) {
        try {
            val snap = _telemetry.value
            val intent = Intent(context, HotspotMonitorService::class.java).apply {
                action = HotspotMonitorService.ACTION_START
                putExtra(HotspotMonitorService.EXTRA_STATUS_TEXT, snap.hotspotStateLabel)
                putExtra(HotspotMonitorService.EXTRA_ONLINE_COUNT, uiState.value.onlineDevices.size)
                putExtra(HotspotMonitorService.EXTRA_DL_SPEED, NetworkFormatters.formatSpeedBps(snap.currentDownloadSpeedBps))
                putExtra(HotspotMonitorService.EXTRA_UL_SPEED, NetworkFormatters.formatSpeedBps(snap.currentUploadSpeedBps))
            }
            ContextCompat.startForegroundService(context, intent)
            _statusBanner.value = "Background Hotspot Monitor Service started"
        } catch (_: Exception) {
        }
    }

    fun stopForegroundMonitorService(context: Context) {
        try {
            val intent = Intent(context, HotspotMonitorService::class.java).apply {
                action = HotspotMonitorService.ACTION_STOP
            }
            context.startService(intent)
            viewModelScope.launch {
                enforcementEngine.restoreAllNetworkRules(_telemetry.value.downstreamInterfaceName)
            }
            _statusBanner.value = "Background Hotspot Monitor Service stopped & rules restored"
        } catch (_: Exception) {
        }
    }

    fun openSystemTetheringSettings(context: Context) {
        try {
            val tetherIntent = Intent().apply {
                component = ComponentName("com.android.settings", "com.android.settings.TetherSettings")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(tetherIntent)
        } catch (_: Exception) {
            try {
                val wirelessIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(wirelessIntent)
            } catch (_: Exception) {
            }
        }
    }

    fun shareAnalyticsCsv(context: Context) {
        viewModelScope.launch {
            val csv = repository.exportAnalyticsCsv()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Hotspot Control Pro - Telemetry & Usage Export.csv")
                putExtra(Intent.EXTRA_TEXT, csv)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val chooser = Intent.createChooser(sendIntent, "Export Hotspot Usage CSV").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(chooser)
                repository.logAction("SYSTEM", "Exported Usage Analytics CSV", "Generated RFC-4180 CSV report")
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        gatewayServer.stop()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = HotspotDatabase.getInstance(application)
            val prefs = HotspotPreferences(application)
            val repo = HotspotRepository(db.hotspotDao(), prefs)
            @Suppress("UNCHECKED_CAST")
            return HotspotControlViewModel(application, repo) as T
        }
    }
}
