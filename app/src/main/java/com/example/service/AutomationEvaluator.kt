package com.example.service

import com.example.data.local.AccessState
import com.example.data.local.AutomationRuleEntity
import com.example.data.local.ConnectedDeviceEntity
import com.example.data.local.DeviceCategory
import com.example.data.local.DeviceProfileEntity
import java.util.Calendar

data class AutomationEvaluationOutcome(
    val updatedDevice: ConnectedDeviceEntity,
    val stateChanged: Boolean,
    val reasonTitle: String = "",
    val reasonDetail: String = "",
    val notificationType: String = ""
)

class AutomationEvaluator {

    fun evaluateDevicePolicies(
        device: ConnectedDeviceEntity,
        rules: List<AutomationRuleEntity>,
        profilesById: Map<String, DeviceProfileEntity>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): AutomationEvaluationOutcome {
        var current = device
        var changed = false
        var title = ""
        var detail = ""
        var notifType = ""

        // 1. Check if a Temporary Block has expired -> Automatically restore internet
        if (current.accessState == AccessState.BLOCKED_TEMPORARY &&
            current.tempBlockUntilEpochMs in 1..nowEpochMs
        ) {
            current = current.copy(
                accessState = AccessState.ALLOWED,
                tempBlockUntilEpochMs = 0L
            )
            changed = true
            title = "Temporary Block Expired"
            detail = "Internet access automatically restored for ${current.customName} (${current.ipAddress})"
            notifType = "RESTORED"
        }

        // 2. Check Quota Expiry Date or Quota Exhaustion
        val quotaRuleEnabled = rules.any { it.triggerType == "QUOTA_REACHED" && it.isEnabled }
        if (current.accessState == AccessState.ALLOWED) {
            val isQuotaExpired = current.quotaExpiryEpochMs in 1..nowEpochMs
            val isCombinedExhausted = current.allocatedCombinedBytes > 0L && current.quotaRemainingBytes <= 0L
            val isDlExhausted = current.allocatedDownloadBytes > 0L && current.downloadedBytes >= current.allocatedDownloadBytes
            val isUlExhausted = current.allocatedUploadBytes > 0L && current.uploadedBytes >= current.allocatedUploadBytes

            if ((current.autoBlockOnQuota || quotaRuleEnabled) &&
                (isQuotaExpired || isCombinedExhausted || isDlExhausted || isUlExhausted)
            ) {
                current = current.copy(accessState = AccessState.QUOTA_EXHAUSTED)
                changed = true
                title = if (isQuotaExpired) "Data Package Expired" else "Data Quota Reached (0 MB Remaining)"
                detail = "${current.customName} (${current.ipAddress}) was automatically blocked after reaching its allocated limit."
                notifType = "QUOTA_REACHED"
            }
        }

        // 3. Check Per-Device or Global Scheduled Block Windows
        val cal = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        val deviceHasSchedule = current.scheduledBlockStartHour in 0..23 && current.scheduledBlockEndHour in 0..23
        val inDeviceBlockedHours = if (deviceHasSchedule) {
            isHourInWindow(currentHour, current.scheduledBlockStartHour, current.scheduledBlockEndHour)
        } else {
            false
        }

        val activeScheduleRule = rules.firstOrNull {
            it.isEnabled && it.triggerType == "SCHEDULE_WINDOW" &&
                (it.targetCategory == "ALL" || it.targetCategory == current.category)
        }
        val inRuleBlockedHours = if (activeScheduleRule != null) {
            isHourInWindow(currentHour, activeScheduleRule.scheduleStartHour, activeScheduleRule.scheduleEndHour)
        } else {
            false
        }

        if ((inDeviceBlockedHours || inRuleBlockedHours) && current.accessState == AccessState.ALLOWED) {
            current = current.copy(accessState = AccessState.SCHEDULE_BLOCKED)
            changed = true
            title = "Scheduled Internet Restriction Active"
            detail = "${current.customName} blocked during scheduled quiet hours."
            notifType = "BLOCKED"
        } else if (!inDeviceBlockedHours && !inRuleBlockedHours && current.accessState == AccessState.SCHEDULE_BLOCKED) {
            current = current.copy(accessState = AccessState.ALLOWED)
            changed = true
            title = "Scheduled Restriction Ended"
            detail = "Internet restored for ${current.customName} after scheduled window."
            notifType = "RESTORED"
        }

        // 4. Check Auto-Block Unknown Device Rule for newly seen unknown devices
        val autoBlockUnknown = rules.any { it.isEnabled && it.triggerType == "UNKNOWN_CONNECT" }
        if (autoBlockUnknown &&
            current.category == DeviceCategory.UNKNOWN &&
            current.accessState == AccessState.ALLOWED &&
            (nowEpochMs - current.firstSeenEpochMs) < 15_000L
        ) {
            current = current.copy(
                accessState = AccessState.BLOCKED_PERMANENT,
                category = DeviceCategory.BLOCKED
            )
            changed = true
            title = "Unknown Client Auto-Blocked"
            detail = "${current.customName} (${current.ipAddress}) was blocked by Automation Rule."
            notifType = "UNKNOWN_DEVICE"
        }

        return AutomationEvaluationOutcome(
            updatedDevice = current,
            stateChanged = changed,
            reasonTitle = title,
            reasonDetail = detail,
            notificationType = notifType
        )
    }

    fun isHourInWindow(currentHour: Int, startHour: Int, endHour: Int): Boolean {
        if (startHour == endHour) return false
        return if (startHour < endHour) {
            currentHour in startHour until endHour
        } else {
            // Overnight window, e.g., 23:00 to 06:00
            currentHour >= startHour || currentHour < endHour
        }
    }
}
