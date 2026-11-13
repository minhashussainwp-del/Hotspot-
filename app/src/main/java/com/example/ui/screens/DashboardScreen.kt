package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.network.UpstreamSource
import com.example.ui.components.RealTimeThroughputCanvasGraph
import com.example.ui.components.StatusPulseDot
import com.example.ui.components.TacticalMetricCard
import com.example.ui.theme.NocAmberWarning
import com.example.ui.theme.NocCrimsonDanger
import com.example.ui.theme.NocCyanPrimary
import com.example.ui.theme.NocEmeraldSecondary
import com.example.ui.theme.NocObsidianBg
import com.example.ui.theme.NocPurpleAccent
import com.example.util.NetworkFormatters
import com.example.viewmodel.HotspotUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    uiState: HotspotUiState,
    onRefreshDevices: () -> Unit,
    onBlockAll: () -> Unit,
    onAllowAllTrusted: () -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onOpenSystemTethering: () -> Unit,
    onStartForegroundMonitor: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToCapabilities: () -> Unit
) {
    val context = LocalContext.current
    val snap = uiState.telemetry

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero NOC Telemetry Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, NocCyanPrimary.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(162.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_noc_banner_1794565175024),
                        contentDescription = "NOC Telemetry Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        NocObsidianBg.copy(alpha = 0.55f),
                                        NocObsidianBg.copy(alpha = 0.94f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusPulseDot(isOnline = snap.isHotspotActive)
                                Text(
                                    text = snap.hotspotStateLabel.uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (snap.isHotspotActive) NocEmeraldSecondary else NocAmberWarning
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NocCyanPrimary.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, NocCyanPrimary.copy(alpha = 0.5f)),
                                onClick = onNavigateToCapabilities
                            ) {
                                Text(
                                    text = "UNROOTED GATEWAY :${uiState.gatewayBoundPort}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NocCyanPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "HOTSPOT CONTROL PRO",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Downstream ${snap.downstreamInterfaceName} (${snap.downstreamIpv4}) • Upstream ${snap.upstreamSource.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = NocCyanPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.statusBannerMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isScanningSubnet) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NocCyanPrimary
                )
            }
        }

        // 2. Real-time Speed Graph
        item {
            RealTimeThroughputCanvasGraph(
                samples = uiState.recentGlobalSamples,
                currentDlBps = uiState.totalClientDownloadSpeedBps,
                currentUlBps = uiState.totalClientUploadSpeedBps
            )
        }

        // 3. Complete 11-Metric NOC Telemetry Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TacticalMetricCard(
                        label = "Internet Source",
                        value = when (snap.upstreamSource) {
                            UpstreamSource.MOBILE_DATA -> "SIM Data"
                            UpstreamSource.WIFI_SHARING -> "Wi-Fi Share"
                            UpstreamSource.ETHERNET -> "Ethernet"
                            UpstreamSource.NONE -> "Offline"
                        },
                        subValue = "${snap.upstreamInterfaceName} • ${if (snap.isInternetConnected) "Online" else "No Link"}",
                        icon = if (snap.upstreamSource == UpstreamSource.WIFI_SHARING) Icons.Default.Wifi else Icons.Default.CellTower,
                        accentColor = if (snap.isInternetConnected) NocEmeraldSecondary else NocCrimsonDanger,
                        modifier = Modifier.weight(1f)
                    )
                    TacticalMetricCard(
                        label = "Connected Clients",
                        value = "${uiState.onlineDevices.size} Online",
                        subValue = "${uiState.devices.size} total known • ${uiState.blockedDevices.size} blocked",
                        icon = Icons.Default.Devices,
                        accentColor = NocCyanPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TacticalMetricCard(
                        label = "Total Download",
                        value = NetworkFormatters.formatSpeedBps(uiState.totalClientDownloadSpeedBps),
                        subValue = "Consumed: ${NetworkFormatters.formatBytes(uiState.devices.sumOf { it.downloadedBytes } + snap.totalRxBytes)}",
                        icon = Icons.Default.Download,
                        accentColor = NocCyanPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TacticalMetricCard(
                        label = "Total Upload",
                        value = NetworkFormatters.formatSpeedBps(uiState.totalClientUploadSpeedBps),
                        subValue = "Transmitted: ${NetworkFormatters.formatBytes(uiState.devices.sumOf { it.uploadedBytes } + snap.totalTxBytes)}",
                        icon = Icons.Default.Upload,
                        accentColor = NocEmeraldSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TacticalMetricCard(
                        label = "Daily / Session Usage",
                        value = NetworkFormatters.formatBytes(uiState.totalClientConsumedBytes),
                        subValue = if (uiState.totalRemainingAllocatedBytes > 0L) {
                            "Rem Pool: ${NetworkFormatters.formatBytes(uiState.totalRemainingAllocatedBytes)}"
                        } else {
                            "Quota Pool: Unlimited"
                        },
                        icon = Icons.Default.DataUsage,
                        accentColor = NocAmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                    TacticalMetricCard(
                        label = "Uptime & Rules",
                        value = NetworkFormatters.formatDurationSeconds(uiState.uptimeSeconds),
                        subValue = "${uiState.activeBandwidthRulesCount} active speed/block rules",
                        icon = Icons.Default.Timer,
                        accentColor = NocPurpleAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Global Operations Control Deck
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GLOBAL TETHERING & CLIENT CONTROLS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Verified Unrooted Actions",
                            style = MaterialTheme.typography.labelSmall,
                            color = NocEmeraldSecondary
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRefreshDevices,
                            colors = ButtonDefaults.buttonColors(containerColor = NocCyanPrimary, contentColor = Color.Black),
                            modifier = Modifier.testTag("btn_refresh_devices")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh Clients")
                        }

                        FilledTonalButton(
                            onClick = onAllowAllTrusted,
                            modifier = Modifier.testTag("btn_allow_trusted")
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Allow Trusted", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Allow All Trusted")
                        }

                        FilledTonalButton(
                            onClick = onPauseAll,
                            modifier = Modifier.testTag("btn_pause_all")
                        ) {
                            Icon(Icons.Default.PauseCircle, contentDescription = "Pause All", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause All Internet")
                        }

                        FilledTonalButton(
                            onClick = onResumeAll,
                            modifier = Modifier.testTag("btn_resume_all")
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = "Resume All", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume All")
                        }

                        Button(
                            onClick = onBlockAll,
                            colors = ButtonDefaults.buttonColors(containerColor = NocCrimsonDanger, contentColor = Color.White),
                            modifier = Modifier.testTag("btn_block_all")
                        ) {
                            Icon(Icons.Default.Block, contentDescription = "Block All", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Block All Devices")
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // System Hotspot Hardware Controls section (honest separation of OS privilege boundary)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SYSTEM HOTSPOT RADIO & BACKGROUND SERVICE",
                                style = MaterialTheme.typography.labelMedium,
                                color = NocAmberWarning
                            )
                            Text(
                                text = "Android 8.0+ reserves direct radio toggling for system Settings (TETHER_PRIVILEGED). Use the shortcut below to toggle the system radio, or keep the Unrooted Gateway Service running.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onOpenSystemTethering,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_system_hotspot")
                                ) {
                                    Icon(Icons.Default.WifiTethering, contentDescription = "Hotspot Radio", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Hotspot Radio")
                                }
                                OutlinedButton(
                                    onClick = onStartForegroundMonitor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_keep_service_alive")
                                ) {
                                    Icon(Icons.Default.Router, contentDescription = "Monitor Service", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pin Monitor")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
