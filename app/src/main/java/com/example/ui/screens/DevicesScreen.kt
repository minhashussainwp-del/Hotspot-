package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AccessState
import com.example.data.local.ConnectedDeviceEntity
import com.example.data.local.DeviceCategory
import com.example.data.local.DeviceProfileEntity
import com.example.ui.components.StatusPulseDot
import com.example.ui.theme.NocAmberWarning
import com.example.ui.theme.NocCrimsonDanger
import com.example.ui.theme.NocCyanPrimary
import com.example.ui.theme.NocEmeraldSecondary
import com.example.util.NetworkFormatters
import com.example.viewmodel.HotspotUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(
    uiState: HotspotUiState,
    onRefreshDevices: () -> Unit,
    onRegisterClient: (String, String, String, String, String) -> Unit,
    onRenameDevice: (String, String) -> Unit,
    onSetAccessState: (String, String, Int) -> Unit,
    onSetCategory: (String, String) -> Unit,
    onAssignProfile: (String, String) -> Unit,
    onSetScheduleWindow: (String, Int, Int) -> Unit,
    onVerifyEnforcement: (String) -> Unit,
    onDeleteDevice: (String) -> Unit
) {
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var inspectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredDevices = remember(uiState.devices, selectedFilter) {
        val now = System.currentTimeMillis()
        when (selectedFilter) {
            "ONLINE" -> uiState.devices.filter { it.isOnline }
            "TRUSTED" -> uiState.devices.filter { it.category == DeviceCategory.TRUSTED }
            "UNKNOWN" -> uiState.devices.filter { it.category == DeviceCategory.UNKNOWN }
            "BLOCKED" -> uiState.devices.filter { it.isBlockedOrPaused || it.category == DeviceCategory.BLOCKED }
            "RECENT" -> uiState.devices.filter { (now - it.lastSeenEpochMs) <= 24 * 3600_000L }
            "OFFLINE" -> uiState.devices.filter { !it.isOnline }
            else -> uiState.devices
        }
    }

    val inspectedDevice = uiState.devices.firstOrNull { it.deviceId == inspectedDeviceId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("devices_screen")
    ) {
        // Top Header & Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CONNECTED CLIENTS (${filteredDevices.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Subnet ${uiState.telemetry.subnetPrefix}.0/24 • Gateway :${uiState.gatewayBoundPort}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NocCyanPrimary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("btn_register_client")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Client", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Client IP")
                }
                IconButton(
                    onClick = onRefreshDevices,
                    modifier = Modifier.testTag("btn_scan_subnet")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan Subnet", tint = NocCyanPrimary)
                }
            }
        }

        // Category Filter Row
        val filterOptions = listOf(
            "ALL" to "All (${uiState.devices.size})",
            "ONLINE" to "Online (${uiState.onlineDevices.size})",
            "TRUSTED" to "Trusted (${uiState.trustedDevices.size})",
            "UNKNOWN" to "Unknown (${uiState.unknownDevices.size})",
            "BLOCKED" to "Blocked (${uiState.blockedDevices.size})",
            "RECENT" to "Recent",
            "OFFLINE" to "Offline (${uiState.offlineDevices.size})"
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filterOptions) { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { selectedFilter = key },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("filter_chip_$key")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredDevices.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiFind,
                        contentDescription = "No clients",
                        tint = NocCyanPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No Hotspot Clients in Selected Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap 'Scan Subnet' to probe active DHCP clients on ${uiState.telemetry.downstreamInterfaceName}, or tap 'Add Client IP' to pre-configure rules for a friend's device IP (e.g. ${uiState.telemetry.subnetPrefix}.101).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRefreshDevices,
                            colors = ButtonDefaults.buttonColors(containerColor = NocCyanPrimary, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Subnet Now")
                        }
                        OutlinedButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Register Friend IP")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredDevices, key = { it.deviceId }) { device ->
                    ConnectedDeviceCard(
                        device = device,
                        profiles = uiState.profiles,
                        onInspect = { inspectedDeviceId = device.deviceId },
                        onToggleBlock = {
                            val nextState = if (device.accessState == AccessState.ALLOWED) {
                                AccessState.BLOCKED_PERMANENT
                            } else {
                                AccessState.ALLOWED
                            }
                            onSetAccessState(device.deviceId, nextState, 0)
                        },
                        onTogglePause = {
                            val nextState = if (device.accessState == AccessState.PAUSED) {
                                AccessState.ALLOWED
                            } else {
                                AccessState.PAUSED
                            }
                            onSetAccessState(device.deviceId, nextState, 0)
                        },
                        onVerifyNow = { onVerifyEnforcement(device.deviceId) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        RegisterClientDialog(
            defaultSubnetPrefix = uiState.telemetry.subnetPrefix,
            profiles = uiState.profiles,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, ip, mac, cat, profId ->
                onRegisterClient(name, ip, mac, cat, profId)
                showAddDialog = false
            }
        )
    }

    if (inspectedDevice != null) {
        ModalBottomSheet(
            onDismissRequest = { inspectedDeviceId = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DeviceDetailsSheetContent(
                device = inspectedDevice,
                profiles = uiState.profiles,
                onRename = { newName -> onRenameDevice(inspectedDevice.deviceId, newName) },
                onSetAccessState = { state, mins -> onSetAccessState(inspectedDevice.deviceId, state, mins) },
                onSetCategory = { cat -> onSetCategory(inspectedDevice.deviceId, cat) },
                onAssignProfile = { profId -> onAssignProfile(inspectedDevice.deviceId, profId) },
                onSetSchedule = { startH, endH -> onSetScheduleWindow(inspectedDevice.deviceId, startH, endH) },
                onVerifyEnforcement = { onVerifyEnforcement(inspectedDevice.deviceId) },
                onDelete = {
                    onDeleteDevice(inspectedDevice.deviceId)
                    inspectedDeviceId = null
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectedDeviceCard(
    device: ConnectedDeviceEntity,
    profiles: List<DeviceProfileEntity>,
    onInspect: () -> Unit,
    onToggleBlock: () -> Unit,
    onTogglePause: () -> Unit,
    onVerifyNow: () -> Unit
) {
    val isAllowed = device.accessState == AccessState.ALLOWED
    val borderAccent = when {
        !isAllowed -> NocCrimsonDanger.copy(alpha = 0.65f)
        device.category == DeviceCategory.TRUSTED -> NocEmeraldSecondary.copy(alpha = 0.55f)
        else -> NocCyanPrimary.copy(alpha = 0.4f)
    }
    val profileName = profiles.firstOrNull { it.profileId == device.profileId }?.name ?: "Custom"

    Card(
        onClick = onInspect,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_card_${device.deviceId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderAccent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Name, Pulse Status, Category & Access Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPulseDot(isOnline = device.isOnline && isAllowed)
                    Column {
                        Text(
                            text = device.customName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${device.ipAddress} • ${device.macAddress}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NocCyanPrimary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAllowed) NocEmeraldSecondary.copy(alpha = 0.16f) else NocCrimsonDanger.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = device.accessState,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAllowed) NocEmeraldSecondary else NocCrimsonDanger,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Row 2: Live Speeds, Data Consumed, Uptime & Profile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "↓ ${NetworkFormatters.formatSpeedBps(device.currentDownloadSpeedBps)} (${NetworkFormatters.formatKbpsLimit(device.downloadLimitKbps)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = NocCyanPrimary
                    )
                    Text(
                        text = "Downloaded: ${NetworkFormatters.formatBytes(device.downloadedBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "↑ ${NetworkFormatters.formatSpeedBps(device.currentUploadSpeedBps)} (${NetworkFormatters.formatKbpsLimit(device.uploadLimitKbps)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = NocEmeraldSecondary
                    )
                    Text(
                        text = "Uploaded: ${NetworkFormatters.formatBytes(device.uploadedBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quota Progress Bar if quota is allocated
            if (device.allocatedCombinedBytes > 0L) {
                val progress = (device.quotaUsedBytes.toFloat() / device.allocatedCombinedBytes.toFloat()).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Quota (${device.quotaPeriod}): ${NetworkFormatters.formatBytes(device.quotaUsedBytes)} / ${NetworkFormatters.formatBytes(device.allocatedCombinedBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Rem: ${NetworkFormatters.formatBytes(device.quotaRemainingBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (progress >= 0.85f) NocAmberWarning else NocEmeraldSecondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (progress >= 0.9f) NocCrimsonDanger else NocCyanPrimary
                    )
                }
            }

            // Metadata line: Duration, Last Seen, Profile, Enforcement status
            Text(
                text = "Duration: ${NetworkFormatters.formatDurationSeconds(device.accumulatedDurationSec)} • Seen: ${NetworkFormatters.formatRelativeTime(device.lastSeenEpochMs)} • Profile: $profileName • ${device.enforcementStatusLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Quick Action Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onToggleBlock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAllowed) NocCrimsonDanger else NocEmeraldSecondary,
                        contentColor = if (isAllowed) Color.White else Color.Black
                    ),
                    modifier = Modifier.testTag("btn_toggle_block_${device.deviceId}")
                ) {
                    Icon(
                        imageVector = if (isAllowed) Icons.Default.Block else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isAllowed) "Block Internet" else "Allow Internet")
                }

                FilledTonalButton(
                    onClick = onTogglePause,
                    modifier = Modifier.testTag("btn_toggle_pause_${device.deviceId}")
                ) {
                    Icon(
                        imageVector = if (device.accessState == AccessState.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (device.accessState == AccessState.PAUSED) "Resume" else "Pause")
                }

                OutlinedButton(
                    onClick = onVerifyNow,
                    modifier = Modifier.testTag("btn_verify_${device.deviceId}")
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Verify")
                }

                OutlinedButton(
                    onClick = onInspect,
                    modifier = Modifier.testTag("btn_details_${device.deviceId}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Controls")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceDetailsSheetContent(
    device: ConnectedDeviceEntity,
    profiles: List<DeviceProfileEntity>,
    onRename: (String) -> Unit,
    onSetAccessState: (String, Int) -> Unit,
    onSetCategory: (String) -> Unit,
    onAssignProfile: (String) -> Unit,
    onSetSchedule: (Int, Int) -> Unit,
    onVerifyEnforcement: () -> Unit,
    onDelete: () -> Unit
) {
    var nameInput by remember(device.customName) { mutableStateOf(device.customName) }
    var schedStart by remember(device.scheduledBlockStartHour) {
        mutableStateOf(if (device.scheduledBlockStartHour >= 0) device.scheduledBlockStartHour.toString() else "23")
    }
    var schedEnd by remember(device.scheduledBlockEndHour) {
        mutableStateOf(if (device.scheduledBlockEndHour >= 0) device.scheduledBlockEndHour.toString() else "6")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "CLIENT POLICY & ACCESS CONTROL",
                style = MaterialTheme.typography.titleLarge,
                color = NocCyanPrimary
            )
            Text(
                text = "IP: ${device.ipAddress} • MAC: ${device.macAddress} • Total Usage: ${NetworkFormatters.formatBytes(device.totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Rename Device
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Device Display Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { onRename(nameInput) }) {
                    Text("Rename")
                }
            }
        }

        // 2. Individual Internet Blocking Modes
        item {
            Text(
                text = "INTERNET ACCESS & TEMPORARY BLOCKS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSetAccessState(AccessState.ALLOWED, 0) },
                    colors = ButtonDefaults.buttonColors(containerColor = NocEmeraldSecondary, contentColor = Color.Black)
                ) {
                    Text("Enable Internet")
                }
                Button(
                    onClick = { onSetAccessState(AccessState.BLOCKED_PERMANENT, 0) },
                    colors = ButtonDefaults.buttonColors(containerColor = NocCrimsonDanger, contentColor = Color.White)
                ) {
                    Text("Permanent Block")
                }
                FilledTonalButton(onClick = { onSetAccessState(AccessState.PAUSED, 0) }) {
                    Text("Pause Internet")
                }
                OutlinedButton(onClick = { onSetAccessState(AccessState.BLOCKED_TEMPORARY, 15) }) {
                    Text("Block 15m")
                }
                OutlinedButton(onClick = { onSetAccessState(AccessState.BLOCKED_TEMPORARY, 60) }) {
                    Text("Block 1h")
                }
                OutlinedButton(onClick = { onSetAccessState(AccessState.BLOCKED_TEMPORARY, 240) }) {
                    Text("Block 4h")
                }
            }
        }

        // 3. Trust / Blocklist Category
        item {
            Text(
                text = "TRUST & SECURITY CLASSIFICATION",
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = device.category == DeviceCategory.TRUSTED,
                    onClick = { onSetCategory(DeviceCategory.TRUSTED) },
                    label = { Text("Trusted List") }
                )
                FilterChip(
                    selected = device.category == DeviceCategory.UNKNOWN,
                    onClick = { onSetCategory(DeviceCategory.UNKNOWN) },
                    label = { Text("Unknown") }
                )
                FilterChip(
                    selected = device.category == DeviceCategory.BLOCKED,
                    onClick = { onSetCategory(DeviceCategory.BLOCKED) },
                    label = { Text("Blocklist") }
                )
            }
        }

        // 4. Assign Reusable Device Profile
        item {
            Text(
                text = "ASSIGN DEVICE PROFILE",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profiles.forEach { prof ->
                    FilterChip(
                        selected = device.profileId == prof.profileId,
                        onClick = { onAssignProfile(prof.profileId) },
                        label = { Text(prof.name) }
                    )
                }
            }
        }

        // 5. Scheduled Block Window
        item {
            Text(
                text = "SCHEDULED AUTOMATIC BLOCK WINDOW (24H)",
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = schedStart,
                    onValueChange = { schedStart = it },
                    label = { Text("Start Hour (0-23)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = schedEnd,
                    onValueChange = { schedEnd = it },
                    label = { Text("End Hour (0-23)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val s = schedStart.toIntOrNull()?.coerceIn(0, 23) ?: 23
                        val e = schedEnd.toIntOrNull()?.coerceIn(0, 23) ?: 6
                        onSetSchedule(s, e)
                    }
                ) {
                    Text("Apply")
                }
                TextButton(onClick = { onSetSchedule(-1, -1) }) {
                    Text("Clear")
                }
            }
        }

        // 6. Verify Enforcement & Delete Record
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalButton(onClick = onVerifyEnforcement) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Live Gateway Test")
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NocCrimsonDanger)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove Record")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegisterClientDialog(
    defaultSubnetPrefix: String,
    profiles: List<DeviceProfileEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("Friend's Phone") }
    var ip by remember { mutableStateOf("$defaultSubnetPrefix.101") }
    var mac by remember { mutableStateOf("28:6A:BA:44:19:02") }
    var category by remember { mutableStateOf(DeviceCategory.TRUSTED) }
    var profileId by remember { mutableStateOf(profiles.firstOrNull()?.profileId ?: "profile_friends") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Hotspot Client IP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Register a connected device's DHCP IP address on your hotspot subnet to apply speed limits, data quotas, and access rules.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friend / Device Name") },
                    singleLine = true,
                    modifier = Modifier.testTag("input_client_name")
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Client IPv4 Address") },
                    singleLine = true,
                    modifier = Modifier.testTag("input_client_ip")
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("MAC Address (Optional)") },
                    singleLine = true
                )
                Text("Category:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(DeviceCategory.TRUSTED, DeviceCategory.UNKNOWN, DeviceCategory.BLOCKED).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Text("Initial Profile:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    profiles.forEach { prof ->
                        FilterChip(
                            selected = profileId == prof.profileId,
                            onClick = { profileId = prof.profileId },
                            label = { Text(prof.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, ip, mac, category, profileId) },
                modifier = Modifier.testTag("btn_confirm_register_client")
            ) {
                Text("Register & Enforce")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
