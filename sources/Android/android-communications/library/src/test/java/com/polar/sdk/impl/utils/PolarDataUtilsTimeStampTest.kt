package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.AccData
import com.polar.sdk.api.errors.PolarBleSdkInternalException
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientAccDataToPolarAcc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests for the ULong -> Long raw PMD timestamp conversion performed in [PolarDataUtils].
 *
 * The raw PMD timestamp is an unsigned nanosecond counter (epoch 1.1.2000), while the public
 * Polar data model exposes it as a signed [Long]. `ULong.toLong()` is an unchecked bit-pattern
 * reinterpretation, so a value greater than [Long.MAX_VALUE] would silently wrap into a negative
 * [Long] instead of failing. The conversion must fail loudly in that case.
 */
class PolarDataUtilsTimeStampTest {

    @Test
    fun `valid raw timestamp is converted to the same numeric Long value`() {
        val timeStamp = 1_700_000_000_000_000_000uL
        val accData = AccData().apply {
            accSamples.add(AccData.AccSample(timeStamp = timeStamp, x = 1, y = 2, z = 3))
        }

        val polarAccData = mapPmdClientAccDataToPolarAcc(accData)

        assertEquals(timeStamp.toLong(), polarAccData.samples[0].timeStamp)
    }

    @Test
    fun `raw timestamp exceeding Long MAX_VALUE fails loudly instead of becoming negative`() {
        val overflowingTimeStamp = Long.MAX_VALUE.toULong() + 1u
        val accData = AccData().apply {
            accSamples.add(AccData.AccSample(timeStamp = overflowingTimeStamp, x = 1, y = 2, z = 3))
        }

        assertThrows(PolarBleSdkInternalException::class.java) {
            mapPmdClientAccDataToPolarAcc(accData)
        }
    }

    @Test
    fun `raw timestamp equal to Long MAX_VALUE is still convertible`() {
        val timeStamp = Long.MAX_VALUE.toULong()
        val accData = AccData().apply {
            accSamples.add(AccData.AccSample(timeStamp = timeStamp, x = 1, y = 2, z = 3))
        }

        val polarAccData = mapPmdClientAccDataToPolarAcc(accData)

        assertEquals(Long.MAX_VALUE, polarAccData.samples[0].timeStamp)
    }
}
