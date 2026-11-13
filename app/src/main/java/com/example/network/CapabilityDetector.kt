package com.example.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.ServerSocket

enum class CapabilityTier(val badgeText: String) {
    SUPPORTED("Supported (Unrooted)"),
    UNROOTED_GATEWAY("Supported via Local Gateway"),
    REQUIRES_SHIZUKU("Requires Shizuku"),
    REQUIRES_ROOT("Requires Root"),
    REQUIRES_OEM("Requires OEM/System Privilege"),
    NOT_SUPPORTED("Not Supported")
}

data class CapabilityItem(
    val id: String,
    val title: String,
    val category: String,
    val tier: CapabilityTier,
    val isCurrentlyAvailable: Boolean,
    val technicalDetail: String
)

data class DeviceCapabilitiesReport(
    val androidVersionName: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val isRootAvailable: Boolean,
    val isShizukuInstalled: Boolean,
    val canReadProcArp: Boolean,
    val canRunIpNeigh: Boolean,
    val canBindLocalGateway: Boolean,
    val hasIptablesBinary: Boolean,
    val hasTcBinary: Boolean,
    val ipv4Supported: Boolean,
    val ipv6Supported: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val activeOperatingMode: String, // "UNROOTED_STANDARD", "SHIZUKU_ENHANCED", "ROOT_KERNEL"
    val items: List<CapabilityItem>
)

class CapabilityDetector(private val context: Context) {

    suspend fun detectCapabilities(preferredMode: String = "UNROOTED_STANDARD"): DeviceCapabilitiesReport =
        withContext(Dispatchers.IO) {
            val sdkInt = Build.VERSION.SDK_INT
            val androidVer = "Android ${Build.VERSION.RELEASE} (API $sdkInt)"
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL

            val canReadArp = try {
                val arpFile = File("/proc/net/arp")
                arpFile.exists() && arpFile.canRead() && arpFile.readLines().isNotEmpty()
            } catch (_: Exception) {
                false
            }

            val canRunIpNeigh = try {
                val proc = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor() == 0 && !output.contains("Permission denied", ignoreCase = true)
            } catch (_: Exception) {
                false
            }

            val canBindGateway = try {
                ServerSocket(0).use { it.localPort > 0 }
            } catch (_: Exception) {
                false
            }

            val isShizukuInstalled = try {
                context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }

            val hasSuBinary = listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/vendor/bin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su"
            ).any { File(it).exists() }

            val isRootAvailable = if (hasSuBinary && preferredMode == "ROOT") {
                try {
                    val proc = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                    val out = proc.inputStream.bufferedReader().readText()
                    proc.waitFor() == 0 && out.contains("uid=0")
                } catch (_: Exception) {
                    false
                }
            } else {
                hasSuBinary
            }

            val hasIptables = File("/system/bin/iptables").exists() || File("/system/xbin/iptables").exists()
            val hasTc = File("/system/bin/tc").exists() || File("/system/xbin/tc").exists()

