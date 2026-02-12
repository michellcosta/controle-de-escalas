package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.NeonButton
import com.controleescalas.app.ui.theme.*

@Composable
fun MainScreen(
    onCreateBaseClick: () -> Unit,
    onExistingAccountClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DeepBlue, DarkBackground)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Logo/Título Principal
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CONTROLE DE\nESCALAS",
                    style = MaterialTheme.typography.displaySmall,
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.displaySmall.lineHeight
                )
                
                Text(
                    "Gestão inteligente de carregamento",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Card de Ações
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Bem-vindo",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        "Escolha uma opção para começar:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    NeonButton(
                        text = "CRIAR TRANSPORTADORA",
                        onClick = onCreateBaseClick,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Add
                    )
                    
                    OutlinedButton(
                        onClick = onExistingAccountClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("JÁ TENHO CONTA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
