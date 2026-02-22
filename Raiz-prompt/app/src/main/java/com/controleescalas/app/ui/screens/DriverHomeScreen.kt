package com.controleescalas.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.components.QuinzenaCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DisponibilidadeViewModel
import com.controleescalas.app.ui.viewmodels.DriverViewModel
import com.controleescalas.app.ui.viewmodels.QuinzenaViewModel
import com.controleescalas.app.ui.theme.HorizontalPadding
import com.controleescalas.app.ui.theme.SpacingSmall
import com.controleescalas.app.ui.theme.SpacingMedium
import com.controleescalas.app.ui.theme.SpacingLarge

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
        containerColor = Color.Transparent,
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
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(HorizontalPadding)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SpacingMedium)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = NeonGreen
                        )
                    } else {
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
                                enter = slideInVertically { it / 2 } + fadeIn()
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
                }

                // Dedicated Feedback Box for Snackbars
                Box(modifier = Modifier.fillMaxSize()) {
                    if (error != null) {
                        Snackbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            containerColor = StatusError,
                            contentColor = TextWhite
                        ) {
                            Text(error!!)
                        }
                        LaunchedEffect(error) {
                            delay(3000)
                            viewModel.clearError()
                        }
                    }

                    if (disponibilidadeMessage != null) {
                        Snackbar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ) {
                            Text(disponibilidadeMessage!!)
                        }
                        LaunchedEffect(disponibilidadeMessage) {
                            delay(2000)
                            disponibilidadeViewModel.clearMessage()
                        }
                    }
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
    
    val (titulo, corStatus) = when {
        isChamadoParaVaga -> Pair("CHAMADO PARA VAGA", NeonGreen)
        isChamadoParaEstacionamento -> Pair("CHAMADO PARA ESTACIONAMENTO", NeonPurple)
        statusInfo?.estado == "CONCLUIDO" -> Pair("CONCLUIDO", NeonBlue)
        statusInfo?.estado == "ESTACIONAMENTO" -> Pair("ESTACIONAMENTO", NeonPurple)
        statusInfo?.estado == "CHEGUEI" -> Pair("CHEGUEI", NeonGreen)
        else -> Pair(statusInfo?.estado?.replace("_", " ") ?: "A CAMINHO", TextGray)
    }
    
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "CHAMADO PARA", style = MaterialTheme.typography.titleLarge, color = corStatus, fontWeight = FontWeight.Bold)
                        Text(text = "VAGA", style = MaterialTheme.typography.titleLarge, color = corStatus, fontWeight = FontWeight.Bold)
                    }
                }
                isChamadoParaEstacionamento -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "CHAMADO PARA", style = MaterialTheme.typography.titleLarge, color = corStatus, fontWeight = FontWeight.Bold)
                        Text(text = "ESTACIONAMENTO", style = MaterialTheme.typography.titleLarge, color = corStatus, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Text(text = titulo, style = MaterialTheme.typography.headlineMedium, color = corStatus, fontWeight = FontWeight.Bold)
                }
            }
            
            when {
                isChamadoParaVaga && vaga != null && rota != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Vá para VAGA $vaga", style = MaterialTheme.typography.bodyLarge, color = TextWhite)
                        Text(text = "ROTA $rota", style = MaterialTheme.typography.bodyLarge, color = TextWhite)
                    }
                }
                isChamadoParaVaga && vaga != null -> {
                    Text(text = "Vá para VAGA $vaga", style = MaterialTheme.typography.bodyLarge, color = TextWhite)
                }
                isChamadoParaEstacionamento -> {
                    Text(text = "Vá para o estacionamento e aguarde", style = MaterialTheme.typography.bodyLarge, color = TextWhite)
                }
                else -> {
                    Text(text = statusInfo?.mensagem ?: "Aguardando instruções...", style = MaterialTheme.typography.bodyLarge, color = TextWhite)
                }
            }
            
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
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (isChamado && statusInfo?.confirmadoEm == null && onConfirmarChamada != null) {
                NeonButton(text = "✓ ENTENDI", onClick = onConfirmarChamada, modifier = Modifier.fillMaxWidth())
            }
            
            if (isChamado && statusInfo?.confirmadoEm != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Confirmado", color = NeonGreen)
                }
            }
            
            if (isCarregando && statusInfo?.confirmadoEm != null && onConcluirCarregamento != null) {
                NeonButton(text = "✓ CONCLUIR", onClick = onConcluirCarregamento, modifier = Modifier.fillMaxWidth())
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
    
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isChamadoParaVaga) 0.3f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "blink"
    )
    
    val corDestaque = when {
        isChamadoParaVaga -> Color(0xFFFFA500)
        statusInfo?.estado == "IR_ESTACIONAMENTO" -> NeonPurple
        else -> null
    }
    
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Escala de Hoje", style = MaterialTheme.typography.titleMedium, color = TextWhite)
                if (escalaInfo != null) {
                    Surface(color = NeonBlue.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
                        Text(escalaInfo.turno, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = NeonBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            if (escalaInfo == null) {
                Text("Você não está escalado hoje.", color = TextGray)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoItem(label = "Onda", value = escalaInfo.nomeOnda)
                        Box(modifier = Modifier.alpha(if (isChamadoParaVaga) blinkAlpha else 1f)) {
                            InfoItem(label = "Vaga", value = escalaInfo.vagaPlanejada, corDestaque = corDestaque)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Hora", style = MaterialTheme.typography.bodySmall, color = TextGray)
                            Text(escalaInfo.horarioPlanejado.ifBlank { "Indefinida" }, color = TextWhite, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.alpha(if (isChamadoParaVaga) blinkAlpha else 1f)) {
                            InfoItem(label = "Rota", value = escalaInfo.rotaCodigo, corDestaque = corDestaque)
                        }
                    }
                }
                
                escalaInfo.sacas?.let { sacas ->
                    InfoItem(label = "Sacas", value = "$sacas")
                }
                
                if (escalaInfo.hasPdf) {
                    NeonButton(text = "Ver Rota (PDF)", onClick = { /* TODO */ }, modifier = Modifier.fillMaxWidth())
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
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextGray)
        Text(value, color = corDestaque ?: TextWhite, fontWeight = if (corDestaque != null) FontWeight.Bold else FontWeight.Medium)
    }
}
