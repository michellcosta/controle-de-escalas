package com.controleescalas.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.theme.DarkSurfaceVariant
import com.controleescalas.app.ui.theme.Surface3

/**
 * Efeito shimmer para skeleton loading
 */
@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )
    
    Box(
        modifier = modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        DarkSurfaceVariant,
                        Surface3,
                        DarkSurfaceVariant
                    ),
                    start = Offset(shimmerOffset - 300f, shimmerOffset - 300f),
                    end = Offset(shimmerOffset, shimmerOffset)
                )
            )
    )
}

/**
 * Skeleton para card de métrica
 */
@Composable
fun MetricCardSkeleton(
    modifier: Modifier = Modifier
) {
    ShimmerEffect(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .height(150.dp)
    )
}

/**
 * Skeleton para card de transportadora
 */
@Composable
fun TransportadoraCardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header skeleton
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ShimmerEffect(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            ShimmerEffect(
                modifier = Modifier
                    .width(80.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        
        // Content skeleton
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

