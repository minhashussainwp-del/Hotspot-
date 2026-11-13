package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class DiscoveredClient(
    val stableId: String,
    val ipAddress: String,
    val ipv6Address: String = "",
    val macAddress: String,
    val hostname: String,
    val vendor: String,
    val interfaceName: String,
    val discoverySource: String // "ARP_NEIGHBOR", "SUBNET_PROBE", "GATEWAY_SOCKET"
)

class ClientDiscoveryEngine {

    private val ouiVendorMap = mapOf(
        "00:1A:11" to "Google Pixel",
        "3C:5A:B4" to "Google Inc.",
        "F4:F5:D8" to "Google Nest",
        "A4:77:33" to "Google Device",
        "DC:A6:32" to "Raspberry Pi",
        "B8:27:EB" to "Raspberry Pi",
        "00:1B:63" to "Apple iPhone/Mac",
        "F0:18:98" to "Apple Inc.",
        "BC:D0:74" to "Apple Device",
        "28:6A:BA" to "Samsung Galaxy",
        "50:01:BB" to "Samsung Electronics",
        "CC:07:AB" to "Samsung Mobile",
        "64:CC:2E" to "Xiaomi Mobile",
        "28:6C:07" to "Xiaomi Corp.",
        "E0:DC:FF" to "OnePlus Tech",
        "00:E0:4C" to "Realtek Adapter",
        "02:15:B2" to "Android Virtual/Tether"
    )

