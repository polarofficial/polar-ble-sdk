// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolarCompanionDeviceManagerTest {

    @Test
    fun `valid mac addresses are accepted`() {
        assertTrue(PolarCompanionDeviceManager.isValidMacAddress("00:11:22:33:44:55"))
        assertTrue(PolarCompanionDeviceManager.isValidMacAddress("AA:BB:CC:DD:EE:FF"))
        assertTrue(PolarCompanionDeviceManager.isValidMacAddress("a1:b2:c3:d4:e5:f6"))
    }

    @Test
    fun `invalid mac addresses are rejected`() {
        assertFalse(PolarCompanionDeviceManager.isValidMacAddress(""))
        assertFalse(PolarCompanionDeviceManager.isValidMacAddress("not-a-mac"))
        assertFalse(PolarCompanionDeviceManager.isValidMacAddress("00:11:22:33:44"))
        assertFalse(PolarCompanionDeviceManager.isValidMacAddress("00:11:22:33:44:5G"))
        assertFalse(PolarCompanionDeviceManager.isValidMacAddress("12345678")) // Polar device id, not a mac
    }
}

