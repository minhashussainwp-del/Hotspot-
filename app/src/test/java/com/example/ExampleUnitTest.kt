package com.example

import com.example.data.local.AccessState
import com.example.data.local.ConnectedDeviceEntity
import com.example.data.local.DeviceCategory
import com.example.data.local.HotspotDatabase
import com.example.data.local.PriorityLevel
import com.example.data.local.QuotaPeriod
import com.example.network.ClientGatewayPolicy
import com.example.network.UnrootedGatewayServer
import com.example.service.AutomationEvaluator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ExampleUnitTest {

    @Test
    fun `block one client while other clients remain online`() = runBlocking {
        val gateway = UnrootedGatewayServer()
        gateway.start(0)

        // Friend A: Allowed, Friend B: Blocked, Friend C: Allowed
        gateway.updateClientPolicy(ClientGatewayPolicy("192.168.43.101", AccessState.ALLOWED))
        gateway.updateClientPolicy(ClientGatewayPolicy("192.168.43.102", AccessState.BLOCKED_PERMANENT))
        gateway.updateClientPolicy(ClientGatewayPolicy("192.168.43.103", AccessState.ALLOWED))

        val payload = ByteArray(4096) { 0x42 }

        val sinkA = ByteArrayOutputStream()
        gateway.pumpShapedStream(ByteArrayInputStream(payload), sinkA, 0L, "192.168.43.101", isDownload = true)

        val sinkB = ByteArrayOutputStream()
        gateway.pumpShapedStream(ByteArrayInputStream(payload), sinkB, 0L, "192.168.43.102", isDownload = true)

        val sinkC = ByteArrayOutputStream()
        gateway.pumpShapedStream(ByteArrayInputStream(payload), sinkC, 0L, "192.168.43.103", isDownload = true)

        assertEquals(4096, sinkA.size())
        assertEquals(0, sinkB.size()) // Friend B is completely blocked
        assertEquals(4096, sinkC.size())

        // Restore internet to Friend B and verify traffic passes
        gateway.updateClientPolicy(ClientGatewayPolicy("192.168.43.102", AccessState.ALLOWED))
        val sinkBRestored = ByteArrayOutputStream()
        gateway.pumpShapedStream(ByteArrayInputStream(payload), sinkBRestored, 0L, "192.168.43.102", isDownload = true)
        assertEquals(4096, sinkBRestored.size())

        gateway.stop()
    }

    @Test
    fun `priority and fair sharing calculate distinct bandwidth allocations`() {
        val gateway = UnrootedGatewayServer()
        gateway.fairSharingEnabled = true
        gateway.updateClientPolicy(ClientGatewayPolicy("192.168.43.101", AccessState.ALLOWED))
        gateway.updateClientPolicy(ClientGatewayPolicy("192.168.43.102", AccessState.ALLOWED))

        val highRate = gateway.computeEffectiveRateBytesPerSec(
            deviceLimitKbps = 1000,
            globalLimitKbps = 0,
            priority = PriorityLevel.HIGH
        )
        val normalRate = gateway.computeEffectiveRateBytesPerSec(
            deviceLimitKbps = 1000,
            globalLimitKbps = 0,
            priority = PriorityLevel.NORMAL
        )
        val lowRate = gateway.computeEffectiveRateBytesPerSec(
            deviceLimitKbps = 1000,
            globalLimitKbps = 0,
            priority = PriorityLevel.LOW
        )

        assertTrue(highRate > normalRate)
        assertTrue(normalRate > lowRate)
    }

    @Test
    fun `quota calculation and automatic exhaustion block`() {
        val evaluator = AutomationEvaluator()
        val rules = HotspotDatabase.defaultAutomationRules()
        val profiles = HotspotDatabase.defaultProfiles().associateBy { it.profileId }

        val twoGb = 2000L * 1024L * 1024L
        val used1Point2Gb = 1200L * 1024L * 1024L
        val friendA = ConnectedDeviceEntity(
            deviceId = "ip_192.168.43.101",
            customName = "Friend A",
            detectedHostname = "Friend-A",
            ipAddress = "192.168.43.101",
            quotaPeriod = QuotaPeriod.MONTHLY,
            allocatedCombinedBytes = twoGb,
            downloadedBytes = used1Point2Gb,
            uploadedBytes = 0L,
            autoBlockOnQuota = true
        )

        // Remaining should be 800 MB
        assertEquals(800L * 1024L * 1024L, friendA.quotaRemainingBytes)
        val outcomeBefore = evaluator.evaluateDevicePolicies(friendA, rules, profiles)
        assertFalse(outcomeBefore.stateChanged)
        assertEquals(AccessState.ALLOWED, outcomeBefore.updatedDevice.accessState)

        // Exhaust quota (2 GB used) -> must automatically transition to QUOTA_EXHAUSTED
        val exhaustedFriendA = friendA.copy(downloadedBytes = twoGb)
        assertEquals(0L, exhaustedFriendA.quotaRemainingBytes)
        val outcomeAfter = evaluator.evaluateDevicePolicies(exhaustedFriendA, rules, profiles)
        assertTrue(outcomeAfter.stateChanged)
        assertEquals(AccessState.QUOTA_EXHAUSTED, outcomeAfter.updatedDevice.accessState)
    }

    @Test
    fun `overnight schedule window evaluation works across midnight`() {
        val evaluator = AutomationEvaluator()
        assertTrue(evaluator.isHourInWindow(currentHour = 23, startHour = 23, endHour = 6))
        assertTrue(evaluator.isHourInWindow(currentHour = 2, startHour = 23, endHour = 6))
        assertFalse(evaluator.isHourInWindow(currentHour = 12, startHour = 23, endHour = 6))
    }
}
