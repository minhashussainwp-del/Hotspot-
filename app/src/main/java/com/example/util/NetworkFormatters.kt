package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NetworkFormatters {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes == Long.MAX_VALUE) return "Unlimited"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeedBps(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        val kb = 1024.0
        val mb = kb * 1024.0
        return when {
            bytesPerSec >= mb -> String.format(Locale.US, "%.2f MB/s", bytesPerSec / mb)
            bytesPerSec >= kb -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / kb)
            else -> "$bytesPerSec B/s"
        }
    }

    fun formatKbpsLimit(kbps: Int): String {
        if (kbps <= 0) return "Unlimited"
        return if (kbps >= 1000 && kbps % 1000 == 0) {
            "${kbps / 1000} MB/s"
        } else if (kbps >= 1024) {
            String.format(Locale.US, "%.1f MB/s", kbps / 1000.0)
        } else {
            "$kbps KB/s"
        }
    }

    fun formatDurationSeconds(totalSeconds: Long): String {
        val sec = totalSeconds.coerceAtLeast(0L)
        val hours = sec / 3600
        val minutes = (sec % 3600) / 60
        val seconds = sec % 60
        return String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
    }

    fun formatRelativeTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0L) return "Never"
        val diffSec = ((nowMs - epochMs) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 10L -> "Just now"
            diffSec < 60L -> "${diffSec}s ago"
            diffSec < 3600L -> "${diffSec / 60}m ago"
            diffSec < 86400L -> "${diffSec / 3600}h ago"
            else -> SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(epochMs))
        }
    }

    fun formatTimestamp(epochMs: Long): String {
        if (epochMs <= 0L) return "No Expiry"
        return SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US).format(Date(epochMs))
    }
}
