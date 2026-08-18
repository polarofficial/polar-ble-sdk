package com.polar.sdk.impl

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.gatt.client.BleMdsClient
import com.polar.sdk.api.PolarDeviceTelemetryType
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class PolarTelemetryApiImplTest {

    private val deviceId = "A1B2C3"

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getAvailableTelemetryTypes waits for MDS client readiness`() = runTest {
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val client = mockk<BleMdsClient>()
        var clientReady = false

        mockkObject(PolarServiceClientUtils)
        every {
            PolarServiceClientUtils.sessionMdsClientReady(deviceId, listener)
        } returns session
        every { session.fetchClient(BleMdsClient.MDS_SERVICE) } returns client
        coEvery { client.clientReady(true) } answers {
            clientReady = true
        }
        every { client.supportedFeatures } answers {
            if (clientReady) 0 else null
        }

        val result = PolarTelemetryApiImpl(listener)
            .getAvailableTelemetryTypes(deviceId)

        assertEquals(
            listOf(PolarDeviceTelemetryType.memfault_mds),
            result.availableTelemetryTypes
        )
        coVerify(exactly = 1) { client.clientReady(true) }
    }
}