package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

enum class UpstreamSource(val label: String) {
    MOBILE_DATA("SIM Mobile Data (4G/5G/LTE)"),
    WIFI_SHARING("Wi-Fi Internet Sharing"),
    ETHERNET("Ethernet Upstream"),
    NONE("No Upstream Internet")
}

data class HotspotTelemetrySnapshot(
    val isHotspotActive: Boolean,
    val hotspotStateLabel: String,
    val downstreamInterfaceName: String,
    val downstreamIpv4: String,
    val downstreamIpv6: String,
    val subnetPrefix: String, // e.g., "192.168.43" or "192.168.232"
    val isInternetConnected: Boolean,
    val upstreamSource: UpstreamSource,
    val upstreamInterfaceName: String,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val currentDownloadSpeedBps: Long,
    val currentUploadSpeedBps: Long,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

class TetheringMonitor(private val context: Context) {

    private var lastSampleTimeMs: Long = 0L
    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L

    suspend fun sampleTelemetry(gatewayBytesRx: Long, gatewayBytesTx: Long): HotspotTelemetrySnapshot =
        withContext(Dispatchers.IO) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

            // 1. Detect Upstream Internet Source
            val activeNetwork = cm?.activeNetwork
            val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val upstreamSource = when {
                caps == null || !hasInternet -> UpstreamSource.NONE
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> UpstreamSource.MOBILE_DATA
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> UpstreamSource.WIFI_SHARING
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> UpstreamSource.ETHERNET
                else -> UpstreamSource.MOBILE_DATA
            }
            val upstreamIface = linkProps?.interfaceName ?: when (upstreamSource) {
                UpstreamSource.MOBILE_DATA -> "rmnet_data0"
                UpstreamSource.WIFI_SHARING -> "wlan0"
                UpstreamSource.ETHERNET -> "eth0"
                UpstreamSource.NONE -> "none"
            }

            // 2. Detect Downstream Hotspot / Tethering Interface
            val apStateReflection = try {
                val method = wifiManager?.javaClass?.getDeclaredMethod("getWifiApState")
                method?.isAccessible = true
                val state = method?.invoke(wifiManager) as? Int ?: 11
                // WIFI_AP_STATE_ENABLED is 13
                state == 13 || state == 3
            } catch (_: Exception) {
                false
            }

            var downstreamIface = ""
            var downstreamIpv4 = ""
            var downstreamIpv6 = ""

            val allInterfaces = try {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }

            // Look for dedicated tethering interfaces first (ap*, swlan*, wlan1, softap*, rndis*, ncm*)
            val tetherCandidateNames = listOf("ap0", "wlan1", "swlan0", "softap0", "rndis0", "ncm0", "ap_br_wlan1", "wlan2")
            val candidateNif = allInterfaces.firstOrNull { nif ->
                nif.isUp && !nif.isLoopback && (
                    tetherCandidateNames.any { nif.name.startsWith(it, ignoreCase = true) } ||
                        (nif.name != upstreamIface && nif.name.startsWith("wlan"))
                    )
            } ?: allInterfaces.firstOrNull { nif ->
                nif.isUp && !nif.isLoopback && nif.inetAddresses.toList().any { it is Inet4Address }
            }

            if (candidateNif != null) {
                downstreamIface = candidateNif.name
                for (addr in candidateNif.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        downstreamIpv4 = addr.hostAddress ?: ""
                    } else if (addr is Inet6Address && !addr.isLoopbackAddress) {
                        downstreamIpv6 = addr.hostAddress?.substringBefore("%") ?: ""
                    }
                }
            }

            val isHotspotTetherInterface = tetherCandidateNames.any {
                downstreamIface.startsWith(it, ignoreCase = true)
            } || apStateReflection

            val subnetPrefix = if (downstreamIpv4.count { it == '.' } == 3) {
                downstreamIpv4.substringBeforeLast(".")
            } else {
                "192.168.43"
            }

            // 3. Measure Real Interface / TrafficStats Counters
            val procCounters = readInterfaceBytes(downstreamIface)
            val osTotalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            val osTotalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)

            val currentRx = when {
                gatewayBytesRx > 0L -> gatewayBytesRx + (procCounters?.first ?: osTotalRx)
                procCounters != null -> procCounters.first
                else -> osTotalRx
            }
            val currentTx = when {
                gatewayBytesTx > 0L -> gatewayBytesTx + (procCounters?.second ?: osTotalTx)
                procCounters != null -> procCounters.second
                else -> osTotalTx
            }

            val now = System.currentTimeMillis()
            var dlSpeedBps = 0L
            var ulSpeedBps = 0L

            if (lastSampleTimeMs > 0L && now > lastSampleTimeMs) {
                val elapsedSec = ((now - lastSampleTimeMs) / 1000.0).coerceAtLeast(0.5)
                val deltaRx = (currentRx - lastRxBytes).coerceAtLeast(0L)
                val deltaTx = (currentTx - lastTxBytes).coerceAtLeast(0L)
                dlSpeedBps = (deltaRx / elapsedSec).toLong()
                ulSpeedBps = (deltaTx / elapsedSec).toLong()
            }

            lastSampleTimeMs = now
            lastRxBytes = currentRx
            lastTxBytes = currentTx

            val isHotspotActive = isHotspotTetherInterface || downstreamIpv4.isNotEmpty()
            val statusLabel = when {
                isHotspotTetherInterface -> "Hotspot Active ($downstreamIface)"
                downstreamIpv4.isNotEmpty() -> "Local Gateway Ready ($downstreamIface)"
                else -> "Hotspot Standby"
            }

            HotspotTelemetrySnapshot(
                isHotspotActive = isHotspotActive,
                hotspotStateLabel = statusLabel,
                downstreamInterfaceName = downstreamIface.ifEmpty { "ap0" },
                downstreamIpv4 = downstreamIpv4.ifEmpty { "192.168.43.1" },
                downstreamIpv6 = downstreamIpv6,
                subnetPrefix = subnetPrefix,
                isInternetConnected = hasInternet,
                upstreamSource = upstreamSource,
                upstreamInterfaceName = upstreamIface,
                totalRxBytes = currentRx,
                totalTxBytes = currentTx,
                currentDownloadSpeedBps = dlSpeedBps,
                currentUploadSpeedBps = ulSpeedBps,
                timestampEpochMs = now
            )
        }

    private fun readInterfaceBytes(ifaceName: String): Pair<Long, Long>? {
        if (ifaceName.isBlank()) return null
        return try {
            val rxFile = File("/sys/class/net/$ifaceName/statistics/rx_bytes")
            val txFile = File("/sys/class/net/$ifaceName/statistics/tx_bytes")
            if (rxFile.canRead() && txFile.canRead()) {
                val rx = rxFile.readText().trim().toLongOrNull() ?: return null
                val tx = txFile.readText().trim().toLongOrNull() ?: return null
                rx to tx
            } else {
                val devFile = File("/proc/net/dev")
                if (!devFile.canRead()) return null
                val line = devFile.readLines().firstOrNull { it.trim().startsWith("$ifaceName:") } ?: return null
                val parts = line.substringAfter(":").trim().split(Regex("\\s+"))
                if (parts.size >= 9) {
                    val rx = parts[0].toLongOrNull() ?: return null
                    val tx = parts[8].toLongOrNull() ?: return null
                    rx to tx
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
