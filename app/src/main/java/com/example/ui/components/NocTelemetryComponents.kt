package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TrafficSampleEntity
import com.example.network.CapabilityTier
import com.example.ui.theme.NocAmberWarning
import com.example.ui.theme.NocCrimsonDanger
import com.example.ui.theme.NocCyanPrimary
import com.example.ui.theme.NocEmeraldSecondary
import com.example.ui.theme.NocPurpleAccent
import com.example.util.NetworkFormatters

@Composable
fun StatusPulseDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val dotColor = if (isOnline) NocEmeraldSecondary else NocCrimsonDanger
    Box(
        modifier = modifier
            .size(10.dp)
            .alpha(if (isOnline) pulseAlpha else 0.85f)
            .clip(CircleShape)
            .background(dotColor)
    )
}

@Composable
fun TacticalMetricCard(
    label: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RealTimeThroughputCanvasGraph(
    samples: List<TrafficSampleEntity>,
    currentDlBps: Long,
    currentUlBps: Long,
    modifier: Modifier = Modifier
) {
    val dlPoints = if (samples.size >= 2) {
        samples.takeLast(30).map { it.downloadSpeedBps.toFloat() }
    } else {
        listOf(0f, currentDlBps.toFloat())
    }
    val ulPoints = if (samples.size >= 2) {
        samples.takeLast(30).map { it.uploadSpeedBps.toFloat() }
    } else {
        listOf(0f, currentUlBps.toFloat())
    }

    val maxPeak = maxOf(
        dlPoints.maxOrNull() ?: 1024f,
        ulPoints.maxOrNull() ?: 1024f,
        10240f // minimum 10 KB/s scale so flat lines don't divide by zero
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIVE THROUGHPUT TELEMETRY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Peak Scale: ${NetworkFormatters.formatSpeedBps(maxPeak.toLong())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NocCyanPrimary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "↓ ${NetworkFormatters.formatSpeedBps(currentDlBps)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = NocCyanPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NocEmeraldSecondary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "↑ ${NetworkFormatters.formatSpeedBps(currentUlBps)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = NocEmeraldSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Draw 4 horizontal tactical grid lines
                    for (i in 0..3) {
                        val y = h * (i / 3f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    fun buildLineAndFillPaths(points: List<Float>): Pair<Path, Path> {
                        val linePath = Path()
                        val fillPath = Path()
                        val stepX = if (points.size > 1) w / (points.size - 1) else w
                        points.forEachIndexed { idx, v ->
                            val x = idx * stepX
                            val norm = (v / maxPeak).coerceIn(0f, 1f)
                            val y = h - (norm * (h - 12f)) - 6f
                            if (idx == 0) {
                                linePath.moveTo(x, y)
                                fillPath.moveTo(x, h)
                                fillPath.lineTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                            if (idx == points.lastIndex) {
                                fillPath.lineTo(x, h)
                                fillPath.close()
                            }
                        }
                        return linePath to fillPath
                    }

                    val (dlLine, dlFill) = buildLineAndFillPaths(dlPoints)
                    drawPath(
                        path = dlFill,
                        brush = Brush.verticalGradient(
                            colors = listOf(NocCyanPrimary.copy(alpha = 0.32f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = dlLine,
                        color = NocCyanPrimary,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    val (ulLine, ulFill) = buildLineAndFillPaths(ulPoints)
                    drawPath(
                        path = ulFill,
                        brush = Brush.verticalGradient(
                            colors = listOf(NocEmeraldSecondary.copy(alpha = 0.24f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = ulLine,
                        color = NocEmeraldSecondary,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun UsageHistoryBarChart(
    labels: List<String>,
    downloadValues: List<Long>,
    uploadValues: List<Long>,
    modifier: Modifier = Modifier
) {
    val maxVal = maxOf(
        (downloadValues + uploadValues).maxOrNull() ?: 1024L,
        1024L
    ).toFloat()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "DOWNLOAD VS UPLOAD DISTRIBUTION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val count = labels.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.28f).coerceIn(8f, 36f)
                val chartHeight = size.height

                for (i in labels.indices) {
                    val dlNorm = ((downloadValues.getOrElse(i) { 0L }) / maxVal).coerceIn(0.04f, 1f)
                    val ulNorm = ((uploadValues.getOrElse(i) { 0L }) / maxVal).coerceIn(0.04f, 1f)
                    val dlH = chartHeight * dlNorm
                    val ulH = chartHeight * ulNorm
                    val centerX = slotWidth * i + slotWidth / 2f

                    drawRoundRect(
                        color = NocCyanPrimary,
                        topLeft = Offset(centerX - barWidth - 2f, chartHeight - dlH),
                        size = Size(barWidth, dlH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = NocEmeraldSecondary,
                        topLeft = Offset(centerX + 2f, chartHeight - ulH),
                        size = Size(barWidth, ulH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                labels.forEach { lbl ->
                    Text(
                        text = lbl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CapabilityBadgeChip(tier: CapabilityTier, isAvailable: Boolean) {
    val (bgColor, textColor) = when {
        tier == CapabilityTier.SUPPORTED -> NocEmeraldSecondary.copy(alpha = 0.16f) to NocEmeraldSecondary
        tier == CapabilityTier.UNROOTED_GATEWAY -> NocCyanPrimary.copy(alpha = 0.16f) to NocCyanPrimary
        tier == CapabilityTier.REQUIRES_SHIZUKU -> NocPurpleAccent.copy(alpha = 0.18f) to NocPurpleAccent
        tier == CapabilityTier.REQUIRES_ROOT -> NocAmberWarning.copy(alpha = 0.18f) to NocAmberWarning
        else -> NocCrimsonDanger.copy(alpha = 0.18f) to NocCrimsonDanger
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = if (isAvailable && tier == CapabilityTier.REQUIRES_ROOT) "Root Active" else tier.badgeText,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
