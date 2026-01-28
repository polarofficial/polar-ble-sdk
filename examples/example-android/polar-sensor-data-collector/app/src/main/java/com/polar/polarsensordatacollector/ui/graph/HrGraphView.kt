package com.polar.polarsensordatacollector.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.polar.polarsensordatacollector.R

private const val TAG = "HrGraphView"

// Y-Axis HR limits
private const val ABSOLUTE_MIN_HR = 40
private const val ABSOLUTE_MAX_HR = 220
private const val MIN_DISPLAY_RANGE = 40
private const val Y_AXIS_SNAP_INTERVAL = 10
private const val Y_AXIS_PADDING = 5
private const val Y_AXIS_GRID_STEP = 10
private const val DEFAULT_MIN_HR = 60
private const val DEFAULT_MAX_HR = 100

// Sample buffer
private const val MAX_HR_SAMPLES = 300

// UI dimensions (dp)
private const val LEFT_PADDING_DP = 50
private const val GRAPH_PADDING_DP = 16
private const val HEADER_PADDING_DP = 8
private const val HR_LINE_STROKE_WIDTH_DP = 2
private const val HR_POINT_RADIUS_DP = 6
private const val GRID_LINE_STROKE_WIDTH_DP = 1
private const val Y_LABEL_X_OFFSET_DP = 10
private const val Y_LABEL_Y_OFFSET_DP = 5

// Text sizes (sp)
private const val HR_TEXT_SIZE_SP = 24
private const val LABEL_TEXT_SIZE_SP = 12

// Colors
private val BUTTON_RED = Color(0xFFD32F2F)
private val GRAPH_BACKGROUND = Color.Black
private val HR_LINE_COLOR = Color.Red
private val GRID_COLOR = Color.Gray.copy(alpha = 0.3f)
private val TEXT_COLOR = Color.White
private val LABEL_COLOR = android.graphics.Color.WHITE

@Composable
fun HrGraphView(
    onClose: () -> Unit
) {
    val hrState by HrDataHolder.hrState.collectAsState()

    val hrValues = remember(hrState.hrSamples) {
        hrState.hrSamples.map { it.hr }
    }

    val (displayMinHr, displayMaxHr) = remember(hrValues) {
        calculateDisplayRange(hrValues)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GRAPH_BACKGROUND)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HEADER_PADDING_DP.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hr_value, hrState.currentHr.toString()),
                    color = TEXT_COLOR,
                    fontSize = HR_TEXT_SIZE_SP.sp
                )
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = BUTTON_RED,
                        contentColor = TEXT_COLOR
                    )
                ) {
                    Text(stringResource(R.string.close))
                }
            }

            HrPlotterCanvas(
                hrValues = hrValues,
                displayMinHr = displayMinHr,
                displayMaxHr = displayMaxHr,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(GRAPH_PADDING_DP.dp)
            )
        }
    }
}

private fun floorToInterval(value: Int, interval: Int): Int {
    return (value / interval) * interval
}

private fun ceilToInterval(value: Int, interval: Int): Int {
    return ((value + interval - 1) / interval) * interval
}

