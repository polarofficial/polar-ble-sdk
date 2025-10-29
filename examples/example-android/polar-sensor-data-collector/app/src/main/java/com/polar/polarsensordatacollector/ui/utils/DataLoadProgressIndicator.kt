package com.polar.polarsensordatacollector.ui.utils

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

data class DataLoadProgress(
    val completedBytes: Long,
    val totalBytes: Long,
    val progressPercent: Int,
    val path: String? = null
)

@Composable
fun DataLoadProgressIndicator(
    progress: DataLoadProgress?,
    dataType: String,
    modifier: Modifier = Modifier
) {
    val progressColor = Color.Red

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            if (progress != null) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress.progressPercent / 100f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "progress"
                )

                Text(
                    text = "Loading $dataType...",
                    style = MaterialTheme.typography.body2,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (progress.path != null) {
                    Text(
                        text = progress.path,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${progress.progressPercent}%",
                    style = MaterialTheme.typography.h5,
                    color = progressColor,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = progressColor,
                    backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${formatBytes(progress.completedBytes)} / ${formatBytes(progress.totalBytes)}",
                    style = MaterialTheme.typography.body1,
                    color = Color.White
                )
            } else {
                Text(
                    text = "Loading $dataType...",
                    style = MaterialTheme.typography.body1,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes == 0L -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.ENGLISH, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.ENGLISH, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.ENGLISH, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}