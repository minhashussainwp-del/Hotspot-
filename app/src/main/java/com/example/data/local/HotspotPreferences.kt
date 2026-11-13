package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core. booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hotspotDataStore by preferencesDataStore(name = "hotspot_control_prefs")

data class GlobalHotspotSettings(
    val maxTotalDownloadKbps: Int = 0, // 0 = Unlimited
    val maxTotalUploadKbps: Int = 0,   // 0 = Unlimited
    val maxConnectedDevices: Int = 10,
    val fairSharingEnabled: Boolean = true,
    val minBandwidthPerClientKbps: Int = 100,
    val maxBandwidthPerClientKbps: Int = 10000,
    val unrootedGatewayEnabled: Boolean = true,
    val unrootedGatewayPort: Int = 8888,
    val preferredEnforcementMode: String = "UNROOTED_STANDARD", // UNROOTED_STANDARD, SHIZUKU, ROOT
    val darkThemeEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val appPinCode: String = "", // Empty = No app lock by default
    val biometricEnabled: Boolean = false,
    val notifyNewDevice: Boolean = true,
    val notifyUnknownDevice: Boolean = true,
    val notifyDisconnected: Boolean = true,
    val notifyBlockedRestored: Boolean = true,
    val notifyQuotaReached: Boolean = true,
    val notifyQuotaWarning: Boolean = true,
    val notifySpeedLimit: Boolean = true,
    val notifyHotspotState: Boolean = true,
    val sessionStartEpochMs: Long = System.currentTimeMillis()
)

class HotspotPreferences(private val context: Context) {

    private object Keys {
        val MAX_DOWNLOAD_KBPS = intPreferencesKey("max_total_download_kbps")
        val MAX_UPLOAD_KBPS = intPreferencesKey("max_total_upload_kbps")
        val MAX_DEVICES = intPreferencesKey("max_connected_devices")
        val FAIR_SHARING = booleanPreferencesKey("fair_sharing_enabled")
        val MIN_CLIENT_KBPS = intPreferencesKey("min_bandwidth_client_kbps")
        val MAX_CLIENT_KBPS = intPreferencesKey("max_bandwidth_client_kbps")
        val GATEWAY_ENABLED = booleanPreferencesKey("unrooted_gateway_enabled")
        val GATEWAY_PORT = intPreferencesKey("unrooted_gateway_port")
        val ENFORCEMENT_MODE = stringPreferencesKey("preferred_enforcement_mode")
        val DARK_THEME = booleanPreferencesKey("dark_theme_enabled")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        val APP_PIN = stringPreferencesKey("app_pin_code")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val NOTIFY_NEW_DEVICE = booleanPreferencesKey("notify_new_device")
        val NOTIFY_UNKNOWN = booleanPreferencesKey("notify_unknown_device")
        val NOTIFY_DISCONNECTED = booleanPreferencesKey("notify_disconnected")
        val NOTIFY_BLOCKED = booleanPreferencesKey("notify_blocked_restored")
        val NOTIFY_QUOTA_REACHED = booleanPreferencesKey("notify_quota_reached")
        val NOTIFY_QUOTA_WARNING = booleanPreferencesKey("notify_quota_warning")
        val NOTIFY_SPEED_LIMIT = booleanPreferencesKey("notify_speed_limit")
        val NOTIFY_HOTSPOT_STATE = booleanPreferencesKey("notify_hotspot_state")
        val SESSION_START_MS = longPreferencesKey("session_start_epoch_ms")
    }