private fun calculateDisplayRange(hrValues: List<Int>): Pair<Int, Int> {
    if (hrValues.isEmpty()) {
        return Pair(DEFAULT_MIN_HR, DEFAULT_MAX_HR)
    }

    val actualMin = hrValues.minOrNull() ?: DEFAULT_MIN_HR
    val actualMax = hrValues.maxOrNull() ?: DEFAULT_MIN_HR

    var displayMin = floorToInterval(actualMin - Y_AXIS_PADDING, Y_AXIS_SNAP_INTERVAL)
        .coerceAtLeast(ABSOLUTE_MIN_HR)
    var displayMax = ceilToInterval(actualMax + Y_AXIS_PADDING, Y_AXIS_SNAP_INTERVAL)
        .coerceAtMost(ABSOLUTE_MAX_HR)

    val currentRange = displayMax - displayMin
    if (currentRange < MIN_DISPLAY_RANGE) {
        val paddingNeeded = MIN_DISPLAY_RANGE - currentRange
        val paddingEachSide = ceilToInterval(paddingNeeded / 2, Y_AXIS_SNAP_INTERVAL)

        displayMin = (displayMin - paddingEachSide).coerceAtLeast(ABSOLUTE_MIN_HR)
        displayMax = (displayMax + paddingEachSide).coerceAtMost(ABSOLUTE_MAX_HR)

        val newRange = displayMax - displayMin
        if (newRange < MIN_DISPLAY_RANGE) {
            if (displayMin == ABSOLUTE_MIN_HR) {
                displayMax = floorToInterval(
                    displayMin + MIN_DISPLAY_RANGE + Y_AXIS_SNAP_INTERVAL,
                    Y_AXIS_SNAP_INTERVAL
                )
                    .coerceAtMost(ABSOLUTE_MAX_HR)
            } else {
                displayMin = ceilToInterval(
                    displayMax - MIN_DISPLAY_RANGE - Y_AXIS_SNAP_INTERVAL,
                    Y_AXIS_SNAP_INTERVAL
                )
                    .coerceAtLeast(ABSOLUTE_MIN_HR)
            }
        }
    }

    displayMin = floorToInterval(displayMin, Y_AXIS_SNAP_INTERVAL).coerceAtLeast(ABSOLUTE_MIN_HR)
    displayMax = ceilToInterval(displayMax, Y_AXIS_SNAP_INTERVAL).coerceAtMost(ABSOLUTE_MAX_HR)

    return Pair(displayMin, displayMax)
}

@Composable
fun HrPlotterCanvas(
    hrValues: List<Int>,
    displayMinHr: Int,
    displayMaxHr: Int,
    modifier: Modifier = Modifier
) {
    val minHr = displayMinHr.toFloat()
    val maxHr = displayMaxHr.toFloat()
    val hrRange = (maxHr - minHr).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val leftPadding = LEFT_PADDING_DP.dp.toPx()
        val graphWidth = width - leftPadding

        for (hr in displayMinHr..displayMaxHr step Y_AXIS_GRID_STEP) {
            val y = height - ((hr - minHr) / hrRange * height)

            drawLine(
                color = GRID_COLOR,
                start = Offset(leftPadding, y),
                end = Offset(width, y),
                strokeWidth = GRID_LINE_STROKE_WIDTH_DP.dp.toPx()
            )

            drawContext.canvas.nativeCanvas.drawText(
                hr.toString(),
                Y_LABEL_X_OFFSET_DP.dp.toPx(),
                y + Y_LABEL_Y_OFFSET_DP.dp.toPx(),
                android.graphics.Paint().apply {
                    color = LABEL_COLOR
                    textSize = LABEL_TEXT_SIZE_SP.sp.toPx()
                }
            )
        }

        if (hrValues.isEmpty()) return@Canvas

        val stepX = graphWidth / (MAX_HR_SAMPLES - 1).coerceAtLeast(1)

        if (hrValues.size > 1) {
            val path = Path()

            hrValues.forEachIndexed { index, hr ->
                val x = leftPadding + index * stepX
                val clampedHr = hr.toFloat().coerceIn(minHr, maxHr)
                val y = height - ((clampedHr - minHr) / hrRange * height)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = HR_LINE_COLOR,
                style = Stroke(width = HR_LINE_STROKE_WIDTH_DP.dp.toPx())
            )
        }

        if (hrValues.isNotEmpty()) {
            val lastIndex = hrValues.size - 1
            val lastHr = hrValues.last().toFloat().coerceIn(minHr, maxHr)
            val x = leftPadding + lastIndex * stepX
            val y = height - ((lastHr - minHr) / hrRange * height)

            drawCircle(
                color = HR_LINE_COLOR,
                radius = HR_POINT_RADIUS_DP.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}