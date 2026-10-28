// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import com.polar.polarbgsync.api.SyncableDataType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFetchPlanTest {
    private val hr = SyncableDataType.HR_SAMPLES_247
    private val ppi = SyncableDataType.PPI_SAMPLES_247
    private val steps = SyncableDataType.STEPS

    @Test
    fun failuresMoveBehindHealthyTypesAndReconnectDoesNotRestartCompletedWork() {
        val plan = SyncFetchPlan(maxRetries = 1)
        val types = listOf(hr, ppi, steps)
        assertTrue(plan.beginCycle(types))
        assertTrue(plan.recordFailure(hr))
        assertEquals(ppi, plan.next)
        assertTrue(plan.recordFailure(ppi))
        assertEquals(steps, plan.next)
        assertFalse(plan.nextIsRetry)
        plan.complete(steps)

        assertFalse(plan.beginCycle(types))
        assertEquals(hr, plan.next)
        assertTrue(plan.nextIsRetry)
        assertFalse(plan.recordFailure(hr))
        assertEquals(ppi, plan.next)
        plan.complete(ppi)
        assertNull(plan.next)
        assertEquals(setOf(hr), plan.exhaustedTypes)
    }

    @Test
    fun retryBudgetsAreIndependentAndSurviveReconnects() {
        val plan = SyncFetchPlan(maxRetries = 3)
        val types = listOf(hr, ppi)
        plan.beginCycle(types)
        repeat(3) {
            assertEquals(hr, plan.next)
            assertTrue(plan.recordFailure(hr))
            assertFalse(plan.beginCycle(types))
            assertEquals(ppi, plan.next)
            assertTrue(plan.recordFailure(ppi))
        }
        assertFalse(plan.recordFailure(hr))
        assertEquals(ppi, plan.next)
        assertFalse(plan.recordFailure(ppi))
        assertNull(plan.next)
        assertEquals(setOf(hr, ppi), plan.exhaustedTypes)
    }

    @Test
    fun allFailedTypesWaitForNewTriggerWhichRestoresRetryBudgets() {
        val plan = SyncFetchPlan(maxRetries = 1)
        val types = listOf(hr, steps)
        plan.beginCycle(types)
        val attempts = mutableListOf<SyncableDataType>()
        repeat(10) {
            plan.next?.let { type ->
                attempts.add(type)
                plan.recordFailure(type)
            }
        }
        assertEquals(listOf(hr, steps, hr, steps), attempts)
        assertNull(plan.next)
        plan.finishCycle()
        assertNull(plan.next)
        assertTrue(plan.beginCycle(types))
        assertTrue(plan.exhaustedTypes.isEmpty())
        assertFalse(plan.nextIsRetry)
        assertTrue(plan.recordFailure(hr))
        assertEquals(steps, plan.next)
    }
}
