package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.fromPbPPiDataSamples
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import java.time.LocalDate
import java.util.regex.Pattern

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val AUTOMATIC_SAMPLES_DIRECTORY = "AUTOS/"
private const val AUTOMATIC_SAMPLES_PATTERN = "AUTOS\\d{3}\\.BPB"
private const val TAG = "PolarAutomaticSamplesUtils"

internal object PolarAutomaticSamplesUtils {

    /**
     * Read 24/7 heart rate samples for given date range.
     */
    suspend fun read247HrSamples(client: BlePsFtpClient, fromDate: LocalDate, toDate: LocalDate): List<Polar247HrSamplesData> {
        BleLogger.d(TAG, "read247HrSamples: from $fromDate to $toDate")
        val autoSamplesPath = "$ARABICA_USER_ROOT_FOLDER$AUTOMATIC_SAMPLES_DIRECTORY"

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(autoSamplesPath)

        val response = client.request(builder.build().toByteArray())
        val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
        val pattern = Pattern.compile(AUTOMATIC_SAMPLES_PATTERN)
        val filteredFiles = dir.entriesList
            .filter { pattern.matcher(it.name).matches() }
            .map { it.name }

        val hrSamplesDataList = mutableListOf<Polar247HrSamplesData>()

        for (fileName in filteredFiles) {
            val filePath = "$autoSamplesPath$fileName"
            val fileBuilder = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath(filePath)
            BleLogger.d(TAG, "Sending GET request for file: $filePath")
            val fileResponse = client.request(fileBuilder.build().toByteArray())
            val sampleSessions = PbAutomaticSampleSessions.parseFrom(fileResponse.toByteArray())
            val sampleDate = PolarTimeUtils.pbDateToLocalDate(sampleSessions.day)
            if (sampleDate in fromDate..toDate) {
                hrSamplesDataList.add(Polar247HrSamplesData.fromProto(sampleSessions))
            } else {
                BleLogger.d(TAG, "Sample date $sampleDate is out of range: $fromDate to $toDate")
            }
        }

        return hrSamplesDataList
    }

    suspend fun read247PPiSamples(client: BlePsFtpClient, fromDate: LocalDate, toDate: LocalDate): List<Polar247PPiSamplesData> {
        BleLogger.d(TAG, "read247PPiSamples: from $fromDate to $toDate")
        val autoSamplesPath = "$ARABICA_USER_ROOT_FOLDER$AUTOMATIC_SAMPLES_DIRECTORY"

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(autoSamplesPath)

        val response = client.request(builder.build().toByteArray())
        val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
        val pattern = Pattern.compile(AUTOMATIC_SAMPLES_PATTERN)
        val filteredFiles = dir.entriesList
            .filter { pattern.matcher(it.name).matches() }
            .map { it.name }

        val ppiSamplesDataList = mutableListOf<Polar247PPiSamplesData>()

        for (fileName in filteredFiles) {
            val filePath = "$autoSamplesPath$fileName"
            val fileBuilder = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                .setPath(filePath)
            BleLogger.d(TAG, "Sending GET request for file: $filePath")
            val fileResponse = client.request(fileBuilder.build().toByteArray())
            val sampleSessions = PbAutomaticSampleSessions.parseFrom(fileResponse.toByteArray())
            val sampleDateProto = sampleSessions.day
            val sampleDateForCheck = LocalDate.of(sampleDateProto.year, sampleDateProto.month, sampleDateProto.day)
            for (sample in sampleSessions.ppiSamplesList) {
                if (sampleDateForCheck in fromDate..toDate) {
                    ppiSamplesDataList.add(Polar247PPiSamplesData(sampleDateForCheck, fromPbPPiDataSamples(sample)))
                } else {
                    BleLogger.d(TAG, "Sample date $sampleDateForCheck is out of range: $fromDate to $toDate")
                }
            }
        }

        return ppiSamplesDataList
    }
}