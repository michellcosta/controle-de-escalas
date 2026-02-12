package com.controleescalas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.StatusMotorista
import com.controleescalas.app.ui.theme.*

/**
 * Painel de Estacionamento
 * Mostra motoristas no estacionamento e indo para o estacionamento
 */
@Composable
fun ParkingPanel(
    motoristasNoEstacionamento: List<Pair<OndaItem, StatusMotorista>>,
    motoristasIndoParaEstacionamento: List<Pair<OndaItem, StatusMotorista>>,
    onCallToVaga: (OndaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (motoristasNoEstacionamento.isEmpty() && motoristasIndoParaEstacionamento.isEmpty()) {
        return
    }
    
    GlassCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = NeonPurple,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "🅿️ Estacionamento",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonPurple,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Motoristas no estacionamento
            if (motoristasNoEstacionamento.isNotEmpty()) {
                Text(
                    "NO ESTACIONAMENTO (${motoristasNoEstacionamento.size}) ✅",
                    style = MaterialTheme.typography.titleSmall,
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                motoristasNoEstacionamento.forEach { (motorista, status) ->
                    ParkingDriverCard(
                        motorista = motorista,
                        status = status,
                        isInParking = true,
                        onCallToVaga = { onCallToVaga(motorista) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // Motoristas indo para estacionamento
            if (motoristasIndoParaEstacionamento.isNotEmpty()) {
                if (motoristasNoEstacionamento.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Text(
                    "INDO PARA ESTACIONAMENTO (${motoristasIndoParaEstacionamento.size}) 🚗",
                    style = MaterialTheme.typography.titleSmall,
                    color = NeonBlue,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                motoristasIndoParaEstacionamento.forEach { (motorista, status) ->
                    ParkingDriverCard(
                        motorista = motorista,
                        status = status,
                        isInParking = false,
                        onCallToVaga = null
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ParkingDriverCard(
    motorista: OndaItem,
    status: StatusMotorista,
    isInParking: Boolean,
    onCallToVaga: (() -> Unit)?
) {
    Surface(
        color = DarkSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isInParking) NeonGreen else NeonBlue,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        motorista.nome,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    if (isInParking) "✅ No estacionamento" else "🚗 A caminho...",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isInParking) NeonGreen else NeonBlue
                )
            }
            
            if (isInParking && onCallToVaga != null) {
                Button(
                    onClick = onCallToVaga,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chamar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
