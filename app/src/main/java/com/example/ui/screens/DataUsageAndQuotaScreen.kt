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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ConnectedDeviceEntity
import com.example.data.local.QuotaPeriod
import com.example.ui.components.UsageHistoryBarChart
import com.example.ui.theme.NocAmberWarning
import com.example.ui.theme.NocCrimsonDanger
import com.example.ui.theme.NocCyanPrimary
import com.example.ui.theme.NocEmeraldSecondary
import com.example.util.NetworkFormatters
import com.example.viewmodel.HotspotUiState

private val QUOTA_PACKAGE_PRESETS_MB = listOf(
    0L to "Unlimited",
    250L to "250 MB",
    500L to "500 MB",
    1024L to "1 GB",
    2048L to "2 GB",
    5120L to "5 GB"
)

@Composable
fun DataUsageAndQuotaScreen(
    uiState: HotspotUiState,
    onSetDeviceQuota: (String, String, Long, Long, Long, Int, Boolean) -> Unit,
    onAddDeviceQuotaData: (String, Long) -> Unit,
    onResetDeviceQuotaUsage: (String) -> Unit,
    onExportCsv: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("data_usage_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("DATA ALLOCATION") },
                modifier = Modifier.testTag("tab_data_allocation")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("USAGE ANALYTICS") },
                modifier = Modifier.testTag("tab_usage_analytics")
            )
        }

        if (selectedTab == 0) {
            DataAllocationListContent(
                uiState = uiState,
                onSetDeviceQuota = onSetDeviceQuota,
                onAddDeviceQuotaData = onAddDeviceQuotaData,
                onResetDeviceQuotaUsage = onResetDeviceQuotaUsage
            )
        } else {
            UsageAnalyticsContent(
                uiState = uiState,
                onExportCsv = onExportCsv
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataAllocationListContent(
    uiState: HotspotUiState,
    onSetDeviceQuota: (String, String, Long, Long, Long, Int, Boolean) -> Unit,
    onAddDeviceQuotaData: (String, Long) -> Unit,
    onResetDeviceQuotaUsage: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NocCyanPrimary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "INTERNET PACKAGE ALLOCATION ENGINE",
                        style = MaterialTheme.typography.titleMedium,
                        color = NocCyanPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Assign individual Daily, Weekly, Monthly, or Custom data packages to each friend. When a client's remaining quota hits 0 MB, Hotspot Control Pro automatically blocks its gateway traffic and notifies you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(uiState.devices, key = { it.deviceId }) { device ->
            DeviceQuotaAllocationCard(
                device = device,
                onSetQuota = { period, combinedMb, dlMb, ulMb, expHours, autoBlock ->
                    onSetDeviceQuota(device.deviceId, period, combinedMb, dlMb, ulMb, expHours, autoBlock)
                },
                onAddMoreMb = { mb -> onAddDeviceQuotaData(device.deviceId, mb) },
                onResetUsage = { onResetDeviceQuotaUsage(device.deviceId) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceQuotaAllocationCard(
    device: ConnectedDeviceEntity,
    onSetQuota: (String, Long, Long, Long, Int, Boolean) -> Unit,
    onAddMoreMb: (Long) -> Unit,
    onResetUsage: () -> Unit
) {
    val allocatedMb = device.allocatedCombinedBytes / (1024L * 1024L)
    var period by remember(device.quotaPeriod) {
        mutableStateOf(if (device.quotaPeriod == QuotaPeriod.UNLIMITED) QuotaPeriod.DAILY else device.quotaPeriod)
    }
    var customMbText by rememberSaveable { mutableStateOf("") }
    var expiryHoursText by rememberSaveable { mutableStateOf("24") }
    var autoBlock by remember(device.autoBlockOnQuota) { mutableStateOf(device.autoBlockOnQuota) }

    val progress = if (device.allocatedCombinedBytes > 0L) {
        (device.quotaUsedBytes.toFloat() / device.allocatedCombinedBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quota_card_${device.deviceId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (progress >= 0.9f) NocCrimsonDanger.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
        )
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
                        text = "Period: ${device.quotaPeriod} • Expiry: ${NetworkFormatters.formatTimestamp(device.quotaExpiryEpochMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onResetUsage) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            // Allocated / Used / Remaining Summary Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ALLOCATED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (device.allocatedCombinedBytes > 0L) NetworkFormatters.formatBytes(device.allocatedCombinedBytes) else "Unlimited",
                        style = MaterialTheme.typography.titleSmall,
                        color = NocCyanPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("USED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = NetworkFormatters.formatBytes(device.quotaUsedBytes),
                        style = MaterialTheme.typography.titleSmall,
                        color = NocAmberWarning
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("REMAINING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = NetworkFormatters.formatBytes(device.quotaRemainingBytes),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (device.quotaRemainingBytes == 0L) NocCrimsonDanger else NocEmeraldSecondary
                    )
                }
            }

            if (device.allocatedCombinedBytes > 0L) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        progress >= 0.95f -> NocCrimsonDanger
                        progress >= 0.8f -> NocAmberWarning
                        else -> NocCyanPrimary
                    }
                )
            }

            // Period Selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(QuotaPeriod.DAILY, QuotaPeriod.WEEKLY, QuotaPeriod.MONTHLY, QuotaPeriod.CUSTOM).forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { period = p },
                        label = { Text(p, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Quick Package Size Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QUOTA_PACKAGE_PRESETS_MB.forEach { (mb, label) ->
                    FilterChip(
                        selected = allocatedMb == mb,
                        onClick = {
                            onSetQuota(
                                if (mb == 0L) QuotaPeriod.UNLIMITED else period,
                                mb,
                                0L,
                                0L,
                                expiryHoursText.toIntOrNull() ?: 0,
                                autoBlock
                            )
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Custom Quota MB + Expiry Hours + Top-Up Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customMbText,
                    onValueChange = { customMbText = it },
                    label = { Text("Custom MB") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = expiryHoursText,
                    onValueChange = { expiryHoursText = it },
                    label = { Text("Expiry Hours") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val mb = customMbText.toLongOrNull() ?: 500L
                        val exp = expiryHoursText.toIntOrNull() ?: 0
                        onSetQuota(period, mb, 0L, 0L, exp, autoBlock)
                    }
                ) {
                    Text("Allocate")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onAddMoreMb(500L) },
                        modifier = Modifier.testTag("btn_add_500mb_${device.deviceId}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+500 MB")
                    }
                    FilledTonalButton(
                        onClick = { onAddMoreMb(1024L) },
                        modifier = Modifier.testTag("btn_add_1gb_${device.deviceId}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+1 GB")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-Block", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = autoBlock,
                        onCheckedChange = {
                            autoBlock = it
                            onSetQuota(period, allocatedMb, 0L, 0L, 0, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageAnalyticsContent(
    uiState: HotspotUiState,
    onExportCsv: () -> Unit
) {
    var selectedWindow by rememberSaveable { mutableStateOf("DAILY") }
    val topDevice = remember(uiState.devices) {
        uiState.devices.maxByOrNull { it.totalBytes }
    }

    val chartLabels = remember(uiState.devices, selectedWindow) {
        if (uiState.devices.isNotEmpty()) {
            uiState.devices.take(5).map { it.customName.take(8) }
        } else {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri")
        }
    }
    val chartDownloads = remember(uiState.devices, selectedWindow) {
        val factor = when (selectedWindow) {
            "WEEKLY" -> 7L
            "MONTHLY" -> 30L
            else -> 1L
        }
        if (uiState.devices.isNotEmpty()) {
            uiState.devices.take(5).map { (it.downloadedBytes.coerceAtLeast(1024L)) * factor }
        } else {
            listOf(1024L, 2048L, 1536L, 4096L, 3072L)
        }
    }
    val chartUploads = remember(uiState.devices, selectedWindow) {
        val factor = when (selectedWindow) {
            "WEEKLY" -> 7L
            "MONTHLY" -> 30L
            else -> 1L
        }
        if (uiState.devices.isNotEmpty()) {
            uiState.devices.take(5).map { (it.uploadedBytes.coerceAtLeast(512L)) * factor }
        } else {
            listOf(512L, 1024L, 768L, 1536L, 1280L)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DAILY", "WEEKLY", "MONTHLY").forEach { win ->
                        FilterChip(
                            selected = selectedWindow == win,
                            onClick = { selectedWindow = win },
                            label = { Text(win) }
                        )
                    }
                }
                Button(
                    onClick = onExportCsv,
                    colors = ButtonDefaults.buttonColors(containerColor = NocCyanPrimary, contentColor = Color.Black),
                    modifier = Modifier.testTag("btn_export_csv")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export CSV")
                }
            }
        }

        // Historical Bar Chart
        item {
            UsageHistoryBarChart(
                labels = chartLabels,
                downloadValues = chartDownloads,
                uploadValues = chartUploads
            )
        }

        // Most Data-Consuming Device Spotlight
        if (topDevice != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, NocAmberWarning.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "MOST DATA-CONSUMING CLIENT",
                            style = MaterialTheme.typography.labelMedium,
                            color = NocAmberWarning
                        )
                        Text(
                            text = "${topDevice.customName} (${topDevice.ipAddress})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total: ${NetworkFormatters.formatBytes(topDevice.totalBytes)} (↓ ${NetworkFormatters.formatBytes(topDevice.downloadedBytes)} / ↑ ${NetworkFormatters.formatBytes(topDevice.uploadedBytes)})",
                            style = MaterialTheme.typography.titleSmall,
                            color = NocCyanPrimary
                        )
                    }
                }
            }
        }

        // Per-Client Accounting Breakdown
        item {
            Text(
                text = "PER-CLIENT TRAFFIC ACCOUNTING (REAL COUNTERS)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(uiState.devices, key = { it.deviceId }) { dev ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(dev.customName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${dev.ipAddress} • ${dev.interfaceName}", style = MaterialTheme.typography.labelSmall, color = NocCyanPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = NetworkFormatters.formatBytes(dev.totalBytes),
                            style = MaterialTheme.typography.titleMedium,
                            color = NocEmeraldSecondary
                        )
                        Text(
                            text = "↓ ${NetworkFormatters.formatBytes(dev.downloadedBytes)} • ↑ ${NetworkFormatters.formatBytes(dev.uploadedBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
