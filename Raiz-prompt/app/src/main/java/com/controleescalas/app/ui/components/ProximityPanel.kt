package com.controleescalas.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.controleescalas.app.ui.theme.*

/**
 * Painel de Proximidade
 * Mostra motoristas se aproximando do galpão (< 100m)
 */
data class ProximityAlert(
    val motoristaId: String,
    val nome: String,
    val distance: Double,
    val onda: String
)

@Composable
fun ProximityPanel(
    proximityAlerts: List<ProximityAlert>,
    onDriverClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (proximityAlerts.isEmpty()) return
    
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
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = NeonOrange,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "🚗 Motoristas Chegando (${proximityAlerts.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonOrange,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Lista de alertas
            proximityAlerts.forEach { alert ->
                ProximityAlertRow(
                    alert = alert,
                    onClick = { onDriverClick(alert.motoristaId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ProximityAlertRow(
    alert: ProximityAlert,
    onClick: () -> Unit
) {
    Surface(
        color = DarkSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(NeonOrange, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        alert.nome,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        alert.onda,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
            }
            
            Surface(
                color = NeonOrange.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "${alert.distance.toInt()}m",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = NeonOrange,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
