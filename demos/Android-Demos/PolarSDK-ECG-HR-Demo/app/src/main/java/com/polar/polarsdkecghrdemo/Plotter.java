package com.polar.polarsdkecghrdemo;

import android.graphics.Paint;

import com.androidplot.xy.AdvancedLineAndPointRenderer;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeries;

import java.util.Arrays;

public class Plotter {
    private static final String TAG = "Plotter";
    private PlotterListener listener;
    private final Number[] plotNumbers = new Number[500];
    private final FadeFormatter formatter;
    private final XYSeries series;
    private int dataIndex;

    public Plotter(String title) {
        for (int i = 0; i < plotNumbers.length - 1; i++) {
            plotNumbers[i] = 60;
        }

        formatter = new FadeFormatter(800);
        formatter.setLegendIconEnabled(false);
        series = new SimpleXYSeries(Arrays.asList(plotNumbers), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, title);
    }

    public SimpleXYSeries getSeries() {
        return (SimpleXYSeries) series;
    }

    public FadeFormatter getFormatter() {
        return formatter;
    }

    public void sendSingleSample(float mV) {
        plotNumbers[dataIndex] = mV;
        if (dataIndex >= plotNumbers.length - 1) {
            dataIndex = 0;
        }
        if (dataIndex < plotNumbers.length - 1) {
            plotNumbers[dataIndex + 1] = null;
        }

        ((SimpleXYSeries) series).setModel(Arrays.asList(plotNumbers), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);
        dataIndex++;
        listener.update();
    }

    public void setListener(PlotterListener listener) {
        this.listener = listener;
    }

    //Custom paint stroke to generate a "fade" effect
    public static class FadeFormatter extends AdvancedLineAndPointRenderer.Formatter {
        private final int trailSize;

        public FadeFormatter(int trailSize) {
            this.trailSize = trailSize;
        }

        @Override
        public Paint getLinePaint(int thisIndex, int latestIndex, int seriesSize) {
            // offset from the latest index:
            int offset;
            if (thisIndex > latestIndex) {
                offset = latestIndex + (seriesSize - thisIndex);
            } else {
                offset = latestIndex - thisIndex;
            }
            float scale = 255f / trailSize;
            int alpha = (int) (255 - (offset * scale));
            getLinePaint().setAlpha(Math.max(alpha, 0));
            return getLinePaint();
        }
    }
}
