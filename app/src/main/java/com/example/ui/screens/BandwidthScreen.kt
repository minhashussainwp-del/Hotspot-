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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ConnectedDeviceEntity
import com.example.data.local.DeviceProfileEntity
import com.example.data.local.PriorityLevel
import com.example.ui.theme.NocAmberWarning
import com.example.ui.theme.NocCyanPrimary
import com.example.ui.theme.NocEmeraldSecondary
import com.example.util.NetworkFormatters
import com.example.viewmodel.HotspotUiState

private val DOWNLOAD_PRESETS_KBPS = listOf(
    0 to "Unlimited",
    100 to "100 KB/s",
    250 to "250 KB/s",
    500 to "500 KB/s",
    1000 to "1 MB/s",
    2000 to "2 MB/s",
    5000 to "5 MB/s",
    10000 to "10 MB/s"
)

private val UPLOAD_PRESETS_KBPS = listOf(
    0 to "Unlimited",
    100 to "100 KB/s",
    250 to "250 KB/s",
    500 to "500 KB/s",
    1000 to "1 MB/s",
    2000 to "2 MB/s"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BandwidthScreen(
    uiState: HotspotUiState,
    onUpdateGlobalBandwidth: (Int, Int, Int, Boolean, Int, Int) -> Unit,
    onSetDeviceSpeedLimit: (String, Int, Int, String) -> Unit,
    onAssignProfile: (String, String) -> Unit,
    onVerifyDeviceEnforcement: (String) -> Unit
) {
    val settings = uiState.settings
    var globalDlInput by remember(settings.maxTotalDownloadKbps) {
        mutableStateOf(settings.maxTotalDownloadKbps.toString())
    }
    var globalUlInput by remember(settings.maxTotalUploadKbps) {
        mutableStateOf(settings.maxTotalUploadKbps.toString())
    }
    var maxClientsInput by remember(settings.maxConnectedDevices) {
        mutableStateOf(settings.maxConnectedDevices.toString())
    }
    var fairShare by remember(settings.fairSharingEnabled) {
        mutableStateOf(settings.fairSharingEnabled)
    }
    var minClientInput by remember(settings.minBandwidthPerClientKbps) {
        mutableStateOf(settings.minBandwidthPerClientKbps.toString())
    }
    var maxClientInput by remember(settings.maxBandwidthPerClientKbps) {
        mutableStateOf(settings.maxBandwidthPerClientKbps.toString())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("bandwidth_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Global Hotspot Bandwidth Management
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NocCyanPrimary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "GLOBAL BANDWIDTH & FAIR SHARING",
                                style = MaterialTheme.typography.titleMedium,
                                color = NocCyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Token-Bucket Shaper on Gateway :${uiState.gatewayBoundPort}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Tune, contentDescription = null, tint = NocCyanPrimary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = globalDlInput,
                            onValueChange = { globalDlInput = it },
                            label = { Text("Max ↓ KB/s (0=Unl)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = globalUlInput,
                            onValueChange = { globalUlInput = it },
                            label = { Text("Max ↑ KB/s (0=Unl)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = minClientInput,
                            onValueChange = { minClientInput = it },
                            label = { Text("Min Floor KB/s") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxClientInput,
                            onValueChange = { maxClientInput = it },
                            label = { Text("Max Ceiling KB/s") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxClientsInput,
                            onValueChange = { maxClientsInput = it },
                            label = { Text("Max Clients") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic Fair Bandwidth Distribution",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Divides global ceiling across active clients weighted by High (1.25x), Normal (1.0x), and Low (0.65x) priority.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = fairShare,
                            onCheckedChange = { fairShare = it }
                        )
                    }

                    Button(
                        onClick = {
                            onUpdateGlobalBandwidth(
                                globalDlInput.toIntOrNull() ?: 0,
                                globalUlInput.toIntOrNull() ?: 0,
                                maxClientsInput.toIntOrNull() ?: 10,
                                fairShare,
                                minClientInput.toIntOrNull() ?: 100,
                                maxClientInput.toIntOrNull() ?: 10000
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NocCyanPrimary, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_global_bandwidth")
                    ) {
                        Text("Apply Global Bandwidth Rules")
                    }
                }
            }
        }

        // 2. Reusable Speed & Quota Profiles Overview
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "REUSABLE DEVICE PROFILES",
                        style = MaterialTheme.typography.labelLarge,
                        color = NocEmeraldSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.profiles.forEach { profile ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${profile.name} (${profile.priorityLevel})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NocCyanPrimary
                                    )
                                    Text(
                                        text = "↓ ${NetworkFormatters.formatKbpsLimit(profile.defaultDownloadLimitKbps)} • ↑ ${NetworkFormatters.formatKbpsLimit(profile.defaultUploadLimitKbps)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = "Quota: ${if (profile.defaultQuotaBytes > 0) NetworkFormatters.formatBytes(profile.defaultQuotaBytes) else "Unlimited"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Per-Device Independent Upload / Download Speed Shaper Cards
        item {
            Text(
                text = "PER-CLIENT INDEPENDENT SPEED LIMITS (${uiState.devices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(uiState.devices, key = { it.deviceId }) { device ->
            DeviceBandwidthControlCard(
                device = device,
                profiles = uiState.profiles,
                onApplyLimits = { dlKbps, ulKbps, prio ->
                    onSetDeviceSpeedLimit(device.deviceId, dlKbps, ulKbps, prio)
                },
                onAssignProfile = { profId ->
                    onAssignProfile(device.deviceId, profId)
                },
                onVerifyStream = {
                    onVerifyDeviceEnforcement(device.deviceId)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceBandwidthControlCard(
    device: ConnectedDeviceEntity,
    profiles: List<DeviceProfileEntity>,
    onApplyLimits: (Int, Int, String) -> Unit,
    onAssignProfile: (String) -> Unit,
    onVerifyStream: () -> Unit
) {
    var selectedDlKbps by remember(device.downloadLimitKbps) { mutableStateOf(device.downloadLimitKbps) }
    var selectedUlKbps by remember(device.uploadLimitKbps) { mutableStateOf(device.uploadLimitKbps) }
    var selectedPriority by remember(device.priorityLevel) { mutableStateOf(device.priorityLevel) }
    var customDlText by rememberSaveable { mutableStateOf("") }
    var customUlText by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bandwidth_card_${device.deviceId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${device.customName} (${device.ipAddress})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Active: ↓ ${NetworkFormatters.formatKbpsLimit(device.downloadLimitKbps)} • ↑ ${NetworkFormatters.formatKbpsLimit(device.uploadLimitKbps)} • Priority: ${device.priorityLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NocCyanPrimary
                    )
                }
                OutlinedButton(onClick = { onApplyLimits(0, 0, PriorityLevel.NORMAL) }) {
                    Text("Unlimited")
                }
            }

            // Download Presets
            Text(
                text = "DOWNLOAD SPEED PRESETS",
                style = MaterialTheme.typography.labelMedium,
                color = NocCyanPrimary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DOWNLOAD_PRESETS_KBPS.forEach { (kbps, label) ->
                    FilterChip(
                        selected = selectedDlKbps == kbps,
                        onClick = {
                            selectedDlKbps = kbps
                            onApplyLimits(kbps, selectedUlKbps, selectedPriority)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Upload Presets
            Text(
                text = "UPLOAD SPEED PRESETS",
                style = MaterialTheme.typography.labelMedium,
                color = NocEmeraldSecondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UPLOAD_PRESETS_KBPS.forEach { (kbps, label) ->
                    FilterChip(
                        selected = selectedUlKbps == kbps,
                        onClick = {
                            selectedUlKbps = kbps
                            onApplyLimits(selectedDlKbps, kbps, selectedPriority)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Priority Queue Selection
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Priority:", style = MaterialTheme.typography.labelMedium)
                listOf(PriorityLevel.HIGH, PriorityLevel.NORMAL, PriorityLevel.LOW).forEach { prio ->
                    FilterChip(
                        selected = selectedPriority == prio,
                        onClick = {
                            selectedPriority = prio
                            onApplyLimits(selectedDlKbps, selectedUlKbps, prio)
                        },
                        label = { Text(prio) }
                    )
                }
            }

            // Custom Speed Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customDlText,
                    onValueChange = { customDlText = it },
                    label = { Text("Custom ↓ KB/s") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = customUlText,
                    onValueChange = { customUlText = it },
                    label = { Text("Custom ↑ KB/s") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val dl = customDlText.toIntOrNull() ?: selectedDlKbps
                        val ul = customUlText.toIntOrNull() ?: selectedUlKbps
                        selectedDlKbps = dl
                        selectedUlKbps = ul
                        onApplyLimits(dl, ul, selectedPriority)
                    }
                ) {
                    Text("Set")
                }
            }

            // Quick Profile Assignment & Stream Verification
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onVerifyStream) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Measure & Verify Speed")
                }
                Text(
                    text = device.enforcementStatusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = NocEmeraldSecondary
                )
            }
        }
    }
}
