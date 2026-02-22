package com.controleescalas.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.theme.DarkSurface
import com.controleescalas.app.ui.theme.GlassBlack
import com.controleescalas.app.ui.theme.GlassDark
import com.controleescalas.app.ui.theme.NeonGreen
import com.controleescalas.app.ui.theme.NeonGreenDark
import com.controleescalas.app.ui.theme.TextGray
import com.controleescalas.app.ui.theme.TextWhite
import com.controleescalas.app.ui.theme.StatusError
import com.controleescalas.app.ui.theme.NeonOrange
import com.controleescalas.app.ui.theme.NeonBlue
import com.controleescalas.app.ui.viewmodels.OperationalViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * GlassCard - Card com efeito de vidro, borda sutil e reflexo de quina
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = DarkSurface.copy(alpha = 0.4f),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        // Borda assimétrica para simular reflexo de luz (Light Edge)
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(400f, 400f)
            )
        ),
        onClick = onClick ?: {}
    ) {
        Box(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

/**
 * NeonButton - Botão com gradiente, brilho (Neon Aura) e animação
 */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    color: Color = NeonGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neonAura")
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        // Neon Aura (Glow) - Apenas se habilitado
        if (enabled && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = color,
                        spotColor = color
                    )
                    .background(color.copy(alpha = auraAlpha), RoundedCornerShape(12.dp))
                    .blur(16.dp)
            )
        }

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = enabled && !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.Black,
                disabledContainerColor = color.copy(alpha = 0.3f)
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 0.dp
            )
        ) {
            if (isLoading) {
                Text("Carregando...", style = MaterialTheme.typography.labelLarge)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * PremiumBackground - Background com gradiente e textura de malha (dot grid)
 */
@Composable
fun PremiumBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        com.controleescalas.app.ui.theme.DeepBlue,
                        com.controleescalas.app.ui.theme.DarkBackground,
                        com.controleescalas.app.ui.theme.DarkBackground
                    )
                )
            )
            .drawBehind {
                // Desenha uma malha de pontos sutis (Dot Grid) para textura
                val dotSize = 1.dp.toPx()
                val spacing = 32.dp.toPx()
                val paintAlpha = 0.05f
                
                val columns = (size.width / spacing).toInt()
                val rows = (size.height / spacing).toInt()
                
                for (x in 0..columns) {
                    for (y in 0..rows) {
                        drawCircle(
                            color = Color.White.copy(alpha = paintAlpha),
                            radius = dotSize / 2f,
                            center = Offset(x.toFloat() * spacing, y.toFloat() * spacing)
                        )
                    }
                }
            }
    ) {
        content()
    }
}

/**
 * CustomTextField - Input estilizado para dark mode
 */
@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isError: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonGreen,
            unfocusedBorderColor = TextGray.copy(alpha = 0.5f),
            focusedLabelColor = NeonGreen,
            unfocusedLabelColor = TextGray,
            cursorColor = NeonGreen,
            errorBorderColor = MaterialTheme.colorScheme.error
        ),
        leadingIcon = if (leadingIcon != null) {
            { Icon(imageVector = leadingIcon, contentDescription = null, tint = if (isError) MaterialTheme.colorScheme.error else NeonGreen) }
        } else null,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        singleLine = true
    )
}

/**
 * SectionHeader - Título de seção com detalhe visual
 */
@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(NeonGreen, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextWhite
        )
    }
}

/**
 * ConnectionStatusIndicator - Indicador visual do estado de conexão
 * Mostra tipo de conexão, se está usando cache e última sincronização
 */
@Composable
fun ConnectionStatusIndicator(
    connectionState: OperationalViewModel.ConnectionState,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when {
        !connectionState.isOnline -> Icons.Default.CloudOff to StatusError
        connectionState.isUsingCache -> Icons.Default.CloudSync to NeonOrange
        connectionState.connectionType == com.controleescalas.app.data.NetworkUtils.ConnectionType.WIFI -> 
            Icons.Default.Wifi to NeonGreen
        else -> Icons.Default.SignalCellularAlt to NeonBlue
    }
    
    val lastSyncText = connectionState.lastSyncTime?.let { time ->
        val diff = (System.currentTimeMillis() - time) / 1000
        when {
            diff < 10 -> "Agora"
            diff < 60 -> "há ${diff}s"
            diff < 3600 -> "há ${diff / 60}min"
            else -> {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                sdf.format(Date(time))
            }
        }
    } ?: "Nunca"
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface.copy(alpha = 0.7f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connectionState.connectionMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite,
                    fontWeight = FontWeight.Medium
                )
                if (connectionState.isUsingCache) {
                    Text(
                        text = "Sincronizando em background...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (connectionState.lastSyncTime != null) {
                    Text(
                        text = "Última atualização: $lastSyncText",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
