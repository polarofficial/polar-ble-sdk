package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.PolarSkinTemperatureDataSample
import com.polar.sdk.api.model.PolarSkinTemperatureResult
import com.polar.sdk.api.model.SkinTemperatureMeasurementType
import com.polar.sdk.api.model.SkinTemperatureSensorLocation
import com.polar.sdk.impl.utils.PolarSkinTemperatureUtils
import com.polar.services.datamodels.protobuf.TemperatureMeasurement
import com.polar.services.datamodels.protobuf.TemperatureMeasurement.TemperatureMeasurementSample
import com.polar.services.datamodels.protobuf.Types
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class PolarSkinTemperatureUtilsTest {

    @Test
    fun `readSkinTemperatureData() should return skin temperature data`() {
        // Arrange
        val mockClient = mockk<BlePsFtpClient>()
        var formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        formatter = formatter.withLocale( Locale.ENGLISH )  // Locale specifies human language for translating, and cultural norms for lowercase/uppercase and abbreviations and such. Example: Locale.US or Locale.CANADA_FRENCH
        val date: LocalDate = LocalDate.parse("20250101", formatter)

        val outputStream = ByteArrayOutputStream().apply {
            val proto = TemperatureMeasurement.TemperatureMeasurementPeriod.newBuilder()
                .setMeasurementType(Types.TemperatureMeasurementType.TM_SKIN_TEMPERATURE)
                .setSensorLocation(Types.SensorLocation.SL_DISTAL)
                .addTemperatureMeasurementSamples(
                    TemperatureMeasurementSample.newBuilder()
                        .setTemperatureCelsius(37.0f)
                        .setRecordingTimeDeltaMilliseconds(0L)
                        .build()
                ).addTemperatureMeasurementSamples(
                    TemperatureMeasurementSample.newBuilder()
                        .setTemperatureCelsius(37.6f)
                        .setRecordingTimeDeltaMilliseconds(1000L)
                        .build()
                )
                .build()
            proto.writeTo(this)
        }

        var expectedSkinTemperatureSamples: MutableList<PolarSkinTemperatureDataSample> = mutableListOf()
        expectedSkinTemperatureSamples.add(0, PolarSkinTemperatureDataSample(0, 37.0f))
        expectedSkinTemperatureSamples.add(1, PolarSkinTemperatureDataSample(1000, 37.6f))

        val expectedResult = PolarSkinTemperatureResult(
            "",
            SkinTemperatureSensorLocation.SL_DISTAL,
            SkinTemperatureMeasurementType.TM_SKIN_TEMPERATURE,
            expectedSkinTemperatureSamples
        )

        every { mockClient.request(any()) } returns Single.just(outputStream)

        // Act
        val testObserver = PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(mockClient, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(expectedResult)
    }

    @Test
    fun `readSkinTemperatureDataFromDayDirectory() returns null when an error is thrown`() {
        // Arrange
        val mockClient = mockk<BlePsFtpClient>()
        var formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        formatter =
            formatter.withLocale(Locale.ENGLISH)  // Locale specifies human language for translating, and cultural norms for lowercase/uppercase and abbreviations and such. Example: Locale.US or Locale.CANADA_FRENCH
        val date: LocalDate = LocalDate.parse("20250101", formatter)
        val expectedError = Throwable("No skin temperature data found")

        every { mockClient.request(any()) } returns Single.error(expectedError)

        // Act
        val testObserver =
            PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(mockClient, date).test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertNoValues()
    }
}