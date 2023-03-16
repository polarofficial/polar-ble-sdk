package com.polar.polarsdkecghrdemo

import android.graphics.Color
import com.androidplot.xy.LineAndPointFormatter
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.XYSeriesFormatter
import com.polar.sdk.api.model.PolarHrData
import java.util.*

/**
 * Implements two series for HR and RR using time for the x values.
 */
class HrAndRrPlotter {
    companion object {
        private const val TAG = "TimePlotter"
        private const val NVALS = 300 // 5 min
        private const val RR_SCALE = .1
    }

    private var listener: PlotterListener? = null
    val hrFormatter: XYSeriesFormatter<*>
    val rrFormatter: XYSeriesFormatter<*>
    val hrSeries: SimpleXYSeries
    val rrSeries: SimpleXYSeries
    private val xHrVals = MutableList(NVALS) { 0.0 }
    private val yHrVals = MutableList(NVALS) { 0.0 }
    private val xRrVals = MutableList(NVALS) { 0.0 }
    private val yRrVals = MutableList(NVALS) { 0.0 }

    init {
        val now = Date()
        val endTime = now.time.toDouble()
        val startTime = endTime - NVALS * 1000
        val delta = (endTime - startTime) / (NVALS - 1)

        // Specify initial values to keep it from auto sizing
        for (i in 0 until NVALS) {
            xHrVals[i] = startTime + i * delta
            yHrVals[i] = 60.0
            xRrVals[i] = startTime + i * delta
            yRrVals[i] = 100.0
        }
        hrFormatter = LineAndPointFormatter(Color.RED, null, null, null)
        hrFormatter.setLegendIconEnabled(false)
        hrSeries = SimpleXYSeries(xHrVals, yHrVals, "HR")
        rrFormatter = LineAndPointFormatter(Color.BLUE, null, null, null)
        rrFormatter.setLegendIconEnabled(false)
        rrSeries = SimpleXYSeries(xRrVals, yRrVals, "RR")
    }

    /**
     * Implements a strip chart by moving series data backwards and adding
     * new data at the end.
     *
     * @param polarHrData The HR data that came in.
     */
    fun addValues(polarHrData: PolarHrData.PolarHrSample) {
        val now = Date()
        val time = now.time
        for (i in 0 until NVALS - 1) {
            xHrVals[i] = xHrVals[i + 1]
            yHrVals[i] = yHrVals[i + 1]
            hrSeries.setXY(xHrVals[i], yHrVals[i], i)
        }
        xHrVals[NVALS - 1] = time.toDouble()
        yHrVals[NVALS - 1] = polarHrData.hr.toDouble()
        hrSeries.setXY(xHrVals[NVALS - 1], yHrVals[NVALS - 1], NVALS - 1)

        // Do RR
        // We don't know at what time the RR intervals start.  All we know is
        // the time the data arrived (the current time, now). This
        // implementation assumes they end at the current time, and spaces them
        // out in the past accordingly.  This seems to get the
        // relative positioning reasonably well.

        // Scale the RR values by this to use the same axis. (Could implement
        // NormedXYSeries and use two axes)
        val rrsMs = polarHrData.rrsMs
        val nRrVals = rrsMs.size
        if (nRrVals > 0) {
            for (i in 0 until NVALS - nRrVals) {
                xRrVals[i] = xRrVals[i + 1]
                yRrVals[i] = yRrVals[i + 1]
                rrSeries.setXY(xRrVals[i], yRrVals[i], i)
            }
            var totalRR = 0.0
            for (i in 0 until nRrVals) {
                totalRR += RR_SCALE * rrsMs[i]
            }
            var index = 0
            var rr: Double
            for (i in NVALS - nRrVals until NVALS) {
                rr = RR_SCALE * rrsMs[index++]
                xRrVals[i] = time - totalRR
                yRrVals[i] = rr
                totalRR -= rr
                rrSeries.setXY(xRrVals[i], yRrVals[i], i)
            }
        }
        listener?.update()
    }

    fun setListener(listener: PlotterListener?) {
        this.listener = listener
    }
}