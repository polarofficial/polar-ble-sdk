package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import java.io.ByteArrayInputStream
import java.io.File
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory

private const val TAG = "PolarDeviceBackup"
private const val ARABICA_SYS_FOLDER = "/SYS/"
private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val USER_WILD_CARD_ROOT_FOLDER = "/U/*/"
private const val WILD_CARD_CHARACTER = "*"

/**
 * Handles backing up the device.
 * @param client BlePsFtpClient
 */
class PolarBackupManager(private val client: BlePsFtpClient) {

    class BackupFileData(
        val data: ByteArray,
        val directory: String,
        val fileName: String
    )

    private class DeviceFolderEntries(val entriesList: List<DeviceFolderEntry>)

    private data class DeviceFolderEntry(
        val name: String,
        val size: Long = 0L,
        val folderPath: String = ""
    )

    /**
     * Backs up the device.
     * @return List of backed up file data.
     */
    suspend fun backupDevice(): List<BackupFileData> {
        BleLogger.d(TAG, "Backing up device")
        BleLogger.d(TAG, "Requesting backup content")

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(ARABICA_SYS_FOLDER)

        val response = client.request(builder.build().toByteArray())
        BleLogger.d(TAG, "Received response from client request")
        val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
        val entries = dir.entriesList.map { entry ->
            BleLogger.d(TAG, "Entry added: ${entry.name}, size: ${entry.size}")
            ARABICA_SYS_FOLDER + entry.name to entry.size
        }

        BleLogger.d(TAG, "Entries retrieved: ${entries.size}")

        val defaultBackupDirectories = linkedSetOf(
            "/U/*/S/PHYSDATA.BPB",
            "/U/*/S/UDEVSET.BPB",
            "/U/*/S/PREFS.BPB",
            "/U/*/USERID.BPB"
        )

        val backupEntry = entries.find { it.first.endsWith("BACKUP.TXT") }

        val lines: MutableSet<String> = if (backupEntry != null) {
            BleLogger.d(TAG, "Backup Entry: ${backupEntry.first}, Size: ${backupEntry.second}")
            val data = loadFile(backupEntry.first)
            BleLogger.d(TAG, "Data loaded for backup entry, size: ${data.size}")
            val stream = ByteArrayInputStream(data)
            val reader = stream.bufferedReader()
            val readLines = reader.useLines { seq ->
                seq.mapNotNull { line ->
                    try {
                        BleLogger.d(TAG, "Reading line: $line")
                        line
                    } catch (e: Exception) {
                        BleLogger.e(TAG, "Failed to read line: $line, error: $e")
                        null
                    }
                }.toMutableSet()
            }
            readLines.addAll(defaultBackupDirectories)
            BleLogger.d(TAG, "Lines read from backup entry plus defaults: ${readLines.size}")
            readLines
        } else {
            BleLogger.w(TAG, "Device does not have BACKUP.TXT, using default backup directories")
            defaultBackupDirectories
        }

        return try {
            lines.flatMap { line ->
                BleLogger.d(TAG, "Backing up line: $line")
                try {
                    backupDirectory(line)
                } catch (error: Throwable) {
                    BleLogger.e(TAG, "Failed to backup line: $line, error: $error")
                    emptyList()
                }
            }
        } catch (it: Throwable) {
            BleLogger.e(TAG, "Failed to get backup content, error: $it")
            emptyList()
        }
    }

    /**
     * Restores the backup files.
     * @param backupFiles List of BackupFileData to be restored.
     */
    suspend fun restoreBackup(backupFiles: List<BackupFileData>) {
        backupFiles.forEach {
            BleLogger.d(TAG, "Restoring backup file: ${it.directory} + ${it.fileName}")
        }
        for (backupFileData in backupFiles) {
            val header = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                .setPath(backupFileData.directory + backupFileData.fileName)
                .build().toByteArray()
            val dataStream = ByteArrayInputStream(backupFileData.data)
            BleLogger.d(TAG, "Sending PftpRequest: ${header.toString(Charsets.UTF_8)}")
            try {
                client.write(header, dataStream).collect { }
                BleLogger.d(TAG, "Successfully restored backup file: ${backupFileData.fileName}")
            } catch (error: Throwable) {
                BleLogger.e(TAG, "Failed to restore backup file: ${backupFileData.fileName}, error: $error")
                // continue with next file
            }
        }
    }

    private suspend fun loadFile(path: String): ByteArray {
        return try {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(path)
                    .build().toByteArray()
            ).toByteArray()
        } catch (error: Throwable) {
            BleLogger.i(TAG, "Failed to load file: $path, error: $error")
            throw error
        }
    }

    private suspend fun backupDirectory(backupDirectory: String): List<BackupFileData> {
        val path = backupDirectory.replace(USER_WILD_CARD_ROOT_FOLDER, ARABICA_USER_ROOT_FOLDER)

        return when {
            path.contains(WILD_CARD_CHARACTER) -> {
                val rootPath = path.substringBefore(WILD_CARD_CHARACTER)
                val subFolderToBackup = path.substringAfter(WILD_CARD_CHARACTER)
                    .removePrefix(File.separator)
                    .split(File.separator)
                    .firstOrNull()

                val backupDirectories = try {
                    loadSubDirectories(rootPath)
                        .filter { it.isFolder() }
                        .flatMap { folder ->
                            loadEntries(folder).entriesList.filter {
                                val name = it.name.replace(File.separator, "")
                                name == subFolderToBackup
                            }.map { folder + it.name }
                        }
                } catch (e: Exception) {
                    BleLogger.e(TAG, "Error loading subdirectories: $rootPath, error: $e")
                    emptyList()
                }

                backUpDirectories(backupDirectories)
            }

            path.isFolder() -> {
                try {
                    backUpDirectories(loadSubDirectories(path))
                } catch (e: Exception) {
                    BleLogger.e(TAG, "Error loading subdirectories: $path, error: $e")
                    emptyList()
                }
            }

            else -> {
                BleLogger.d(TAG, "Backup file: $path")
                try {
                    val data = loadFile(path)
                    val file = path.substringAfterLast(File.separator)
                    val filePath = path.substringBefore(file)
                    listOf(BackupFileData(data, filePath, file))
                } catch (it: Throwable) {
                    BleLogger.e(TAG, "Error loading file: $path, error: $it")
                    emptyList()
                }
            }
        }
    }

    private suspend fun loadEntries(path: String): DeviceFolderEntries {
        val entriesList = mutableListOf<DeviceFolderEntry>()
        fetchRecursively(path).forEach { (entryPath, size) ->
            val name = entryPath.substringAfterLast('/')
            entriesList.add(DeviceFolderEntry(name, size, entryPath))
        }
        return DeviceFolderEntries(entriesList)
    }

    private suspend fun fetchRecursively(path: String): List<Pair<String, Long>> {
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(path)
        val response = client.request(builder.build().toByteArray())
        val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
        val entries = dir.entriesList.associate { path + it.name to it.size }

        if (entries.isEmpty()) return emptyList()

        return entries.flatMap { (entryPath, size) ->
            if (entryPath.endsWith("/")) {
                fetchRecursively(entryPath)
            } else {
                listOf(entryPath to size)
            }
        }
    }

    private suspend fun loadSubDirectories(path: String): List<String> {
        return loadEntries(path).entriesList.map { path + it.name }
    }

    private suspend fun backUpDirectories(folders: List<String>): List<BackupFileData> {
        return folders.flatMap { path -> backupDirectory(path) }
    }

    private fun String.isFolder() = this.endsWith("/")
}