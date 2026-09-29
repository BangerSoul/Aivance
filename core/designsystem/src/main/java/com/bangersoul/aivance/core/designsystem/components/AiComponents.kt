package com.bangersoul.aivance.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bangersoul.aivance.core.designsystem.R
import com.bangersoul.aivance.core.designsystem.theme.AivanceTheme

/**
 * A subtle chip for keywords (e.g., "Kotlin", "Clean Architecture").
 */
@Composable
fun KeywordChip(
    text: String,
    modifier: Modifier = Modifier,
    isMatched: Boolean = true
) {
    val containerColor = if (isMatched) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
    }
    val contentColor = if (isMatched) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val description = if (isMatched) stringResource(R.string.ds_keyword_a11y_matched, text)
        else stringResource(R.string.ds_keyword_a11y_missing, text)

    Surface(
        modifier = modifier.semantics {
            contentDescription = description
        },
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = if (isMatched) null else BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * A premium circular gauge with an animated progress stroke and the score in the center.
 */
@Composable
fun ScoreGauge(
    score: Int,
    modifier: Modifier = Modifier,
    maxScore: Int = 100,
    size: Dp = 100.dp,
    strokeWidth: Dp = 8.dp
) {
    val progress = score.toFloat() / maxScore
    val animatedProgress = remember { Animatable(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val gaugeDescription = stringResource(R.string.ds_score_gauge_a11y, score, maxScore)

    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            targetValue = progress,
            animationSpec = tween(durationMillis = 1500)
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = gaugeDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            // Background track
            drawArc(
                color = trackColor,
                startAngle = -225f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

            // Progress track
            drawArc(
                color = primaryColor,
                startAngle = -225f,
                sweepAngle = 270f * animatedProgress.value,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )
        }

        Text(
            text = score.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 4).sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun KeywordChipPreview() {
    AivanceTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            KeywordChip(text = "Kotlin")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun ScoreGaugePreview() {
    AivanceTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            ScoreGauge(score = 85)
        }
    }
}
