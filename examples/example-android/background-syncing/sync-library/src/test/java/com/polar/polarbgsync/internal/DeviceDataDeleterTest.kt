// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import android.content.SharedPreferences
import android.util.Log
import com.polar.polarbgsync.api.SyncableDataType
import com.polar.sdk.api.PolarBleApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.*
import java.time.LocalDate

class DeviceDataDeleterTest {
    @Test
    fun cancellationDuringStoredDataDeletionStopsRemainingTypes() = runBlocking {
        mockStatic(Log::class.java).use {
            val api = mock(PolarBleApi::class.java)
            val store = mock(LocalDataStore::class.java)
            val cutoff = LocalDate.now().minusDays(2)
            `when`(store.getSyncedDates("dev", SyncableDataType.STEPS))
                .thenReturn(setOf(cutoff.minusDays(1)))
            `when`(api.deleteStoredDeviceData("dev", PolarBleApi.PolarStoredDataType.ACTIVITY, cutoff))
                .thenThrow(CancellationException("stopped"))
            val deleter = DeviceDataDeleter(api, store, mock(SharedPreferences::class.java))

            try {
                deleter.deleteOldSyncedData("dev", linkedSetOf(SyncableDataType.STEPS, SyncableDataType.SLEEP), 2)
                fail("Expected cancellation")
            } catch (e: CancellationException) {
                assertEquals("stopped", e.message)
            }
            verify(store, never()).getSyncedDates("dev", SyncableDataType.SLEEP)
            Unit
        }
    }

    @Test
    fun cancellationWhileListingTrainingSessionsIsPropagated() = runBlocking {
        mockStatic(Log::class.java).use {
            val api = mock(PolarBleApi::class.java)
            `when`(api.getTrainingSessionReferences("dev", null, LocalDate.now().minusDays(2)))
                .thenReturn(flow { throw CancellationException("listing stopped") })
            val deleter = DeviceDataDeleter(
                api, mock(LocalDataStore::class.java), mock(SharedPreferences::class.java)
            )

            try {
                deleter.deleteOldSyncedData("dev", setOf(SyncableDataType.TRAINING_SESSIONS), 2)
                fail("Expected cancellation")
            } catch (e: CancellationException) {
                assertEquals("listing stopped", e.message)
            }
        }
    }
}
