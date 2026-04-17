package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.PolarSkinTemperatureResult
import com.polar.sdk.api.model.SkinTemperatureMeasurementType
import com.polar.sdk.api.model.SkinTemperatureSensorLocation
import com.polar.sdk.api.model.fromPbTemperatureMeasurementSamples
import com.polar.services.datamodels.protobuf.TemperatureMeasurement.TemperatureMeasurementPeriod
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
    suspend fun readSkinTemperatureDataFromDayDirectory(client: BlePsFtpClient, date: LocalDate): PolarSkinTemperatureResult? {
        BleLogger.d(TAG, "readSkinTemperatureDataFromDayDirectory: $date")
        val skinTempFilePath = "$ARABICA_USER_ROOT_FOLDER${date.format(dateFormatter)}/$SKIN_TEMPERATURE_DIRECTORY$SKIN_TEMPERATURE_PROTO"
        return try {
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(skinTempFilePath)
                    .build()
                    .toByteArray()
            )
            val proto = TemperatureMeasurementPeriod.parseFrom(response.toByteArray())
            PolarSkinTemperatureResult(
                proto.sourceDeviceId,
                SkinTemperatureSensorLocation.from(proto.sensorLocation.ordinal),
                SkinTemperatureMeasurementType.from(proto.measurementType.ordinal),
                fromPbTemperatureMeasurementSamples(proto.temperatureMeasurementSamplesList)
            )
        } catch (error: Throwable) {
            BleLogger.w(TAG, "Failed to fetch skin temperature data for date: $date, error: $error")
            null
        }
    }
}
