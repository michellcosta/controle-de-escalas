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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.theme.MeliBlue
import com.controleescalas.app.ui.theme.CardBorderLight
import com.controleescalas.app.ui.theme.TextBlack
import com.controleescalas.app.ui.theme.TextGray
import com.controleescalas.app.ui.theme.TextGrayLightMode
import com.controleescalas.app.ui.theme.StatusError
import com.controleescalas.app.ui.theme.NeonOrange
import com.controleescalas.app.ui.theme.NeonGreen
import com.controleescalas.app.ui.viewmodels.OperationalViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fundo da tela (branco, estilo Meli). O recorte do card vem da borda + sombra.
 */
@Composable
fun PremiumBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        content()
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val cardModifier = modifier.padding(vertical = 4.dp)
    val inner: @Composable () -> Unit = {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            color = containerColor,
            shadowElevation = 4.dp,
            tonalElevation = 0.dp
        ) { inner() }
    } else {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = containerColor,
            shadowElevation = 4.dp,
            tonalElevation = 0.dp
        ) { inner() }
    }
}

@Composable
fun AppMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MeliBlue,
    textColor: Color = TextBlack
) {
    AppCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Botão principal preenchido (azul, estilo Meli).
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    color: Color = MeliBlue
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.3f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
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
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

/**
 * Botão tonal (estilo Meli): fundo azul-claro, texto azul. Para ações secundárias
 * (ex.: "Ligar", "Mensagens", "Não consegui entregar").
 */
@Composable
fun AppTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MeliBlue.copy(alpha = 0.12f),
            contentColor = MeliBlue,
            disabledContainerColor = MeliBlue.copy(alpha = 0.06f),
            disabledContentColor = MeliBlue.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
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
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * CustomTextField - Input estilizado para o tema claro
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
            focusedBorderColor = MeliBlue,
            unfocusedBorderColor = CardBorderLight,
            focusedLabelColor = MeliBlue,
            unfocusedLabelColor = TextGrayLightMode,
            cursorColor = MeliBlue,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        leadingIcon = if (leadingIcon != null) {
            { Icon(imageVector = leadingIcon, contentDescription = null, tint = if (isError) MaterialTheme.colorScheme.error else MeliBlue) }
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
                .background(MeliBlue, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
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
        else -> Icons.Default.SignalCellularAlt to MeliBlue
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (connectionState.isUsingCache) {
                    Text(
                        text = "Sincronizando em background...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGrayLightMode,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (connectionState.lastSyncTime != null) {
                    Text(
                        text = "Última atualização: $lastSyncText",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGrayLightMode,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
