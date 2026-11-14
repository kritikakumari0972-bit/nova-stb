package com.nova.stb.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nova.stb.data.RemotePath
import com.nova.stb.data.StbRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StbRepository(application)
    private val mutableState = MutableStateFlow(MainUiState())
    val state = mutableState.asStateFlow()

    fun updateHost(value: String) = mutableState.update { it.copy(host = value) }
    fun updatePort(value: String) = mutableState.update { it.copy(port = value.filter(Char::isDigit).take(5)) }
    fun updatePairingPort(value: String) = mutableState.update { it.copy(pairingPort = value.filter(Char::isDigit).take(5)) }
    fun updatePairingCode(value: String) = mutableState.update { it.copy(pairingCode = value.filter(Char::isDigit).take(6)) }
    fun setPlainTcpAcknowledged(value: Boolean) = mutableState.update { it.copy(allowPlainTcp = value) }
    fun selectTab(tab: AppTab) = mutableState.update { it.copy(tab = tab) }

    fun connectTcp() {
        val snapshot = mutableState.value
        runTask("Connecting over local ADB TCP…") {
            require(snapshot.allowPlainTcp) {
                "Confirm that this is a trusted home network before using plain TCP ADB."
            }
            val info = repository.connectTcp(
                snapshot.host.trim(),
                snapshot.port.toIntOrNull() ?: error("Enter a valid ADB port.")
            )
            mutableState.update {
                it.copy(
                    connected = true,
                    deviceInfo = info,
                    statusMessage = "Connected to ${info.manufacturer} ${info.model}."
                )
            }
            loadFilesAfterConnect()
        }
    }

    fun pairWirelessDebugging() {
        val snapshot = mutableState.value
        runTask("Pairing secure wireless debugging…") {
            val message = repository.pairWirelessDebugging(
                snapshot.host.trim(),
                snapshot.pairingPort.toIntOrNull() ?: error("Enter the pairing port shown by the STB."),
                snapshot.pairingCode
            )
            mutableState.update { it.copy(statusMessage = message) }
        }
    }

    fun connectWirelessDebugging() {
        val snapshot = mutableState.value
        runTask("Connecting over encrypted wireless debugging…") {
            val info = repository.connectWirelessDebugging(
                snapshot.host.trim(),
                snapshot.port.toIntOrNull() ?: error("Enter the connection port shown by the STB.")
            )
            mutableState.update {
                it.copy(
                    connected = true,
                    deviceInfo = info,
                    statusMessage = "Secure connection established with ${info.model}."
                )
            }
            loadFilesAfterConnect()
        }
    }

    private suspend fun loadFilesAfterConnect() {
        runCatching { repository.listFiles(RemotePath.ROOT) }
            .onSuccess { entries ->
                mutableState.update { it.copy(currentPath = RemotePath.ROOT, remoteFiles = entries) }
            }
            .onFailure { error ->
                mutableState.update {
                    it.copy(statusMessage = "Connected. Shared storage listing unavailable: ${error.message}")
                }
            }
    }

    fun disconnect() {
        repository.disconnect()
        mutableState.update {
            it.copy(
                connected = false,
                deviceInfo = null,
                remoteFiles = emptyList(),
                userApps = emptyList(),
                pendingDownloadPath = null,
                statusMessage = "Disconnected."
            )
        }
    }

    fun refreshDeviceInfo() {
        runTask("Reading STB information…") {
            val info = repository.readDeviceInfo()
            mutableState.update {
                it.copy(deviceInfo = info, connected = true, statusMessage = "Device information updated.")
            }
        }
    }

    fun refreshFiles() {
        val path = mutableState.value.currentPath
        runTask("Reading $path…") {
            val files = repository.listFiles(path)
            mutableState.update { it.copy(remoteFiles = files, statusMessage = "Loaded ${files.size} items from $path.") }
        }
    }

    fun openDirectory(path: String) {
        runTask("Opening folder…") {
            val safePath = RemotePath.normalize(path)
            val files = repository.listFiles(safePath)
            mutableState.update { it.copy(currentPath = safePath, remoteFiles = files, statusMessage = "Opened $safePath.") }
        }
    }

    fun navigateUp() {
        val parent = RemotePath.parent(mutableState.value.currentPath)
        if (parent != mutableState.value.currentPath) openDirectory(parent)
    }

    fun uploadFile(uri: Uri) {
        val directory = mutableState.value.currentPath
        runTask("Uploading file to STB…") {
            val destination = repository.upload(uri, directory)
            mutableState.update { it.copy(statusMessage = "Uploaded to $destination.") }
            val refreshed = repository.listFiles(directory)
            mutableState.update { it.copy(remoteFiles = refreshed) }
        }
    }

    fun requestDownload(path: String) {
        runCatching { RemotePath.normalize(path) }
            .onSuccess { safe -> mutableState.update { it.copy(pendingDownloadPath = safe) } }
            .onFailure { error ->
                mutableState.update { it.copy(statusMessage = error.message ?: "Invalid remote path.") }
            }
    }

    fun cancelPendingDownload() {
        mutableState.update { it.copy(pendingDownloadPath = null) }
    }

    fun downloadPendingTo(uri: Uri) {
        val path = mutableState.value.pendingDownloadPath ?: return
        runTask("Downloading ${path.substringAfterLast('/')}…") {
            repository.download(path, uri)
            mutableState.update {
                it.copy(
                    pendingDownloadPath = null,
                    statusMessage = "Saved ${path.substringAfterLast('/')} to your phone."
                )
            }
        }
    }

    fun installApk(uri: Uri) {
        runTask("Installing APK…") {
            mutableState.update { it.copy(installProgress = 0f, installStatus = "Preparing APK…") }
            repository.installApk(uri) { progress, message ->
                mutableState.update {
                    it.copy(
                        installProgress = progress,
                        installStatus = message.ifBlank { "Installing…" }
                    )
                }
            }
            mutableState.update {
                it.copy(
                    installProgress = 100f,
                    installStatus = "Installation completed.",
                    statusMessage = "APK installed successfully."
                )
            }
            refreshAppsAfterInstall()
        }
    }

    private suspend fun refreshAppsAfterInstall() {
        runCatching { repository.listUserApps() }
            .onSuccess { apps -> mutableState.update { it.copy(userApps = apps) } }
    }

    fun refreshApps() {
        runTask("Reading installed user apps…") {
            val apps = repository.listUserApps()
            mutableState.update { it.copy(userApps = apps, statusMessage = "Found ${apps.size} user-installed apps.") }
        }
    }

    fun launchApp(packageName: String) {
        runTask("Launching $packageName…") {
            repository.launchApp(packageName)
            mutableState.update { it.copy(statusMessage = "Launch request sent to $packageName.") }
        }
    }

    fun requestUninstall(packageName: String) {
        mutableState.update { it.copy(pendingUninstallPackage = packageName) }
    }

    fun cancelUninstall() {
        mutableState.update { it.copy(pendingUninstallPackage = null) }
    }

    fun confirmUninstall() {
        val packageName = mutableState.value.pendingUninstallPackage ?: return
        runTask("Uninstalling $packageName…") {
            repository.uninstallUserApp(packageName)
            val apps = repository.listUserApps()
            mutableState.update {
                it.copy(
                    userApps = apps,
                    pendingUninstallPackage = null,
                    statusMessage = "Uninstalled $packageName for the current user."
                )
            }
        }
    }

    private fun runTask(label: String, task: suspend () -> Unit) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, statusMessage = label) }
            try {
                task()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(statusMessage = error.message ?: "The operation failed.") }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    override fun onCleared() {
        repository.disconnect()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