    val settingsFlow: Flow<GlobalHotspotSettings> = context.hotspotDataStore.data.map { prefs ->
        GlobalHotspotSettings(
            maxTotalDownloadKbps = prefs[Keys.MAX_DOWNLOAD_KBPS] ?: 0,
            maxTotalUploadKbps = prefs[Keys.MAX_UPLOAD_KBPS] ?: 0,
            maxConnectedDevices = prefs[Keys.MAX_DEVICES] ?: 10,
            fairSharingEnabled = prefs[Keys.FAIR_SHARING] ?: true,
            minBandwidthPerClientKbps = prefs[Keys.MIN_CLIENT_KBPS] ?: 100,
            maxBandwidthPerClientKbps = prefs[Keys.MAX_CLIENT_KBPS] ?: 10000,
            unrootedGatewayEnabled = prefs[Keys.GATEWAY_ENABLED] ?: true,
            unrootedGatewayPort = prefs[Keys.GATEWAY_PORT] ?: 8888,
            preferredEnforcementMode = prefs[Keys.ENFORCEMENT_MODE] ?: "UNROOTED_STANDARD",
            darkThemeEnabled = prefs[Keys.DARK_THEME] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            appPinCode = prefs[Keys.APP_PIN] ?: "",
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            notifyNewDevice = prefs[Keys.NOTIFY_NEW_DEVICE] ?: true,
            notifyUnknownDevice = prefs[Keys.NOTIFY_UNKNOWN] ?: true,
            notifyDisconnected = prefs[Keys.NOTIFY_DISCONNECTED] ?: true,
            notifyBlockedRestored = prefs[Keys.NOTIFY_BLOCKED] ?: true,
            notifyQuotaReached = prefs[Keys.NOTIFY_QUOTA_REACHED] ?: true,
            notifyQuotaWarning = prefs[Keys.NOTIFY_QUOTA_WARNING] ?: true,
            notifySpeedLimit = prefs[Keys.NOTIFY_SPEED_LIMIT] ?: true,
            notifyHotspotState = prefs[Keys.NOTIFY_HOTSPOT_STATE] ?: true,
            sessionStartEpochMs = prefs[Keys.SESSION_START_MS] ?: System.currentTimeMillis()
        )
    }

    suspend fun updateGlobalBandwidth(
        maxDownloadKbps: Int,
        maxUploadKbps: Int,
        maxDevices: Int,
        fairSharing: Boolean,
        minClientKbps: Int,
        maxClientKbps: Int
    ) {
        context.hotspotDataStore.edit { prefs ->
            prefs[Keys.MAX_DOWNLOAD_KBPS] = maxDownloadKbps
            prefs[Keys.MAX_UPLOAD_KBPS] = maxUploadKbps
            prefs[Keys.MAX_DEVICES] = maxDevices
            prefs[Keys.FAIR_SHARING] = fairSharing
            prefs[Keys.MIN_CLIENT_KBPS] = minClientKbps
            prefs[Keys.MAX_CLIENT_KBPS] = maxClientKbps
        }
    }

    suspend fun updateGatewaySettings(enabled: Boolean, port: Int) {
        context.hotspotDataStore.edit { prefs ->
            prefs[Keys.GATEWAY_ENABLED] = enabled
            prefs[Keys.GATEWAY_PORT] = port.coerceIn(1024, 65535)
        }
    }

    suspend fun updateEnforcementMode(mode: String) {
        context.hotspotDataStore.edit { prefs ->
            prefs[Keys.ENFORCEMENT_MODE] = mode
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.hotspotDataStore.edit { prefs ->
            prefs[Keys.DARK_THEME] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.hotspotDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_DONE] = completed
        }
    }

    suspend fun updatePinLock(pin: String, biometric: Boolean) {
        context.hotspotDataStore.edit { prefs ->
            prefs[Keys.APP_PIN] = pin
            prefs[Keys.BIOMETRIC_ENABLED] = biometric
        }
    }

    suspend fun updateNotificationToggle(keyName: String, enabled: Boolean) {
        context.hotspotDataStore.edit { prefs ->
            when (keyName) {
                "new_device" -> prefs[Keys.NOTIFY_NEW_DEVICE] = enabled
                "unknown_device" -> prefs[Keys.NOTIFY_UNKNOWN] = enabled
                "disconnected" -> prefs[Keys.NOTIFY_DISCONNECTED] = enabled
                "blocked" -> prefs[Keys.NOTIFY_BLOCKED] = enabled
                "quota_reached" -> prefs[Keys.NOTIFY_QUOTA_REACHED] = enabled
                "quota_warning" -> prefs[Keys.NOTIFY_QUOTA_WARNING] = enabled
                "speed_limit" -> prefs[Keys.NOTIFY_SPEED_LIMIT] = enabled
                "hotspot_state" -> prefs[Keys.NOTIFY_HOTSPOT_STATE] = enabled
            }
        }
    }
}
