package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.NotificationSettingsManager
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminNotificationSettingsScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Manager de configurações de notificação
    val settingsManager = remember { NotificationSettingsManager(context) }
    
    // Estados das notificações
    val notificacaoMotoristaConcluido by settingsManager.notificacaoMotoristaConcluido.collectAsState(initial = true)
    val notificacaoChamadaMotorista by settingsManager.notificacaoChamadaMotorista.collectAsState(initial = false)
    val notificacaoStatusUpdate by settingsManager.notificacaoStatusUpdate.collectAsState(initial = true)
    val notificacaoEscalaUpdate by settingsManager.notificacaoEscalaUpdate.collectAsState(initial = true)
    val notificacaoGeneric by settingsManager.notificacaoGeneric.collectAsState(initial = true)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notificações", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Voltar", tint = TextWhite)
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ícone e descrição
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = NeonGreen
                )
            }
            
            Text(
                "Configure quais notificações você deseja receber",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            SectionHeader(title = "Notificações do Sistema")
            
            // Notificação: Motorista Concluiu Carregamento
            GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Conclusão de Carregamento",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Receber notificação quando um motorista concluir o carregamento",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    Switch(
                        checked = notificacaoMotoristaConcluido,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.setNotificacaoMotoristaConcluido(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = NeonGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            // Notificação: Chamada de Motorista
            GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Chamada de Motorista",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Receber notificação de chamadas para carregamento",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    Switch(
                        checked = notificacaoChamadaMotorista,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.setNotificacaoChamadaMotorista(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = NeonGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            // Notificação: Atualização de Status
            GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Atualização de Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Receber notificação quando houver atualização de status",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    Switch(
                        checked = notificacaoStatusUpdate,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.setNotificacaoStatusUpdate(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = NeonGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            // Notificação: Atualização de Escala
            GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Atualização de Escala",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Receber notificação quando houver atualização de escala",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    Switch(
                        checked = notificacaoEscalaUpdate,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.setNotificacaoEscalaUpdate(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = NeonGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
            // Notificação: Genéricas
            GlassCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Notificações Genéricas",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Receber outras notificações do sistema",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    Switch(
                        checked = notificacaoGeneric,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                settingsManager.setNotificacaoGeneric(enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = NeonGreen.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }
}
