package com.polar.polarsensordatacollector.ui.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataHoldersTest {

    @Before
    fun setUp() {
        HrDataHolder.clear()
        EcgDataHolder.clear()
        AccDataHolder.clear()
    }

    @Test
    fun `hr update hr appends sample and sets current`() {
        // Act
        HrDataHolder.updateHr(60)
        HrDataHolder.updateHr(72)

        // Assert
        val state = HrDataHolder.hrState.value
        assertEquals(listOf(HrDataHolder.HrSample(60), HrDataHolder.HrSample(72)), state.hrSamples)
        assertEquals(72, state.currentHr)
    }

    @Test
    fun `hr clear resets state`() {
        // Arrange
        HrDataHolder.updateHr(60)

        // Act
        HrDataHolder.clear()

        // Assert
        assertEquals(HrDataHolder.HrState(), HrDataHolder.hrState.value)
    }

    @Test
    fun `ecg update ecg appends sample and sets current`() {
        // Act
        EcgDataHolder.updateEcg(10)
        EcgDataHolder.updateEcg(-20)

        // Assert
        val state = EcgDataHolder.ecgState.value
        assertEquals(listOf(10, -20), state.ecgSamples.map { it.voltage })
        assertEquals(-20, state.currentVoltage)
    }

    @Test
    fun `ecg update ecg drops oldest sample when buffer is full`() {
        // Act
        repeat(ECG_BUFFER_SIZE + 2) { EcgDataHolder.updateEcg(it) }

        // Assert
        val samples = EcgDataHolder.ecgState.value.ecgSamples
        assertEquals(ECG_BUFFER_SIZE, samples.size)
        assertEquals(2, samples.first().voltage)
        assertEquals(ECG_BUFFER_SIZE + 1, samples.last().voltage)
    }

    @Test
    fun `ecg clear resets state`() {
        // Arrange
        EcgDataHolder.updateEcg(5)

        // Act
        EcgDataHolder.clear()

        // Assert
        assertEquals(EcgDataHolder.EcgState(), EcgDataHolder.ecgState.value)
    }

    @Test
    fun `acc update acc appends sample and sets current`() {
        // Act
        AccDataHolder.updateAcc(1, 2, 3)
        AccDataHolder.updateAcc(4, 5, 6)

        // Assert
        val state = AccDataHolder.accState.value
        assertEquals(2, state.accSamples.size)
        assertEquals(AccDataHolder.AccSample(4, 5, 6), state.currentAcc)
    }

    @Test
    fun `acc clear resets state`() {
        // Arrange
        AccDataHolder.updateAcc(1, 2, 3)

        // Act
        AccDataHolder.clear()

        // Assert
        assertTrue(AccDataHolder.accState.value.accSamples.isEmpty())
        assertEquals(AccDataHolder.AccSample(0, 0, 0), AccDataHolder.accState.value.currentAcc)
    }
}
