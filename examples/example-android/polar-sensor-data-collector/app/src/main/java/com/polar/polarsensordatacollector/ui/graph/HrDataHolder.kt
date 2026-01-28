package com.polar.polarsensordatacollector.ui.graph

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HrDataHolder {
    private const val TAG = "HrDataHolder"

    data class HrSample(
        val hr: Int,
    )

    data class HrState(
        val hrSamples: List<HrSample> = emptyList(),
        val currentHr: Int = 0,
    )

    private val _hrState = MutableStateFlow(HrState())
    val hrState: StateFlow<HrState> = _hrState.asStateFlow()

    private val hrSamplesList = mutableListOf<HrSample>()

    fun updateHr(hr: Int) {
        val sample = HrSample(hr)
        hrSamplesList.add(sample)

        val newState = HrState(
            hrSamples = hrSamplesList.toList(),
            currentHr = hr
        )
        _hrState.value = newState
    }

    fun clear() {
        hrSamplesList.clear()
        _hrState.value = HrState()
    }
}