package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.*

internal class BDDeviceListTest {

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @MockK
    private lateinit var bluetoothGatt: BluetoothGatt

    @MockK
    private lateinit var bluetoothDevice: BluetoothDevice

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @After
    fun afterTests() {
        unmockkAll()
    }

    @Test
    fun `get session returns null if no matching gatt object`() {
        // Arrange
        val deviceList = BDDeviceList()

        // Act
        val session = deviceList.getSession(bluetoothGatt)

        // Assert
        Assert.assertNull(session)
    }

    @Test
    fun `get session returns null if no matching session for address string`() {
        // Arrange
        val deviceList = BDDeviceList()

        // Act
        val session = deviceList.getSession("no match string")

        // Assert
        Assert.assertNull(session)
    }

    @Test
    fun `get session returns null if no BluetoothDevice object`() {
        // Arrange
        val deviceList = BDDeviceList()

        // Act
        val session = deviceList.getSession(bluetoothDevice)

        // Assert
        Assert.assertNull(session)
    }

    @Test
    fun `get session returns null because session do not contain any gatt object`() {
        // Arrange
        val mockSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        every { mockSession.gattMutex } answers { Object() }
        every { mockSession.gatt } answers { null }

        val deviceList = BDDeviceList()
        deviceList.addSession(mockSession)

        // Act
        val session = deviceList.getSession(bluetoothGatt)

        // Assert
        Assert.assertNull(session)
    }

    @Test
    fun `get session returns session for matching gatt object`() {
        // Arrange
        val mockSession = mockk<BDDeviceSessionImpl>(relaxed = true)
        every { mockSession.gattMutex } answers { Object() }
        every { mockSession.gatt } answers { bluetoothGatt }

        val deviceList = BDDeviceList()
        deviceList.addSession(mockSession)

        // Act
        val session = deviceList.getSession(bluetoothGatt)

        // Assert
        Assert.assertEquals(mockSession, session)
    }

}