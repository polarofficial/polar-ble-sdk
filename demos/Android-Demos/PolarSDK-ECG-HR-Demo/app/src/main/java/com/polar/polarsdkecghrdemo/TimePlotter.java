package com.polar.polarsdkecghrdemo;

import android.content.Context;
import android.graphics.Color;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeriesFormatter;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import polar.com.sdk.api.model.PolarHrData;

/**
 * Implements two series for HR and RR using time for the x values.
 */
public class TimePlotter {
    private static final int NVALS = 300;  // 5 min

    String title;
    private String TAG = "Polar_Plotter";
    private double RR_SCALE = .1;
    private PlotterListener listener;
    private Context context;
    private XYSeriesFormatter hrFormatter;
    private XYSeriesFormatter rrFormatter;
    private SimpleXYSeries hrSeries;
    private SimpleXYSeries rrSeries;
    private Double[] xHrVals = new Double[NVALS];
    private Double[] yHrVals = new Double[NVALS];
    private Double[] xRrVals = new Double[NVALS];
    private Double[] yRrVals = new Double[NVALS];

    public TimePlotter(Context context, String title) {
        this.context = context;
        this.title = title;  // Not used
        Date now = new Date();
        double endTime = now.getTime();
        double startTime = endTime - NVALS * 1000;
        double delta = (endTime - startTime) / (NVALS - 1);

        // Specify initial values to keep it from auto sizing
        for (int i = 0; i < NVALS; i++) {
            xHrVals[i] = new Double(startTime + i * delta);
            yHrVals[i] = new Double(60);
            xRrVals[i] = new Double(startTime + i * delta);
            yRrVals[i] = new Double(100);
        }

        hrFormatter = new LineAndPointFormatter(Color.RED,
                null, null, null);
        hrFormatter.setLegendIconEnabled(false);
        hrSeries = new SimpleXYSeries(Arrays.asList(xHrVals),
                Arrays.asList(yHrVals),
                "HR");

        rrFormatter = new LineAndPointFormatter(Color.BLUE,
                null, null, null);
        rrFormatter.setLegendIconEnabled(false);
        rrSeries = new SimpleXYSeries(Arrays.asList(xRrVals),
                Arrays.asList(yRrVals),
                "HR");
    }

    public SimpleXYSeries getHrSeries() {
        return (SimpleXYSeries) hrSeries;
    }

    public SimpleXYSeries getRrSeries() {
        return (SimpleXYSeries) rrSeries;
    }

    public XYSeriesFormatter getHrFormatter() {
        return hrFormatter;
    }

    public XYSeriesFormatter getRrFormatter() {
        return rrFormatter;
    }

    /**
     * Implements a strip chart by moving series data backwards and adding
     * new data at the end.
     *
     * @param polarHrData The HR data that came in.
     */
    public void addValues(PolarHrData polarHrData) {
        Date now = new Date();
        long time = now.getTime();
        for (int i = 0; i < NVALS - 1; i++) {
            xHrVals[i] = xHrVals[i + 1];
            yHrVals[i] = yHrVals[i + 1];
            hrSeries.setXY(xHrVals[i], yHrVals[i], i);
        }
        xHrVals[NVALS - 1] = new Double(time);
        yHrVals[NVALS - 1] = new Double(polarHrData.hr);
        hrSeries.setXY(xHrVals[NVALS - 1], yHrVals[NVALS - 1], NVALS - 1);

        // Do RR
        // We don't know at what time the RR intervals start.  All we know is
        // the time the data arrived (the current time, now). This
        // implementation assumes they end at the current time, and spaces them
        // out in the past accordingly.  This seems to get the
        // relative positioning reasonably well.

        // Scale the RR values by this to use the same axis. (Could implement
        // NormedXYSeries and use two axes)
        List<Integer> rrsMs = polarHrData.rrsMs;
        int nRrVals = rrsMs.size();
        if (nRrVals > 0) {
            for (int i = 0; i < NVALS - nRrVals; i++) {
                xRrVals[i] = xRrVals[i + 1];
                yRrVals[i] = yRrVals[i + 1];
                rrSeries.setXY(xRrVals[i], yRrVals[i], i);
            }
            double totalRR = 0;
            for (int i = 0; i < nRrVals; i++) {
                totalRR += RR_SCALE * rrsMs.get(i);
            }
            int index = 0;
            double rr;
            for (int i = NVALS - nRrVals; i < NVALS; i++) {
                rr = RR_SCALE * rrsMs.get(index++);
                xRrVals[i] = new Double(time - totalRR);
                yRrVals[i] = new Double(rr);
                totalRR -= rr;
                rrSeries.setXY(xRrVals[i], yRrVals[i], i);
            }
        }

        listener.update();
    }

    public void setListener(PlotterListener listener) {
        this.listener = listener;
    }
}
