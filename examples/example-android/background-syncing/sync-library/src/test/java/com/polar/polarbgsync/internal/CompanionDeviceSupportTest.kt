// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionDeviceSupportTest {
    @Test
    fun resolvesPersistedMacToPolarIdAfterProcessRestart() {
        val registry = CompanionDeviceRegistry(mapOf("B5F8B5C3" to "AA:BB:CC:DD:EE:FF"))

        assertEquals("B5F8B5C3", registry.resolve("aa:bb:cc:dd:ee:ff"))
        assertEquals("B5F8B5C3", registry.resolve("b5f8b5c3"))
        assertEquals("AA:BB:CC:DD:EE:FF", registry.address("B5F8B5C3"))
    }

    @Test
    fun unknownAddressCannotResolveToAnotherSavedDevice() {
        val registry = CompanionDeviceRegistry(mapOf("B5F8B5C3" to "AA:BB:CC:DD:EE:FF"))

        assertEquals("11:22:33:44:55:66", registry.resolve("11:22:33:44:55:66"))
    }

    @Test
    fun remembersNewAddressAndForgettingRemovesBothLookups() {
        val registry = CompanionDeviceRegistry(emptyMap())
        registry.remember("B5F8B5C3", "aa:bb:cc:dd:ee:ff")

        assertEquals("B5F8B5C3", registry.resolve("AA:BB:CC:DD:EE:FF"))
        registry.forget("AA:BB:CC:DD:EE:FF")

        assertEquals("AA:BB:CC:DD:EE:FF", registry.resolve("AA:BB:CC:DD:EE:FF"))
        assertEquals("B5F8B5C3", registry.address("B5F8B5C3"))
    }

    @Test
    fun sessionsUsingMacAddressesRemainSupported() {
        val registry = CompanionDeviceRegistry(emptyMap())
        registry.remember("AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF")

        assertEquals("AA:BB:CC:DD:EE:FF", registry.resolve("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun associationTimeoutAllowsSdkConsentDelayAndNormalGattSetup() {
        val timeout = connectionSetupTimeoutMillis(true)

        assertEquals(90_000L, timeout)
        assertTrue(timeout >= 60_000L + connectionSetupTimeoutMillis(false))
        assertEquals(30_000L, connectionSetupTimeoutMillis(false))
    }
}
