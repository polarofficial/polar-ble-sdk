package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.impl.BDBleApiImpl
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
     * @return Single emitting the list of backed up file data.
     */
    fun backupDevice(): Single<List<BackupFileData>> {
        BleLogger.d(TAG, "Backing up device")

        return Single.fromCallable {
            BleLogger.d(TAG, "Requesting backup content")

            val builder = PftpRequest.PbPFtpOperation.newBuilder()
            builder.command = PftpRequest.PbPFtpOperation.Command.GET
            builder.path = ARABICA_SYS_FOLDER

            val entries = client.request(builder.build().toByteArray())
                    .toFlowable()
                    .flatMap { byteArrayOutputStream: ByteArrayOutputStream ->
                        BleLogger.d(TAG, "Received response from client request")
                        val dir = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                        val entries: MutableList<Pair<String, Long>> = mutableListOf()

                        for (entry in dir.entriesList) {
                            entries.add(ARABICA_SYS_FOLDER + entry.name to entry.size)
                            BleLogger.d(TAG, "Entry added: ${entry.name}, size: ${entry.size}")
                        }

                        Flowable.fromIterable(entries)
                    }
                    .toList()
                    .blockingGet()

            BleLogger.d(TAG, "Entries retrieved: ${entries.size}")

            val backupEntry = entries.find { it.first.endsWith("BACKUP.TXT") }

            if (backupEntry != null) {
                BleLogger.d(TAG, "Backup Entry: ${backupEntry.first}, Size: ${backupEntry.second}")

                val data = loadFile(backupEntry.first).blockingGet()
                BleLogger.d(TAG, "Data loaded for backup entry, size: ${data.size}")

                val stream = ByteArrayInputStream(data)
                val reader = stream.bufferedReader()
                val lines = reader.useLines { lines ->
                    lines.mapNotNull { line ->
                        try {
                            BleLogger.d(TAG, "Reading line: $line")
                            line
                        } catch (e: Exception) {
                            BleLogger.e(TAG, "Failed to read line: $line, error: $e")
                            null
                        }
                    }.toList()
                }
                BleLogger.d(TAG, "Lines read from backup entry: ${lines.size}")
                lines
            } else {
                BleLogger.w(TAG, "Device does not have BACKUP.TXT")
                emptyList()
            }
        }.flatMap { lines ->
            Observable.fromIterable(lines)
                    .flatMapSingle { line ->
                        BleLogger.d(TAG, "Backing up line: $line")
                        backupDirectory(line)
                                .onErrorResumeNext { error ->
                                    BleLogger.e(TAG, "Failed to backup line: $line, error: $error")
                                    Single.just(emptyList())
                                }
                    }
                    .toList()
                    .map { it.flatten() }
        }.onErrorReturn {
            BleLogger.e(TAG, "Failed to get backup content, error: $it")
            emptyList()
        }
    }

    /**
     * Restores the backup files.
     * @param backupFiles List of BackupFileData to be restored.
     * @return Completable indicating the completion of the restore operation.
     */
    fun restoreBackup(backupFiles: List<BackupFileData>): Completable {
        backupFiles.forEach {
            BleLogger.d(TAG, "Restoring backup file: ${it.directory} + ${it.fileName}")
        }
        return Completable.defer {
            Observable.fromIterable(backupFiles)
                    .flatMapCompletable { backupFileData ->
                        val header = PftpRequest.PbPFtpOperation.newBuilder()
                                .setCommand(PftpRequest.PbPFtpOperation.Command.PUT)
                                .setPath(backupFileData.directory + backupFileData.fileName)
                                .build().toByteArray()
                        val dataStream = ByteArrayInputStream(backupFileData.data)
                        client.write(header, dataStream)
                                .doOnSubscribe {
                                    BleLogger.d(TAG, "Sending PftpRequest: ${header.toString(Charsets.UTF_8)}")
                                }
                                .doOnComplete {
                                    BleLogger.d(TAG, "Successfully restored backup file: ${backupFileData.fileName}")
                                }
                                .doOnError { error ->
                                    BleLogger.e(TAG, "Failed to restore backup file: ${backupFileData.fileName}, error: $error")
                                }
                                .ignoreElements()
                                .onErrorResumeNext { error ->
                                    BleLogger.e(TAG, "Failed to restore backup file: ${backupFileData.fileName}, error: $error")
                                    Completable.complete()
                                }
                    }
        }
    }

    private fun loadFile(path: String): Single<ByteArray> {
        return client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                        .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                        .setPath(path)
                        .build().toByteArray()
        )
                .map { byteArray ->
                    byteArray.toByteArray()
                }
                .doOnError { error ->
                    BleLogger.i(TAG, "Failed to load file: $path, error: $error")
                }
    }

    private fun backupDirectory(backupDirectory: String): Single<List<BackupFileData>> {
        val path = backupDirectory.replace(
                USER_WILD_CARD_ROOT_FOLDER,
                ARABICA_USER_ROOT_FOLDER
        )

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
                                }.map {
                                    folder + it.name
                                }
                            }
                } catch (e: Exception) {
                    BleLogger.e(TAG, "Error loading subdirectories: $rootPath, error: $e")
                    emptyList()
                }

                val backupFiles = backUpDirectories(backupDirectories)
                Single.just(backupFiles)
            }

            path.isFolder() -> {
                val backupFiles = try {
                    backUpDirectories(loadSubDirectories(path))
                } catch (e: Exception) {
                    BleLogger.e(TAG, "Error loading subdirectories: $path, error: $e")
                    emptyList()
                }
                Single.just(backupFiles)
            }

            else -> {
                BleLogger.d(TAG, "Backup file: $path")
                loadFile(path)
                        .map { data ->
                            val file = path.substringAfterLast(File.separator)
                            val filePath = path.substringBefore(file)
                            listOf(BackupFileData(data, filePath, file))
                        }
                        .onErrorReturn {
                            BleLogger.e(TAG, "Error loading file: $path, error: $it")
                            emptyList()
                        }
            }
        }
    }

    private fun loadEntries(path: String): DeviceFolderEntries {
        val entriesList = mutableListOf<DeviceFolderEntry>()

        fetchRecursively(path)
                .blockingForEach { (entryPath, size) ->
                    val name = entryPath.substringAfterLast('/')
                    entriesList.add(DeviceFolderEntry(name, size, entryPath))
                }

        return DeviceFolderEntries(entriesList)
    }

    private fun fetchRecursively(path: String): Flowable<Pair<String, Long>> {
        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = path
        return client.request(builder.build().toByteArray())
                .flatMapPublisher { byteArrayOutputStream: ByteArrayOutputStream ->
                    val dir = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                    val entries: MutableMap<String, Long> = mutableMapOf()

                    for (entry in dir.entriesList) {
                        entries[path + entry.name] = entry.size
                    }

                    if (entries.isNotEmpty()) {
                        Flowable.fromIterable(entries.toList())
                                .flatMap { entry ->
                                    if (entry.first.endsWith("/")) {
                                        fetchRecursively(entry.first)
                                    } else {
                                        Flowable.just(entry)
                                    }
                                }
                    } else {
                        Flowable.empty()
                    }
                }
    }

    private fun loadSubDirectories(path: String): List<String> {
        return loadEntries(path).entriesList.map { entry ->
            path + entry.name
        }
    }

    private fun backUpDirectories(folders: List<String>): List<BackupFileData> {
        return folders.flatMap { path ->
            backupDirectory(path).blockingGet()
        }
    }

    private fun String.isFolder() = this.endsWith("/")
}