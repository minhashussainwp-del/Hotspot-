package com.example.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HotspotMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var heartbeatJob: Job? = null
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT) ?: "Gateway & Telemetry Active"
        val onlineCount = intent?.getIntExtra(EXTRA_ONLINE_COUNT, 0) ?: 0
        val dlLabel = intent?.getStringExtra(EXTRA_DL_SPEED) ?: "0 B/s"
        val ulLabel = intent?.getStringExtra(EXTRA_UL_SPEED) ?: "0 B/s"

        val notification = notificationHelper.buildForegroundNotification(
            statusText = statusText,
            onlineClients = onlineCount,
            downloadSpeedLabel = dlLabel,
            uploadSpeedLabel = ulLabel
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationHelper.FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
        }

        if (heartbeatJob == null) {
            heartbeatJob = serviceScope.launch {
                while (isActive) {
                    delay(10_000L)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.service.ACTION_START_MONITOR"
        const val ACTION_STOP = "com.example.service.ACTION_STOP_MONITOR"
        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_ONLINE_COUNT = "extra_online_count"
        const val EXTRA_DL_SPEED = "extra_dl_speed"
        const val EXTRA_UL_SPEED = "extra_ul_speed"
    }
}
