// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class PolarCompanionAssociationResultBusTest {

    @Test
    fun `all registered listeners are notified`() {
        var firstCalled = 0
        var secondCalled = 0
        val first: () -> Unit = { firstCalled += 1 }
        val second: () -> Unit = { secondCalled += 1 }

        PolarCompanionAssociationResultBus.addListener(first)
        PolarCompanionAssociationResultBus.addListener(second)
        PolarCompanionAssociationResultBus.notifyAssociationFlowFinished()

        assertEquals(1, firstCalled)
        assertEquals(1, secondCalled)

        PolarCompanionAssociationResultBus.removeListener(first)
        PolarCompanionAssociationResultBus.removeListener(second)
    }

    @Test
    fun `removed listener is not notified`() {
        var called = 0
        val listener: () -> Unit = { called += 1 }

        PolarCompanionAssociationResultBus.addListener(listener)
        PolarCompanionAssociationResultBus.removeListener(listener)
        PolarCompanionAssociationResultBus.notifyAssociationFlowFinished()

        assertEquals(0, called)
    }

    @Test
    fun `notify with no listeners does not throw`() {
        PolarCompanionAssociationResultBus.notifyAssociationFlowFinished()
    }
}


