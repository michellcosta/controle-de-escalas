package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.NeonButton
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DisponibilidadeViewModel
import com.controleescalas.app.data.models.DisponibilidadeMotorista
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(
    baseId: String,
    viewModel: DisponibilidadeViewModel = viewModel()
) {
    val disponibilidade by viewModel.disponibilidade.collectAsState()
    val message by viewModel.message.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showInfoDialog by remember { mutableStateOf(false) }
    
    // Garantir que existe disponibilidade ao carregar (cria se não existir)
    // E iniciar observação em tempo real
    LaunchedEffect(baseId) {
        viewModel.garantirDisponibilidadeParaAmanha(baseId)
        viewModel.observarDisponibilidade(baseId) // Observar em tempo real
    }
    
    // Limpar listener quando sair da tela
    DisposableEffect(baseId) {
        onDispose {
            viewModel.pararObservacaoDisponibilidade()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestão de Disponibilidade", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            Icons.Default.Help,
                            contentDescription = "Informações sobre disponibilidade",
                            tint = TextWhite
                        )
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // Estatísticas
            val totalMotoristas = disponibilidade?.motoristas?.size ?: 0
            val totalDisponiveis = disponibilidade?.motoristas?.count { it.disponivel == true } ?: 0
            val totalIndisponiveis = disponibilidade?.motoristas?.count { it.disponivel == false } ?: 0
            val semResposta = disponibilidade?.motoristas?.count { it.disponivel == null } ?: 0
            
            // Card de estatísticas
            if (totalMotoristas > 0) {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalDisponiveis",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Disponíveis",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextGray
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalIndisponiveis",
                                style = MaterialTheme.typography.titleMedium,
                                color = NeonOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Indisponíveis",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextGray
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$semResposta",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextGray,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sem resposta",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextGray
                            )
                        }
                    }
                }
            }
            
            // Abas
            val tabs = listOf("Disponível", "Indisponível", "Sem Resposta")
            Column(modifier = Modifier.weight(1f)) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = DarkSurface,
                    contentColor = TextWhite
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                    // Badge com contagem
                                    val count = when (index) {
                                        0 -> totalDisponiveis
                                        1 -> totalIndisponiveis
                                        else -> semResposta
                                    }
                                    if (count > 0) {
                                        Surface(
                                            color = when (index) {
                                                0 -> NeonGreen
                                                1 -> NeonOrange
                                                else -> TextGray
                                            },
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = "$count",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
                
                // Conteúdo das abas
                Spacer(modifier = Modifier.height(16.dp))
                
                val motoristasFiltrados = when (selectedTabIndex) {
                    0 -> disponibilidade?.motoristas?.filter { it.disponivel == true } ?: emptyList()
                    1 -> disponibilidade?.motoristas?.filter { it.disponivel == false } ?: emptyList()
                    else -> disponibilidade?.motoristas?.filter { it.disponivel == null } ?: emptyList()
                }
                
                if (motoristasFiltrados.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = TextGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = when (selectedTabIndex) {
                                    0 -> "Nenhum motorista disponível ainda"
                                    1 -> "Nenhum motorista indisponível"
                                    else -> "Todos os motoristas já responderam"
                                },
                                color = TextGray,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (selectedTabIndex) {
                                    0 -> "Os motoristas que responderem 'SIM' aparecerão aqui"
                                    1 -> "Os motoristas que responderem 'NÃO' aparecerão aqui"
                                    else -> "Os motoristas que ainda não responderem aparecerão aqui"
                                },
                                color = TextGray.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(motoristasFiltrados) { motorista ->
                            AvailabilityItemRow(motorista)
                        }
                    }
                }
            }
            }
        }

        // Diálogo de informações sobre disponibilidade automática
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = NeonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Disponibilidade Automática",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "A disponibilidade é solicitada automaticamente todos os dias às 00:00 para o dia seguinte. Os motoristas sempre têm a pergunta ativa no app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray
                        )
                        
                        disponibilidade?.let { disp ->
                            Text(
                                text = "Disponibilidade para: ${formatarDataDisponibilidade(disp.data)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Entendi", color = NeonGreen)
                    }
                },
                containerColor = DarkSurface,
                titleContentColor = TextWhite,
                textContentColor = TextGray
            )
        }

        // Feedback
        message?.let { msg ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                 Snackbar(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                ) {
                    Text(msg)
                }
            }
            LaunchedEffect(msg) {
                // Limpar mensagem após delay se necessário, ou deixar ViewModel controlar
            }
        }
    }
}

@Composable
fun AvailabilityItemRow(motorista: DisponibilidadeMotorista) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Nome do motorista (compacto)
                Text(
                    text = motorista.nome.ifEmpty { "Motorista ${motorista.motoristaId.take(8)}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                // Horário da resposta (compacto)
                if (motorista.respondidoEm != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(motorista.respondidoEm)),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray.copy(alpha = 0.8f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Aguardando",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Indicador de status simplificado (apenas ícone)
            when (motorista.disponivel) {
                true -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Disponível",
                        tint = NeonGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                false -> {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Indisponível",
                        tint = NeonOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
                null -> {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Sem resposta",
                        tint = TextGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Formata data de disponibilidade (YYYY-MM-DD) para exibição (DD/MM/YYYY)
 */
fun formatarDataDisponibilidade(data: String): String {
    return try {
        val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfOutput = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val date = sdfInput.parse(data)
        sdfOutput.format(date ?: Date())
    } catch (e: Exception) {
        data
    }
}
