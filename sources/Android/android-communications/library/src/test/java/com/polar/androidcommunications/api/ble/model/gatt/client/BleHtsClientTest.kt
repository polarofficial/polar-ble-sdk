package com.polar.androidcommunications.api.ble.model.gatt.client

import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.HealthThermometer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.math.abs

class BleHtsClientTest {

    @MockK
    private lateinit var txInterface: BleGattTxInterface

    private lateinit var bleHtsClient: BleHtsClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        bleHtsClient = BleHtsClient(txInterface)
        every { txInterface.isConnected } returns true
        every { txInterface.setCharacteristicNotify(any(), any(), any()) } returns Unit
    }

    @Test
    fun test_TemperatureMeasurement() {
        //Arrange
        val characteristic: UUID = HealthThermometer.TEMPERATURE_MEASUREMENT
        val status = 0
        val notifying = true

        val expectedCelsius1 = 27.20
        val measurementFrame1 = byteArrayOf(0x00.toByte(), 0xa0.toByte(), 0x0a.toByte(), 0x00.toByte(),0xfe.toByte())

        val expectedCelsius2 = 27.21
        val measurementFrame2 = byteArrayOf(0x00.toByte(), 0xa1.toByte(), 0x0a.toByte(), 0x00.toByte(),0xfe.toByte())

        //Act
        val result = bleHtsClient.observeHtsNotifications(true)
        val testObserver = TestSubscriber<BleHtsClient.TemperatureMeasurement>()
        result.subscribe(testObserver)
        bleHtsClient.processServiceData(characteristic, measurementFrame1, status, notifying)
        bleHtsClient.processServiceData(characteristic, measurementFrame2, status, notifying)

        //Assert
        testObserver.assertNoErrors()
        testObserver.assertValueCount(2)
        val tempCelsius1 = testObserver.values()[0]

        assert(abs(expectedCelsius1 - tempCelsius1.temperatureCelsius) <0.001)
        val tempCelsius2 = testObserver.values()[1]
        assert(abs(expectedCelsius2 - tempCelsius2.temperatureCelsius) <0.001)
    }


}