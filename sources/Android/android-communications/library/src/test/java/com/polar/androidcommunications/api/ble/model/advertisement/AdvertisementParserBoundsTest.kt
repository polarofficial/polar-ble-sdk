package com.polar.androidcommunications.api.ble.model.advertisement

import com.polar.androidcommunications.common.ble.BleUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AdvertisementParserBoundsTest {
    @Test
    fun containsService_ignoresIncompleteUuidButKeepsCompletePairs() {
        for (type in listOf(BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_MORE, BleUtils.AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE)) {
            // Arrange
            val content = BleAdvertisementContent()
            content.advertisementData[type] = byteArrayOf(0x0D)

            // Act
            val containsIncompleteService = content.containsService("180D")

            // Assert
            assertFalse(containsIncompleteService)

            // Arrange
            content.advertisementData[type] = byteArrayOf(0x0D, 0x18, 0x0F)

            // Act
            val containsCompleteService = content.containsService("180D")
            val containsTrailingService = content.containsService("180F")

            // Assert
            assertTrue(containsCompleteService)
            assertFalse(containsTrailingService)
        }
    }

    @Test
    fun malformedAdvertisements_doNotThrowDuringProcessing() {
        // Arrange
        val random = Random(25)
        repeat(2000) {
            // Arrange
            val bytes = random.nextBytes(random.nextInt(0, 300))
            val content = BleAdvertisementContent()

            // Act & Assert: processing and reading malformed data must not throw.
            content.processAdvertisementData(
                BleUtils.advertisementBytes2Map(bytes), BleUtils.EVENT_TYPE.ADV_IND, -60
            )
            content.containsService("180D")
            // Exercise the manufacturer parser even when random fields were truncated.
            content.processAdvManufacturerData(
                hashMapOf(BleUtils.AD_TYPE.GAP_ADTYPE_MANUFACTURER_SPECIFIC to
                    (byteArrayOf(0x6B, 0) + bytes)), content.polarHrAdvertisement
            )
            content.polarHrAdvertisement.fastAverageHr
            content.polarHrAdvertisement.slowAverageHr
            content.polarHrAdvertisement.khzCode
        }
    }
}
