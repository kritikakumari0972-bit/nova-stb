package com.nova.stb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.stb.data.RemoteEntry
import com.nova.stb.ui.AppTab
import com.nova.stb.ui.MainUiState
import com.nova.stb.ui.MainViewModel

private val NovaBackground = Color(0xFF0B1020)
private val NovaSurface = Color(0xFF171F34)
private val NovaSurfaceAlt = Color(0xFF202A43)
private val NovaPrimary = Color(0xFF8B9CFF)
private val NovaMint = Color(0xFF62E8D1)
private val NovaText = Color(0xFFF4F6FF)
private val NovaMuted = Color(0xFFA8B2CC)

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(application)
        )[MainViewModel::class.java]

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NovaPrimary,
                    secondary = NovaMint,
                    background = NovaBackground,
                    surface = NovaSurface,
                    onPrimary = Color(0xFF10162A),
                    onBackground = NovaText,
                    onSurface = NovaText
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = NovaBackground) {
                    NovaStbScreen(viewModel)
                }
            }
        }
    }
}

@Composable
private fun NovaStbScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.installApk(uri)
    }
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.uploadFile(uri)
    }
    val downloadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.downloadPendingTo(uri) else viewModel.cancelPendingDownload()
    }

    LaunchedEffect(state.pendingDownloadPath) {
        state.pendingDownloadPath?.let { path ->
            downloadPicker.launch(path.substringAfterLast('/').ifBlank { "download.bin" })
        }
    }

    Scaffold(
        containerColor = NovaBackground,
        bottomBar = {
            NavigationBar(containerColor = NovaSurface) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Text(tab.marker, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(tab.label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(state.connected)
            StatusPanel(state)
            when (state.tab) {
                AppTab.CONNECT -> ConnectScreen(viewModel, state)
                AppTab.INSTALL -> InstallScreen(state) { apkPicker.launch(arrayOf("*/*")) }
                AppTab.FILES -> FilesScreen(viewModel, state) { uploadPicker.launch(arrayOf("*/*")) }
                AppTab.APPS -> AppsScreen(viewModel, state)
                AppTab.DEVICE -> DeviceScreen(viewModel, state)
            }
            Text(
                "NovaSTB Alpha • Phone-first Jio STB companion",
                color = NovaMuted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }

    if (state.pendingUninstallPackage != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelUninstall,
            title = { Text("Uninstall this app?") },
            text = {
                Text(
                    "Remove ${state.pendingUninstallPackage} for the current Android user? " +
                        "System apps are not listed here."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmUninstall) { Text("Uninstall") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelUninstall) { Text("Cancel") }
            },
            containerColor = NovaSurface
        )
    }
}

@Composable
private fun Header(connected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = NovaSurfaceAlt,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("N", color = NovaMint, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("NovaSTB", color = NovaText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Your STB, managed from your phone", color = NovaMuted, fontSize = 11.sp)
            }
        }
        Surface(
            color = if (connected) Color(0xFF123A3B) else Color(0xFF30283B),
            shape = CircleShape
        ) {
            Text(
                if (connected) "CONNECTED" else "OFFLINE",
                color = if (connected) NovaMint else Color(0xFFFFC58B),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun StatusPanel(state: MainUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NovaSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = NovaMint
                )
                Spacer(Modifier.size(12.dp))
            } else {
                Surface(
                    color = NovaMint.copy(alpha = 0.16f),
                    shape = CircleShape,
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("•", color = NovaMint, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.size(12.dp))
            }
            Text(state.statusMessage, color = NovaText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ConnectScreen(viewModel: MainViewModel, state: MainUiState) {
    Panel("Connect to your STB", "Use the STB's local IP address while both devices are on the same home network.") {
        OutlinedTextField(
            value = state.host,
            onValueChange = viewModel::updateHost,
            label = { Text("STB IP address") },
            placeholder = { Text("Example: 192.168.1.50") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.port,
            onValueChange = viewModel::updatePort,
            label = { Text("ADB / connection port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.allowPlainTcp, onCheckedChange = viewModel::setPlainTcpAcknowledged)
            Text(
                "I am on my trusted home Wi-Fi and understand plain TCP ADB is not encrypted.",
                color = NovaMuted,
                fontSize = 11.sp
            )
        }
        Button(
            onClick = viewModel::connectTcp,
            enabled = !state.busy && !state.connected && state.allowPlainTcp,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = NovaPrimary,
                contentColor = Color(0xFF10162A)
            )
        ) {
            Text("Connect using ADB TCP", fontWeight = FontWeight.Bold)
        }
        if (state.connected) {
            OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Disconnect")
            }
        }
        Text(
            "ADB must already be enabled on the STB and its authorization prompt must be accepted on the TV. " +
                "NovaSTB does not enable debugging or bypass device security.",
            color = NovaMuted,
            fontSize = 11.sp
        )
    }

    Panel("Secure wireless debugging", "For STBs that provide Android's six-digit wireless debugging pairing screen.") {
        OutlinedTextField(
            value = state.pairingPort,
            onValueChange = viewModel::updatePairingPort,
            label = { Text("Pairing port") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.pairingCode,
            onValueChange = viewModel::updatePairingCode,
            label = { Text("Six-digit pairing code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = viewModel::pairWirelessDebugging,
            enabled = !state.busy && state.host.isNotBlank() &&
                state.pairingPort.isNotBlank() && state.pairingCode.length == 6,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pair securely") }
        Text(
            "After pairing, enter the separate wireless debugging connection port in the field above, then choose Connect securely.",
            color = NovaMuted,
            fontSize = 11.sp
        )
        Button(
            onClick = viewModel::connectWirelessDebugging,
            enabled = !state.busy && !state.connected && state.host.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Connect securely (TLS)") }
    }
}

@Composable
private fun InstallScreen(state: MainUiState, chooseApk: () -> Unit) {
    Panel("Install an APK", "Choose an APK stored on your Motorola phone. NovaSTB streams it to Package Manager over ADB.") {
        Button(
            onClick = chooseApk,
            enabled = state.connected && !state.busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = NovaPrimary,
                contentColor = Color(0xFF10162A)
            )
        ) {
            Text("Choose APK from phone", fontWeight = FontWeight.Bold)
        }
        Text(
            "This alpha accepts single .apk files. Split .apks, .apkm and .xapk bundles are planned and are not advertised as working yet.",
            color = NovaMuted,
            fontSize = 11.sp
        )
        if (state.installStatus.isNotBlank()) {
            Text(state.installStatus, color = NovaMint, fontSize = 12.sp)
            LinearProgressIndicator(
                progress = { (state.installProgress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = NovaMint
            )
        }
    }
    Panel("Before installing", "Compatibility still matters.") {
        Text("• Prefer Android TV apps with remote-control navigation.", color = NovaMuted, fontSize = 12.sp)
        Text("• Apps requiring Play Services, DRM certification or unsupported hardware may not run on JioOS.", color = NovaMuted, fontSize = 12.sp)
        Text("• Install files only from sources you trust.", color = NovaMuted, fontSize = 12.sp)
    }
}

@Composable
private fun FilesScreen(viewModel: MainViewModel, state: MainUiState, chooseUpload: () -> Unit) {
    Panel("STB file manager", "Browse shared storage visible to the ADB shell. Private app data and protected partitions are not exposed.") {
        Text(state.currentPath, color = NovaMint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = viewModel::navigateUp,
                enabled = state.connected && state.currentPath != "/sdcard"
            ) { Text("Up") }
            OutlinedButton(
                onClick = viewModel::refreshFiles,
                enabled = state.connected && !state.busy
            ) { Text("Refresh") }
            Button(
                onClick = chooseUpload,
                enabled = state.connected && !state.busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaPrimary,
                    contentColor = Color(0xFF10162A)
                )
            ) { Text("Upload") }
        }
        if (!state.connected) {
            Text("Connect to the STB to browse files.", color = NovaMuted, fontSize = 12.sp)
        } else if (state.remoteFiles.isEmpty()) {
            Text("This folder is empty, unavailable, or not readable by the STB's ADB user.", color = NovaMuted, fontSize = 12.sp)
        } else {
            state.remoteFiles.forEach { entry ->
                RemoteFileRow(
                    entry = entry,
                    onOpen = { viewModel.openDirectory(entry.path) },
                    onDownload = { viewModel.requestDownload(entry.path) },
                    enabled = !state.busy
                )
            }
        }
    }
}

@Composable
private fun RemoteFileRow(
    entry: RemoteEntry,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    enabled: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NovaSurfaceAlt),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).clickable(enabled = enabled && entry.isDirectory, onClick = onOpen)
            ) {
                Text(
                    (if (entry.isDirectory) "▰  " else "▤  ") + entry.name,
                    color = NovaText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(if (entry.isDirectory) "Folder" else "File", color = NovaMuted, fontSize = 10.sp)
            }
            if (entry.isDirectory) {
                TextButton(onClick = onOpen, enabled = enabled) { Text("Open") }
            } else {
                TextButton(onClick = onDownload, enabled = enabled) { Text("Save") }
            }
        }
    }
}

@Composable
private fun AppsScreen(viewModel: MainViewModel, state: MainUiState) {
    Panel("Installed user apps", "Lists third-party packages reported by Android Package Manager.") {
        OutlinedButton(
            onClick = viewModel::refreshApps,
            enabled = state.connected && !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh app list") }

        if (!state.connected) {
            Text("Connect to the STB first.", color = NovaMuted, fontSize = 12.sp)
        } else if (state.userApps.isEmpty()) {
            Text("No apps loaded yet. Tap Refresh app list.", color = NovaMuted, fontSize = 12.sp)
        } else {
            state.userApps.forEach { packageName ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NovaSurfaceAlt),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(packageName, color = NovaText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.launchApp(packageName) }, enabled = !state.busy) {
                                Text("Launch")
                            }
                            TextButton(onClick = { viewModel.requestUninstall(packageName) }, enabled = !state.busy) {
                                Text("Uninstall", color = Color(0xFFFFA4A4))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceScreen(viewModel: MainViewModel, state: MainUiState) {
    Panel("Device details", "Information read using standard, non-root ADB shell properties.") {
        OutlinedButton(
            onClick = viewModel::refreshDeviceInfo,
            enabled = state.connected && !state.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Refresh device information") }
        val info = state.deviceInfo
        if (info == null) {
            Text("No device information yet.", color = NovaMuted, fontSize = 12.sp)
        } else {
            DeviceInfoLine("Manufacturer", info.manufacturer)
            DeviceInfoLine("Model", info.model)
            DeviceInfoLine("Android version", info.androidRelease)
            DeviceInfoLine("SDK level", info.sdk)
        }
        Divider(color = NovaMuted.copy(alpha = 0.2f))
        Text("Target device profile: Jio JMSB200A", color = NovaMint, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(
            "The profile is a label, not a guarantee that every Jio firmware exposes ADB or supports every Android app. Firmware support must be tested on the actual box.",
            color = NovaMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun DeviceInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = NovaMuted, fontSize = 12.sp)
        Text(value, color = NovaText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Panel(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NovaSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text(title, color = NovaText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = NovaMuted, fontSize = 11.sp)
            content()
        }
    }
}
