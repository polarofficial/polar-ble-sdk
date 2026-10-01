// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class PolarCompanionDevicePresenceBusTest {

    private val macAddress = "00:11:22:33:44:55"

    @Test
    fun `listener is notified on device appeared and disappeared`() {
        val events = mutableListOf<Pair<String, Boolean>>()
        val listener = PolarCompanionDevicePresenceBus.Listener { address, appeared ->
            events.add(address to appeared)
        }

        PolarCompanionDevicePresenceBus.addListener(macAddress, listener)
        PolarCompanionDevicePresenceBus.notifyDeviceAppeared(macAddress)
        PolarCompanionDevicePresenceBus.notifyDeviceDisappeared(macAddress)

        assertEquals(listOf(macAddress to true, macAddress to false), events)

        PolarCompanionDevicePresenceBus.removeAllListeners(macAddress)
    }

    @Test
    fun `mac address matching is case insensitive`() {
        val events = mutableListOf<Boolean>()
        val listener = PolarCompanionDevicePresenceBus.Listener { _, appeared -> events.add(appeared) }

        PolarCompanionDevicePresenceBus.addListener(macAddress.lowercase(), listener)
        PolarCompanionDevicePresenceBus.notifyDeviceAppeared(macAddress.uppercase())

        assertEquals(listOf(true), events)

        PolarCompanionDevicePresenceBus.removeAllListeners(macAddress)
    }

    @Test
    fun `removed listener no longer receives events`() {
        val events = mutableListOf<Boolean>()
        val listener = PolarCompanionDevicePresenceBus.Listener { _, appeared -> events.add(appeared) }

        PolarCompanionDevicePresenceBus.addListener(macAddress, listener)
        PolarCompanionDevicePresenceBus.removeListener(macAddress, listener)
        PolarCompanionDevicePresenceBus.notifyDeviceAppeared(macAddress)

        assertEquals(emptyList<Boolean>(), events)
    }
}

