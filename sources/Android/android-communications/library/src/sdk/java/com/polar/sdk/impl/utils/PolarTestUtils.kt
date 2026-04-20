package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.DeviationFromBaseline
import com.polar.sdk.api.model.PolarSpo2TestData
import com.polar.sdk.api.model.Spo2Class
import com.polar.sdk.api.model.Spo2TestStatus
import com.polar.services.datamodels.protobuf.Spo2TestResult
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val SPO2_TEST_DIRECTORY = "SPO2TEST/"
private const val SPO2_TEST_PROTO = "SPO2TRES.BPB"
private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private val testTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val TAG = "PolarTestUtils"

/**
 * Represents a single SPO2 test result proto together with its time-subdirectory name (HHMMSS)
 * and the date it belongs to.
 */
data class Spo2TestEntry(val date: LocalDate, val timeDirName: String, val protoBytes: ByteArray)

internal object PolarTestUtils {

    /**
     * Read and return all SPO2 test proto entries for a given date.
     *
     * Files reside at `/U/0/<yyyyMMdd>/SPO2TEST/<HHMMSS>/SPO2TRES.BPB`.
     * Multiple time subdirectories may exist; all are read and returned.
     */
    suspend fun readSpo2TestProtoFromDayDirectory(client: BlePsFtpClient, date: LocalDate): List<Spo2TestEntry> {
        BleLogger.d(TAG, "readSpo2TestProtoFromDayDirectory: $date")
        val spo2TestDirPath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$SPO2_TEST_DIRECTORY"

        return try {
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(spo2TestDirPath)
                    .build()
                    .toByteArray()
            )

            val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
            val timeSubDirs = dir.entriesList.filter { it.name.endsWith("/") }

            if (timeSubDirs.isEmpty()) {
                BleLogger.d(TAG, "No time subdirectory found in $spo2TestDirPath")
                return emptyList()
            }

            val results = mutableListOf<Spo2TestEntry>()
            for (subDir in timeSubDirs) {
                val timeDirName = subDir.name.trimEnd('/')
                val filePath = "$spo2TestDirPath${subDir.name}$SPO2_TEST_PROTO"
                try {
                    val fileResponse = client.request(
                        PftpRequest.PbPFtpOperation.newBuilder()
                            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                            .setPath(filePath)
                            .build()
                            .toByteArray()
                    )
                    results.add(Spo2TestEntry(date = date, timeDirName = timeDirName, protoBytes = fileResponse.toByteArray()))
                } catch (error: Throwable) {
                    BleLogger.w(TAG, "No SPO2 test proto at $filePath: $error")
                }
            }
            results
        } catch (error: Throwable) {
            BleLogger.w(TAG, "readSpo2TestProtoFromDayDirectory() failed while reading $spo2TestDirPath, error occurred $error.")
            emptyList()
        }
    }

    fun mapSpo2TestEntry(entry: Spo2TestEntry): PolarSpo2TestData {
        val proto = Spo2TestResult.PbSpo2TestResult.parseFrom(entry.protoBytes)
        return mapSpo2TestProto(proto, entry.date, entry.timeDirName)
    }

    internal fun mapSpo2TestProto(
        proto: Spo2TestResult.PbSpo2TestResult,
        date: LocalDate,
        timeDirName: String
    ): PolarSpo2TestData {
        val tzOffsetMinutes = proto.timeZoneOffset
        val testTime = dateTimeFromFolderNames(date, timeDirName)
            ?: if (proto.testTime != 0L) {
                val localDateTime = java.time.Instant.ofEpochMilli(proto.testTime.toLong())
                    .atOffset(ZoneOffset.ofTotalSeconds(tzOffsetMinutes * 60))
                    .toLocalDateTime()
                testTimeFormatter.format(localDateTime)
            } else null
        return PolarSpo2TestData(
            recordingDevice = proto.recordingDevice,
            testTime = testTime,
            timeZoneOffsetMinutes = tzOffsetMinutes,
            testStatus = Spo2TestStatus.from(proto.testStatus.number),
            bloodOxygenPercent = if (proto.hasBloodOxygenPercent()) proto.bloodOxygenPercent else null,
            spo2Class = if (proto.hasSpo2Class()) Spo2Class.from(proto.spo2Class.number) else null,
            spo2ValueDeviationFromBaseline = if (proto.hasSpo2ValueDeviationFromBaseline()) DeviationFromBaseline.from(proto.spo2ValueDeviationFromBaseline.number) else null,
            spo2QualityAveragePercent = if (proto.hasSpo2QualityAveragePercent()) proto.spo2QualityAveragePercent else null,
            averageHeartRateBpm = if (proto.hasAverageHeartRateBpm()) proto.averageHeartRateBpm.toUInt() else null,
            heartRateVariabilityMs = if (proto.hasHeartRateVariabilityMs()) proto.heartRateVariabilityMs else null,
            spo2HrvDeviationFromBaseline = if (proto.hasSpo2HrvDeviationFromBaseline()) DeviationFromBaseline.from(proto.spo2HrvDeviationFromBaseline.number) else null,
            altitudeMeters = if (proto.hasAltitudeMeters()) proto.altitudeMeters else null
        )
    }

    internal fun dateTimeFromFolderNames(date: LocalDate, timeDirName: String): String? {
        if (timeDirName.length != 6) return null
        val hh = timeDirName.substring(0, 2).toIntOrNull() ?: return null
        val mm = timeDirName.substring(2, 4).toIntOrNull() ?: return null
        val ss = timeDirName.substring(4, 6).toIntOrNull() ?: return null
        return testTimeFormatter.format(LocalDateTime.of(date.year, date.monthValue, date.dayOfMonth, hh, mm, ss))
    }
}
