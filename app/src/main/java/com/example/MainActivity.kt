package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.BandwidthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DataUsageAndQuotaScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.SettingsAndCapabilitiesScreen
import com.example.ui.theme.HotspotControlProTheme
import com.example.viewmodel.HotspotControlViewModel
import com.example.viewmodel.HotspotUiState

enum class BottomNavTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Default.Dashboard, "nav_dashboard"),
    DEVICES("devices", R.string.nav_devices, Icons.Default.Devices, "nav_devices"),
    BANDWIDTH("bandwidth", R.string.nav_bandwidth, Icons.Default.Speed, "nav_bandwidth"),
    DATA_USAGE("data_usage", R.string.nav_usage, Icons.Default.DataUsage, "nav_usage"),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: HotspotControlViewModel by viewModels {
        HotspotControlViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            HotspotControlProTheme(darkTheme = uiState.settings.darkThemeEnabled) {
                HotspotControlProApp(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun HotspotControlProApp(
    uiState: HotspotUiState,
    viewModel: HotspotControlViewModel
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(BottomNavTab.DASHBOARD) }
    var settingsSubTab by rememberSaveable { mutableIntStateOf(0) }
    var isPinUnlocked by rememberSaveable { mutableStateOf(false) }

    // Request POST_NOTIFICATIONS runtime permission on Android 13+ gracefully
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // BackHandler on secondary tabs returns to Dashboard
    if (currentTab != BottomNavTab.DASHBOARD) {
        BackHandler {
            currentTab = BottomNavTab.DASHBOARD
        }
    }

    // Optional PIN lock gate (only active if user configured a non-empty PIN in Settings)
    val configuredPin = uiState.settings.appPinCode
    if (configuredPin.isNotBlank() && !isPinUnlocked) {
        PinUnlockScreen(
            expectedPin = configuredPin,
            onUnlocked = { isPinUnlocked = true }
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                BottomNavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.labelRes)
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                BottomNavTab.DASHBOARD -> DashboardScreen(
                    uiState = uiState,
                    onRefreshDevices = { viewModel.refreshConnectedDevices() },
                    onBlockAll = { viewModel.blockAllDevices() },
                    onAllowAllTrusted = { viewModel.allowAllTrustedDevices() },
                    onPauseAll = { viewModel.pauseAllInternet() },
                    onResumeAll = { viewModel.resumeAllInternet() },
                    onOpenSystemTethering = { viewModel.openSystemTetheringSettings(context) },
                    onStartForegroundMonitor = { viewModel.startForegroundMonitorService(context) },
                    onNavigateToDevices = { currentTab = BottomNavTab.DEVICES },
                    onNavigateToCapabilities = {
                        settingsSubTab = 1
                        currentTab = BottomNavTab.SETTINGS
                    }
                )

                BottomNavTab.DEVICES -> DevicesScreen(
                    uiState = uiState,
                    onRefreshDevices = { viewModel.refreshConnectedDevices() },
                    onRegisterClient = { name, ip, mac, cat, prof ->
                        viewModel.registerOrUpdateManualClient(name, ip, mac, cat, prof)
                    },
                    onRenameDevice = { id, newName -> viewModel.renameDevice(id, newName) },
                    onSetAccessState = { id, state, mins -> viewModel.setDeviceAccessState(id, state, mins) },
                    onSetCategory = { id, cat -> viewModel.setDeviceCategory(id, cat) },
                    onAssignProfile = { id, profId -> viewModel.assignProfileToDevice(id, profId) },
                    onSetScheduleWindow = { id, s, e -> viewModel.setDeviceScheduleWindow(id, s, e) },
                    onVerifyEnforcement = { id -> viewModel.verifyDeviceEnforcementNow(id) },
                    onDeleteDevice = { id -> viewModel.deleteDevice(id) }
                )

                BottomNavTab.BANDWIDTH -> BandwidthScreen(
                    uiState = uiState,
                    onUpdateGlobalBandwidth = { maxDl, maxUl, maxDev, fair, minC, maxC ->
                        viewModel.updateGlobalBandwidthSettings(maxDl, maxUl, maxDev, fair, minC, maxC)
                    },
                    onSetDeviceSpeedLimit = { id, dl, ul, prio ->
                        viewModel.setDeviceSpeedLimit(id, dl, ul, prio)
                    },
                    onAssignProfile = { id, profId -> viewModel.assignProfileToDevice(id, profId) },
                    onVerifyDeviceEnforcement = { id -> viewModel.verifyDeviceEnforcementNow(id) }
                )

                BottomNavTab.DATA_USAGE -> DataUsageAndQuotaScreen(
                    uiState = uiState,
                    onSetDeviceQuota = { id, period, combMb, dlMb, ulMb, expH, autoB ->
                        viewModel.setDeviceQuota(id, period, combMb, dlMb, ulMb, expH, autoB)
                    },
                    onAddDeviceQuotaData = { id, addMb -> viewModel.addDeviceQuotaData(id, addMb) },
                    onResetDeviceQuotaUsage = { id -> viewModel.resetDeviceQuotaUsage(id) },
                    onExportCsv = { viewModel.shareAnalyticsCsv(context) }
                )

                BottomNavTab.SETTINGS -> SettingsAndCapabilitiesScreen(
                    uiState = uiState,
                    initialTab = settingsSubTab,
                    onToggleAutomationRule = { rule -> viewModel.toggleAutomationRule(rule) },
                    onCreateAutomationRule = { title, desc, trig, target, act, sh, eh ->
                        viewModel.createAutomationRule(title, desc, trig, target, act, sh, eh)
                    },
                    onDeleteAutomationRule = { ruleId -> viewModel.deleteAutomationRule(ruleId) },
                    onUpdateGatewayConfig = { en, port -> viewModel.updateGatewayConfiguration(en, port) },
                    onUpdateEnforcementMode = { mode -> viewModel.updateEnforcementMode(mode) },
                    onRefreshCapabilities = { viewModel.refreshCapabilitiesReport() },
                    onSetDarkTheme = { dark -> viewModel.setDarkTheme(dark) },
                    onUpdatePinLock = { pin, bio -> viewModel.updatePinLock(pin, bio) },
                    onToggleNotification = { key, en -> viewModel.toggleNotificationPreference(key, en) }
                )
            }
        }
    }
}

@Composable
private fun PinUnlockScreen(
    expectedPin: String,
    onUnlocked: () -> Unit
) {
    var enteredPin by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "HOTSPOT CONTROL PRO LOCKED",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = enteredPin,
                onValueChange = {
                    enteredPin = it
                    showError = false
                },
                label = { Text("Enter Security PIN") },
                singleLine = true,
                isError = showError,
                modifier = Modifier.fillMaxWidth()
            )
            if (showError) {
                Text(
                    text = "Incorrect PIN code",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (enteredPin == expectedPin) {
                        onUnlocked()
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock Console")
            }
        }
    }
}
