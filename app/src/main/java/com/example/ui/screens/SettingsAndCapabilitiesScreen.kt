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
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.local.AutomationRuleEntity
import com.example.ui.components.CapabilityBadgeChip
import com.example.ui.theme.NocAmberWarning
import com.example.ui.theme.NocCrimsonDanger
import com.example.ui.theme.NocCyanPrimary
import com.example.ui.theme.NocEmeraldSecondary
import com.example.util.NetworkFormatters
import com.example.viewmodel.HotspotUiState

@Composable
fun SettingsAndCapabilitiesScreen(
    uiState: HotspotUiState,
    initialTab: Int = 0,
    onToggleAutomationRule: (AutomationRuleEntity) -> Unit,
    onCreateAutomationRule: (String, String, String, String, String, Int, Int) -> Unit,
    onDeleteAutomationRule: (Int) -> Unit,
    onUpdateGatewayConfig: (Boolean, Int) -> Unit,
    onUpdateEnforcementMode: (String) -> Unit,
    onRefreshCapabilities: () -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onUpdatePinLock: (String, Boolean) -> Unit,
    onToggleNotification: (String, Boolean) -> Unit
) {
    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_capabilities_screen")
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("AUTOMATION") },
                modifier = Modifier.testTag("tab_automation")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("CAPABILITIES") },
                modifier = Modifier.testTag("tab_capabilities")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("SETTINGS & LOGS") },
                modifier = Modifier.testTag("tab_settings_logs")
            )
        }

        when (selectedTab) {
            0 -> AutomationEngineTab(
                uiState = uiState,
                onToggleRule = onToggleAutomationRule,
                onCreateRule = onCreateAutomationRule,
                onDeleteRule = onDeleteAutomationRule
            )
            1 -> AdvancedCapabilitiesTab(
                uiState = uiState,
                onUpdateEnforcementMode = onUpdateEnforcementMode,
                onRefreshCapabilities = onRefreshCapabilities
            )
            else -> SystemSettingsAndAuditLogsTab(
                uiState = uiState,
                onUpdateGatewayConfig = onUpdateGatewayConfig,
                onSetDarkTheme = onSetDarkTheme,
                onUpdatePinLock = onUpdatePinLock,
                onToggleNotification = onToggleNotification
            )
        }
    }
}

