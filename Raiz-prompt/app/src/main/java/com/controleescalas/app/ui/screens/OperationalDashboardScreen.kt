package com.controleescalas.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.StatusMotorista
import com.controleescalas.app.ui.components.*
import com.controleescalas.app.ui.components.ConnectionStatusIndicator
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.ui.viewmodels.OperationalViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Valida se a vaga já está sendo usada na mesma onda
 * @param ondas Lista de todas as ondas do turno
 * @param ondaIndex Índice da onda atual
 * @param vaga Vaga a ser validada
 * @param motoristaIdAtual ID do motorista atual (null se for adição, não null se for edição)
 * @return Pair<Boolean, String?> - (é válida, mensagem de erro)
 */
fun validarVagaNaOnda(
    ondas: List<Onda>,
    ondaIndex: Int,
    vaga: String,
    motoristaIdAtual: String? = null
): Pair<Boolean, String?> {
    if (vaga.isBlank()) return Pair(true, null)
    
    val onda = ondas.getOrNull(ondaIndex) ?: return Pair(true, null)
    
    val vagaJaExiste = onda.itens.any { item ->
        item.vaga == vaga && item.motoristaId != motoristaIdAtual
    }
    
    return if (vagaJaExiste) {
        Pair(false, "A vaga $vaga já está sendo usada por outro motorista nesta onda")
    } else {
        Pair(true, null)
    }
}

/**
 * Valida se a rota já está sendo usada em qualquer onda do turno
 * @param ondas Lista de todas as ondas do turno
 * @param rota Rota a ser validada
 * @param motoristaIdAtual ID do motorista atual (null se for adição, não null se for edição)
 * @return Pair<Boolean, String?> - (é válida, mensagem de erro)
 */
