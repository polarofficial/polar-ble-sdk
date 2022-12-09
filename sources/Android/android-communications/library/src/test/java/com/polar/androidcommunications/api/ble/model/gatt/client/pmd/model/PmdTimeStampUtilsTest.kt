package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.polar.androidcommunications.api.ble.exceptions.NegativeTimeStampError
import com.polar.androidcommunications.api.ble.exceptions.TimeStampAndFrequencyZeroError
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Assert.assertThrows

import org.junit.Rule
import org.junit.Test
import kotlin.math.round

internal class PmdTimeStampUtilsTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `assert on invalid input, both sample rate and timestamp are zero`() {
        // Arrange
        val previousTimeStamp = 0uL
        val timeStamp = 100000uL //ns
        val samplesSize = 100
        val samplingRate = 0

        // Act &  Assert
        assertThrows(TimeStampAndFrequencyZeroError::class.java) {
            PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = previousTimeStamp,
                frameTimeStamp = timeStamp,
                samplesSize = samplesSize,
                sampleRate = samplingRate
            )
        }
    }

    @Test
    fun `assert on invalid input, time stamps are negative`() {
        // Arrange
        val previousTimeStamp = 0uL
        val timeStamp = 100000uL //ns
        val samplesSize = 100
        val samplingRate = 52

        // 1/52Hz = 0.019230769230769s = 19230769ns
        // timeStamp - 19230769ns => Exception

        // Act &  Assert
        assertThrows(NegativeTimeStampError::class.java) {
            PmdTimeStampUtils.getTimeStamps(
                previousFrameTimeStamp = previousTimeStamp,
                frameTimeStamp = timeStamp,
                samplesSize = samplesSize,
                sampleRate = samplingRate
            )
        }
    }

    @Test
    fun `samples time stamps based on frequency`() {
        // Arrange
        val previousTimeStamp = 0uL
        val timeStamp = 10000000000uL // 10 seconds
        val samplesSize = 10
        val samplingRate = 1 // 1Hz

        // Act
        val timestamps = PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp = previousTimeStamp,
            frameTimeStamp = timeStamp,
            samplesSize = samplesSize,
            sampleRate = samplingRate
        )
        // Assert
        Assert.assertEquals(samplesSize, timestamps.size)
        Assert.assertEquals(1000000000uL, timestamps[0])
        Assert.assertEquals(2000000000uL, timestamps[1])
        Assert.assertEquals(3000000000uL, timestamps[2])
        Assert.assertEquals(4000000000uL, timestamps[3])
        Assert.assertEquals(5000000000uL, timestamps[4])
        Assert.assertEquals(6000000000uL, timestamps[5])
        Assert.assertEquals(7000000000uL, timestamps[6])
        Assert.assertEquals(8000000000uL, timestamps[7])
        Assert.assertEquals(9000000000uL, timestamps[8])
        Assert.assertEquals(10000000000uL, timestamps[9])
    }

    @Test
    fun `samples time based on frequency only one sample`() {
        // Arrange
        val previousTimeStamp = 0uL
        val timeStamp = 1000000000uL // 1 second
        val samplesSize = 1
        val samplingRate = 1 // 1Hz

        // Act
        val timestamps = PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp = previousTimeStamp,
            frameTimeStamp = timeStamp,
            samplesSize = samplesSize,
            sampleRate = samplingRate
        )
        // Assert
        Assert.assertEquals(samplesSize, timestamps.size)
        Assert.assertEquals(1000000000uL, timestamps[0])
    }

    @Test
    fun `samples time stamps based on previous time stamp test 1`() {
        // Arrange
        val previousTimeStamp = 1000000000uL //1 seconds
        val timeStamp = 11000000000uL // 11 seconds
        val samplesSize = 10
        val samplingRate = 1 // 1Hz

        /*
        Index: prev   0    1    2    3    4    5    6    7    8    9
                 |____|____|____|____|____|____|____|____|____|____|
        Stamp:   1    2    3    4    5    6    7    8    9   10   11
        */
        // Act
        val timestamps = PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp = previousTimeStamp,
            frameTimeStamp = timeStamp,
            samplesSize = samplesSize,
            sampleRate = samplingRate
        )
        // Assert
        Assert.assertEquals(samplesSize, timestamps.size)
        Assert.assertEquals(2000000000uL, timestamps[0])
        Assert.assertEquals(3000000000uL, timestamps[1])
        Assert.assertEquals(4000000000uL, timestamps[2])
        Assert.assertEquals(5000000000uL, timestamps[3])
        Assert.assertEquals(6000000000uL, timestamps[4])
        Assert.assertEquals(7000000000uL, timestamps[5])
        Assert.assertEquals(8000000000uL, timestamps[6])
        Assert.assertEquals(9000000000uL, timestamps[7])
        Assert.assertEquals(10000000000uL, timestamps[8])
        Assert.assertEquals(11000000000uL, timestamps[9])
    }

    @Test
    fun `samples time stamps based on previous time stamp test 2`() {
        // Arrange
        val previousTimeStamp = 100uL // 1 seconds
        val timeStamp = 2000000000uL // 2 seconds
        val samplesSize = 3
        val samplingRate = 1 // 1Hz

        //val startTimeStamp = lastSampleTimeStamp - (timeStampDelta * (samplesSize - 1))

        // (2000000000 - 100) / 3 = 666666633.333333333333333
        /*
        Index: prev                 0               1               2
                 |__666666633.33ns__|__666666633ns__|__666666633ns__|
        Stamp:  100     666666733.33    1333333366.66      2000000000
        */
        // Act
        val timestamps = PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp = previousTimeStamp,
            frameTimeStamp = timeStamp,
            samplesSize = samplesSize,
            sampleRate = samplingRate
        )
        // Assert
        Assert.assertEquals(samplesSize, timestamps.size)
        Assert.assertEquals(round(666666733.33).toULong(), timestamps[0])
        Assert.assertEquals(round(1333333366.66).toULong(), timestamps[1])
        Assert.assertEquals(2000000000uL, timestamps[2])
    }

    @Test
    fun `samples time stamps based on previous time stamp only one sample`() {
        // Arrange
        val previousTimeStamp = 1000000000uL //1 seconds
        val timeStamp = 2000000000uL // 2 seconds
        val samplesSize = 1
        val samplingRate = 1 // 1Hz

        /*
        Index: prev   0
                 |____|
        Stamp:   1    2
        */
        // Act
        val timestamps = PmdTimeStampUtils.getTimeStamps(
            previousFrameTimeStamp = previousTimeStamp,
            frameTimeStamp = timeStamp,
            samplesSize = samplesSize,
            sampleRate = samplingRate
        )
        // Assert
        Assert.assertEquals(samplesSize, timestamps.size)
        Assert.assertEquals(timeStamp, timestamps[0])
    }
}