@Composable
private fun AutomationEngineTab(
    uiState: HotspotUiState,
    onToggleRule: (AutomationRuleEntity) -> Unit,
    onCreateRule: (String, String, String, String, String, Int, Int) -> Unit,
    onDeleteRule: (Int) -> Unit
) {
    var showAddRuleDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AUTOMATION RULES ENGINE",
                        style = MaterialTheme.typography.titleMedium,
                        color = NocCyanPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Time-based, quota-based, and connection-triggered policies persisted in Room.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showAddRuleDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NocCyanPrimary, contentColor = Color.Black),
                    modifier = Modifier.testTag("btn_add_automation_rule")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Rule")
                }
            }
        }

        items(uiState.automationRules, key = { it.ruleId }) { rule ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (rule.isEnabled) NocEmeraldSecondary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = rule.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = rule.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Trigger: ${rule.triggerType} • Target: ${rule.targetCategory} • Action: ${rule.actionType}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NocCyanPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { onToggleRule(rule) }
                        )
                        IconButton(onClick = { onDeleteRule(rule.ruleId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = NocCrimsonDanger)
                        }
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        var title by remember { mutableStateOf("Block Guests After Midnight") }
        var desc by remember { mutableStateOf("Automatically pause unknown/guest internet between 00:00 and 06:00") }
        var trigger by remember { mutableStateOf("SCHEDULE_WINDOW") }
        var target by remember { mutableStateOf("UNKNOWN") }
        var action by remember { mutableStateOf("PAUSE_ALL") }
        var startH by remember { mutableStateOf("0") }
        var endH by remember { mutableStateOf("6") }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("Create Automation Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Rule Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startH,
                            onValueChange = { startH = it },
                            label = { Text("Start Hr (0-23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endH,
                            onValueChange = { endH = it },
                            label = { Text("End Hr (0-23)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateRule(
                            title,
                            desc,
                            trigger,
                            target,
                            action,
                            startH.toIntOrNull()?.coerceIn(0, 23) ?: 0,
                            endH.toIntOrNull()?.coerceIn(0, 23) ?: 6
                        )
                        showAddRuleDialog = false
                    }
                ) {
                    Text("Save Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedCapabilitiesTab(
    uiState: HotspotUiState,
    onUpdateEnforcementMode: (String) -> Unit,
    onRefreshCapabilities: () -> Unit
) {
    val report = uiState.capabilities
    val currentMode = uiState.settings.preferredEnforcementMode

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("capabilities_matrix_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NocCyanPrimary.copy(alpha = 0.5f))
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
                                text = "HARDWARE & OS CAPABILITY INSPECTION",
                                style = MaterialTheme.typography.titleMedium,
                                color = NocCyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${report?.manufacturer ?: "Android"} ${report?.model ?: "Device"} • ${report?.androidVersionName ?: ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onRefreshCapabilities) {
                            Icon(Icons.Default.Refresh, contentDescription = "Re-run inspection", tint = NocCyanPrimary)
                        }
                    }

                    Text(
                        text = "Active Adapter Mode: ${report?.activeOperatingMode ?: "UNROOTED_STANDARD"} • IPv4: ${if (report?.ipv4Supported == true) "Yes" else "No"} • IPv6: ${if (report?.ipv6Supported == true) "Yes" else "No"} • Local Gateway :${uiState.gatewayBoundPort} (${if (uiState.gatewayIsRunning) "Active" else "Standby"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = NocEmeraldSecondary
                    )

                    Text("Select Capability Adapter Priority:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "UNROOTED_STANDARD" to "Unrooted Standard (Recommended)",
                            "SHIZUKU" to "Shizuku Mode",
                            "ROOT" to "Root Mode (iptables/tc)"
                        ).forEach { (modeKey, label) ->
                            FilterChip(
                                selected = currentMode == modeKey,
                                onClick = { onUpdateEnforcementMode(modeKey) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        if (report != null) {
            items(report.items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            CapabilityBadgeChip(tier = item.tier, isAvailable = item.isCurrentlyAvailable)
                        }
                        Text(
                            text = item.technicalDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemSettingsAndAuditLogsTab(
    uiState: HotspotUiState,
    onUpdateGatewayConfig: (Boolean, Int) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onUpdatePinLock: (String, Boolean) -> Unit,
    onToggleNotification: (String, Boolean) -> Unit
) {
    val settings = uiState.settings
    var gatewayPortText by remember(settings.unrootedGatewayPort) {
        mutableStateOf(settings.unrootedGatewayPort.toString())
    }
    var gatewayEnabled by remember(settings.unrootedGatewayEnabled) {
        mutableStateOf(settings.unrootedGatewayEnabled)
    }
    var pinInput by remember(settings.appPinCode) {
        mutableStateOf(settings.appPinCode)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Unrooted Local Hotspot Gateway Server Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, NocCyanPrimary.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "UNROOTED HOTSPOT GATEWAY SERVER",
                        style = MaterialTheme.typography.titleMedium,
                        color = NocCyanPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Listening on ${uiState.telemetry.downstreamIpv4}:${uiState.gatewayBoundPort}. Clients connected to your hotspot can route through this gateway for zero-root speed shaping, instant blocking, and exact byte accounting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = gatewayPortText,
                            onValueChange = { gatewayPortText = it },
                            label = { Text("Gateway Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = gatewayEnabled,
                            onCheckedChange = {
                                gatewayEnabled = it
                                onUpdateGatewayConfig(it, gatewayPortText.toIntOrNull() ?: 8888)
                            }
                        )
                        Button(
                            onClick = {
                                onUpdateGatewayConfig(gatewayEnabled, gatewayPortText.toIntOrNull() ?: 8888)
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }

        // 2. Theme & Optional PIN Security
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "APPEARANCE & OPTIONAL ACCESS LOCK",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dark NOC Tactical Telemetry Theme", style = MaterialTheme.typography.bodyMedium)
                            Text("Switch between Dark Obsidian NOC and Light Technical mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.darkThemeEnabled,
                            onCheckedChange = onSetDarkTheme
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it },
                            label = { Text("Optional App PIN (Leave blank for no lock)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onUpdatePinLock(pinInput.trim(), false) }) {
                            Text("Save PIN")
                        }
                    }
                }
            }
        }

        // 3. Notification Customization
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "NOTIFICATION ALERTS CUSTOMIZATION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val toggles = listOf(
                        Triple("new_device", "New Device Connected", settings.notifyNewDevice),
                        Triple("unknown_device", "Unknown Client Detected", settings.notifyUnknownDevice),
                        Triple("blocked", "Internet Blocked / Restored", settings.notifyBlockedRestored),
                        Triple("quota_reached", "Data Quota Reached (0 MB)", settings.notifyQuotaReached),
                        Triple("speed_limit", "Speed Limit Applied", settings.notifySpeedLimit),
                        Triple("hotspot_state", "Hotspot & Service Status", settings.notifyHotspotState)
                    )
                    toggles.forEach { (key, label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = value,
                                onCheckedChange = { onToggleNotification(key, it) }
                            )
                        }
                    }
                }
            }
        }

        // 4. Security & Enforcement Action Audit History
        item {
            Text(
                text = "ACTION AUDIT LOGS (${uiState.actionLogs.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(uiState.actionLogs.take(30), key = { it.logId }) { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[${log.actionCategory}] ${log.title}",
                            style = MaterialTheme.typography.titleSmall,
                            color = NocCyanPrimary
                        )
                        Text(
                            text = NetworkFormatters.formatRelativeTime(log.timestampEpochMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = log.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
