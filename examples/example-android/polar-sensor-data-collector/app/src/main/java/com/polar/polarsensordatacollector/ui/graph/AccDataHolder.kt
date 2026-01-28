package com.polar.polarsensordatacollector.ui.graph

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections.emptyList

object AccDataHolder {

    data class AccSample(
        val x: Int,
        val y: Int,
        val z: Int
    )

    data class AccState(
        val accSamples: List<AccSample> = emptyList(),
        val currentAcc: AccSample = AccSample(0, 0, 0)
    )

    private val _accState = MutableStateFlow(AccState())
    val accState: StateFlow<AccState> = _accState.asStateFlow()

    private val accSamplesList = mutableListOf<AccSample>()

    fun updateAcc(x: Int, y: Int, z: Int) {
        val sample = AccSample(x, y, z)
        accSamplesList.add(sample)
        _accState.value = AccState(accSamples = accSamplesList.toList(), currentAcc = sample)
    }

    fun clear() {
        accSamplesList.clear()
        _accState.value = AccState()
    }
}