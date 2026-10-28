// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionAssociationAttemptTest {
    @Test
    fun completionIsAcceptedOnlyOnce() {
        val attempt = CompanionAssociationAttempt {}
        assertTrue(attempt.tryFinish())
        assertFalse(attempt.tryFinish())
        assertFalse(attempt.tryFinish(cancelled = true))
        assertFalse(attempt.cancelled)
    }

    @Test
    fun cancellationSurvivesLateSuccessOrFailure() {
        val attempt = CompanionAssociationAttempt {}
        assertTrue(attempt.tryFinish(cancelled = true))
        assertFalse(attempt.tryFinish())
        assertTrue(attempt.cancelled)
    }
}
