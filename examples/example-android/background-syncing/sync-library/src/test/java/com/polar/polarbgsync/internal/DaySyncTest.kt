// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import com.polar.polarbgsync.api.SyncResult
import com.polar.polarbgsync.api.SyncableDataType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class DaySyncTest {
    private val start = LocalDate.of(2026, 1, 1)

    private fun result(day: LocalDate) = SyncResult("dev", SyncableDataType.SLEEP, day, "p", 1)

    @Test
    fun emptyDaysAreCompletedAndCheckpointed() = runBlocking {
        val completed = mutableListOf<LocalDate>()
        val all = syncDays(start, start.plusDays(2), { emptyList() }, { d, _ -> completed.add(d) })
        assertTrue(all.isEmpty())
        assertEquals(listOf(start, start.plusDays(1), start.plusDays(2)), completed)
    }

    @Test
    fun failureStopsAndOnlyEarlierDaysAreCheckpointed() = runBlocking {
        val completed = mutableListOf<LocalDate>()
        try {
            syncDays(start, start.plusDays(3), { d ->
                if (d == start.plusDays(1)) throw IllegalStateException("boom")
                listOf(result(d))
            }, { d, _ -> completed.add(d) })
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        assertEquals(listOf(start), completed)
    }

    @Test
    fun cancellationDuringSwallowingCallDoesNotCheckpointThatDay() = runBlocking {
        val completed = mutableListOf<LocalDate>()
        val job = async {
            syncDays(start, start.plusDays(2), { d ->
                if (d == start.plusDays(1)) {
                    // Simulates an SDK call that swallows cancellation and returns empty.
                    coroutineContext[kotlinx.coroutines.Job]!!.cancel()
                    return@syncDays emptyList()
                }
                listOf(result(d))
            }, { d, _ -> completed.add(d) })
        }
        try {
            job.await()
            fail("expected cancellation")
        } catch (e: CancellationException) {
            // expected
        }
        assertEquals(listOf(start), completed)
    }

    private val type = SyncableDataType.SLEEP

    private suspend fun range(
        cursors: DayCursors,
        to: LocalDate,
        persisted: MutableList<LocalDate>,
        reported: MutableList<SyncResult> = mutableListOf(),
        fetchDay: suspend (LocalDate) -> List<SyncResult>
    ) = syncDateRange(cursors, "dev", type, start, to, fetchDay, { reported.addAll(it) }, { persisted.add(it) })

    @Test
    fun failureAfterEmptyAndNonEmptyDaysPersistsNothingAndResumesAfterLastCompletedDay() = runBlocking {
        val cursors = DayCursors()
        val persisted = mutableListOf<LocalDate>()
        val fetched = mutableListOf<LocalDate>()
        val reported = mutableListOf<SyncResult>()
        try {
            range(cursors, start.plusDays(3), persisted, reported) { d ->
                fetched.add(d)
                when (d) {
                    start -> emptyList()
                    start.plusDays(1) -> listOf(result(d))
                    else -> throw IllegalStateException("device error")
                }
            }
            fail("expected exception")
        } catch (e: IllegalStateException) {
        }
        assertTrue(persisted.isEmpty())
        assertEquals(1, reported.size)
        assertEquals(DayCursor(start.plusDays(2), true), cursors.get("dev", type))

        fetched.clear()
        val all = range(cursors, start.plusDays(3), persisted, reported) { d ->
            fetched.add(d)
            emptyList()
        }
        assertEquals(listOf(start.plusDays(2), start.plusDays(3)), fetched)
        assertTrue(all.isEmpty())
        // Data saved before the failure still counts at full completion.
        assertEquals(listOf(start.plusDays(3)), persisted)
        assertEquals(null, cursors.get("dev", type))
    }

    @Test
    fun fullyEmptyRangeSucceedsWithoutPersistingAndClearsCursor() = runBlocking {
        val cursors = DayCursors()
        val persisted = mutableListOf<LocalDate>()
        range(cursors, start.plusDays(1), persisted) { emptyList() }
        assertTrue(persisted.isEmpty())
        assertEquals(null, cursors.get("dev", type))
    }

    @Test
    fun localSaveFailureDoesNotPersistOrAdvanceCursorPastFailedDay() = runBlocking {
        val cursors = DayCursors()
        val persisted = mutableListOf<LocalDate>()
        try {
            range(cursors, start.plusDays(2), persisted) { d ->
                if (d == start.plusDays(1)) throw java.io.IOException("disk full")
                listOf(result(d))
            }
            fail("expected exception")
        } catch (e: java.io.IOException) {
        }
        assertTrue(persisted.isEmpty())
        assertEquals(start.plusDays(1), cursors.get("dev", type)!!.nextDay)
    }

    @Test
    fun sdkRangeScopedTypesUseOneCallForWholeRangeAndPersistOnlyOnSuccessWithData() = runBlocking {
        assertTrue(isSdkRangeScoped(SyncableDataType.HR_SAMPLES_247))
        assertTrue(isSdkRangeScoped(SyncableDataType.PPI_SAMPLES_247))
        assertTrue(!isSdkRangeScoped(SyncableDataType.SLEEP))

        val calls = mutableListOf<Pair<LocalDate, LocalDate>>()
        val persisted = mutableListOf<LocalDate>()
        val end = start.plusDays(30)
        syncWholeRange(start, end, { f, t -> calls.add(f to t); listOf(result(f)) }, {}, { persisted.add(it) })
        assertEquals(listOf(start to end), calls)
        assertEquals(listOf(end), persisted)

        persisted.clear()
        syncWholeRange(start, end, { _, _ -> emptyList() }, {}, { persisted.add(it) })
        assertTrue(persisted.isEmpty())

        try {
            syncWholeRange(start, end, { _, _ -> throw java.io.IOException("x") }, {}, { persisted.add(it) })
            fail("expected exception")
        } catch (e: java.io.IOException) {
        }
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun sdkLogFilterAllowsOnlyMetadataLines() {
        assertTrue(shouldLogSdkMessage("BDBleApiImpl/Companion association skipped"))
        assertTrue(shouldLogSdkMessage("PolarAutomaticSamplesUtils/read247HrSamples: from 2026-09-16 to 2026-10-16"))
        assertTrue(shouldLogSdkMessage("PolarAutomaticSamplesUtils/read247PPiSamples: from 2026-09-16 to 2026-10-16"))
        assertTrue(shouldLogSdkMessage("PolarAutomaticSamplesUtils/Sending GET request for file: /U/0/AUTOS/AUTOS001.BPB"))
        assertTrue(shouldLogSdkMessage("Error: PolarAutomaticSamplesUtils/Failed to parse 247 HR samples file x"))
        assertTrue(shouldLogSdkMessage("BDBleApiImpl/No active sessions remaining, cancelling devicesStateMonitorJob"))
        assertTrue(shouldLogSdkMessage("BDBleApiImpl/Ignoring replayed SESSION_CLOSED of previous session"))
        assertTrue(!shouldLogSdkMessage("BlePsFtpClient/RFC76 message has read successfully"))
        assertTrue(!shouldLogSdkMessage("PolarAutomaticSamplesUtils/Sample date 2026-01-01 is out of range"))
        assertTrue(!shouldLogSdkMessage("[API] getSteps identifier=x"))
    }
}
