package com.controleescalas.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.components.DisponibilidadeCard
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.NeonButton
import com.controleescalas.app.ui.components.QuinzenaCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DisponibilidadeViewModel
import com.controleescalas.app.ui.viewmodels.DriverViewModel
import com.controleescalas.app.ui.viewmodels.QuinzenaViewModel

data class DriverEscalaInfo(
    val turno: String,
    val nomeOnda: String,
    val horarioPlanejado: String,
    val vagaPlanejada: String,
    val rotaCodigo: String,
    val hasPdf: Boolean,
    val sacas: Int? = null
)

data class DriverStatusInfo(
    val estado: String,
    val mensagem: String,
    val vagaAtual: String? = null,
    val rotaAtual: String? = null,
    val confirmadoEm: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHomeScreen(
    motoristaId: String,
    baseId: String,
    onLogout: () -> Unit = {},
    viewModel: DriverViewModel = viewModel(),
    disponibilidadeViewModel: DisponibilidadeViewModel = viewModel(),
    quinzenaViewModel: QuinzenaViewModel = viewModel()
) {
    val escalaInfo by viewModel.escalaInfo.collectAsState()
    val statusInfo by viewModel.statusInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    // Disponibilidade
    val minhaDisponibilidade by disponibilidadeViewModel.minhaDisponibilidade.collectAsState()
    val disponibilidadeMessage by disponibilidadeViewModel.message.collectAsState()
    
    // Quinzena
    val minhaQuinzena by quinzenaViewModel.minhaQuinzena.collectAsState()

    LaunchedEffect(motoristaId, baseId) {
        viewModel.loadDriverData(motoristaId, baseId)
        disponibilidadeViewModel.carregarMinhaDisponibilidade(baseId, motoristaId)
        quinzenaViewModel.carregarMinhaQuinzena(baseId, motoristaId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Minha Jornada", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = "Sair",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DeepBlue, DarkBackground, DarkBackground)
                    )
                )
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = NeonGreen
                )
            } else {
                    // Usar animações de entrada para cada card
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { 
                            delay(100)
                            visible = true 
                        }

                        // DISPONIBILIDADE
                        AnimatedVisibility(
                            visible = visible,
                            enter = slideInVertically { it / 2 } + fadeIn()
                        ) {
                            minhaDisponibilidade?.let { disp ->
                                DisponibilidadeCard(
                                    data = disponibilidadeViewModel.disponibilidade.value?.data ?: "",
                                    jaRespondeu = disp.disponivel != null,
                                    disponivel = disp.disponivel,
                                    onMarcarDisponivel = {
                                        disponibilidadeViewModel.marcarDisponibilidade(
                                            baseId, motoristaId, true
                                        )
                                    },
                                    onMarcarIndisponivel = {
                                        disponibilidadeViewModel.marcarDisponibilidade(
                                            baseId, motoristaId, false
                                        )
                                    }
                                )
                            }
                        }
                        
                        // QUINZENA
                        AnimatedVisibility(
                            visible = visible,
                            enter = slideInVertically { it / 2 } + fadeIn(initialAlpha = 0f)
                        ) {
                            QuinzenaCard(quinzena = minhaQuinzena)
                        }
                        
                        // STATUS/ESCALA
                        AnimatedVisibility(
                            visible = visible,
                            enter = slideInVertically { it / 2 } + fadeIn()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                if (escalaInfo != null) {
                                    StatusCard(
                                        statusInfo = statusInfo,
                                        onConfirmarChamada = {
                                            viewModel.confirmarChamada(motoristaId, baseId)
                                        },
                                        onConcluirCarregamento = {
                                            viewModel.concluirCarregamento(motoristaId, baseId)
                                        }
                                    )
                                }
                                
                                EscalaCompactCard(
                                    escalaInfo = escalaInfo,
                                    statusInfo = statusInfo
                                )
                            }
                        }
                    }
            }
            
            error?.let { errorMessage ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    containerColor = Color.Red,
                    contentColor = TextWhite
                ) {
                    Text(errorMessage)
                }
            }
            
            disponibilidadeMessage?.let { message ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                ) {
                    Text(message)
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    statusInfo: DriverStatusInfo?,
    onConfirmarChamada: (() -> Unit)? = null,
    onConcluirCarregamento: (() -> Unit)? = null
) {
    val isChamadoParaVaga = statusInfo?.estado == "CARREGANDO"
    val isChamadoParaEstacionamento = statusInfo?.estado == "IR_ESTACIONAMENTO"
    val isChamado = isChamadoParaVaga || isChamadoParaEstacionamento
    val isCarregando = statusInfo?.estado == "CARREGANDO"
    
    val scale by animateFloatAsState(
        targetValue = if (isChamado) 1.05f else 1f,
        animationSpec = tween(300)
    )
    
    // Determinar título e cor baseado no status
    val (titulo, corStatus) = when {
        isChamadoParaVaga -> Pair("CHAMADO PARA VAGA", NeonGreen)
        isChamadoParaEstacionamento -> Pair("CHAMADO PARA ESTACIONAMENTO", NeonPurple)
        statusInfo?.estado == "CONCLUIDO" -> Pair("CONCLUIDO", NeonBlue)
        statusInfo?.estado == "ESTACIONAMENTO" -> Pair("ESTACIONAMENTO", NeonPurple)
        statusInfo?.estado == "CHEGUEI" -> Pair("CHEGUEI", NeonGreen)
        else -> Pair(statusInfo?.estado?.replace("_", " ") ?: "A CAMINHO", TextGray)
    }
    
    // Obter vaga e rota diretamente do statusInfo
    val vaga = statusInfo?.vagaAtual
    val rota = statusInfo?.rotaAtual
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            when {
                isChamadoParaVaga -> {
                    // Exibir em 2 linhas quando chamado para vaga
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CHAMADO PARA",
                            style = MaterialTheme.typography.titleLarge,
                            color = corStatus,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "VAGA",
                            style = MaterialTheme.typography.titleLarge,
                            color = corStatus,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                isChamadoParaEstacionamento -> {
                    // Exibir em 2 linhas quando chamado para estacionamento
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CHAMADO PARA",
                            style = MaterialTheme.typography.titleLarge,
                            color = corStatus,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "ESTACIONAMENTO",
                            style = MaterialTheme.typography.titleLarge,
                            color = corStatus,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    Text(
                        text = titulo,
                        style = when {
                            statusInfo?.estado == "ESTACIONAMENTO" -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.headlineMedium
                        },
                        color = corStatus,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Mensagem específica baseada no status (em 2 linhas quando houver vaga e rota)
            when {
                isChamadoParaVaga && vaga != null && rota != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Vá para VAGA $vaga",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextWhite,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "ROTA $rota",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextWhite,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                isChamadoParaVaga && vaga != null -> {
                    Text(
                        text = "Vá para VAGA $vaga",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextWhite,
                        textAlign = TextAlign.Center
                    )
                }
                isChamadoParaEstacionamento -> {
                    Text(
                        text = "Vá para o estacionamento e aguarde",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextWhite,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    Text(
                        text = statusInfo?.mensagem ?: "Aguardando instruções...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextWhite,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Mostrar vaga apenas quando chamado para vaga (não quando apenas escalado)
            if (isChamadoParaVaga && vaga != null) {
                Surface(
                    color = corStatus.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "VAGA $vaga",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = corStatus,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Botão "Entendi" quando houver chamada e não houver confirmação
            if (isChamado && statusInfo?.confirmadoEm == null && onConfirmarChamada != null) {
                NeonButton(
                    text = "✓ ENTENDI",
                    onClick = onConfirmarChamada,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Mostrar confirmação se já foi confirmado
            if (isChamado && statusInfo?.confirmadoEm != null) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Confirmado",
                        tint = NeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirmado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonGreen
                    )
                }
            }
            
            // Botão "Concluir Carregamento" quando estiver carregando e já confirmado
            if (isCarregando && statusInfo?.confirmadoEm != null && onConcluirCarregamento != null) {
                Spacer(modifier = Modifier.height(8.dp))
                NeonButton(
                    text = "✓ CONCLUIR",
                    onClick = onConcluirCarregamento,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CallCard(statusInfo: DriverStatusInfo?) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = NeonGreen,
                modifier = Modifier.size(48.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "CHAMADA ATIVA",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    statusInfo?.mensagem ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextWhite
                )
            }
        }
    }
}

@Composable
fun EscalaCompactCard(
    escalaInfo: DriverEscalaInfo?,
    statusInfo: DriverStatusInfo? = null
) {
    val isChamadoParaVaga = statusInfo?.estado == "CARREGANDO"
    val isChamadoParaEstacionamento = statusInfo?.estado == "IR_ESTACIONAMENTO"
    
    // Animação de piscar para vaga e rota quando chamado para vaga
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isChamadoParaVaga) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = 0),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "blink_alpha"
    )
    
    // Cor de destaque quando chamado
    val corDestaque = if (isChamadoParaVaga) {
        Color(0xFFFFA500) // Laranja/Amarelo
    } else if (isChamadoParaEstacionamento) {
        NeonPurple
    } else {
        null
    }
    
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Escala de Hoje",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )
                
                if (escalaInfo != null) {
                    Surface(
                        color = NeonBlue.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            escalaInfo.turno,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = NeonBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Badge de estacionamento
                if (isChamadoParaEstacionamento) {
                    Surface(
                        color = NeonPurple.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "ESTACIONAMENTO",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = NeonPurple,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            if (escalaInfo == null) {
                Text(
                    "Você não está escalado hoje.",
                    color = TextGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // Layout reorganizado: Onda/Vaga à esquerda, Horário/Rota à direita (abaixo do turno)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Coluna esquerda: Onda e Vaga
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoItem(
                            label = "Onda",
                            value = escalaInfo.nomeOnda
                        )
                        
                        // Vaga com animação de piscar quando chamado para vaga
                        Box(
                            modifier = Modifier.alpha(if (isChamadoParaVaga) blinkAlpha else 1f)
                        ) {
                            InfoItem(
                                label = "Vaga",
                                value = escalaInfo.vagaPlanejada,
                                corDestaque = corDestaque
                            )
                        }
                    }
                    
                    // Coluna direita: Horário e Rota (alinhados abaixo do turno)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ✅ Layout customizado para Horário: "Hora" alinhado à direita, valor abaixo
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Hora",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (escalaInfo.horarioPlanejado.isBlank()) "Indefinida" else escalaInfo.horarioPlanejado,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                        }
                        
                        // Rota com animação de piscar quando chamado para vaga
                        Box(
                            modifier = Modifier.alpha(if (isChamadoParaVaga) blinkAlpha else 1f)
                        ) {
                            InfoItem(
                                label = "Rota",
                                value = escalaInfo.rotaCodigo,
                                corDestaque = corDestaque
                            )
                        }
                    }
                }
                
                // Mostrar sacas se existir
                escalaInfo.sacas?.let { sacas ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        InfoItem(label = "Sacas", value = "$sacas")
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                
                if (escalaInfo.hasPdf) {
                    NeonButton(
                        text = "Ver Rota (PDF)",
                        onClick = { /* TODO */ },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun InfoItem(
    label: String,
    value: String,
    corDestaque: Color? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextGray
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = corDestaque ?: TextWhite,
            fontWeight = if (corDestaque != null) FontWeight.Bold else FontWeight.Medium
        )
    }
}
