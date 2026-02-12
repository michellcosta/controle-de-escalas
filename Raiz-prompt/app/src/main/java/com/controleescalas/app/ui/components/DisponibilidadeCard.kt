package com.controleescalas.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Card de disponibilidade compacto para o motorista
 * Permite alterar a resposta mesmo depois de ter respondido
 */
@Composable
fun DisponibilidadeCard(
    data: String, // YYYY-MM-DD
    jaRespondeu: Boolean,
    disponivel: Boolean?,
    onMarcarDisponivel: () -> Unit,
    onMarcarIndisponivel: () -> Unit
) {
    val dataFormatada = remember(data) {
        try {
            val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sdfOutput = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = sdfInput.parse(data)
            sdfOutput.format(date ?: Date())
        } catch (e: Exception) {
            data
        }
    }
    
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho compacto
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📅",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Column {
                        Text(
                            text = "Disponibilidade",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dataFormatada,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                }
                
                // Indicador de status atual (se já respondeu)
                if (jaRespondeu && disponivel != null) {
                    Surface(
                        color = if (disponivel) NeonGreen.copy(alpha = 0.2f) else StatusError.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (disponivel) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (disponivel) NeonGreen else StatusError,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (disponivel) "Disponível" else "Indisponível",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (disponivel) NeonGreen else StatusError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Texto informativo (compacto)
            if (jaRespondeu) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Você pode alterar sua resposta a qualquer momento",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = "Você está disponível para trabalhar amanhã?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Botões de ação (sempre visíveis)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botão DISPONÍVEL
                Button(
                    onClick = onMarcarDisponivel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (disponivel == true) NeonGreen else NeonGreen.copy(alpha = 0.7f),
                        contentColor = Color.Black
                    ),
                    border = if (disponivel == true) BorderStroke(2.dp, NeonGreen.copy(alpha = 0.8f)) else null
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "SIM",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                
                // Botão INDISPONÍVEL
                OutlinedButton(
                    onClick = onMarcarIndisponivel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusError,
                        containerColor = if (disponivel == false) StatusError.copy(alpha = 0.1f) else Color.Transparent
                    ),
                    border = if (disponivel == false) BorderStroke(2.dp, StatusError) else BorderStroke(1.dp, StatusError.copy(alpha = 0.5f))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "NÃO",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
