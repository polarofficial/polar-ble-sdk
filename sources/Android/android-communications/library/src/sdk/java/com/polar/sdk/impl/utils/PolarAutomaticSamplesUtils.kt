package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.AutomaticSampleTriggerType
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.fromPbPPiDataSamples
import fi.polar.remote.representation.protobuf.AutomaticSamples
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import io.reactivex.rxjava3.core.Single
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import java.util.Calendar
import java.util.Date
import java.util.regex.Pattern

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val AUTOMATIC_SAMPLES_DIRECTORY = "AUTOS/"
private const val AUTOMATIC_SAMPLES_PATTERN = "AUTOS\\d{3}\\.BPB"
private const val TAG = "PolarAutomaticSamplesUtils"
internal object PolarAutomaticSamplesUtils {

    /**
     * Read 24/7 heart rate samples for given date range.
     */
    fun read247HrSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date): Single<List<Polar247HrSamplesData>> {
        BleLogger.d(TAG, "read247HrSamples: from $fromDate to $toDate")
        return Single.create { emitter ->
            val autoSamplesPath = "$ARABICA_USER_ROOT_FOLDER$AUTOMATIC_SAMPLES_DIRECTORY"
            val builder = PftpRequest.PbPFtpOperation.newBuilder()
            builder.command = PftpRequest.PbPFtpOperation.Command.GET
            builder.path = autoSamplesPath
            val disposable = client.request(builder.build().toByteArray()).subscribe(
                    { response ->
                        val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
                        val pattern = Pattern.compile(AUTOMATIC_SAMPLES_PATTERN)
                        val filteredFiles = dir.entriesList
                                .filter { pattern.matcher(it.name).matches() }
                                .map { it.name }

                        val hrSamplesDataList = mutableListOf<Polar247HrSamplesData>()

                        val fileRequests = filteredFiles.map { fileName ->
                            val filePath = "$autoSamplesPath$fileName"
                            val fileBuilder = PftpRequest.PbPFtpOperation.newBuilder()
                            fileBuilder.command = PftpRequest.PbPFtpOperation.Command.GET
                            fileBuilder.path = filePath
                            BleLogger.d(TAG, "Sending GET request for file: $filePath")
                            client.request(fileBuilder.build().toByteArray()).map { fileResponse ->
                                val sampleSessions = PbAutomaticSampleSessions.parseFrom(fileResponse.toByteArray())
                                val sampleDateProto = sampleSessions.day
                                sampleSessions.samplesList.forEach { sample ->
                                    val sampleTimeProto = sample.time
                                    val sampleDate = Calendar.getInstance().apply {
                                        set(sampleDateProto.year, sampleDateProto.month - 1, sampleDateProto.day, sampleTimeProto.hour, sampleTimeProto.minute, sampleTimeProto.seconds)
                                        set(Calendar.MILLISECOND, 0)
                                    }.time

                                    val sampleDateForCheck = Calendar.getInstance().apply {
                                        set(sampleDateProto.year, sampleDateProto.month - 1, sampleDateProto.day, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.time

                                    if (sampleDateForCheck in fromDate..toDate) {
                                        val hrSamples = sample.heartRateList
                                        val triggerType = when (sample.triggerType) {
                                            AutomaticSamples.PbMeasTriggerType.TRIGGER_TYPE_HIGH_ACTIVITY -> AutomaticSampleTriggerType.TRIGGER_TYPE_HIGH_ACTIVITY
                                            AutomaticSamples.PbMeasTriggerType.TRIGGER_TYPE_LOW_ACTIVITY -> AutomaticSampleTriggerType.TRIGGER_TYPE_LOW_ACTIVITY
                                            AutomaticSamples.PbMeasTriggerType.TRIGGER_TYPE_TIMED -> AutomaticSampleTriggerType.TRIGGER_TYPE_TIMED
                                            AutomaticSamples.PbMeasTriggerType.TRIGGER_TYPE_MANUAL -> AutomaticSampleTriggerType.TRIGGER_TYPE_MANUAL
                                            else -> throw IllegalArgumentException("Unknown trigger type: ${sample.triggerType}")
                                        }
                                        hrSamplesDataList.add(Polar247HrSamplesData(sampleDate, hrSamples, triggerType))
                                    } else {
                                        BleLogger.d(TAG, "Sample date $sampleDate is out of range: $fromDate to $toDate")
                                    }
                                }
                            }
                        }

                        Single.merge(fileRequests)
                                .doOnComplete {
                                    emitter.onSuccess(hrSamplesDataList)
                                }
                                .doOnError { error ->
                                    BleLogger.e(TAG, "Error processing files: $error")
                                    emitter.onError(error)
                                }
                                .subscribe()
                    },
                    { error ->
                        BleLogger.e(TAG, "read247HrSamples() failed for path: $autoSamplesPath, error: $error")
                        emitter.onError(error)
                    }
            )
            emitter.setDisposable(disposable)
        }
    }

    fun read247PPiSamples(client: BlePsFtpClient, fromDate: Date, toDate: Date): Single<List<Polar247PPiSamplesData>> {
        BleLogger.d(TAG, "read247PPiSamples: from $fromDate to $toDate")
        return Single.create { emitter ->
            val autoSamplesPath = "$ARABICA_USER_ROOT_FOLDER$AUTOMATIC_SAMPLES_DIRECTORY"
            val builder = PftpRequest.PbPFtpOperation.newBuilder()
            builder.command = PftpRequest.PbPFtpOperation.Command.GET
            builder.path = autoSamplesPath
            val disposable = client.request(builder.build().toByteArray()).subscribe(
                { response ->
                    val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
                    val pattern = Pattern.compile(AUTOMATIC_SAMPLES_PATTERN)
                    val filteredFiles = dir.entriesList
                        .filter { pattern.matcher(it.name).matches() }
                        .map { it.name }

                    val ppiSamplesDataList = mutableListOf<Polar247PPiSamplesData>()

                    val fileRequests = filteredFiles.map { fileName ->
                        val filePath = "$autoSamplesPath$fileName"
                        val fileBuilder = PftpRequest.PbPFtpOperation.newBuilder()
                        fileBuilder.command = PftpRequest.PbPFtpOperation.Command.GET
                        fileBuilder.path = filePath
                        BleLogger.d(TAG, "Sending GET request for file: $filePath")
                        client.request(fileBuilder.build().toByteArray()).map { fileResponse ->
                            val sampleSessions = PbAutomaticSampleSessions.parseFrom(fileResponse.toByteArray())
                            val sampleDateProto = sampleSessions.day
                            sampleSessions.ppiSamplesList.forEach { sample ->

                                val sampleDateForCheck = Calendar.getInstance().apply {
                                    set(sampleDateProto.year, sampleDateProto.month - 1, sampleDateProto.day, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.time

                                if (sampleDateForCheck in fromDate..toDate) {
                                    ppiSamplesDataList.add(Polar247PPiSamplesData(sampleDateForCheck, fromPbPPiDataSamples(sample)))
                                } else {
                                    BleLogger.d(TAG, "Sample date $sampleDateForCheck is out of range: $fromDate to $toDate")
                                }
                            }
                        }
                    }

                    Single.merge(fileRequests)
                        .doOnComplete {
                            emitter.onSuccess(ppiSamplesDataList)
                        }
                        .doOnError { error ->
                            BleLogger.e(TAG, "Error processing files: $error")
                            emitter.onError(error)
                        }
                        .subscribe()
                },
                { error ->
                    BleLogger.e(TAG, "read247PPiSamples() failed for path: $autoSamplesPath, error: $error")
                    emitter.onError(error)
                }
            )
            emitter.setDisposable(disposable)
        }
    }
}