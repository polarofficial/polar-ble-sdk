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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polar.polarsensordatacollector.R
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

private const val MAX_ACC_SAMPLES = 3000
private const val LEFT_PADDING_DP = 50
private const val GRAPH_PADDING_DP = 16
private const val HEADER_PADDING_DP = 8
private const val GRID_LINE_STROKE_WIDTH_DP = 1
private const val ACC_LINE_STROKE_WIDTH_DP = 1
private const val ACC_POINT_RADIUS_DP = 4
private const val LABEL_TEXT_SIZE_SP = 12
private const val Y_LABEL_X_OFFSET_DP = 10
private const val Y_LABEL_Y_OFFSET_DP = 5
private const val ACC_GRID_LINES = 6

private val GRAPH_BACKGROUND = Color.Black
private val GRID_COLOR = Color.Gray.copy(alpha = 0.3f)
private val TEXT_COLOR = Color.White
private val BUTTON_RED = Color(0xFFD32F2F)
private val ACC_X_COLOR = Color.Cyan
private val ACC_Y_COLOR = Color.Green
private val ACC_Z_COLOR = Color.Magenta

private var lastRange: Pair<Float, Float>? = null

@Composable
fun AccGraphView(onClose: () -> Unit) {
    var samples by remember { mutableStateOf(emptyList<AccDataHolder.AccSample>()) }

    LaunchedEffect(Unit) {
        AccDataHolder.accState.collectLatest { state ->
            samples = state.accSamples
        }
    }

    val xValues = samples.map { it.x.toFloat() }
    val yValues = samples.map { it.y.toFloat() }
    val zValues = samples.map { it.z.toFloat() }

    val (displayMin, displayMax) = calculateRange(xValues + yValues + zValues)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GRAPH_BACKGROUND)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HEADER_PADDING_DP.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val latest = samples.lastOrNull()

                Column {
                    Text(
                        text = stringResource(
                            R.string.acc_value,
                            latest?.x?.toString() ?: "--",
                            latest?.y?.toString() ?: "--",
                            latest?.z?.toString() ?: "--"
                        ),
                        color = TEXT_COLOR,
                        fontSize = 20.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(ACC_X_COLOR, "X")
                        LegendDot(ACC_Y_COLOR, "Y")
                        LegendDot(ACC_Z_COLOR, "Z")
                    }
                }

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

            AccPlotterCanvas(
                xValues = xValues,
                yValues = yValues,
                zValues = zValues,
                displayMin = displayMin,
                displayMax = displayMax,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(GRAPH_PADDING_DP.dp)
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        modifier = Modifier.padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(10.dp)) {
            drawCircle(color)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, color = TEXT_COLOR, fontSize = 12.sp)
    }
}

private fun calculateRange(values: List<Float>): Pair<Float, Float> {
    if (values.isEmpty()) return -1000f to 1000f

    val visible = values.takeLast(MAX_ACC_SAMPLES)
    val maxAbs = visible.maxOf { abs(it) }

    val step = when {
        maxAbs <= 1000f -> 1000f
        maxAbs <= 2000f -> 2000f
        maxAbs <= 5000f -> 5000f
        maxAbs <= 10000f -> 10000f
        maxAbs <= 20000f -> 20000f
        else -> 50000f
    }

    val snapped = ceil(maxAbs / step) * step
    val newRange = -snapped to snapped

    val updatedRange = if (lastRange != null) {
        val prevMax = abs(lastRange!!.second)
        if (snapped > prevMax) newRange else lastRange!!
    } else {
        newRange
    }

    lastRange = updatedRange
    return updatedRange
}

@Composable
fun AccPlotterCanvas(
    xValues: List<Float>,
    yValues: List<Float>,
    zValues: List<Float>,
    displayMin: Float,
    displayMax: Float,
    modifier: Modifier = Modifier
) {
    val range = (displayMax - displayMin).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val leftPadding = LEFT_PADDING_DP.dp.toPx()
        val graphWidth = width - leftPadding

        repeat(ACC_GRID_LINES + 1) { i ->
            val value = displayMin + i * (range / ACC_GRID_LINES)
            val y = height - ((value - displayMin) / range * height)

            drawLine(
                GRID_COLOR,
                Offset(leftPadding, y),
                Offset(width, y),
                GRID_LINE_STROKE_WIDTH_DP.dp.toPx()
            )

            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.0f", value),
                Y_LABEL_X_OFFSET_DP.dp.toPx(),
                y + Y_LABEL_Y_OFFSET_DP.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = LABEL_TEXT_SIZE_SP.sp.toPx()
                }
            )
        }

        val visibleCount = MAX_ACC_SAMPLES
        val stepX = graphWidth / (visibleCount - 1)

        drawAccLine(xValues, displayMin, range, stepX, leftPadding, height, ACC_X_COLOR)
        drawAccLine(yValues, displayMin, range, stepX, leftPadding, height, ACC_Y_COLOR)
        drawAccLine(zValues, displayMin, range, stepX, leftPadding, height, ACC_Z_COLOR)
    }
}

private fun DrawScope.drawAccLine(
    values: List<Float>,
    min: Float,
    range: Float,
    stepX: Float,
    leftPadding: Float,
    height: Float,
    color: Color
) {
    if (values.isEmpty()) return

    val startIndex =
        if (values.size > MAX_ACC_SAMPLES) values.size - MAX_ACC_SAMPLES else 0

    val path = Path()

    values.subList(startIndex, values.size).forEachIndexed { index, v ->
        val x = leftPadding + index * stepX
        val y = height - ((v - min) / range * height)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = ACC_LINE_STROKE_WIDTH_DP.dp.toPx())
    )

    val lastIndex = values.lastIndex - startIndex
    if (lastIndex >= 0) {
        drawCircle(
            color = color,
            radius = ACC_POINT_RADIUS_DP.dp.toPx(),
            center = Offset(
                leftPadding + lastIndex * stepX,
                height - ((values.last() - min) / range * height)
            )
        )
    }
}
