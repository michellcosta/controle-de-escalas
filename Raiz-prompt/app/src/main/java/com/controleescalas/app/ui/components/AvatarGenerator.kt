package com.controleescalas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controleescalas.app.ui.theme.*
import kotlin.math.abs

/**
 * Gera uma cor baseada em uma string (para avatares consistentes)
 */
fun generateColorFromString(input: String): Color {
    var hash = 0
    for (i in input.indices) {
        hash = input[i].code + ((hash shl 5) - hash)
    }
    
    val colors = listOf(
        NeonGreen,
        NeonBlue,
        NeonPurple,
        NeonOrange,
        NeonCyan,
        GradientBlueStart,
        GradientPurpleStart
    )
    
    return colors[abs(hash) % colors.size]
}

/**
 * Avatar com inicial e cor gerada
 */
@Composable
fun ColoredAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 40
) {
    val initial = name.take(1).uppercase()
    val color = generateColorFromString(name)
    
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size * 0.4).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

