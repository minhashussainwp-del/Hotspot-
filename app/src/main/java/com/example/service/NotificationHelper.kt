package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.GlobalHotspotSettings
import java.util.concurrent.atomic.AtomicInteger

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ALERTS = "hotspot_alerts_channel"
        const val CHANNEL_FOREGROUND = "hotspot_fgs_channel"
        const val FOREGROUND_NOTIFICATION_ID = 1001
    }

    private val notificationIdSeq = AtomicInteger(2000)

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            val fgsChannel = NotificationChannel(
                CHANNEL_FOREGROUND,
                "Hotspot Gateway & Telemetry Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent status for active hotspot client monitoring and bandwidth shaping"
            }
            nm.createNotificationChannel(alertsChannel)
            nm.createNotificationChannel(fgsChannel)
        }
    }

    fun buildForegroundNotification(
        statusText: String,
        onlineClients: Int,
        downloadSpeedLabel: String,
        uploadSpeedLabel: String
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Hotspot Control Pro • $statusText")
            .setContentText("$onlineClients online • ↓ $downloadSpeedLabel • ↑ $uploadSpeedLabel")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun notifyEvent(
        eventType: String,
        title: String,
        message: String,
        settings: GlobalHotspotSettings
    ) {
        val enabled = when (eventType) {
            "NEW_DEVICE" -> settings.notifyNewDevice
            "UNKNOWN_DEVICE" -> settings.notifyUnknownDevice
            "DISCONNECTED" -> settings.notifyDisconnected
            "BLOCKED", "RESTORED" -> settings.notifyBlockedRestored
            "QUOTA_REACHED" -> settings.notifyQuotaReached
            "QUOTA_WARNING" -> settings.notifyQuotaWarning
            "SPEED_LIMIT" -> settings.notifySpeedLimit
            "HOTSPOT_STATE" -> settings.notifyHotspotState
            else -> true
        }
        if (!enabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationIdSeq.get(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(notificationIdSeq.incrementAndGet(), notification)
    }
}
