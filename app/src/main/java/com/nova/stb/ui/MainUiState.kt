package com.nova.stb.ui

import com.nova.stb.data.DeviceInfo
import com.nova.stb.data.RemoteEntry

enum class AppTab(val label: String, val marker: String) {
    CONNECT("Connect", "01"),
    INSTALL("Install", "02"),
    FILES("Files", "03"),
    APPS("Apps", "04"),
    DEVICE("Device", "05")
}

data class MainUiState(
    val host: String = "",
    val port: String = "5555",
    val pairingPort: String = "",
    val pairingCode: String = "",
    val allowPlainTcp: Boolean = false,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val statusMessage: String = "Connect your Jio STB on the same trusted Wi-Fi network.",
    val deviceInfo: DeviceInfo? = null,
    val tab: AppTab = AppTab.CONNECT,
    val currentPath: String = "/sdcard",
    val remoteFiles: List<RemoteEntry> = emptyList(),
    val userApps: List<String> = emptyList(),
    val installProgress: Float = 0f,
    val installStatus: String = "",
    val pendingDownloadPath: String? = null,
    val pendingUninstallPackage: String? = null
)
