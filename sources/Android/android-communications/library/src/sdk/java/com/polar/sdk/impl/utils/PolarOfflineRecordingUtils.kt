package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.*

internal object PolarOfflineRecordingUtils {

    private const val TAG = "OfflineRecordingUtils"
    private const val PMD_FILE_PATH = "/PMDFILES.TXT"

    fun mapOfflineRecordingFileNameToMeasurementType(fileName: String): PmdMeasurementType {
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
        fetchRecursively: (client: BlePsFtpClient, path: String, condition: (String) -> Boolean) -> Flowable<Pair<String, Long>>
    ): Flowable<PolarOfflineRecordingEntry> {

        return fetchRecursively(client, "/U/0/") { entry ->
            entry.matches(Regex("^(\\d{8})(/)")) ||
                    entry == "R/" ||
                    entry.matches(Regex("^(\\d{6})(/)")) ||
                    entry.contains(".REC")
        }.subscribeOn(Schedulers.io())
            .flatMap { entry ->
                try {
                    val components = entry.first.split("/")
                    val format = SimpleDateFormat("yyyyMMdd HHmmss", Locale.getDefault())
                    val date = format.parse("${components[3]} ${components[5]}")
                        ?: throw PolarInvalidArgument("Cannot parse date from ${components[3]} and ${components[5]}")

                    val type = mapPmdMeasurementTypeToPolarDeviceDataType(
                        mapOfflineRecordingFileNameToMeasurementType(
                            components[6].substringBefore(".").filter { !it.isDigit() }
                        )
                    )

                    Flowable.just(PolarOfflineRecordingEntry(entry.first, entry.second, date, type))
                } catch (e: Exception) {
                    BleLogger.e(TAG, "Error processing offline recording entry ${entry.first}: ${e.message}")
                    Flowable.empty()
                }
            }
            .groupBy { it.path.replace(Regex("\\d+\\.REC$"), ".REC") }
            .onBackpressureBuffer(
                8192,
                {
                    BleLogger.w(TAG, "Offline recording entries dropped due to buffer overflow")
                },
                BackpressureOverflowStrategy.DROP_LATEST
            )
            .flatMap { groupedEntries ->
                groupedEntries.toList().flatMapPublisher { entriesList ->
                    if (entriesList.isEmpty()) return@flatMapPublisher Flowable.empty()
                    val totalSize = entriesList.sumOf { it.size.toInt() }
                    val first = entriesList.first()
                    Flowable.just(
                        PolarOfflineRecordingEntry(
                            path = first.path.replace(Regex("\\d+\\.REC$"), ".REC"),
                            size = totalSize.toLong(),
                            date = first.date,
                            type = first.type
                        )
                    )
                }.onErrorResumeNext { throwable: Throwable ->
                    Flowable.error(throwable)
                }
            }
    }

    fun listOfflineRecordingsV2(
        client: BlePsFtpClient,
        getFile: (BlePsFtpClient, String) -> Single<ByteArray>
    ): Single<List<PolarOfflineRecordingEntry>> {

        return Single.create { emitter ->
            val disposable = getFile(client, PMD_FILE_PATH)
                .subscribe(
                    { byteArray ->
                        try {
                            val offlineRecordings = ByteArrayInputStream(byteArray)
                                .bufferedReader()
                                .useLines { lines ->
                                    lines.mapNotNull { line ->
                                        try {
                                            val parts = line.split(" ")
                                            if (parts.size < 2 || !parts[1].endsWith(".REC")) return@mapNotNull null

                                            val path = parts[1].replace(Regex("(\\w*?)\\d+\\.REC$"), "$1.REC")
                                            val type = mapPmdMeasurementTypeToPolarDeviceDataType(
                                                mapOfflineRecordingFileNameToMeasurementType(
                                                    path.split("/")[6].substringBefore(".").filter { !it.isDigit() }
                                                )
                                            )

                                            val format = SimpleDateFormat("yyyyMMdd HHmmss", Locale.getDefault())
                                            val date = format.parse("${path.split("/")[3]} ${path.split("/")[5]}")

                                            PolarOfflineRecordingEntry(path, parts[0].toLong(), date, type)
                                        } catch (e: Exception) {
                                            BleLogger.e(TAG, "Failed to parse line: $line (${e.message})")
                                            null
                                        }
                                    }.toList()
                                }

                            val merged = offlineRecordings
                                .groupBy { it.path to it.date }
                                .map { (_, entries) ->
                                    val totalSize = entries.sumOf { it.size }
                                    val first = entries.first()
                                    first.copy(size = totalSize)
                                }

                            emitter.onSuccess(merged)
                        } catch (e: Exception) {
                            BleLogger.e(TAG, "Failed to parse PMD.TXT: ${e.message}")
                            emitter.onError(e)
                        }
                    },
                    { error ->
                        BleLogger.e(TAG, "Failed to get file $PMD_FILE_PATH: ${error.message}")
                        emitter.onError(error)
                    }
                )

            emitter.setCancellable { disposable.dispose() }
        }
    }
}