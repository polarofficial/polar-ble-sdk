package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

internal object PolarOfflineRecordingUtils {

    private const val TAG = "OfflineRecordingUtils"
    private const val PMD_FILE_PATH = "/PMDFILES.TXT"

    private fun mapOfflineRecordingFileNameToMeasurementType(fileName: String): PmdMeasurementType {
        val fileNameWithoutExtension = fileName.substringBeforeLast(".")
        return when (fileNameWithoutExtension.replace(Regex("\\d+"), "")) {
            "ACC" -> PmdMeasurementType.ACC
            "GYRO" -> PmdMeasurementType.GYRO
            "MAG" -> PmdMeasurementType.MAGNETOMETER
            "PPG" -> PmdMeasurementType.PPG
            "PPI" -> PmdMeasurementType.PPI
            "HR" -> PmdMeasurementType.OFFLINE_HR
            "TEMP" -> PmdMeasurementType.TEMPERATURE
            "SKINTEMP" -> PmdMeasurementType.SKIN_TEMP
            else -> throw IllegalArgumentException("Unknown offline file $fileName")
        }
    }

    private fun mapPmdMeasurementTypeToPolarDeviceDataType(type: PmdMeasurementType): PolarBleApi.PolarDeviceDataType {
        return when (type) {
            PmdMeasurementType.ACC -> PolarBleApi.PolarDeviceDataType.ACC
            PmdMeasurementType.GYRO -> PolarBleApi.PolarDeviceDataType.GYRO
            PmdMeasurementType.MAGNETOMETER -> PolarBleApi.PolarDeviceDataType.MAGNETOMETER
            PmdMeasurementType.PPG -> PolarBleApi.PolarDeviceDataType.PPG
            PmdMeasurementType.PPI -> PolarBleApi.PolarDeviceDataType.PPI
            PmdMeasurementType.OFFLINE_HR -> PolarBleApi.PolarDeviceDataType.HR
            PmdMeasurementType.TEMPERATURE -> PolarBleApi.PolarDeviceDataType.TEMPERATURE
            PmdMeasurementType.SKIN_TEMP -> PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE
            else -> throw IllegalArgumentException("Unknown PMD measurement type: $type")
        }
    }

    fun listOfflineRecordingsV1(
        client: BlePsFtpClient,
        fetchRecursively: (client: BlePsFtpClient, path: String, condition: (String) -> Boolean) -> Flow<Pair<String, Long>>
    ): Flow<PolarOfflineRecordingEntry> = flow {

        val grouped = mutableMapOf<String, MutableList<PolarOfflineRecordingEntry>>()

        fetchRecursively(client, "/U/0/") { entry ->
            entry.matches(Regex("^(\\d{8})(/)")) ||
                    entry == "R/" ||
                    entry.matches(Regex("^(\\d{6})(/)")) ||
                    entry.contains(".REC")
        }.collect { entry ->
            try {
                val components = entry.first.split("/")
                val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ENGLISH)
                val date = LocalDateTime.parse("${components[3]}${components[5]}", dateTimeFormatter)
                    ?: throw PolarInvalidArgument("Cannot parse date from ${components[3]} and ${components[5]}")

                val type = mapPmdMeasurementTypeToPolarDeviceDataType(
                    mapOfflineRecordingFileNameToMeasurementType(
                        components[6].substringBefore(".").filter { !it.isDigit() }
                    )
                )
                if (entry.second <= 0) {
                    BleLogger.e(TAG, "Encountered zero size entry in path ${entry.first}. Ignoring empty recording.")
                    return@collect
                }

                val groupKey = entry.first.replace(Regex("\\d+\\.REC$"), ".REC")
                grouped.getOrPut(groupKey) { mutableListOf() }.add(
                    PolarOfflineRecordingEntry(entry.first, entry.second, date, type)
                )
            } catch (e: Exception) {
                BleLogger.e(TAG, "Error processing offline recording entry ${entry.first}: ${e.message}")
            }
        }

        for ((groupKey, entries) in grouped) {
            if (entries.isEmpty()) continue
            val totalSize = entries.sumOf { it.size }
            val first = entries.first()
            emit(PolarOfflineRecordingEntry(
                path = groupKey,
                size = totalSize,
                date = first.date,
                type = first.type
            ))
        }
    }

    fun listOfflineRecordingsV2(file: ByteArray): List<PolarOfflineRecordingEntry> {
        val offlineRecordings = ByteArrayInputStream(file)
            .bufferedReader()
            .useLines { lines ->
                lines.mapNotNull { line ->
                    try {
                        val parts = line.split(" ")
                        if (parts.size < 2 || !parts[1].endsWith(".REC")) return@mapNotNull null
                        val path = parts[1].replace(Regex("(\\w*?)\\d+\\.REC$"), "$1.REC")
                        if (parts[0].toInt() <= 0) {
                            BleLogger.e(TAG, "Encountered zero size entry in path $path. Ignoring empty recording.")
                            return@mapNotNull null
                        }
                        val type = mapPmdMeasurementTypeToPolarDeviceDataType(
                            mapOfflineRecordingFileNameToMeasurementType(
                                path.split("/")[6].substringBefore(".").filter { !it.isDigit() }
                            )
                        )
                        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ENGLISH)
                        val date = LocalDateTime.parse("${path.split("/")[3]}${path.split("/")[5]}", dateTimeFormatter)

                        PolarOfflineRecordingEntry(path, parts[0].toLong(), date, type)
                    } catch (e: Exception) {
                        BleLogger.e(TAG, "Failed to parse line: $line (${e.message})")
                        null
                    }
                }.toList()
            }

        return offlineRecordings
            .groupBy { it.path to it.date }
            .map { (_, entries) ->
                val totalSize = entries.sumOf { it.size }
                val first = entries.first()
                first.copy(size = totalSize)
            }
    }
}