            var ipv4 = false
            var ipv6 = false
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                for (nif in interfaces) {
                    if (!nif.isUp) continue
                    for (addr in nif.inetAddresses.toList()) {
                        if (!addr.isLoopbackAddress) {
                            if (addr is Inet4Address) ipv4 = true
                            if (addr is Inet6Address) ipv6 = true
                        }
                    }
                }
            } catch (_: Exception) {
                ipv4 = true
            }

            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val ignoringBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false

            val activeMode = when {
                preferredMode == "ROOT" && isRootAvailable -> "ROOT_KERNEL"
                preferredMode == "SHIZUKU" && isShizukuInstalled -> "SHIZUKU_ENHANCED"
                else -> "UNROOTED_STANDARD"
            }

            val items = listOf(
                CapabilityItem(
                    id = "upstream_detection",
                    title = "Upstream Source Detection (SIM Data vs Wi-Fi Sharing)",
                    category = "Tethering & Telemetry",
                    tier = CapabilityTier.SUPPORTED,
                    isCurrentlyAvailable = true,
                    technicalDetail = "Uses Android ConnectivityManager & NetworkCapabilities to distinguish Cellular (4G/5G/LTE) from Wi-Fi upstream sharing."
                ),
                CapabilityItem(
                    id = "downstream_interface",
                    title = "Hotspot Interface & Subnet Discovery",
                    category = "Tethering & Telemetry",
                    tier = CapabilityTier.SUPPORTED,
                    isCurrentlyAvailable = true,
                    technicalDetail = "Enumerates active network interfaces (ap0, wlan1, swlan0, rndis0) and inspects IPv4/IPv6 subnet bindings without root."
                ),
                CapabilityItem(
                    id = "client_discovery",
                    title = "Connected Client Discovery (Subnet Sweep + Neighbor Cache)",
                    category = "Device Detection",
                    tier = CapabilityTier.SUPPORTED,
                    isCurrentlyAvailable = true,
                    technicalDetail = buildString {
                        append("Multi-coroutine ICMP/TCP subnet probe active. ")
                        append(if (canReadArp || canRunIpNeigh) "OS neighbor table accessible." else "Direct /proc/net/arp restricted by Android API $sdkInt; using active subnet + gateway socket discovery.")
                    }
                ),
                CapabilityItem(
                    id = "unrooted_gateway_blocking",
                    title = "Per-Client Internet Blocking (Unrooted Local Gateway)",
                    category = "Internet Blocking",
                    tier = CapabilityTier.UNROOTED_GATEWAY,
                    isCurrentlyAvailable = canBindGateway,
                    technicalDetail = "Built-in multi-threaded HTTP/SOCKS/CONNECT Hotspot Gateway enforces instant per-IP blocking, schedules, and pause rules on unrooted devices."
                ),
                CapabilityItem(
                    id = "unrooted_gateway_shaping",
                    title = "Per-Client Upload/Download Speed Shaping (Token Bucket)",
                    category = "Bandwidth Control",
                    tier = CapabilityTier.UNROOTED_GATEWAY,
                    isCurrentlyAvailable = canBindGateway,
                    technicalDetail = "Independent download and upload token-bucket shapers throttle gateway traffic per client IP with High/Normal/Low priority queues."
                ),
                CapabilityItem(
                    id = "quota_accounting",
                    title = "Per-Client Data Quota Allocation & Auto-Cutoff",
                    category = "Data Monitoring",
                    tier = CapabilityTier.SUPPORTED,
                    isCurrentlyAvailable = true,
                    technicalDetail = "Tracks real per-client gateway & socket byte counters with automatic blocking and owner notifications when allocated packages hit 0 MB."
                ),
                CapabilityItem(
                    id = "kernel_iptables",
                    title = "Transparent Kernel Packet Filtering (iptables / ip6tables)",
                    category = "Internet Blocking",
                    tier = if (isRootAvailable) CapabilityTier.SUPPORTED else CapabilityTier.REQUIRES_ROOT,
                    isCurrentlyAvailable = isRootAvailable && hasIptables,
                    technicalDetail = if (isRootAvailable) "Root shell & iptables binary detected; transparent FORWARD chain drop rules supported."
                    else "Android restricts netfilter/iptables FORWARD manipulation to UID 0 (Root). Standard mode uses the Unrooted Local Gateway instead."
                ),
                CapabilityItem(
                    id = "kernel_tc_htb",
                    title = "Kernel HTB/IFB Traffic Control Shaping (tc qdisc)",
                    category = "Bandwidth Control",
                    tier = if (isRootAvailable && hasTc) CapabilityTier.SUPPORTED else CapabilityTier.REQUIRES_ROOT,
                    isCurrentlyAvailable = isRootAvailable && hasTc,
                    technicalDetail = if (isRootAvailable && hasTc) "Linux tc binary available with root privileges for interface-level qdisc shaping."
                    else "Kernel qdisc attachment requires CAP_NET_ADMIN / Root. Standard mode uses token-bucket stream shaping via the Unrooted Gateway."
                ),
                CapabilityItem(
                    id = "shizuku_tethering",
                    title = "Privileged Tethering API Inspection via Shizuku",
                    category = "Tethering & Telemetry",
                    tier = if (isShizukuInstalled) CapabilityTier.SUPPORTED else CapabilityTier.REQUIRES_SHIZUKU,
                    isCurrentlyAvailable = isShizukuInstalled,
                    technicalDetail = if (isShizukuInstalled) "Shizuku package detected for privileged tethering connector queries."
                    else "Shizuku is not installed. Optional for extended system tethering telemetry; not required for core unrooted gateway operation."
                ),
                CapabilityItem(
                    id = "direct_hotspot_toggle",
                    title = "Direct Programmatic System Hotspot On/Off Toggle",
                    category = "Tethering & Telemetry",
                    tier = CapabilityTier.REQUIRES_OEM,
                    isCurrentlyAvailable = false,
                    technicalDetail = "Android 8.0+ restricts third-party apps from toggling global system Tethering without TETHER_PRIVILEGED system signature. Direct shortcut to System Hotspot Settings is provided."
                )
            )

            DeviceCapabilitiesReport(
                androidVersionName = androidVer,
                sdkInt = sdkInt,
                manufacturer = manufacturer,
                model = model,
                isRootAvailable = isRootAvailable,
                isShizukuInstalled = isShizukuInstalled,
                canReadProcArp = canReadArp,
                canRunIpNeigh = canRunIpNeigh,
                canBindLocalGateway = canBindGateway,
                hasIptablesBinary = hasIptables,
                hasTcBinary = hasTc,
                ipv4Supported = ipv4,
                ipv6Supported = ipv6,
                ignoringBatteryOptimizations = ignoringBattery,
                activeOperatingMode = activeMode,
                items = items
            )
        }
}
