// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import android.companion.AssociationRequest
import android.content.Context
import android.content.IntentSender
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.common.ble.companion.PolarCompanionAssociationActivity
import com.polar.androidcommunications.common.ble.companion.PolarCompanionDeviceManager
import com.polar.sdk.api.PolarCompanionDeviceApi.PolarCompanionDeviceCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolarCompanionDeviceApiImplTest {

    private val macAddress = "00:11:22:33:44:55"
    private val context = mockk<Context>(relaxed = true).also {
        every { it.applicationContext } returns it
    }
    private val listener = mockk<BleDeviceListener>()

    private fun newApi() = PolarCompanionDeviceApiImpl(context) { listener }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `ensureCompanionHandling does nothing when CDM not available`() {
        mockkConstructor(PolarCompanionDeviceManager::class)
        every { anyConstructed<PolarCompanionDeviceManager>().isAvailable() } returns false

        newApi().ensureCompanionHandling(macAddress, mockk<PolarCompanionDeviceCallback>(relaxed = true))

        verify(exactly = 0) { anyConstructed<PolarCompanionDeviceManager>().isAssociated(any()) }
    }

    @Test
    fun `ensureCompanionHandling starts presence observation when already associated`() {
        mockkConstructor(PolarCompanionDeviceManager::class)
        every { anyConstructed<PolarCompanionDeviceManager>().isAvailable() } returns true
        every { anyConstructed<PolarCompanionDeviceManager>().isAssociated(macAddress) } returns true
        every { anyConstructed<PolarCompanionDeviceManager>().startObservingDevicePresence(macAddress) } returns true

        newApi().ensureCompanionHandling(macAddress, mockk<PolarCompanionDeviceCallback>(relaxed = true))

        verify(exactly = 1) { anyConstructed<PolarCompanionDeviceManager>().startObservingDevicePresence(macAddress) }
        verify(exactly = 0) { anyConstructed<PolarCompanionDeviceManager>().associate(any(), any(), any()) }
    }

    @Test
    fun `ensureCompanionHandling starts observation immediately on direct association`() {
        mockkConstructor(PolarCompanionDeviceManager::class)
        every { anyConstructed<PolarCompanionDeviceManager>().isAvailable() } returns true
        every { anyConstructed<PolarCompanionDeviceManager>().isAssociated(macAddress) } returns false
        every { anyConstructed<PolarCompanionDeviceManager>().buildAssociationRequest(any(), any(), any(), any()) } returns mockk<AssociationRequest>()
        every { anyConstructed<PolarCompanionDeviceManager>().startObservingDevicePresence(macAddress) } returns true
        every {
            anyConstructed<PolarCompanionDeviceManager>().associate(any(), any(), any())
        } answers {
            val callback = thirdArg<PolarCompanionDeviceManager.AssociationCallback>()
            callback.onAssociationCreated(macAddress)
        }

        val callback = mockk<PolarCompanionDeviceCallback>(relaxed = true)
        newApi().ensureCompanionHandling(macAddress, callback)

        verify(exactly = 1) { anyConstructed<PolarCompanionDeviceManager>().startObservingDevicePresence(macAddress) }
        verify(exactly = 1) { callback.onAssociationSucceeded(macAddress) }
    }

    @Test
    fun `ensureCompanionHandling launches trampoline activity when consent required`() {
        mockkConstructor(PolarCompanionDeviceManager::class)
        mockkObject(PolarCompanionAssociationActivity.Companion)
        every { anyConstructed<PolarCompanionDeviceManager>().isAvailable() } returns true
        every { anyConstructed<PolarCompanionDeviceManager>().isAssociated(macAddress) } returns false
        every { anyConstructed<PolarCompanionDeviceManager>().buildAssociationRequest(any(), any(), any(), any()) } returns mockk<AssociationRequest>()
        val intentSender = mockk<IntentSender>()
        every { PolarCompanionAssociationActivity.createIntent(any(), any()) } returns mockk(relaxed = true)
        every {
            anyConstructed<PolarCompanionDeviceManager>().associate(any(), any(), any())
        } answers {
            val callback = thirdArg<PolarCompanionDeviceManager.AssociationCallback>()
            callback.onAssociationPending(intentSender)
        }

        newApi().ensureCompanionHandling(macAddress, mockk<PolarCompanionDeviceCallback>(relaxed = true))

        verify(exactly = 1) { context.startActivity(any()) }
    }

    @Test
    fun `ensureCompanionHandling targets association request at the connected device address`() {
        mockkConstructor(PolarCompanionDeviceManager::class)
        every { anyConstructed<PolarCompanionDeviceManager>().isAvailable() } returns true
        every { anyConstructed<PolarCompanionDeviceManager>().isAssociated(macAddress) } returns false
        every { anyConstructed<PolarCompanionDeviceManager>().buildAssociationRequest(any(), any(), any(), any()) } returns mockk<AssociationRequest>()
        every { anyConstructed<PolarCompanionDeviceManager>().associate(any(), any(), any()) } answers { }

        newApi().ensureCompanionHandling(macAddress, mockk<PolarCompanionDeviceCallback>(relaxed = true))

        verify(exactly = 1) {
            anyConstructed<PolarCompanionDeviceManager>().buildAssociationRequest(any(), any(), any(), macAddress)
        }
    }

    @Test
    fun `ensureCompanionHandling ignores association created for a different device`() {
        val otherMac = "66:77:88:99:AA:BB"
        mockkConstructor(PolarCompanionDeviceManager::class)
        every { anyConstructed<PolarCompanionDeviceManager>().isAvailable() } returns true
        every { anyConstructed<PolarCompanionDeviceManager>().isAssociated(macAddress) } returns false
        every { anyConstructed<PolarCompanionDeviceManager>().buildAssociationRequest(any(), any(), any(), any()) } returns mockk<AssociationRequest>()
        every {
            anyConstructed<PolarCompanionDeviceManager>().associate(any(), any(), any())
        } answers {
            thirdArg<PolarCompanionDeviceManager.AssociationCallback>().onAssociationCreated(otherMac)
        }

        val callback = mockk<PolarCompanionDeviceCallback>(relaxed = true)
        newApi().ensureCompanionHandling(macAddress, callback)

        verify(exactly = 0) { anyConstructed<PolarCompanionDeviceManager>().startObservingDevicePresence(any()) }
        verify(exactly = 0) { callback.onAssociationSucceeded(any()) }
    }

    @Test
    fun `resumeAllAssociatedDevicesPresenceObservation starts observation for every associated device`() {
        mockkConstructor(PolarCompanionDeviceManager::class)
        val secondMac = "AA:BB:CC:DD:EE:FF"
        every { anyConstructed<PolarCompanionDeviceManager>().isAvailable() } returns true
        every { anyConstructed<PolarCompanionDeviceManager>().associatedDeviceAddresses() } returns listOf(macAddress, secondMac)
        every { anyConstructed<PolarCompanionDeviceManager>().startObservingDevicePresence(any()) } returns true

        newApi().resumeAllAssociatedDevicesPresenceObservation(mockk<PolarCompanionDeviceCallback>(relaxed = true))

        verify(exactly = 1) { anyConstructed<PolarCompanionDeviceManager>().startObservingDevicePresence(macAddress) }
        verify(exactly = 1) { anyConstructed<PolarCompanionDeviceManager>().startObservingDevicePresence(secondMac) }
    }

    @Test
    fun `resumeAllAssociatedDevicesPresenceObservation does nothing when CDM not available`() {
        mockkConstructor(PolarCompanionDeviceManager::class)
        every { anyConstructed<PolarCompanionDeviceManager>().isAvailable() } returns false

        newApi().resumeAllAssociatedDevicesPresenceObservation(mockk<PolarCompanionDeviceCallback>(relaxed = true))

        verify(exactly = 0) { anyConstructed<PolarCompanionDeviceManager>().associatedDeviceAddresses() }
    }

    @Test
    fun `isAssociated delegates to manager for a valid mac address`() {
        mockkConstructor(PolarCompanionDeviceManager::class)
        every { anyConstructed<PolarCompanionDeviceManager>().isAssociated(macAddress) } returns true

        assertTrue(newApi().isAssociated(macAddress))
    }

    @Test
    fun `isCompanionDeviceManagerAvailable delegates to manager`() {
        mockkConstructor(PolarCompanionDeviceManager::class)
        every { anyConstructed<PolarCompanionDeviceManager>().isAvailable() } returns false

        assertFalse(newApi().isCompanionDeviceManagerAvailable())
    }
}







