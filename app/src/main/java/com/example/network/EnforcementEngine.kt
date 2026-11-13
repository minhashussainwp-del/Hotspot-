package com.example.network

import com.example.data.local.AccessState
import com.example.data.local.ConnectedDeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class EnforcementResult(
    val success: Boolean,
    val adapterUsed: String, // "UNROOTED_GATEWAY" or "ROOT_KERNEL_IPTABLES_TC"
    val statusBadge: String,
    val detailMessage: String
)

/**
 * Capability-aware Network Enforcement Engine:
 * 1. UNROOTED STANDARD MODE (Default): Applies policies to the UnrootedGatewayServer
 *    (real socket blocking + token-bucket speed shaping) and verifies stream enforcement.
 * 2. ROOT MODE (Optional when su + iptables/tc exist): Applies validated iptables/ip6tables
 *    FORWARD rules and Linux tc HTB classes on the downstream hotspot interface only,
 *    never touching the owner's upstream interface.
 */
class EnforcementEngine(
    val gatewayServer: UnrootedGatewayServer
) {
    private val ipPattern = Regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}$")
    private val ipv6Pattern = Regex("^[0-9a-fA-F:]+$")
    private val ifacePattern = Regex("^[a-zA-Z0-9_.-]{1,16}$")

    private val activeRootBlockedIps = ConcurrentHashMap.newKeySet<String>()
    private val activeRootShapedInterfaces = ConcurrentHashMap.newKeySet<String>()

    suspend fun applyDevicePolicy(
        device: ConnectedDeviceEntity,
        downstreamInterface: String,
        useRootIfAvailable: Boolean
    ): EnforcementResult = withContext(Dispatchers.IO) {
        // Always keep the Unrooted Gateway Server updated and running
        if (!gatewayServer.isRunning()) {
            gatewayServer.start()
        }

        val gatewayPolicy = ClientGatewayPolicy(
            ipAddress = device.ipAddress,
            accessState = device.accessState,
            downloadLimitKbps = device.downloadLimitKbps,
            uploadLimitKbps = device.uploadLimitKbps,
            priorityLevel = device.priorityLevel,
            remainingQuotaBytes = device.quotaRemainingBytes
        )
        val gatewayCheck = gatewayServer.verifyEnforcementForClient(gatewayPolicy)

        // If Root mode is explicitly requested and available, also apply kernel iptables/tc rules
        if (useRootIfAvailable && isValidIp(device.ipAddress) && isValidIface(downstreamInterface)) {
            val rootSuccess = applyRootRulesForDevice(device, downstreamInterface)
            if (rootSuccess) {
                return@withContext EnforcementResult(
                    success = true,
                    adapterUsed = "ROOT_KERNEL_IPTABLES_TC",
                    statusBadge = "Enforced (Kernel + Gateway :${gatewayCheck.listeningPort})",
                    detailMessage = "Kernel iptables/tc on $downstreamInterface + Gateway verified (${gatewayCheck.message})"
                )
            }
        }

        val badge = when {
            device.accessState != AccessState.ALLOWED ->
                "Blocked via Gateway :${gatewayCheck.listeningPort}"
            device.downloadLimitKbps > 0 || device.uploadLimitKbps > 0 ->
                "Shaped (${device.downloadLimitKbps}↓/${device.uploadLimitKbps}↑ KB/s) on :${gatewayCheck.listeningPort}"
            else ->
                "Allowed on Gateway :${gatewayCheck.listeningPort}"
        }

        EnforcementResult(
            success = gatewayCheck.blockedTestPassed,
            adapterUsed = "UNROOTED_GATEWAY",
            statusBadge = badge,
            detailMessage = gatewayCheck.message
        )
    }

    private fun applyRootRulesForDevice(
        device: ConnectedDeviceEntity,
        downstreamInterface: String
    ): Boolean {
        if (!isValidIp(device.ipAddress) || !isValidIface(downstreamInterface)) return false
        val ip = device.ipAddress
        return try {
            val commands = mutableListOf<String>()
            // Always clean existing FORWARD drop rule for this IP first to avoid duplicates
            commands.add("iptables -D FORWARD -i $downstreamInterface -s $ip -j DROP 2>/dev/null || true")
            commands.add("iptables -D FORWARD -o $downstreamInterface -d $ip -j DROP 2>/dev/null || true")

            if (device.accessState != AccessState.ALLOWED) {
                commands.add("iptables -I FORWARD 1 -i $downstreamInterface -s $ip -j DROP")
                commands.add("iptables -I FORWARD 1 -o $downstreamInterface -d $ip -j DROP")
                if (device.ipv6Address.isNotBlank() && ipv6Pattern.matches(device.ipv6Address)) {
                    commands.add("ip6tables -D FORWARD -s ${device.ipv6Address} -j DROP 2>/dev/null || true")
                    commands.add("ip6tables -I FORWARD 1 -s ${device.ipv6Address} -j DROP")
                }
                activeRootBlockedIps.add(ip)
            } else {
                activeRootBlockedIps.remove(ip)
            }

            // Apply tc HTB download ceiling on downstream interface if configured
            if (device.downloadLimitKbps > 0 && device.accessState == AccessState.ALLOWED) {
                val classMinor = ip.substringAfterLast(".").toIntOrNull()?.coerceIn(2, 254) ?: 10
                val rateKbit = (device.downloadLimitKbps * 8).coerceAtLeast(64)
                val prioNum = when (device.priorityLevel) {
                    "HIGH" -> 1
                    "LOW" -> 3
                    else -> 2
                }
                commands.add("tc qdisc add dev $downstreamInterface root handle 1: htb default 99 2>/dev/null || true")
                commands.add("tc class replace dev $downstreamInterface parent 1: classid 1:$classMinor htb rate ${rateKbit}kbit ceil ${rateKbit}kbit prio $prioNum")
                commands.add("tc filter replace dev $downstreamInterface protocol ip parent 1:0 prio $prioNum u32 match ip dst $ip/32 flowid 1:$classMinor")
                activeRootShapedInterfaces.add(downstreamInterface)
            }

            // Execute and verify rule presence
            val joined = commands.joinToString(" && ")
            val proc = ProcessBuilder("su", "-c", joined).redirectErrorStream(true).start()
            val exitCode = proc.waitFor()
            if (exitCode != 0) return false

            if (device.accessState != AccessState.ALLOWED) {
                val checkProc = ProcessBuilder("su", "-c", "iptables -C FORWARD -i $downstreamInterface -s $ip -j DROP")
                    .redirectErrorStream(true).start()
                checkProc.waitFor() == 0
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreAllNetworkRules(downstreamInterface: String) = withContext(Dispatchers.IO) {
        for (ip in activeRootBlockedIps.toList()) {
            if (isValidIp(ip) && isValidIface(downstreamInterface)) {
                try {
                    val cmd = "iptables -D FORWARD -i $downstreamInterface -s $ip -j DROP 2>/dev/null || true; " +
                        "iptables -D FORWARD -o $downstreamInterface -d $ip -j DROP 2>/dev/null || true"
                    ProcessBuilder("su", "-c", cmd).start().waitFor()
                } catch (_: Exception) {
                }
            }
        }
        activeRootBlockedIps.clear()

        for (iface in activeRootShapedInterfaces.toList()) {
            if (isValidIface(iface)) {
                try {
                    ProcessBuilder("su", "-c", "tc qdisc del dev $iface root 2>/dev/null || true").start().waitFor()
                } catch (_: Exception) {
                }
            }
        }
        activeRootShapedInterfaces.clear()
    }

    private fun isValidIp(ip: String): Boolean = ipPattern.matches(ip)
    private fun isValidIface(iface: String): Boolean = ifacePattern.matches(iface)
}
