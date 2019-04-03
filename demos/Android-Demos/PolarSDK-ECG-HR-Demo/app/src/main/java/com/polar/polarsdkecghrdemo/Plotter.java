package com.polar.polarsdkecghrdemo;

import android.content.Context;
import android.graphics.Paint;

import com.androidplot.xy.AdvancedLineAndPointRenderer;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeries;
import java.util.Arrays;

public class Plotter {

    String title;
    private String TAG = "Polar_Plotter";
    private PlotterListener listener;
    private Context context;
    private Number[] plotNumbers = new Number[500];
    private FadeFormatter formatter;
    private XYSeries series;
    private int dataIndex;


    public Plotter(Context context, String title){
        this.context = context;
        this.title = title;

        for(int i = 0; i < plotNumbers.length - 1; i++){
            plotNumbers[i] = 60;
        }

        formatter = new FadeFormatter(800);
        formatter.setLegendIconEnabled(false);

        series = new SimpleXYSeries(Arrays.asList(plotNumbers), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, title);
    }

    public SimpleXYSeries getSeries(){
        return (SimpleXYSeries) series;
    }

    public FadeFormatter getFormatter(){
        return formatter;
    }

    public void sendSingleSample(float mV){
        plotNumbers[dataIndex] = mV;
        if(dataIndex >= plotNumbers.length - 1){
            dataIndex = 0;
        }
        if(dataIndex < plotNumbers.length - 1){
            plotNumbers[dataIndex + 1] = null;
        }

        ((SimpleXYSeries) series).setModel(Arrays.asList(plotNumbers), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);
        dataIndex++;
        listener.update();
    }

    public void setListener(PlotterListener listener){
        this.listener = listener;
    }

    //Custom paint stroke to generate a "fade" effect
    public static class FadeFormatter extends AdvancedLineAndPointRenderer.Formatter {
        private int trailSize;

        public FadeFormatter(int trailSize) {
            this.trailSize = trailSize;
        }

        @Override
        public Paint getLinePaint(int thisIndex, int latestIndex, int seriesSize) {
            // offset from the latest index:
            int offset;
            if(thisIndex > latestIndex) {
                offset = latestIndex + (seriesSize - thisIndex);
            } else {
                offset =  latestIndex - thisIndex;
            }
            float scale = 255f / trailSize;
            int alpha = (int) (255 - (offset * scale));
            getLinePaint().setAlpha(alpha > 0 ? alpha : 0);
            return getLinePaint();
        }
    }
}