fun validarRotaNoTurno(
    ondas: List<Onda>,
    rota: String,
    motoristaIdAtual: String? = null
): Pair<Boolean, String?> {
    if (rota.isBlank()) return Pair(true, null)
    
    val rotaFormatada = rota.trim().uppercase()
    val rotaJaExiste = ondas.flatMap { it.itens }.any { item ->
        item.rota.trim().uppercase() == rotaFormatada && item.motoristaId != motoristaIdAtual
    }
    
    return if (rotaJaExiste) {
        Pair(false, "A rota $rota já está sendo usada por outro motorista neste turno")
    } else {
        Pair(true, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationalDashboardScreen(
    baseId: String,
    onNavigateToAdminPanel: () -> Unit = {},
    onOpenAssistente: (() -> Unit)? = null,
    viewModel: OperationalViewModel = viewModel()
) {
    val turnoAtual by viewModel.turnoAtual.collectAsState()
    val ondas by viewModel.ondas.collectAsState()
    val motoristasStatus by viewModel.motoristasStatus.collectAsState()
    val motoristasDisponiveis by viewModel.motoristasDisponiveis.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showAddDriverDialog by remember { mutableStateOf(false) }
    var showFillDetailsDialog by remember { mutableStateOf(false) }
    var selectedOndaIndex by remember { mutableIntStateOf(-1) }
    var selectedMotoristaForOnda by remember { mutableStateOf<AdminMotoristaCardData?>(null) }
    
    // Verificar se há escala válida para compartilhar (ondas com itens)
    val temEscalaValida = remember(ondas) {
        ondas.isNotEmpty() && ondas.any { it.itens.isNotEmpty() }
    }
    
    // Função para formatar o texto da escala para compartilhar
    fun formatarTextoEscalaParaWhatsApp(ondas: List<Onda>, turno: String): String {
        val dataFormatada = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(java.util.Date())
        val turnoFormatado = if (turno == "AM") "AM" else "PM"
        
        val nomesOndas = listOf("PRIMEIRA", "SEGUNDA", "TERCEIRA", "QUARTA", "QUINTA", "SEXTA", "SÉTIMA", "OITAVA")
        
        return buildString {
            // Cabeçalho
            appendLine("⚠️ PLANEJAMENTO _${turnoFormatado}_ ⚠️")
            appendLine()
            appendLine("🗓️ Data: $dataFormatada")
            appendLine()
            appendLine()
            
            // Iterar pelas ondas
            ondas.forEachIndexed { index, onda ->
                if (onda.itens.isNotEmpty()) {
                    // Nome da onda (PRIMEIRA, SEGUNDA, etc.)
                    val nomeOnda = nomesOndas.getOrElse(index) { "${index + 1}ª" }
                    
                    // Título da onda com horário
                    appendLine("🚨 `$nomeOnda ONDA:`  ${onda.horario}")
                    appendLine()
                    
                    // Ordenar itens por vaga (numérica) antes de formatar
                    val itensOrdenados = onda.itens.sortedBy { item ->
                        item.vaga.toIntOrNull() ?: Int.MAX_VALUE
                    }
                    
                    // Listar cada item
                    itensOrdenados.forEach { item ->
                        val vagaNum = item.vaga.toIntOrNull() ?: 0
                        val vagaFormatada = String.format("%02d", vagaNum)
                        val nomeMotorista = item.nome
                        val rota = item.rota
                        
                        // Construir linha: VAGA XX - *Nome .. Rota*
                        append("VAGA $vagaFormatada - *$nomeMotorista .. $rota*")
                        
                        // Adicionar informações de sacas se houver
                        if (item.sacas != null && item.sacas > 0) {
                            append(".(${item.sacas}.sacas)")
                        }
                        
                        appendLine()
                    }
                    
                    // Quebra de linha dupla entre ondas
                    appendLine()
                    appendLine()
                }
            }
        }
    }
    
    // Função para compartilhar escala via WhatsApp
    fun compartilharEscalaNoWhatsApp() {
        try {
            val textoEscala = formatarTextoEscalaParaWhatsApp(ondas, turnoAtual)
            println("📤 OperationalDashboardScreen: Compartilhando escala no WhatsApp")
            println("📄 Texto gerado:\n$textoEscala")
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, textoEscala)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback genérico se WhatsApp não estiver instalado
                val intentGenerico = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, textoEscala)
                }
                context.startActivity(Intent.createChooser(intentGenerico, "Compartilhar via"))
            }
        } catch (e: Exception) {
            println("❌ Erro ao compartilhar escala: ${e.message}")
            e.printStackTrace()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "Erro ao compartilhar escala: ${e.message}",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadData(baseId)
    }
    
    // Auto-save ao trocar de turno (sem mostrar mensagem)
    LaunchedEffect(turnoAtual) {
        if (turnoAtual.isNotEmpty()) {
            viewModel.saveCurrentEscala(showMessage = false)
        }
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Operações do Dia", color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                                .format(java.util.Date()),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                },
                actions = {
                    onOpenAssistente?.let { onAssistente ->
                        IconButton(onClick = onAssistente) {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = "Assistente",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    // Botão de compartilhar - apenas visível se houver escala válida
                    if (temEscalaValida) {
                        IconButton(onClick = { compartilharEscalaNoWhatsApp() }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Compartilhar escala no WhatsApp",
                                tint = NeonGreen
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.addOnda() },
                containerColor = NeonGreen,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Onda")
            }
        }
    ) { paddingValues ->
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonGreen)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    WavesContent(
                        ondas = ondas,
                        turnoAtual = turnoAtual,
                        motoristasStatus = motoristasStatus,
                        motoristasDisponiveis = motoristasDisponiveis,
                        onTurnoChange = { viewModel.changeTurno(it) },
                        onCallDriver = { motorista -> 
                            viewModel.callDriverToVaga(motorista) 
                        },
                        onCallDriverWithVagaRota = { motorista, vaga, rota ->
                            viewModel.callDriverToVaga(motorista, vaga, rota)
                        },
                        onCallToParking = { viewModel.callDriverToParking(it) },
                        onCompleteLoad = { viewModel.completeDriverLoad(it) },
                        onReset = { viewModel.resetDriverStatus(it) },
                        onMarcarComoConcluido = { viewModel.marcarMotoristaComoConcluido(it) },
                        onResetarStatus = { viewModel.resetarStatusMotorista(it) },
                        onUpdateDriver = { m: OndaItem, v: String, r: String, s: Int? -> 
                            viewModel.updateDriverInOnda(ondas.indexOfFirst { it.itens.contains(m) }, m, v, r, s) 
                        },
                        onUpdateWave = { index, nome, horario, tipo ->
                            viewModel.updateOnda(index, nome, horario, tipo)
                        },
                        onAddDriverToOnda = { index ->
                            selectedOndaIndex = index
                            showAddDriverDialog = true
                        },
                        onRemoveDriver = { ondaIndex, motoristaId ->
                            viewModel.removeMotoristaFromOnda(ondaIndex, motoristaId)
                        },
                        onSaveAndNotifyAll = {
                            viewModel.saveAndNotifyAll()
                        },
                        onRemoveWave = { index ->
                            viewModel.removeOnda(index)
                        }
                    )
                }
            }
            
            // ETAPA 1: Dialog de Seleção de Motorista
            if (showAddDriverDialog) {
                AddDriverDialog(
                    motoristas = motoristasDisponiveis,
                    targetName = ondas.getOrNull(selectedOndaIndex)?.nome ?: "Onda",
                    onDismiss = { 
                        showAddDriverDialog = false
                        selectedMotoristaForOnda = null
                    },
                    onSelect = { motorista ->
                        selectedMotoristaForOnda = motorista
                        showAddDriverDialog = false
                        showFillDetailsDialog = true
                    }
                )
            }
            
            // ETAPA 2: Dialog para preencher Vaga, Rota e Sacas
            if (showFillDetailsDialog && selectedMotoristaForOnda != null) {
                FillDriverDetailsDialog(
                    motorista = selectedMotoristaForOnda!!,
                    targetName = ondas.getOrNull(selectedOndaIndex)?.nome ?: "Onda",
                    ondas = ondas,
                    ondaIndex = selectedOndaIndex,
                    onDismiss = {
                        showFillDetailsDialog = false
                        selectedMotoristaForOnda = null
                    },
                    onConfirm = { vaga, rota, sacas ->
                        viewModel.addMotoristaToOndaWithDetails(
                            ondaIndex = selectedOndaIndex,
                            motorista = selectedMotoristaForOnda!!,
                            vaga = vaga,
                            rota = rota,
                            sacas = sacas
                        )
                        showFillDetailsDialog = false
                        selectedMotoristaForOnda = null
                    }
                )
            }
            
            Box(modifier = Modifier.fillMaxSize()) {
                val currentMessage = message
                val currentError = error
                
                if (currentMessage != null || currentError != null) {
                    FeedbackSnackbar(
                        message = currentMessage,
                        error = currentError,
                        onClear = { viewModel.clearMessages() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                
                // SnackbarHost para mensagens de compartilhamento
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) { snackbarData ->
                    val messageText = snackbarData.visuals.message
                    val isError = messageText.contains("erro", ignoreCase = true)
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor = if (isError) Color(0xFFEF4444) else NeonGreen,
                        contentColor = if (isError) TextWhite else Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun WavesContent(
    ondas: List<Onda>,
    turnoAtual: String,
    motoristasStatus: Map<String, StatusMotorista>,
    motoristasDisponiveis: List<AdminMotoristaCardData>,
    onTurnoChange: (String) -> Unit, // Novo parâmetro
    onCallDriver: (OndaItem) -> Unit,
    onCallDriverWithVagaRota: (OndaItem, String, String) -> Unit,
    onCallToParking: (OndaItem) -> Unit,
    onCompleteLoad: (OndaItem) -> Unit,
    onReset: (OndaItem) -> Unit,
    onMarcarComoConcluido: (OndaItem) -> Unit,
    onResetarStatus: (OndaItem) -> Unit,
    onUpdateDriver: (OndaItem, String, String, Int?) -> Unit,
    onUpdateWave: (Int, String, String, String) -> Unit,
    onAddDriverToOnda: (Int) -> Unit,
    onRemoveDriver: (Int, String) -> Unit,
    onSaveAndNotifyAll: () -> Unit,
    onRemoveWave: (Int) -> Unit
) {
    val (ondasNormais, ondasDedicadas) = ondas.partition { it.tipo != "DEDICADO" }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp)
    ) {
        item {
            // Seletor de Turno movido para dentro do LazyColumn
            TurnoSelector(
                turnoAtual = turnoAtual,
                onTurnoChange = onTurnoChange,
                ondasCountAM = 0,
                ondasCountPM = 0
            )
        }

        item {
            PatioCard()
        }

        itemsIndexed(ondasNormais) { idx, onda ->
            val originalIndex = ondas.indexOf(onda)
            WaveOperationCard(
                ondaIndex = originalIndex,
                turnoAtual = turnoAtual,
                onda = onda,
                ondas = ondas,
                motoristasStatus = motoristasStatus,
                motoristasDisponiveis = motoristasDisponiveis,
                onCallDriver = onCallDriver,
                onCallDriverWithVagaRota = onCallDriverWithVagaRota,
                onCallToParking = onCallToParking,
                onCompleteLoad = onCompleteLoad,
                onReset = onReset,
                onMarcarComoConcluido = onMarcarComoConcluido,
                onResetarStatus = onResetarStatus,
                onUpdateDriver = { m, v, r, s -> 
                    onUpdateDriver(m, v, r, s) 
                },
                onUpdateWave = { nome, horario, tipo ->
                    onUpdateWave(originalIndex, nome, horario, tipo)
                },
                onAddDriver = { onAddDriverToOnda(originalIndex) },
                onRemoveDriver = { motoristaId -> onRemoveDriver(originalIndex, motoristaId) },
                onRemoveWave = { onRemoveWave(originalIndex) }
            )
        }
        
        itemsIndexed(ondasDedicadas) { idx, onda ->
            val originalIndex = ondas.indexOf(onda)
            WaveOperationCard(
                ondaIndex = originalIndex,
                turnoAtual = turnoAtual,
                onda = onda,
                ondas = ondas,
                motoristasStatus = motoristasStatus,
                motoristasDisponiveis = motoristasDisponiveis,
                onCallDriver = onCallDriver,
                onCallDriverWithVagaRota = onCallDriverWithVagaRota,
                onCallToParking = onCallToParking,
                onCompleteLoad = onCompleteLoad,
                onReset = onReset,
                onMarcarComoConcluido = onMarcarComoConcluido,
                onResetarStatus = onResetarStatus,
                onUpdateDriver = { m, v, r, s -> 
                    onUpdateDriver(m, v, r, s) 
                },
                onUpdateWave = { nome, horario, tipo ->
                    onUpdateWave(originalIndex, nome, horario, tipo)
                },
                onAddDriver = { onAddDriverToOnda(originalIndex) },
                onRemoveDriver = { motoristaId -> onRemoveDriver(originalIndex, motoristaId) },
                onRemoveWave = { onRemoveWave(originalIndex) }
            )
        }
        
        if (ondas.isEmpty()) {
            item {
                EmptyStateMessage("Nenhuma onda criada.\nClique em + para adicionar.")
            }
        }
        
        // Botão NOTIFICAR TODOS (aparece se houver motoristas escalados)
        val totalMotoristas = ondas.sumOf { it.itens.size }
        if (totalMotoristas > 0) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onSaveAndNotifyAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            "NOTIFICAR TODOS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "$totalMotoristas motoristas escalados",
                            fontSize = 12.sp,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun ParkingContent(
    ondas: List<Onda>,
    motoristasStatus: Map<String, StatusMotorista>,
    onCallToVaga: (OndaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Painel de Proximidade
        item {
            val proximityAlerts = emptyList<ProximityAlert>()
            ProximityPanel(
                proximityAlerts = proximityAlerts,
                onDriverClick = { /* TODO */ }
            )
        }
        
        // Painel de Estacionamento
        item {
            val motoristasNoEstacionamento = ondas.flatMap { onda ->
                onda.itens.mapNotNull { motorista ->
                    val status = motoristasStatus[motorista.motoristaId]
                    if (status?.estado == "ESTACIONAMENTO") Pair(motorista, status) else null
                }
            }
            
            val motoristasIndoParaEstacionamento = ondas.flatMap { onda ->
                onda.itens.mapNotNull { motorista ->
                    val status = motoristasStatus[motorista.motoristaId]
                    if (status?.estado == "IR_ESTACIONAMENTO") Pair(motorista, status) else null
                }
            }
            
            ParkingPanel(
                motoristasNoEstacionamento = motoristasNoEstacionamento,
                motoristasIndoParaEstacionamento = motoristasIndoParaEstacionamento,
                onCallToVaga = onCallToVaga
            )
        }
    }
}

/**
 * Modal para preencher Vaga, Rota e Sacas antes de adicionar motorista à onda
 */
@Composable
fun FillDriverDetailsDialog(
    motorista: AdminMotoristaCardData,
    targetName: String,
    ondas: List<Onda>,
    ondaIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (vaga: String, rota: String, sacas: Int?) -> Unit
) {
    var vaga by remember { mutableStateOf("") }
    var rota by remember { mutableStateOf("") }
    var sacas by remember { mutableStateOf("") }
    var erroVaga by remember { mutableStateOf<String?>(null) }
    var erroRota by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Column {
                Text(
                    "Adicionar ${motorista.nome} à $targetName",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = when(motorista.modalidade) {
                        "DEDICADO" -> NeonPurple.copy(alpha = 0.2f)
                        "UTILITARIO" -> Color(0xFF00BCD4).copy(alpha = 0.2f)
                        "PASSEIO" -> NeonOrange.copy(alpha = 0.2f)
                        else -> NeonBlue.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = motorista.modalidade,
                        style = MaterialTheme.typography.labelSmall,
                        color = when(motorista.modalidade) {
                            "DEDICADO" -> NeonPurple
                            "UTILITARIO" -> Color(0xFF00BCD4)
                            "PASSEIO" -> NeonOrange
                            else -> NeonBlue
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Campo Vaga (obrigatório com formatação automática ao perder foco)
                val vagaInteractionSource = remember { MutableInteractionSource() }
                val vagaFocused by vagaInteractionSource.collectIsFocusedAsState()
                
                // Aplicar formatação quando perde o foco
                LaunchedEffect(vagaFocused) {
                    if (!vagaFocused && vaga.isNotBlank()) {
                        val vagaFormatada = formatarVaga(vaga)
                        if (vagaFormatada != vaga) {
                            vaga = vagaFormatada
                        }
                    }
                }
                
                OutlinedTextField(
                    value = if (vagaFocused) vaga else formatarVaga(vaga),
                    onValueChange = { newValue ->
                        // Permitir apenas números
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            vaga = newValue
                            // Validar vaga em tempo real
                            val (valida, mensagem) = validarVagaNaOnda(ondas, ondaIndex, newValue)
                            erroVaga = mensagem
                        }
                    },
                    interactionSource = vagaInteractionSource,
                    label = { Text("Vaga *", color = TextGray) },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, null, tint = NeonGreen)
                    },
                    placeholder = { Text("Ex: 01", color = TextGray.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = erroVaga != null,
                    supportingText = erroVaga?.let { { Text(it, color = Color(0xFFEF4444)) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = if (erroVaga != null) Color(0xFFEF4444) else NeonGreen,
                        unfocusedBorderColor = if (erroVaga != null) Color(0xFFEF4444) else TextGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Campo Rota (obrigatório com formatação automática ao perder foco)
                val rotaInteractionSource = remember { MutableInteractionSource() }
                val rotaFocused by rotaInteractionSource.collectIsFocusedAsState()
                
                // Aplicar formatação e validação quando perde o foco
                LaunchedEffect(rotaFocused) {
                    if (!rotaFocused && rota.isNotBlank()) {
                        val rotaFormatada = formatarRota(rota)
                        rota = rotaFormatada
                        // Validar rota após formatação
                        val (valida, mensagem) = validarRotaNoTurno(ondas, rotaFormatada)
                        erroRota = mensagem
                    }
                }
                
                OutlinedTextField(
                    value = if (rotaFocused) rota else formatarRota(rota),
                    onValueChange = { newValue ->
                        rota = newValue // Permite digitar livremente enquanto focado
                        // Validar rota em tempo real (após formatação)
                        if (!rotaFocused && newValue.isNotBlank()) {
                            val rotaFormatada = formatarRota(newValue)
                            val (valida, mensagem) = validarRotaNoTurno(ondas, rotaFormatada)
                            erroRota = mensagem
                        }
                    },
                    interactionSource = rotaInteractionSource,
                    label = { Text("Rota *", color = TextGray) },
                    leadingIcon = {
                        Icon(Icons.Default.Place, null, tint = NeonBlue)
                    },
                    placeholder = { Text("Ex: T-15", color = TextGray.copy(alpha = 0.5f)) },
                    isError = erroRota != null,
                    supportingText = erroRota?.let { { Text(it, color = Color(0xFFEF4444)) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = if (erroRota != null) Color(0xFFEF4444) else NeonBlue,
                        unfocusedBorderColor = if (erroRota != null) Color(0xFFEF4444) else TextGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Campo Sacas (opcional)
                OutlinedTextField(
                    value = sacas,
                    onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) sacas = it },
                    label = { Text("Sacas (opcional)", color = TextGray) },
                    leadingIcon = {
                        Icon(Icons.Default.ShoppingCart, null, tint = NeonOrange)
                    },
                    placeholder = { Text("Ex: 12", color = TextGray.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = NeonOrange,
                        unfocusedBorderColor = TextGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    "* Campos obrigatórios",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validar antes de salvar
                    val vagaFormatada = formatarVaga(vaga)
                    val rotaFormatada = formatarRota(rota)
                    val (vagaValida, mensagemVaga) = validarVagaNaOnda(ondas, ondaIndex, vagaFormatada)
                    val (rotaValida, mensagemRota) = validarRotaNoTurno(ondas, rotaFormatada)
                    
                    erroVaga = mensagemVaga
                    erroRota = mensagemRota
                    
                    if (vagaFormatada.isNotBlank() && rota.isNotBlank() && vagaValida && rotaValida) {
                        val sacasInt = sacas.toIntOrNull()
                        onConfirm(vagaFormatada, rotaFormatada, sacasInt)
                    }
                },
                enabled = vaga.isNotBlank() && rota.isNotBlank() && erroVaga == null && erroRota == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Color.Black,
                    disabledContainerColor = TextGray.copy(alpha = 0.3f)
                )
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADICIONAR", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = TextGray)
            }
        }
    )
}

@Composable
fun AddDriverDialog(
    motoristas: List<AdminMotoristaCardData>,
    targetName: String,
    onDismiss: () -> Unit,
    onSelect: (AdminMotoristaCardData) -> Unit
) {
    var selectedModalidade by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showInfoDialog by remember { mutableStateOf(false) }
    
    // ✅ NOVO: Filtrar admins e ordenar alfabeticamente
    val motoristasFiltrados = remember(motoristas) {
        motoristas
            .filter { it.papel != "admin" } // Não mostrar admins
            .sortedBy { it.nome.uppercase() } // Ordenar alfabeticamente
    }
    
    // ✅ NOVO: Filtro combinado (pesquisa + modalidade)
    val filteredMotoristas = remember(motoristasFiltrados, searchQuery, selectedModalidade) {
        motoristasFiltrados.filter { motorista ->
            val matchesSearch = searchQuery.isBlank() || 
                motorista.nome.lowercase().contains(searchQuery.lowercase().trim()) ||
                motorista.telefone.contains(searchQuery.trim())
            
            // Se "Todos" estiver selecionado (selectedModalidade == null), mostrar todos
            val matchesModalidade = selectedModalidade == null || 
                motorista.modalidade == selectedModalidade
            
            matchesSearch && matchesModalidade
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Adicionar Motorista à $targetName", 
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Informações",
                        tint = NeonBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Campo de pesquisa (sem placeholder para ser mais compacto)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Pesquisar",
                            tint = NeonGreen
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = TextGray,
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = TextGray
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                // Filtro de Modalidade (organizado com opção "Todos")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Opção "Todos" primeiro
                    FilterChip(
                        selected = selectedModalidade == null,
                        onClick = { selectedModalidade = null },
                        label = { Text("TODOS") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen,
                            selectedLabelColor = Color.Black
                        )
                    )
                    
                    // Modalidades organizadas
                    listOf("FROTA", "PASSEIO", "DEDICADO", "UTILITARIO").forEach { modalidade ->
                        FilterChip(
                            selected = selectedModalidade == modalidade,
                            onClick = { 
                                selectedModalidade = if (selectedModalidade == modalidade) null else modalidade 
                            },
                            label = { Text(modalidade) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when(modalidade) {
                                    "DEDICADO" -> NeonPurple
                                    "UTILITARIO" -> NeonBlue
                                    "PASSEIO" -> NeonOrange
                                    else -> NeonGreen
                                },
                                selectedLabelColor = if (modalidade == "DEDICADO") TextWhite else Color.Black
                            )
                        )
                    }
                }
                
                if (filteredMotoristas.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhum motorista disponível", color = TextGray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredMotoristas) { motorista ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                onClick = { onSelect(motorista) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Person, null, tint = NeonGreen)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(motorista.nome, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                        Text(motorista.telefone, color = TextGray, style = MaterialTheme.typography.bodySmall)
                                        // Badge Modalidade
                                        Surface(
                                            color = when(motorista.modalidade) {
                                                "DEDICADO" -> Color(0xFFE65100)
                                                "UTILITARIO" -> Color(0xFF00ACC1)
                                                "PASSEIO" -> Color(0xFF7CB342)
                                                else -> Color(0xFF3949AB)
                                            },
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text(
                                                text = motorista.modalidade,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("FECHAR", color = NeonGreen) }
        }
    )
    
    // Diálogo de informações
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = DarkSurface,
            title = { 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = NeonBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Como usar", color = TextWhite)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "• Use o campo de pesquisa para buscar por nome ou telefone",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Selecione uma modalidade para filtrar (FROTA, PASSEIO, DEDICADO, UTILITÁRIO)",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Use 'TODOS' para ver todos os motoristas",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Clique em um motorista para adicioná-lo à onda",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• Apenas motoristas e auxiliares são exibidos (admins não aparecem)",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("ENTENDI", color = Color.Black)
                }
            }
        )
    }
}

@Composable
fun FeedbackSnackbar(
    message: String?, 
    error: String?, 
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        message?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = NeonGreen,
                contentColor = Color.Black
            ) { Text(msg) }
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2000)
                onClear()
            }
        }
        error?.let { err ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                containerColor = StatusError,
                contentColor = TextWhite
            ) { Text(err) }
            LaunchedEffect(err) {
                kotlinx.coroutines.delay(3000)
                onClear()
            }
        }
    }
}

@Composable
fun EmptyStateMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = TextGray,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TurnoSelector(
    turnoAtual: String,
    onTurnoChange: (String) -> Unit,
    ondasCountAM: Int,
    ondasCountPM: Int
) {
    val isDarkMode = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onTurnoChange("AM") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (turnoAtual == "AM") (if (isDarkMode) NeonGreen else LightSurfaceVariant) else if (isDarkMode) DarkSurface else LightSurface,
                contentColor = if (turnoAtual == "AM") (if (isDarkMode) Color.Black else TextBlack) else if (isDarkMode) TextGray else TextGrayLightMode
            )
        ) {
            Text("TURNO AM", fontWeight = FontWeight.Bold)
        }
        
        Button(
            onClick = { onTurnoChange("PM") },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (turnoAtual == "PM") (if (isDarkMode) NeonBlue else NeonBlueContrast) else if (isDarkMode) DarkSurface else LightSurface,
                contentColor = if (turnoAtual == "PM") (if (isDarkMode) Color.Black else Color.White) else if (isDarkMode) TextGray else TextGrayLightMode
            )
        ) {
            Text("TURNO PM", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WaveOperationCard(
    ondaIndex: Int,
    turnoAtual: String,
    onda: Onda,
    ondas: List<Onda>,
    motoristasStatus: Map<String, StatusMotorista>,
    motoristasDisponiveis: List<AdminMotoristaCardData>,
    onCallDriver: (OndaItem) -> Unit,
    onCallDriverWithVagaRota: (OndaItem, String, String) -> Unit,
    onCallToParking: (OndaItem) -> Unit,
    onCompleteLoad: (OndaItem) -> Unit,
    onReset: (OndaItem) -> Unit,
    onMarcarComoConcluido: (OndaItem) -> Unit,
    onResetarStatus: (OndaItem) -> Unit,
    onUpdateDriver: (OndaItem, String, String, Int?) -> Unit,
    onUpdateWave: (String, String, String) -> Unit,
    onAddDriver: () -> Unit,
    onRemoveDriver: (String) -> Unit,
    onRemoveWave: (Int) -> Unit
) {
    var showEditWaveDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteWaveDialog by remember { mutableStateOf(false) }
    var motoristaPendingRemoval by remember { mutableStateOf<String?>(null) }
    // ✅ Estado persistido: mantém expansão/colapso mesmo após navegação
    var isExpanded by rememberSaveable(
        key = "wave_expanded_${turnoAtual}_${ondaIndex}"
    ) { mutableStateOf(true) }
    
    // ✅ Flag para rastrear se já foi auto-compactado (evita re-colapsar ao voltar)
    var jaFoiAutoCompactado by rememberSaveable(
        key = "wave_auto_compactado_${turnoAtual}_${ondaIndex}"
    ) { mutableStateOf(false) }
    
    // Verificar se todos os motoristas estão concluídos
    val todosConcluidos = remember(onda.itens, motoristasStatus) {
        onda.itens.isNotEmpty() && onda.itens.all { motorista ->
            val status = motoristasStatus[motorista.motoristaId]
            status?.estado == "CONCLUIDO"
        }
    }
    
    // ✅ Flag para rastrear se o usuário expandiu manualmente (permite expansão manual mesmo após auto-compactação)
    var expansaoManual by rememberSaveable(
        key = "wave_expansao_manual_${turnoAtual}_${ondaIndex}"
    ) { mutableStateOf(false) }
    
    // ✅ CORREÇÃO IMEDIATA: Se todos estão concluídos e o card está fechado, marcar como auto-compactado
    // Isso garante que o card permanece fechado mesmo após navegação
    if (todosConcluidos && !isExpanded && !jaFoiAutoCompactado) {
        Log.d("WaveCard", "🔧 CORREÇÃO IMEDIATA: Todos concluídos e card está fechado. Marcando como auto-compactado.")
        jaFoiAutoCompactado = true
    }
    
    // ✅ CORREÇÃO IMEDIATA PRINCIPAL: Se todos estão concluídos e o card está expandido,
    // mas NÃO foi expansão manual, então deve estar fechado (foi auto-compactado anteriormente)
    // Isso resolve o problema quando o rememberSaveable é resetado
    if (todosConcluidos && isExpanded && !expansaoManual) {
        Log.d("WaveCard", "🔧 CORREÇÃO IMEDIATA PRINCIPAL: Todos concluídos e card está expandido, mas não foi expansão manual. Forçando fechamento e marcando como auto-compactado.")
        isExpanded = false
        jaFoiAutoCompactado = true
    }
    
    // ✅ CORREÇÃO IMEDIATA: Se todos estão concluídos e já foi auto-compactado, garantir que está fechado
    // Isso evita que o card abra quando o rememberSaveable é resetado
    if (todosConcluidos && jaFoiAutoCompactado && isExpanded && !expansaoManual) {
        Log.d("WaveCard", "🔧 CORREÇÃO IMEDIATA: Todos concluídos e já foi auto-compactado, mas card está expandido. Forçando fechamento.")
        isExpanded = false
    }
    
    // ✅ CORREÇÃO PRINCIPAL: Usar LaunchedEffect para verificar após a composição inicial
    // Isso garante que o expansaoManual foi restaurado pelo rememberSaveable antes de fazer qualquer correção
    LaunchedEffect(Unit) {
        // Aguardar um frame para garantir que o rememberSaveable foi restaurado
        kotlinx.coroutines.delay(1)
        
        // ✅ DETECÇÃO DE EXPANSÃO MANUAL: Se isExpanded=true foi salvo pelo rememberSaveable
        // e todos estão concluídos e NÃO foi auto-compactado, então foi expansão manual
        // Isso detecta quando o usuário expandiu manualmente e o estado foi salvo
        if (todosConcluidos && isExpanded && !jaFoiAutoCompactado && !expansaoManual) {
            // Se o card está expandido e foi salvo como expandido, assumir que foi expansão manual
            Log.d("WaveCard", "🔍 DETECÇÃO: Card está expandido (salvo pelo rememberSaveable) e todos estão concluídos. Assumindo expansão manual.")
            expansaoManual = true // Marcar como expansão manual para evitar colapso
            return@LaunchedEffect // Não executar a correção abaixo
        }
        
        // Se todos estão concluídos e já foi auto-compactado, garantir que está fechado
        if (todosConcluidos && jaFoiAutoCompactado && isExpanded && !expansaoManual) {
            Log.d("WaveCard", "🔧 CORREÇÃO (LaunchedEffect): Todos concluídos e já foi auto-compactado, mas card está expandido. Forçando fechamento.")
            isExpanded = false
        }
    }
    
    // ✅ LOG: Estado inicial ao criar/restaurado o card (com chaves para debug)
    LaunchedEffect(Unit) {
        Log.d("WaveCard", "🔄 Card criado/restaurado - Onda: ${onda.nome}, Turno: $turnoAtual, Index: $ondaIndex")
        Log.d("WaveCard", "   Chave isExpanded: wave_expanded_${turnoAtual}_${ondaIndex}")
        Log.d("WaveCard", "   Chave jaFoiAutoCompactado: wave_auto_compactado_${turnoAtual}_${ondaIndex}")
        Log.d("WaveCard", "   Chave expansaoManual: wave_expansao_manual_${turnoAtual}_${ondaIndex}")
        Log.d("WaveCard", "   Estado: isExpanded=$isExpanded, jaFoiAutoCompactado=$jaFoiAutoCompactado, expansaoManual=$expansaoManual, todosConcluidos=$todosConcluidos")
    }
    
    // Auto-compactar APENAS UMA VEZ quando todos ficarem concluídos (com delay de 2 segundos)
    // MAS: não auto-compactar se foi expansão manual ou se já foi auto-compactado
    LaunchedEffect(todosConcluidos, isExpanded) {
        // Só executa se todos estão concluídos, card está expandido, não foi auto-compactado ainda, e não foi expansão manual
        if (todosConcluidos && isExpanded && !jaFoiAutoCompactado && !expansaoManual) {
            Log.d("WaveCard", "⚡ LaunchedEffect: Auto-compactando em 2 segundos...")
            kotlinx.coroutines.delay(2000) // Delay de 2 segundos
            Log.d("WaveCard", "✅ Auto-compactando agora! isExpanded: $isExpanded -> false")
            isExpanded = false
            jaFoiAutoCompactado = true // Marca que já foi auto-compactado
            Log.d("WaveCard", "   Estado após auto-compactação: isExpanded: $isExpanded, jaFoiAutoCompactado: $jaFoiAutoCompactado")
        }
    }
    
    // ✅ LOG: Quando o estado isExpanded muda manualmente
    LaunchedEffect(isExpanded) {
        Log.d("WaveCard", "📝 Estado isExpanded mudou para: $isExpanded (Onda: ${onda.nome}, Turno: $turnoAtual)")
    }
    
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header da Onda (clicável para expandir/colapsar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        val novoEstado = !isExpanded
                        isExpanded = novoEstado
                        // Se o usuário expandir manualmente, marcar como expansão manual
                        if (novoEstado) {
                            expansaoManual = true
                            Log.d("WaveCard", "👆 Expansão manual detectada - Onda: ${onda.nome}, expansaoManual=true")
                        } else {
                            // Se o usuário colapsar manualmente, não resetar expansaoManual
                            // (mantém o estado para que possa expandir novamente se quiser)
                            Log.d("WaveCard", "👆 Colapso manual detectado - Onda: ${onda.nome}, mantendo expansaoManual=$expansaoManual")
                        }
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ícone de expandir/colapsar
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                        tint = TextGray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = onda.nome,
                                style = MaterialTheme.typography.titleLarge,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = onda.horario,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // Badge de Tipo (Dedicado, Frota, Passeio, etc)
                            if (onda.tipo != "NORMAL") {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = when (onda.tipo.uppercase()) {
                                        "DEDICADO" -> NeonPurple.copy(alpha = 0.2f)
                                        "FROTA" -> NeonBlue.copy(alpha = 0.2f)
                                        "PASSEIO" -> NeonOrange.copy(alpha = 0.2f)
                                        else -> TextGray.copy(alpha = 0.2f)
                                    },
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = onda.tipo.uppercase(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (onda.tipo.uppercase()) {
                                            "DEDICADO" -> NeonPurple
                                            "FROTA" -> NeonBlue
                                            "PASSEIO" -> NeonOrange
                                            else -> TextGray
                                        },
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        // Contagem de motoristas quando compactado
                        if (!isExpanded && onda.itens.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            // Calcular quantos motoristas estão concluídos
                            val totalMotoristas = onda.itens.size
                            val concluidos = onda.itens.count { motorista ->
                                val status = motoristasStatus[motorista.motoristaId]
                                status?.estado == "CONCLUIDO"
                            }
                            val todosConcluidos = concluidos == totalMotoristas && totalMotoristas > 0
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (todosConcluidos) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Todos concluídos",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = if (todosConcluidos) {
                                        "✓ $concluidos/$totalMotoristas concluídos"
                                    } else {
                                        "$concluidos/$totalMotoristas concluídos"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (todosConcluidos) Color(0xFF10B981) else TextGray
                                )
                            }
                        }
                    }
                }
                
                Row {
                    // Botão de editar onda
                    IconButton(
                        onClick = { showEditWaveDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar Onda",
                            tint = TextGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Botão de excluir onda
                    IconButton(
                        onClick = { showConfirmDeleteWaveDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir Onda",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Botão de adicionar motorista
                    IconButton(
                        onClick = onAddDriver,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Adicionar Motorista",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            // Lista de Motoristas (animada)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        thickness = 1.dp,
                        color = CardBorder
                    )
                    
                    if (onda.itens.isEmpty()) {
                        Text(
                            text = "Nenhum motorista nesta onda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        // Motoristas já vêm ordenados por modalidade do ViewModel
                        // Aqui apenas exibimos na ordem que chegaram
                        var showActionsDialog by remember { mutableStateOf(false) }
                        var selectedMotoristaForActions by remember { mutableStateOf<OndaItem?>(null) }
                        var showEditDialog by remember { mutableStateOf(false) }
                        
                        onda.itens.forEach { motorista ->
                            val status = motoristasStatus[motorista.motoristaId]
                            
                            DriverOperationRow(
                                motorista = motorista,
                                status = status,
                                onCardClick = {
                                    selectedMotoristaForActions = motorista
                                    showActionsDialog = true
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // Modal de ações (chamar para vaga/estacionamento + editar/excluir)
                        if (showActionsDialog && selectedMotoristaForActions != null) {
                            val ondaAtualIndexParaActions = ondas.indexOfFirst { it.itens.contains(selectedMotoristaForActions!!) }
                            if (ondaAtualIndexParaActions >= 0) {
                                // Buscar telefone do motorista (com trim para evitar problemas de espaços)
                                val motoristaIdBuscado = selectedMotoristaForActions!!.motoristaId.trim()
                                val telefoneMotorista = motoristasDisponiveis
                                    .firstOrNull { it.id.trim() == motoristaIdBuscado }
                                    ?.telefone
                                    ?.takeIf { it.isNotBlank() } // Garantir que o telefone não está vazio
                                
                                DriverActionsDialog(
                                    motorista = selectedMotoristaForActions!!,
                                    ondas = ondas,
                                    ondaIndex = ondaAtualIndexParaActions,
                                    telefone = telefoneMotorista,
                                    onDismiss = {
                                        showActionsDialog = false
                                        selectedMotoristaForActions = null
                                    },
                                onSaveChanges = { vaga, rota, sacas ->
                                    // Salvar as alterações dos campos
                                    onUpdateDriver(selectedMotoristaForActions!!, vaga, rota, sacas)
                                },
                                onChamarParaVaga = { vaga, rota ->
                                    // Atualizar vaga e rota se mudaram
                                    if (vaga != selectedMotoristaForActions!!.vaga || rota != selectedMotoristaForActions!!.rota) {
                                        onUpdateDriver(selectedMotoristaForActions!!, vaga, rota, selectedMotoristaForActions!!.sacas)
                                    }
                                    // Chamar motorista para vaga com vaga e rota atualizadas
                                    onCallDriverWithVagaRota(selectedMotoristaForActions!!, vaga, rota)
                                },
                                onCallDriverWithVagaRota = onCallDriverWithVagaRota,
                                onChamarParaEstacionamento = {
                                    onCallToParking(selectedMotoristaForActions!!)
                                },
                                onMarcarComoConcluido = {
                                    onMarcarComoConcluido(selectedMotoristaForActions!!)
                                },
                                onResetarStatus = {
                                    onResetarStatus(selectedMotoristaForActions!!)
                                },
                                onEdit = {
                                    showEditDialog = true
                                },
                                onRemove = {
                                    // Chamar diretamente onRemoveDriver sem abrir segundo dialog
                                    // O primeiro dialog já foi confirmado dentro do DriverActionsDialog
                                    onRemoveDriver(selectedMotoristaForActions!!.motoristaId)
                                }
                            )
                            }
                        }
                        
                        // Dialog de edição (aberto pelo modal de ações)
                        if (showEditDialog && selectedMotoristaForActions != null) {
                            val ondaAtualIndex = ondas.indexOfFirst { it.itens.contains(selectedMotoristaForActions!!) }
                            if (ondaAtualIndex >= 0) {
                                EditDriverDialog(
                                    motorista = selectedMotoristaForActions!!,
                                    ondas = ondas,
                                    ondaIndex = ondaAtualIndex,
                                    onDismiss = { showEditDialog = false },
                                    onSave = { novaVaga, novaRota, novasSacas ->
                                        onUpdateDriver(selectedMotoristaForActions!!, novaVaga, novaRota, novasSacas)
                                        showEditDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Dialog de edição de onda
    if (showEditWaveDialog) {
        EditWaveDialog(
            onda = onda,
            onDismiss = { showEditWaveDialog = false },
            onSave = { nome, horario, tipo ->
                onUpdateWave(nome, horario, tipo)
                showEditWaveDialog = false
            }
        )
    }
    
    // Dialog de confirmação de remoção
    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Remover Motorista", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Tem certeza que deseja remover este motorista da onda?",
                    color = TextGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        motoristaPendingRemoval?.let { onRemoveDriver(it) }
                        showConfirmDeleteDialog = false
                        motoristaPendingRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Text("Remover", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmDeleteDialog = false
                    motoristaPendingRemoval = null
                }) {
                    Text("Cancelar", color = TextGray)
                }
            }
        )
    }
    
    // Dialog de confirmação de exclusão de onda
    if (showConfirmDeleteWaveDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteWaveDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Excluir Onda", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Tem certeza que deseja excluir a onda \"${onda.nome}\"?\n\nEsta ação não pode ser desfeita.",
                    color = TextGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveWave(ondaIndex)
                        showConfirmDeleteWaveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Text("EXCLUIR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteWaveDialog = false }) {
                    Text("CANCELAR", color = TextGray)
                }
            }
        )
    }
}

@Composable
fun DriverOperationRow(
    motorista: OndaItem,
    status: StatusMotorista?,
    onCardClick: () -> Unit
) {
    val estadoAtual = status?.estado?.takeIf { it.isNotBlank() } ?: "A_CAMINHO"
    val isConcluido = estadoAtual == "CONCLUIDO"
    
    val statusColor = when (estadoAtual) {
        "CARREGANDO" -> NeonGreen
        "CONCLUIDO" -> Color(0xFF10B981)
        "A_CAMINHO" -> NeonBlue
        "A CAMINHO" -> NeonBlue
        "AGUARDANDO" -> NeonBlue  // Status legado (compatibilidade)
        "CHEGUEI" -> NeonGreen
        "ESTACIONAMENTO" -> NeonPurple
        "IR_ESTACIONAMENTO" -> NeonBlue
        "PROXIMO" -> Color(0xFFFF8C00)
        else -> TextGray
    }
    
    val modalidadeColor = when (motorista.modalidade) {
        "DEDICADO" -> NeonPurple
        "UTILITARIO" -> Color(0xFF00BCD4)
        "PASSEIO" -> NeonOrange
        else -> NeonBlue
    }
    
    Surface(
        color = DarkSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isConcluido) 0.5f else 1f) // Opacidade reduzida quando concluído
            .clickable { onCardClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ==========================================
            // LINHA 1: Nome (vertical - esquerda) + Status (vertical - direita)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Nome com strikethrough quando concluído
                Text(
                    text = motorista.nome,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    textDecoration = if (isConcluido) TextDecoration.LineThrough else null,
                    modifier = Modifier.alpha(if (isConcluido) 0.6f else 1f) // Opacidade extra no nome quando concluído
                )
                
                // Status Badge com indicador de confirmação
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColor.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, statusColor)
                    ) {
                        Text(
                            text = when (estadoAtual) {
                                "CARREGANDO" -> "CARREGANDO"
                                "CONCLUIDO" -> "CONCLUÍDO"
                                "A_CAMINHO", "A CAMINHO" -> "A CAMINHO"
                                "AGUARDANDO" -> "A CAMINHO"  // Status legado convertido
                                "CHEGUEI" -> "CHEGUEI"
                                "ESTACIONAMENTO" -> "ESTACIONAMENTO"
                                "IR_ESTACIONAMENTO" -> "IR P/ ESTAC."
                                "PROXIMO" -> "PRÓXIMO"
                                else -> "A CAMINHO"
                            },
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    
                    // Indicador de confirmação
                    if (status?.confirmadoEm != null && (estadoAtual == "CARREGANDO" || estadoAtual == "IR_ESTACIONAMENTO")) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Confirmado",
                            tint = Color(0xFF4CAF50), // Verde
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // ==========================================
            // LINHA 2: Vaga, Rota, Sacas (horizontal - esquerda) + Modalidade (direita)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Informações na horizontal
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Vaga
                    Text(
                        text = "Vaga ${motorista.vaga}",
                        color = NeonGreen,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Rota
                    Text(
                        text = if (motorista.rota.isBlank()) "--" else motorista.rota,
                        color = if (motorista.rota.isBlank()) TextGray else NeonBlue,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Sacas
                    motorista.sacas?.let { quantidade ->
                        Text(
                            text = "$quantidade sacas",
                            color = NeonOrange,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Modalidade
                Surface(
                    color = modalidadeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = motorista.modalidade,
                        color = modalidadeColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Badge de status consolidado
 */
@Composable
fun StatusBadge(count: Int, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Número
            Text(
                text = count.toString(),
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            // Label
            Text(
                text = label,
                color = color,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Formata a rota automaticamente: converte para maiúsculas e adiciona traço após as letras
 * Ex: "M12" -> "M-12", "MD12" -> "MD-12", "M-12" -> "M-12"
 * Preserva a ordem dos caracteres digitados
 */
fun formatarRota(input: String): String {
    if (input.isBlank()) return ""
    
    val trimmed = input.trim().uppercase()
    
    // Se já está no formato correto (letra(s)-número(s)), manter como está
    val formatoCorreto = Regex("^[A-Z]+-\\d+$")
    if (formatoCorreto.matches(trimmed)) {
        return trimmed
    }
    
    // Remover traços e espaços existentes, mas preservar a ordem dos caracteres
    val cleanInput = trimmed.replace("-", "").replace(" ", "")
    
    // Separar letras (início) e números (fim), preservando a ordem
    val letters = StringBuilder()
    val numbers = StringBuilder()
    
    var encontrouNumero = false
    for (char in cleanInput) {
        when {
            char.isLetter() -> {
                if (encontrouNumero) {
                    // Se já encontrou número e aparece letra, ignorar (formato inválido)
                    continue
                }
                letters.append(char)
            }
            char.isDigit() -> {
                encontrouNumero = true
                numbers.append(char) // Preserva a ordem dos dígitos
            }
        }
    }
    
    return when {
        letters.isNotEmpty() && numbers.isNotEmpty() -> "$letters-$numbers"
        letters.isNotEmpty() -> letters.toString()
        numbers.isNotEmpty() -> numbers.toString()
        else -> cleanInput
    }
}

/**
 * Formata a vaga para garantir 2 dígitos (01, 02, 03, etc.)
 * Se for um único dígito (1-9), adiciona zero à esquerda
 * Se já tiver 2+ dígitos, mantém como está
 */
fun formatarVaga(vaga: String): String {
    if (vaga.isBlank()) return ""
    
    // Remove espaços e caracteres não numéricos
    val vagaLimpa = vaga.trim().filter { it.isDigit() }
    
    if (vagaLimpa.isEmpty()) return ""
    
    val numero = vagaLimpa.toIntOrNull()
    return if (numero != null && numero in 1..9) {
        // Formata números de 1-9 para 01-09
        String.format("%02d", numero)
    } else {
        // Mantém valores com 2+ dígitos como estão
        vagaLimpa
    }
}

/**
 * Modal de ações do motorista (chamar para vaga ou estacionamento + editar/excluir)
 */
@Composable
fun DriverActionsDialog(
    motorista: OndaItem,
    ondas: List<Onda>,
    ondaIndex: Int,
    telefone: String? = null, // Telefone do motorista para WhatsApp
    onDismiss: () -> Unit,
    onSaveChanges: (vaga: String, rota: String, sacas: Int?) -> Unit,
    onChamarParaVaga: (vaga: String, rota: String) -> Unit,
    onCallDriverWithVagaRota: (OndaItem, String, String) -> Unit,
    onChamarParaEstacionamento: () -> Unit,
    onMarcarComoConcluido: () -> Unit,
    onResetarStatus: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    
    // Função para abrir WhatsApp
    fun abrirWhatsApp(telefone: String) {
        try {
            // Remove caracteres não numéricos do telefone
            val telefoneLimpo = telefone.filter { it.isDigit() }
            if (telefoneLimpo.isNotEmpty()) {
                // Adiciona código do país (55 para Brasil) se não tiver
                val telefoneFormatado = if (telefoneLimpo.startsWith("55")) {
                    telefoneLimpo
                } else {
                    "55$telefoneLimpo"
                }
                val uri = Uri.parse("https://wa.me/$telefoneFormatado")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("DriverActionsDialog", "Erro ao abrir WhatsApp: ${e.message}")
        }
    }
    var vagaEditavel by remember { mutableStateOf(motorista.vaga) }
    var rotaEditavel by remember { mutableStateOf(formatarRota(motorista.rota)) }
    var sacasEditavel by remember { mutableStateOf(motorista.sacas?.toString() ?: "") }
    var showConfirmDelete by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var erroVaga by remember { mutableStateOf<String?>(null) }
    var erroRota by remember { mutableStateOf<String?>(null) }
    
    // Dialog de confirmação de exclusão
    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            containerColor = DarkSurface,
            title = {
                Text("Confirmar Exclusão", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Deseja realmente excluir ${motorista.nome} desta onda?",
                    color = TextGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        onRemove()
                        onDismiss()
                    }
                ) {
                    Text("EXCLUIR", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("CANCELAR", color = TextGray)
                }
            }
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    motorista.nome,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { showHelpDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Help,
                        contentDescription = "Ajuda",
                        tint = NeonBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // CARD 1: Campos editáveis com botão Salvar
                GlassCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        // Campo Vaga (formatação ao perder foco)
                        val vagaEditavelInteractionSource = remember { MutableInteractionSource() }
                        val vagaEditavelFocused by vagaEditavelInteractionSource.collectIsFocusedAsState()
                        
                        // Aplicar formatação quando perde o foco
                        LaunchedEffect(vagaEditavelFocused) {
                            if (!vagaEditavelFocused && vagaEditavel.isNotBlank()) {
                                val vagaFormatada = formatarVaga(vagaEditavel)
                                if (vagaFormatada != vagaEditavel) {
                                    vagaEditavel = vagaFormatada
                                }
                            }
                        }
                        
                        OutlinedTextField(
                            value = if (vagaEditavelFocused) vagaEditavel else formatarVaga(vagaEditavel),
                            onValueChange = { newValue ->
                                // Permitir apenas números
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    vagaEditavel = newValue
                                    // Validar vaga em tempo real
                                    val (valida, mensagem) = validarVagaNaOnda(ondas, ondaIndex, newValue, motorista.motoristaId)
                                    erroVaga = mensagem
                                }
                            },
                            interactionSource = vagaEditavelInteractionSource,
                            label = { Text("Vaga", color = TextGray) },
                            leadingIcon = {
                                Icon(Icons.Default.LocationOn, null, tint = NeonBlue, modifier = Modifier.size(18.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = erroVaga != null,
                            supportingText = erroVaga?.let { { Text(it, color = Color(0xFFEF4444)) } },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = if (erroVaga != null) Color(0xFFEF4444) else NeonBlue,
                                unfocusedBorderColor = if (erroVaga != null) Color(0xFFEF4444) else TextGray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Campo Rota (formatação ao perder foco)
                        val rotaEditavelInteractionSource = remember { MutableInteractionSource() }
                        val rotaEditavelFocused by rotaEditavelInteractionSource.collectIsFocusedAsState()
                        
                        // Aplicar formatação quando perde o foco
                        LaunchedEffect(rotaEditavelFocused) {
                            if (!rotaEditavelFocused && rotaEditavel.isNotBlank()) {
                                rotaEditavel = formatarRota(rotaEditavel)
                            }
                        }
                        
                        OutlinedTextField(
                            value = if (rotaEditavelFocused) rotaEditavel else formatarRota(rotaEditavel),
                            onValueChange = { newValue ->
                                rotaEditavel = newValue // Permite digitar livremente enquanto focado
                                // Validar rota em tempo real (após formatação)
                                if (!rotaEditavelFocused && newValue.isNotBlank()) {
                                    val rotaFormatada = formatarRota(newValue)
                                    val (valida, mensagem) = validarRotaNoTurno(ondas, rotaFormatada, motorista.motoristaId)
                                    erroRota = mensagem
                                }
                            },
                            interactionSource = rotaEditavelInteractionSource,
                            label = { Text("Rota", color = TextGray) },
                            placeholder = { Text("Ex: F-9", color = TextGray.copy(alpha = 0.5f)) },
                            leadingIcon = {
                                Icon(Icons.Default.Place, null, tint = NeonBlue, modifier = Modifier.size(18.dp))
                            },
                            isError = erroRota != null,
                            supportingText = erroRota?.let { { Text(it, color = Color(0xFFEF4444)) } },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = if (erroRota != null) Color(0xFFEF4444) else NeonBlue,
                                unfocusedBorderColor = if (erroRota != null) Color(0xFFEF4444) else TextGray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Campo Sacas
                        OutlinedTextField(
                            value = sacasEditavel,
                            onValueChange = { 
                                if (it.isEmpty() || it.all { char -> char.isDigit() }) 
                                    sacasEditavel = it 
                            },
                            label = { Text("Sacas (opcional)", color = TextGray) },
                            placeholder = { Text("Ex: 12", color = TextGray.copy(alpha = 0.5f)) },
                            leadingIcon = {
                                Icon(Icons.Default.ShoppingCart, null, tint = NeonOrange, modifier = Modifier.size(18.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = NeonOrange,
                                unfocusedBorderColor = TextGray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Botão Salvar
                        Button(
                            onClick = {
                                // Validar antes de salvar
                                val vagaFormatada = formatarVaga(vagaEditavel)
                                val rotaFormatada = formatarRota(rotaEditavel)
                                val (vagaValida, mensagemVaga) = validarVagaNaOnda(ondas, ondaIndex, vagaFormatada, motorista.motoristaId)
                                val (rotaValida, mensagemRota) = validarRotaNoTurno(ondas, rotaFormatada, motorista.motoristaId)
                                
                                erroVaga = mensagemVaga
                                erroRota = mensagemRota
                                
                                if (vagaValida && rotaValida) {
                                    val sacasInt = sacasEditavel.toIntOrNull()
                                    onSaveChanges(vagaFormatada, rotaFormatada, sacasInt)
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SALVAR", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                // CARD 2: Botões de ação
                GlassCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        // Botões de ação em coluna
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Botão: Chamar para Vaga
                            Button(
                                onClick = {
                                    // Validar antes de chamar
                                    val vagaFormatada = formatarVaga(vagaEditavel)
                                    val rotaFormatada = formatarRota(rotaEditavel)
                                    val (vagaValida, mensagemVaga) = validarVagaNaOnda(ondas, ondaIndex, vagaFormatada, motorista.motoristaId)
                                    val (rotaValida, mensagemRota) = validarRotaNoTurno(ondas, rotaFormatada, motorista.motoristaId)
                                    
                                    erroVaga = mensagemVaga
                                    erroRota = mensagemRota
                                    
                                    if (vagaValida && rotaValida) {
                                        onChamarParaVaga(vagaFormatada, rotaFormatada)
                                        onDismiss()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Directions, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Vaga",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Botão: Chamar para Estacionamento
                            Button(
                                onClick = {
                                    onChamarParaEstacionamento()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonPurple,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Place, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Estacionamento",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Botão: WhatsApp (se telefone disponível)
                            telefone?.let { tel ->
                                if (tel.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            abrirWhatsApp(tel)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF25D366), // Cor verde do WhatsApp
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Message, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "WhatsApp",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Dialog de ajuda
                if (showHelpDialog) {
                    AlertDialog(
                        onDismissRequest = { showHelpDialog = false },
                        containerColor = DarkSurface,
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = NeonBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text("Ações do Motorista", color = TextWhite, fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "Como funciona este modal:",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                // Seção: Edição de Campos
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "📝 Edição de Campos:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "• Vaga: Edite a vaga de carregamento do motorista",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                    Text(
                                        "• Rota: Edite a rota do motorista",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                    Text(
                                        "• Sacas: Informe a quantidade de sacas (opcional)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                    Text(
                                        "• Clique em SALVAR para confirmar as alterações",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                }
                                
                                // Seção: Ações do Motorista
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "🎯 Ações do Motorista:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "• Vaga: Chama o motorista para a vaga de carregamento",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                    Text(
                                        "• Estacionamento: Chama o motorista para aguardar no estacionamento",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                    Text(
                                        "• WhatsApp: Abre conversa no WhatsApp do motorista (se disponível)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                }
                                
                                // Seção: Botões Inferiores
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "⚙️ Botões Inferiores:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "• CONCLUIR: Marca o carregamento como concluído",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                    Text(
                                        "• RESETAR STATUS: Volta o status do motorista para 'A CAMINHO'",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                    Text(
                                        "• Cancelar (X): Fecha o modal sem salvar alterações",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                    Text(
                                        "• Excluir (🗑️): Remove o motorista da onda",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showHelpDialog = false }) {
                                Text("Entendi", color = NeonBlue)
                            }
                        },
                        titleContentColor = TextWhite,
                        textContentColor = TextGray
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Linha 1: Concluir e Resetar Status
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Botão: Marcar como Concluído (sem ícone, apenas texto)
                    TextButton(
                        onClick = {
                            onMarcarComoConcluido()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "CONCLUIR",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Botão Resetar Status
                    TextButton(
                        onClick = {
                            onResetarStatus()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "RESETAR\nSTATUS",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonBlue,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Visible,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                // Linha 2: Cancelar e Excluir
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Botão Cancelar (ícone de Close)
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancelar",
                            tint = TextGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Botão Excluir (ícone de lixeira)
                    TextButton(
                        onClick = {
                            showConfirmDelete = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    )
}
