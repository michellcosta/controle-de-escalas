package com.controleescalas.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.DisponibilidadeMotorista
import com.controleescalas.app.data.models.DisponibilidadeStatus
import com.controleescalas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog para o admin visualizar a lista de disponibilidade dos motoristas
 */
@Composable
fun DisponibilidadeListDialog(
    data: String, // YYYY-MM-DD
    motoristas: List<DisponibilidadeMotorista>,
    status: DisponibilidadeStatus?,
    onDismiss: () -> Unit,
    onCreateEscala: (List<String>) -> Unit // IDs dos disponíveis
) {
    val dataFormatada = try {
        val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfOutput = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val date = sdfInput.parse(data)
        sdfOutput.format(date ?: Date())
    } catch (e: Exception) {
        data
    }
    
    val motoristasDisponiveis = motoristas.filter { it.disponivel == true }
    val motoristasIndisponiveis = motoristas.filter { it.disponivel == false }
    val motoristasSemResposta = motoristas.filter { it.disponivel == null }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Column {
                Text(
                    "Disponibilidade - $dataFormatada",
                    color = TextWhite,
                    style = MaterialTheme.typography.titleLarge
                )
                status?.let {
                    Text(
                        "${it.disponiveis} disponíveis de ${it.totalMotoristas}",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Estatísticas
                status?.let { stats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip(
                            label = "Disponíveis",
                            value = stats.disponiveis.toString(),
                            color = NeonGreen
                        )
                        StatChip(
                            label = "Indisponíveis",
                            value = stats.indisponiveis.toString(),
                            color = StatusError
                        )
                        StatChip(
                            label = "Sem resposta",
                            value = stats.semResposta.toString(),
                            color = TextGray
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = TextGray.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Lista de motoristas
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Disponíveis
                    if (motoristasDisponiveis.isNotEmpty()) {
                        item {
                            Text(
                                "✅ Disponíveis (${motoristasDisponiveis.size})",
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        items(motoristasDisponiveis) { motorista ->
                            MotoristaDisponibilidadeCard(motorista, NeonGreen, Icons.Default.CheckCircle)
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    
                    // Indisponíveis
                    if (motoristasIndisponiveis.isNotEmpty()) {
                        item {
                            Text(
                                "❌ Indisponíveis (${motoristasIndisponiveis.size})",
                                color = StatusError,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        items(motoristasIndisponiveis) { motorista ->
                            MotoristaDisponibilidadeCard(motorista, StatusError, Icons.Default.Close)
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    
                    // Sem resposta
                    if (motoristasSemResposta.isNotEmpty()) {
                        item {
                            Text(
                                "⏳ Sem resposta (${motoristasSemResposta.size})",
                                color = TextGray,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        items(motoristasSemResposta) { motorista ->
                            MotoristaDisponibilidadeCard(motorista, TextGray, Icons.Default.Info)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (motoristasDisponiveis.isNotEmpty()) {
                Button(
                    onClick = {
                        onCreateEscala(motoristasDisponiveis.map { it.motoristaId })
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text("CRIAR ESCALA COM DISPONÍVEIS", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("FECHAR", color = TextGray)
            }
        }
    )
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )
    }
}

@Composable
private fun MotoristaDisponibilidadeCard(
    motorista: DisponibilidadeMotorista,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    motorista.nome,
                    color = TextWhite,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    motorista.telefone,
                    color = TextGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
