// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import org.junit.Test
import org.junit.Assert.assertEquals

class PolarCompanionDeviceServiceBaseTest {

    private class TestService : PolarCompanionDeviceServiceBase()

    @Suppress("DEPRECATION")
    @Test
    fun `onDeviceAppeared forwards address to the presence bus`() {
        val macAddress = "00:11:22:33:44:55"
        val events = mutableListOf<Boolean>()
        val listener = PolarCompanionDevicePresenceBus.Listener { _, appeared -> events.add(appeared) }
        PolarCompanionDevicePresenceBus.addListener(macAddress, listener)

        TestService().onDeviceAppeared(macAddress)

        assertEquals(listOf(true), events)
        PolarCompanionDevicePresenceBus.removeAllListeners(macAddress)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `onDeviceDisappeared forwards address to the presence bus`() {
        val macAddress = "00:11:22:33:44:55"
        val events = mutableListOf<Boolean>()
        val listener = PolarCompanionDevicePresenceBus.Listener { _, appeared -> events.add(appeared) }
        PolarCompanionDevicePresenceBus.addListener(macAddress, listener)

        TestService().onDeviceDisappeared(macAddress)

        assertEquals(listOf(false), events)
        PolarCompanionDevicePresenceBus.removeAllListeners(macAddress)
    }
}

