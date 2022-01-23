package com.polar.polarsdkecghrdemo

import com.androidplot.xy.AdvancedLineAndPointRenderer
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.XYSeries

class EcgPlotter(title: String, ecgFrequency: Int) {
    companion object {
        private const val TAG = "EcgPlotter"
        private const val SECONDS_TO_PLOT = 5
    }

    private var listener: PlotterListener? = null
    private val plotNumbers: MutableList<Number?>
    val formatter: AdvancedLineAndPointRenderer.Formatter
    private val series: XYSeries
    private var dataIndex = 0

    init {
        val ySamplesSize = ecgFrequency * SECONDS_TO_PLOT
        plotNumbers = MutableList(ySamplesSize) { null }
        formatter = AdvancedLineAndPointRenderer.Formatter()
        formatter.isLegendIconEnabled = false
        series = SimpleXYSeries(plotNumbers, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, title)
    }

    fun getSeries(): SimpleXYSeries {
        return series as SimpleXYSeries
    }

    fun sendSingleSample(mV: Float) {
        plotNumbers[dataIndex] = mV
        if (dataIndex >= plotNumbers.size - 1) {
            dataIndex = 0
        }
        if (dataIndex < plotNumbers.size - 1) {
            plotNumbers[dataIndex + 1] = null
        } else {
            plotNumbers[0] = null
        }

        (series as SimpleXYSeries).setModel(plotNumbers, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY)
        dataIndex++
        listener?.update()
    }

    fun setListener(listener: PlotterListener) {
        this.listener = listener
    }
}