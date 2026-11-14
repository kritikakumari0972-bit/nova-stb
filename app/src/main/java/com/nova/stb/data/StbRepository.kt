package com.nova.stb.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.FileKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdk: String
)

data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean
)

/** One authenticated ADB session. Its key is kept in app-private, no-backup storage. */
class StbRepository(context: Context) {
    private val appContext = context.applicationContext
    private val keyProvider = FileKeyProvider(
        File(File(appContext.noBackupFilesDir, "adb"), "adbkey"),
        identityComment = "NovaSTB"
    )
    private val connectionLock = Mutex()

    @Volatile
    private var adbClient: AdbClient? = null

    suspend fun connectTcp(host: String, port: Int): DeviceInfo = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Enter the STB's local IP address or host name." }
        require(port in 1..65535) { "Enter a valid TCP port." }
        replaceConnection { AdbClient.connectTcp(host.trim(), port, keyProvider) }
    }

    suspend fun pairWirelessDebugging(
        host: String,
        pairingPort: Int,
        pairingCode: String
    ): String = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Enter the STB's local IP address or host name." }
        require(pairingPort in 1..65535) { "Enter a valid pairing port." }
        require(pairingCode.length == 6 && pairingCode.all { it in '0'..'9' }) {
            "The pairing code must contain six digits."
        }
        val result = AdbClient.pairTls(host.trim(), pairingPort, pairingCode, keyProvider)
        if (result.isSuccess) {
            "Wireless debugging pairing completed. Enter its connection port and connect."
        } else {
            throw result.exceptionOrNull() ?: IOException("Wireless pairing failed.")
        }
    }

    suspend fun connectWirelessDebugging(host: String, port: Int): DeviceInfo =
        withContext(Dispatchers.IO) {
            require(host.isNotBlank()) { "Enter the STB's local IP address or host name." }
            require(port in 1..65535) { "Enter a valid connection port." }
            replaceConnection { AdbClient.connectTls(host.trim(), port, keyProvider) }
        }

    private suspend fun replaceConnection(create: suspend () -> AdbClient): DeviceInfo =
        connectionLock.withLock {
            adbClient?.close()
            adbClient = null
            val candidate = create()
            try {
                val info = readDeviceInfo(candidate)
                adbClient = candidate
                info
            } catch (error: Throwable) {
                candidate.close()
                throw error
            }
        }

    suspend fun readDeviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        readDeviceInfo(requireClient())
    }

    private suspend fun readDeviceInfo(client: AdbClient): DeviceInfo {
        suspend fun property(name: String): String {
            val result = client.shell("getprop $name")
            if (!result.isSuccess) {
                throw IOException(result.stderrText.ifBlank { "ADB could not read device information." })
            }
            return result.stdoutText.trim().ifBlank { "Unknown" }
        }
        return DeviceInfo(
            manufacturer = property("ro.product.manufacturer"),
            model = property("ro.product.model"),
            androidRelease = property("ro.build.version.release"),
            sdk = property("ro.build.version.sdk")
        )
    }

    suspend fun listFiles(path: String): List<RemoteEntry> = withContext(Dispatchers.IO) {
        val safePath = RemotePath.normalize(path)
        val result = requireClient().shell("ls -1p ${shellQuote(safePath)}")
        if (!result.isSuccess) {
            throw IOException(result.stderrText.ifBlank { result.stdoutText.ifBlank { "Unable to list this folder." } })
        }
        result.stdoutText
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .mapNotNull { rawName ->
                val directory = rawName.endsWith("/")
                val name = if (directory) rawName.dropLast(1) else rawName
                if (name.isBlank() || name == "." || name == "..") null
                else RemoteEntry(name, RemotePath.child(safePath, name), directory)
            }
            .sortedWith(compareBy<RemoteEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    suspend fun upload(
        uri: Uri,
        remoteDirectory: String,
        onProgress: (bytesDone: Long, totalBytes: Long?) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val destination = RemotePath.child(remoteDirectory, name)
        val temporaryPath = RemotePath.child(
            remoteDirectory,
            ".novastb-upload-${UUID.randomUUID()}.part"
        )
        val totalBytes = contentSize(uri)
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("Android could not open the selected file.")

        onProgress(0L, totalBytes)
        try {
            input.use {
                requireClient().sync.push(it, temporaryPath) { bytesDone ->
                    onProgress(bytesDone, totalBytes)
                }
            }
            val moveResult = requireClient().shell(
                "mv -f ${shellQuote(temporaryPath)} ${shellQuote(destination)}"
            )
            if (!moveResult.isSuccess) {
                throw IOException(
                    moveResult.stderrText.ifBlank {
                        moveResult.stdoutText.ifBlank { "The upload completed but could not be moved into place." }
                    }
                )
            }
            destination
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                runCatching {
                    requireClient().shell("rm -f ${shellQuote(temporaryPath)}")
                }
            }
            throw error
        }
    }

    suspend fun download(
        remotePath: String,
        destination: Uri,
        onProgress: (bytesDone: Long, totalBytes: Long?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val safePath = RemotePath.normalize(remotePath)
        val stat = requireClient().sync.stat(safePath)
        require(stat.isFile) { "Choose a file, not a folder, to download." }
        val totalBytes = stat.size.takeIf { it >= 0L }
        val temporaryFile = File.createTempFile("novastb-download-", ".part", appContext.cacheDir)

        onProgress(0L, totalBytes)
        try {
            temporaryFile.outputStream().use { output ->
                requireClient().sync.pull(safePath, output) { bytesDone ->
                    onProgress(bytesDone, totalBytes)
                }
            }

            // Only create/write the user-selected destination after the remote read completed.
            withContext(NonCancellable) {
                val output = appContext.contentResolver.openOutputStream(destination, "w")
                    ?: throw IOException("Android could not create the destination file.")
                output.use { target ->
                    temporaryFile.inputStream().use { source -> source.copyTo(target) }
                }
            }
        } finally {
            temporaryFile.delete()
        }
    }

    private fun contentSize(uri: Uri): Long? {
        return appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index < 0 || cursor.isNull(index)) null
            else cursor.getLong(index).takeIf { it >= 0L }
        }
    }

    suspend fun installApk(uri: Uri, onProgress: (Float, String) -> Unit) =
        withContext(Dispatchers.IO) {
            val name = displayName(uri)
            require(name.endsWith(".apk", ignoreCase = true)) {
                "Choose a single .apk file. Split bundles are not supported in this alpha."
            }
            val tempApk = File.createTempFile("novastb-install-", ".apk", appContext.cacheDir)
            try {
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Android could not open this APK.")
                input.use { source -> tempApk.outputStream().use { target -> source.copyTo(target) } }
                require(tempApk.length() > 0L) { "The selected APK is empty." }
                requireClient().install.install(tempApk, flags = listOf("-r")).collect { progress ->
                    onProgress(progress.percentage, progress.statusText)
                }
            } finally {
                tempApk.delete()
            }
        }

    suspend fun listUserApps(): List<String> = withContext(Dispatchers.IO) {
        val result = requireClient().shell("pm list packages -3")
        if (!result.isSuccess) {
            throw IOException(result.stderrText.ifBlank { "Unable to list installed applications." })
        }
        result.stdoutText
            .lineSequence()
            .map { it.trim().removePrefix("package:") }
            .filter { it.isNotBlank() && isValidPackageName(it) }
            .distinct()
            .sorted()
            .toList()
    }

    suspend fun launchApp(packageName: String) = withContext(Dispatchers.IO) {
        require(isValidPackageName(packageName)) { "Invalid package name." }
        val result = requireClient().shell("monkey -p $packageName 1")
        if (!result.isSuccess || result.stdoutText.contains("No activities found", true)) {
            throw IOException(result.stderrText.ifBlank { result.stdoutText.ifBlank { "The app could not be launched." } })
        }
    }

    suspend fun uninstallUserApp(packageName: String) = withContext(Dispatchers.IO) {
        require(isValidPackageName(packageName)) { "Invalid package name." }
        require(packageName in listUserApps()) {
            "NovaSTB only uninstalls third-party user apps listed by Package Manager."
        }
        val result = requireClient().shell("pm uninstall --user 0 $packageName")
        if (!result.isSuccess || !result.stdoutText.contains("Success", ignoreCase = true)) {
            throw IOException(result.stderrText.ifBlank { result.stdoutText.ifBlank { "Uninstall failed." } })
        }
    }

    fun displayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "selected-file"
    }

    private fun requireClient(): AdbClient {
        val client = adbClient
        if (client == null || client.isClosed) throw IOException("Not connected. Connect to the STB first.")
        return client
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'".toRegex(), """'"'"'""") + "'"

    private fun isValidPackageName(value: String): Boolean {
        val segments = value.split('.')
        return segments.size >= 2 && segments.all { segment ->
            segment.isNotEmpty() &&
                (segment.first().isLetter() || segment.first() == '_') &&
                segment.drop(1).all { it.isLetterOrDigit() || it == '_' }
        }
    }

    fun disconnect() {
        adbClient?.close()
        adbClient = null
    }
}
