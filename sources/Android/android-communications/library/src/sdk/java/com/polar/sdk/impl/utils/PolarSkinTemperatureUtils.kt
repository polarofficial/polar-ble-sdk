package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.PolarSkinTemperatureResult
import com.polar.sdk.api.model.SkinTemperatureMeasurementType
import com.polar.sdk.api.model.SkinTemperatureSensorLocation
import com.polar.sdk.api.model.fromPbTemperatureMeasurementSamples
import com.polar.services.datamodels.protobuf.TemperatureMeasurement.TemperatureMeasurementPeriod
import io.reactivex.rxjava3.core.Maybe
import protocol.PftpRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val SKIN_TEMPERATURE_DIRECTORY = "SKINTEMP/"
private const val SKIN_TEMPERATURE_PROTO = "TEMPCONT.BPB"
private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
private const val TAG = "PolarSkinTemperatureUtils"

internal object PolarSkinTemperatureUtils {

    /**
     * Read skin temperature data for a given date.
     */
    fun readSkinTemperatureDataFromDayDirectory(client: BlePsFtpClient, date: LocalDate): Maybe<PolarSkinTemperatureResult> {
        BleLogger.d(TAG, "readSkinTemperatureDataFromDayDirectory: $date")
        return Maybe.create { emitter ->
            val skinTempFilePath =
                "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$SKIN_TEMPERATURE_DIRECTORY$SKIN_TEMPERATURE_PROTO"
            val disposable = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(skinTempFilePath)
                    .build()
                    .toByteArray()
            ).subscribe(
                { response ->
                    val proto = TemperatureMeasurementPeriod.parseFrom(response.toByteArray())
                    emitter.onSuccess(
                        PolarSkinTemperatureResult(
                            proto.sourceDeviceId,
                            SkinTemperatureSensorLocation.from(proto.sensorLocation.ordinal),
                            SkinTemperatureMeasurementType.from(proto.measurementType.ordinal),
                            fromPbTemperatureMeasurementSamples(proto.temperatureMeasurementSamplesList)
                        )
                    )
                },
                { error ->
                    BleLogger.w(TAG, "Failed to fetch skin temperature data for date: $date, error: $error")
                    emitter.onComplete()
                }
            )
            emitter.setDisposable(disposable)
        }
    }
}