    suspend fun discoverConnectedClients(
        subnetPrefix: String,
        selfIp: String,
        downstreamInterface: String,
        activeGatewayClients: Set<String>
    ): List<DiscoveredClient> = withContext(Dispatchers.IO) {
        val discoveredByIp = linkedMapOf<String, DiscoveredClient>()

        // 1. Parse /proc/net/arp if readable
        parseProcArp(selfIp, downstreamInterface).forEach { client ->
            discoveredByIp[client.ipAddress] = client
        }

        // 2. Parse `ip neigh show` output
        parseIpNeigh(selfIp, downstreamInterface).forEach { client ->
            val existing = discoveredByIp[client.ipAddress]
            if (existing == null || existing.macAddress.startsWith("UNAVAILABLE")) {
                discoveredByIp[client.ipAddress] = client
            }
        }

        // 3. Include any client actively connected to the Unrooted Hotspot Gateway Server
        for (clientIp in activeGatewayClients) {
            if (clientIp.isNotBlank() && clientIp != selfIp && clientIp != "127.0.0.1" && !discoveredByIp.containsKey(clientIp)) {
                val host = resolveHostnameFast(clientIp)
                discoveredByIp[clientIp] = DiscoveredClient(
                    stableId = "gw_$clientIp",
                    ipAddress = clientIp,
                    macAddress = "UNAVAILABLE (OS PRIVACY)",
                    hostname = host,
                    vendor = "Hotspot Gateway Client",
                    interfaceName = downstreamInterface,
                    discoverySource = "GATEWAY_SOCKET"
                )
            }
        }

        // 4. Fast Parallel Subnet Reachability Sweep across hotspot DHCP range
        // Standard Android hotspots assign 192.168.43.2..254 or 192.168.x.2..60
        if (subnetPrefix.count { it == '.' } == 2) {
            val candidateOctets = (2..64).toList() + listOf(100, 101, 102, 103, 104, 105, 128, 200, 254)
            val reachableIps = coroutineScope {
                candidateOctets.distinct().map { hostOctet ->
                    async {
                        val targetIp = "$subnetPrefix.$hostOctet"
                        if (targetIp == selfIp || discoveredByIp.containsKey(targetIp)) {
                            null
                        } else if (probeHostReachable(targetIp)) {
                            targetIp
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            // Re-check ip neigh after probing to see if the kernel populated neighbor entries
            val postProbeNeigh = parseIpNeigh(selfIp, downstreamInterface).associateBy { it.ipAddress }

            for (ip in reachableIps) {
                val neighMatch = postProbeNeigh[ip]
                if (neighMatch != null) {
                    discoveredByIp[ip] = neighMatch
                } else {
                    val host = resolveHostnameFast(ip)
                    discoveredByIp[ip] = DiscoveredClient(
                        stableId = "ip_$ip",
                        ipAddress = ip,
                        macAddress = "UNAVAILABLE (OS PRIVACY)",
                        hostname = host,
                        vendor = inferVendorFromHostname(host),
                        interfaceName = downstreamInterface,
                        discoverySource = "SUBNET_PROBE"
                    )
                }
            }
        }

        discoveredByIp.values.toList()
    }

    private fun parseProcArp(selfIp: String, defaultIface: String): List<DiscoveredClient> {
        val result = mutableListOf<DiscoveredClient>()
        try {
            val arpFile = File("/proc/net/arp")
            if (!arpFile.canRead()) return emptyList()
            val lines = arpFile.readLines().drop(1)
            for (line in lines) {
                val cols = line.trim().split(Regex("\\s+"))
                if (cols.size >= 6) {
                    val ip = cols[0]
                    val flags = cols[2]
                    val mac = cols[3].uppercase()
                    val iface = cols[5]
                    if (ip != selfIp && flags != "0x0" && mac != "00:00:00:00:00:00") {
                        val host = resolveHostnameFast(ip)
                        val stableId = if (isLocallyAdministeredMac(mac)) "mac_${mac}_$ip" else "mac_$mac"
                        result.add(
                            DiscoveredClient(
                                stableId = stableId,
                                ipAddress = ip,
                                macAddress = mac,
                                hostname = host,
                                vendor = lookupVendor(mac, host),
                                interfaceName = iface.ifBlank { defaultIface },
                                discoverySource = "ARP_NEIGHBOR"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun parseIpNeigh(selfIp: String, defaultIface: String): List<DiscoveredClient> {
        val result = mutableListOf<DiscoveredClient>()
        try {
            val proc = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
            val lines = proc.inputStream.bufferedReader().readLines()
            proc.waitFor()
            for (line in lines) {
                if (line.contains("FAILED", ignoreCase = true) || line.contains("INCOMPLETE", ignoreCase = true)) continue
                val tokens = line.trim().split(Regex("\\s+"))
                if (tokens.isEmpty()) continue
                val ip = tokens[0]
                if (ip == selfIp || ip.startsWith("fe80:") || ip == "127.0.0.1") continue
                val devIdx = tokens.indexOf("dev")
                val iface = if (devIdx >= 0 && devIdx + 1 < tokens.size) tokens[devIdx + 1] else defaultIface
                val lladdrIdx = tokens.indexOf("lladdr")
                val mac = if (lladdrIdx >= 0 && lladdrIdx + 1 < tokens.size) {
                    tokens[lladdrIdx + 1].uppercase()
                } else {
                    "UNAVAILABLE (OS PRIVACY)"
                }
                val host = resolveHostnameFast(ip)
                val stableId = if (!mac.startsWith("UNAVAILABLE") && !isLocallyAdministeredMac(mac)) {
                    "mac_$mac"
                } else {
                    "ip_$ip"
                }
                result.add(
                    DiscoveredClient(
                        stableId = stableId,
                        ipAddress = ip,
                        macAddress = mac,
                        hostname = host,
                        vendor = lookupVendor(mac, host),
                        interfaceName = iface,
                        discoverySource = "ARP_NEIGHBOR"
                    )
                )
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun probeHostReachable(ip: String): Boolean {
        try {
            val addr = InetAddress.getByName(ip)
            if (addr.isReachable(120)) return true
        } catch (_: Exception) {
        }
        // Quick TCP probe on common mobile/laptop ports (53, 80, 443, 5353, 62078 iPhone sync)
        val probePorts = intArrayOf(80, 443, 62078, 53)
        for (port in probePorts) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 75)
                    return true
                }
            } catch (e: Exception) {
                // Connection refused (ECONNREFUSED) still proves the host is alive on the LAN!
                if (e.message?.contains("Connection refused", ignoreCase = true) == true) {
                    return true
                }
            }
        }
        return false
    }

    private fun resolveHostnameFast(ip: String): String {
        return try {
            val addr = InetAddress.getByName(ip)
            val host = addr.canonicalHostName
            if (host != null && host != ip) host else "Client-${ip.substringAfterLast(".")}"
        } catch (_: Exception) {
            "Client-${ip.substringAfterLast(".")}"
        }
    }

    private fun isLocallyAdministeredMac(mac: String): Boolean {
        if (mac.length < 2) return true
        val secondChar = mac[1].uppercaseChar()
        return secondChar == '2' || secondChar == '6' || secondChar == 'A' || secondChar == 'E'
    }

    private fun lookupVendor(mac: String, hostname: String): String {
        if (mac.length >= 8) {
            val prefix = mac.take(8).uppercase()
            ouiVendorMap[prefix]?.let { return it }
            if (isLocallyAdministeredMac(mac)) {
                return "Randomized MAC (Android 10+ / iOS)"
            }
        }
        return inferVendorFromHostname(hostname)
    }

    private fun inferVendorFromHostname(hostname: String): String {
        val lower = hostname.lowercase()
        return when {
            lower.contains("iphone") || lower.contains("ipad") || lower.contains("macbook") -> "Apple Device"
            lower.contains("galaxy") || lower.contains("samsung") -> "Samsung Device"
            lower.contains("pixel") || lower.contains("android") -> "Android Smartphone"
            lower.contains("desktop") || lower.contains("laptop") || lower.contains("win") -> "Windows PC"
            else -> "Wi-Fi Hotspot Client"
        }
    }
}
