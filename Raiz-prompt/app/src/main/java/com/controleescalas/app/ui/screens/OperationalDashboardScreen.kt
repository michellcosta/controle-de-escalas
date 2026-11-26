package com.controleescalas.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.data.models.StatusMotorista
import com.controleescalas.app.ui.components.*
import com.controleescalas.app.ui.components.ConnectionStatusIndicator
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.data.models.isSlot
import com.controleescalas.app.ui.viewmodels.BulkScaleAction
import com.controleescalas.app.ui.viewmodels.OperationalViewModel
import com.controleescalas.app.utils.BrasilDate
import com.controleescalas.app.utils.WhatsAppShare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

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

/**
 * Converte o índice de uma onda REAL (sem a PRÉ-ESCALA — é o que os diálogos de
 * destino mostram) para o índice posicional na lista completa, que é o que o
 * ViewModel usa. Índice a partir do fim das reais é onda NOVA, que o ViewModel
 * cria no fim da lista.
 */
fun List<Onda>.indicePosicional(indiceReal: Int): Int {
    val ondasReais = filter { !it.nome.equals("PRÉ-ESCALA", true) }
    return if (indiceReal < ondasReais.size) {
        indexOfFirst { it.nome == ondasReais[indiceReal].nome }
    } else {
        size + (indiceReal - ondasReais.size)
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
    val turnosDaBase by viewModel.turnosDaBase.collectAsState()
    val ondas by viewModel.ondas.collectAsState()
    val motoristasStatus by viewModel.motoristasStatus.collectAsState()
    val motoristasDisponiveis by viewModel.motoristasDisponiveis.collectAsState()
    val disponibilidadeMap by viewModel.disponibilidadeMap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showAddDriverDialog by remember { mutableStateOf(false) }
    var selectedOndaIndex by remember { mutableIntStateOf(-1) }
    
    // Verificar se há escala válida para compartilhar (motoristas reais em ondas;
    // PRÉ-ESCALA e slots da importação não entram no compartilhamento)
    val temEscalaValida = remember(ondas) {
        val ondasReais = ondas.filter { !it.nome.equals("PRÉ-ESCALA", true) }
        ondasReais.isNotEmpty() && ondasReais.any { it.itens.any { !it.isSlot } }
    }
    
    // Função para formatar o texto da escala para compartilhar
    fun formatarTextoEscalaParaWhatsApp(ondas: List<Onda>, turno: String): String {
        val dataFormatada = BrasilDate.format("dd/MM/yy")
        // Nome real do turno (AM0/AM/PM/SD/SN) — não converter para AM/PM
        val turnoFormatado = turno.uppercase()
        // PRÉ-ESCALA não vai no compartilhamento: é rascunho, não escala montada
        val ondasReais = ondas.filter { !it.nome.equals("PRÉ-ESCALA", true) }

        val nomesOndas = listOf("PRIMEIRA", "SEGUNDA", "TERCEIRA", "QUARTA", "QUINTA", "SEXTA", "SÉTIMA", "OITAVA")

        return buildString {
            // Cabeçalho
            appendLine("⚠️ PLANEJAMENTO _${turnoFormatado}_ ⚠️")
            appendLine()
            appendLine("🗓️ Data: $dataFormatada")
            appendLine()
            appendLine()

            // Iterar pelas ondas reais
            ondasReais.forEachIndexed { index, onda ->
                if (onda.itens.isNotEmpty()) {
                    // Nome da onda (PRIMEIRA, SEGUNDA, etc.)
                    val nomeOnda = nomesOndas.getOrElse(index) { "${index + 1}ª" }

                    // Título da onda com horário
                    appendLine("🚨 `$nomeOnda ONDA:`  ${onda.horario}")
                    appendLine()

                    // Ordenar itens por vaga (numérica) antes de formatar;
                    // slots da importação (sem motorista) não entram na mensagem
                    val itensOrdenados = onda.itens
                        .filter { !it.isSlot }
                        .sortedBy { item ->
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

            WhatsAppShare.compartilharTextoNoWhatsApp(context, textoEscala)
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

    // B9 da auditoria: auto-save ao trocar de turno REMOVIDO. Todas as funções
    // de edição já gravam no Firestore individualmente (updateDriverInOnda,
    // addOnda, removeOnda, add/removeMotorista etc.), e este LaunchedEffect
    // disparava também na primeira composição — set() do documento inteiro
    // sem nenhuma alteração = escrita de cota de graça em cada troca de aba.
    
    // Long-press no motorista abre o seletor "Mover para onda"
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = "Operações do Dia",
                subtitle = BrasilDate.formatDdMmYyyy(),
                actions = {
                    onOpenAssistente?.let { onAssistente ->
                        IconButton(onClick = onAssistente) {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = "Assistente"
                            )
                        }
                    }
                    // Botão de compartilhar - apenas visível se houver escala válida
                    if (temEscalaValida) {
                        IconButton(onClick = { compartilharEscalaNoWhatsApp() }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Compartilhar escala no WhatsApp",
                                tint = Color(0xFF25D366)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(bottom = 104.dp),
                onClick = {
                    // "+" abre direto a montagem rápida de motoristas
                    // (as ondas são criadas pelo próprio dialog, modo "ondas diferentes")
                    selectedOndaIndex = ondas.indexOfFirst {
                        !it.nome.equals("PRÉ-ESCALA", true)
                    }.coerceAtLeast(0)
                    showAddDriverDialog = true
                },
                containerColor = MeliBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Motoristas")
            }
        }
    ) { paddingValues ->
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MeliBlue)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    // Importar a tabela de ondas do dia por foto (OCR) —
                    // cria as ondas com slots (rota + vaga = doca) para o turno atual
                    var showImportarRotas by remember { mutableStateOf(false) }
                    var showConfirmarLimpar by remember { mutableStateOf(false) }
                    // Long-press em motorista/rota → escolher a onda destino
                    var motoristaParaMover by remember { mutableStateOf<OndaItem?>(null) }
                    var ondaOrigemParaMover by remember { mutableIntStateOf(-1) }
                    val preEscalaViewModel: com.controleescalas.app.ui.viewmodels.PreEscalaViewModel = viewModel()

                    // Feedback do import (mensagem/erro do VM) no snackbar
                    LaunchedEffect(Unit) {
                        preEscalaViewModel.message.collect { msg ->
                            msg?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short) }
                        }
                    }
                    LaunchedEffect(Unit) {
                        preEscalaViewModel.error.collect { erro ->
                            erro?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        WavesContent(
                            ondas = ondas,
                            turnos = turnosDaBase,
                            turnoAtual = turnoAtual,
                            motoristasStatus = motoristasStatus,
                            motoristasDisponiveis = motoristasDisponiveis,
                            disponibilidadeMap = disponibilidadeMap,
                            onImportarClick = { showImportarRotas = true },
                            onLimparClick = { showConfirmarLimpar = true },
                            onMoverParaOnda = { origem, item, destino ->
                                viewModel.moverMotoristaEntreOndas(origem, destino, item)
                            },
                            onMoverSlot = { origem, slot, destino ->
                                viewModel.moverMotoristaEntreOndas(origem, destino, slot)
                            },
                            onPreencherSlot = { ondaIndex, slot, motorista ->
                                viewModel.preencherSlot(ondaIndex, slot, motorista)
                            },
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

                    // Import da tabela de ondas por foto: reorganiza as ondas do turno
                    // atual pela tabela e coloca cada motorista já escalado (pré-escala)
                    // na onda e na vaga da sua rota
                    if (showImportarRotas) {
                        ImportarRotasDialog(
                            baseId = baseId,
                            data = BrasilDate.hoje(),
                            turno = turnoAtual,
                            viewModel = preEscalaViewModel,
                            onDismiss = { showImportarRotas = false }
                        )
                    }

                    // Confirmação da limpeza total do turno
                    if (showConfirmarLimpar) {
                        AlertDialog(
                            onDismissRequest = { showConfirmarLimpar = false },
                            containerColor = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            title = {
                                Text("Limpar todas as ondas?", color = TextBlack, fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Text(
                                    "Todas as ondas do turno $turnoAtual serão apagadas — motoristas, rotas e vagas. " +
                                        "Isso não pode ser desfeito.",
                                    color = TextGrayLightMode,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.limparTodasOndas()
                                        showConfirmarLimpar = false
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusError,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("LIMPAR TUDO", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirmarLimpar = false }) {
                                    Text("CANCELAR", color = TextGrayLightMode)
                                }
                            }
                        )
                    }

                    // Long-press em motorista/rota → escolher a onda destino
                    if (motoristaParaMover != null) {
                        val item = motoristaParaMover!!
                        val ondasReais = ondas.filter { !it.nome.equals("PRÉ-ESCALA", true) }
                        AlertDialog(
                            onDismissRequest = {
                                motoristaParaMover = null
                                ondaOrigemParaMover = -1
                            },
                            containerColor = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            title = {
                                Column {
                                    Text(
                                        "Mover ${item.nome.ifBlank { "rota ${item.rota}" }}",
                                        color = TextBlack,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Escolha a onda de destino:",
                                        color = TextGrayLightMode,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ondasReais.forEachIndexed { destino, onda ->
                                        TextButton(
                                            onClick = {
                                                viewModel.moverMotoristaEntreOndas(ondaOrigemParaMover, destino, item)
                                                motoristaParaMover = null
                                                ondaOrigemParaMover = -1
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "${onda.nome}${if (destino == ondaOrigemParaMover) " (atual)" else ""}",
                                                color = if (destino == ondaOrigemParaMover) TextGrayLightMode else TextBlack,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.moverMotoristaEntreOndas(
                                                ondaOrigemParaMover,
                                                ondasReais.size, // nova onda no fim
                                                item
                                            )
                                            motoristaParaMover = null
                                            ondaOrigemParaMover = -1
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "➕ Nova onda",
                                            color = MeliBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = {
                                    motoristaParaMover = null
                                    ondaOrigemParaMover = -1
                                }) {
                                    Text("CANCELAR", color = TextGrayLightMode)
                                }
                            }
                        )
                    }
                }
            }

            if (showAddDriverDialog && selectedOndaIndex >= 0) {
                val jaEscalados = ondas
                    .flatMap { it.itens }
                    .map { it.motoristaId }
                    .toSet()
                AddDriversMultiSelectDialog(
                    motoristas = motoristasDisponiveis.filter { it.papel != "admin" },
                    disponibilidadeMap = disponibilidadeMap,
                    ondaAtualIndex = selectedOndaIndex,
                    ondaAtualNome = ondas.getOrNull(selectedOndaIndex)?.nome ?: "Onda",
                    motoristasJaEscalados = jaEscalados,
                    modalidadeFiltroHabilitada = true,
                    ondasExistentes = ondas.map { it.nome },
                    rotasAtuais = ondas.flatMap { it.itens }
                        .filter { it.rota.isNotBlank() }
                        .associate { it.motoristaId to it.rota },
                    onDismiss = { showAddDriverDialog = false },
                    onConfirm = { selecionados, destinoOndaIndex, distribuirEm, rotas, destinosPorMotorista ->
                        // Montagem rápida em lote: vaga fica vazia (preenchida depois).
                        // O bulkApply remove duplicatas e preserva rota/sacas quando a
                        // ação não informa (ex.: mover da pré-escala mantém a rota).
                        // destinosPorMotorista usa índice no espaço de ondas REAIS
                        // (sem PRÉ-ESCALA) — converter para índice posicional
                        val primeiraNova = ondas.size
                        val acoes = selecionados.mapIndexed { i, motorista ->
                            val ondaIndex = destinosPorMotorista[motorista.id]?.let { ondas.indicePosicional(it) }
                                ?: if (distribuirEm != null && distribuirEm > 0) {
                                    primeiraNova + (i % distribuirEm)
                                } else {
                                    destinoOndaIndex
                                }
                            BulkScaleAction(
                                isAdd = true,
                                motoristaId = motorista.id,
                                nome = motorista.nome,
                                ondaIndex = ondaIndex,
                                vaga = "",
                                rota = rotas[motorista.id], // null = preservar rota anterior
                                sacas = null
                            )
                        }
                        viewModel.bulkApplyScaleActions(acoes)
                        showAddDriverDialog = false
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
                        containerColor = if (isError) Color(0xFFEF4444) else MeliBlue,
                        contentColor = TextWhite
                    )
                }
            }
        }
    }
}

@Composable
fun WavesContent(
    ondas: List<Onda>,
    turnos: List<String>,
    turnoAtual: String,
    motoristasStatus: Map<String, StatusMotorista>,
    motoristasDisponiveis: List<AdminMotoristaCardData>,
    disponibilidadeMap: Map<String, Boolean?>,
    onImportarClick: () -> Unit,
    onLimparClick: () -> Unit,
    onMoverParaOnda: (Int, OndaItem, Int) -> Unit,
    onMoverSlot: (Int, OndaItem, Int) -> Unit,
    onPreencherSlot: (Int, OndaItem, AdminMotoristaCardData) -> Unit,
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
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp)
    ) {
        item {
            // Seletor de Turno movido para dentro do LazyColumn
            TurnoSelector(
                turnos = turnos,
                turnoAtual = turnoAtual,
                onTurnoChange = onTurnoChange
            )

            // Ações do turno (rolam junto com o conteúdo)
            Button(
                onClick = onImportarClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeliBlue,
                    contentColor = Color.White
                )
            ) {
                Text("📥 Importar ondas", fontWeight = FontWeight.Bold)
            }

            if (ondas.isNotEmpty()) {
                TextButton(
                    onClick = onLimparClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    Text(
                        "🗑️ Limpar todas as ondas do turno $turnoAtual",
                        color = StatusError,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
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
                disponibilidadeMap = disponibilidadeMap,
                onMoverParaOnda = onMoverParaOnda,
                onMoverSlot = onMoverSlot,
                onPreencherSlot = onPreencherSlot,
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
                disponibilidadeMap = disponibilidadeMap,
                onMoverParaOnda = onMoverParaOnda,
                onMoverSlot = onMoverSlot,
                onPreencherSlot = onPreencherSlot,
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
        
        // Botão NOTIFICAR TODOS (aparece se houver motoristas escalados). Só
        // motoristas reais das ondas reais: rotas livres (slots) e a PRÉ-ESCALA
        // ficam de fora, como no saveAndNotifyAll
        val totalMotoristas = ondas
            .filter { !it.nome.equals("PRÉ-ESCALA", true) }
            .sumOf { onda -> onda.itens.count { !it.isSlot } }
        if (totalMotoristas > 0) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onSaveAndNotifyAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeliBlue,
                        contentColor = Color.White
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
                            color = Color.White.copy(alpha = 0.85f)
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
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 120.dp)
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
                containerColor = MeliBlue,
                contentColor = Color.White
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
            color = TextGrayLightMode,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TurnoSelector(
    turnos: List<String>,
    turnoAtual: String,
    onTurnoChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val selecionadoFundo = MeliBlue
        val selecionadoTexto = Color.White
        val livreFundo = MaterialTheme.colorScheme.surfaceVariant
        val livreTexto = MaterialTheme.colorScheme.onSurfaceVariant

        turnos.forEach { turno ->
            Button(
                onClick = { onTurnoChange(turno) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (turnoAtual == turno) selecionadoFundo else livreFundo,
                    contentColor = if (turnoAtual == turno) selecionadoTexto else livreTexto
                )
            ) {
                Text(turno, fontWeight = FontWeight.Bold)
            }
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
    disponibilidadeMap: Map<String, Boolean?>,
    onMoverParaOnda: (Int, OndaItem, Int) -> Unit,
    onMoverSlot: (Int, OndaItem, Int) -> Unit,
    onPreencherSlot: (Int, OndaItem, AdminMotoristaCardData) -> Unit,
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
    
    AppCard(modifier = Modifier.fillMaxWidth()) {
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
                        tint = TextGrayLightMode,
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
                                color = MeliBlue,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = onda.horario,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextGrayLightMode,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // Badge de Tipo (Dedicado, Frota, Passeio, etc)
                            if (onda.tipo != "NORMAL") {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = modalidadeColor(onda.tipo).copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = onda.tipo.uppercase(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = modalidadeColor(onda.tipo),
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
                                    color = if (todosConcluidos) Color(0xFF10B981) else TextGrayLightMode
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
                            tint = TextGrayLightMode,
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
                            tint = TextBlack,
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
                        color = CardBorderLight
                    )
                    
                    if (onda.itens.isEmpty()) {
                        Text(
                            text = "Nenhum motorista nesta onda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGrayLightMode,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        // Motoristas já vêm ordenados por modalidade do ViewModel
                        // Aqui apenas exibimos na ordem que chegaram
                        var showActionsDialog by remember { mutableStateOf(false) }
                        var selectedMotoristaForActions by remember { mutableStateOf<OndaItem?>(null) }
                        var showEditDialog by remember { mutableStateOf(false) }
                        var showEscolherSlotDialog by remember { mutableStateOf(false) }
                        var slotParaPreencher by remember { mutableStateOf<OndaItem?>(null) }

                        onda.itens.forEach { motorista ->
                            val status = motoristasStatus[motorista.motoristaId]

                            DriverOperationRow(
                                motorista = motorista,
                                status = status,
                                onCardClick = {
                                    if (motorista.isSlot) {
                                        slotParaPreencher = motorista
                                        showEscolherSlotDialog = true
                                    } else {
                                        selectedMotoristaForActions = motorista
                                        showActionsDialog = true
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Escolher motorista para preencher um slot da importação
                        if (showEscolherSlotDialog && slotParaPreencher != null) {
                            val ondaIndexSlot = ondas.indexOfFirst { it.itens.contains(slotParaPreencher!!) }
                            if (ondaIndexSlot >= 0) {
                                EscolherMotoristaSlotDialog(
                                    slot = slotParaPreencher!!,
                                    motoristas = motoristasDisponiveis,
                                    disponibilidadeMap = disponibilidadeMap,
                                    ondas = ondas,
                                    ondaAtualIndex = ondaIndexSlot,
                                    onMoverSlot = { destinoOndaIndex ->
                                        onMoverSlot(ondaIndexSlot, slotParaPreencher!!, destinoOndaIndex)
                                        showEscolherSlotDialog = false
                                        slotParaPreencher = null
                                    },
                                    onDismiss = {
                                        showEscolherSlotDialog = false
                                        slotParaPreencher = null
                                    },
                                    onConfirm = { motorista ->
                                        onPreencherSlot(ondaIndexSlot, slotParaPreencher!!, motorista)
                                        showEscolherSlotDialog = false
                                        slotParaPreencher = null
                                    }
                                )
                            } else {
                                showEscolherSlotDialog = false
                                slotParaPreencher = null
                            }
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
                                },
                                onMoverParaOnda = { destinoOndaIndex ->
                                    onMoverParaOnda(
                                        ondas.indexOfFirst { it.itens.contains(selectedMotoristaForActions!!) },
                                        selectedMotoristaForActions!!,
                                        destinoOndaIndex
                                    )
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
            containerColor = Color.White,
            title = {
                Text("Remover Motorista", color = TextBlack, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Tem certeza que deseja remover este motorista da onda?",
                    color = TextGrayLightMode
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
                    Text("Cancelar", color = TextGrayLightMode)
                }
            }
        )
    }
    
    // Dialog de confirmação de exclusão de onda
    if (showConfirmDeleteWaveDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteWaveDialog = false },
            containerColor = Color.White,
            title = {
                Text("Excluir Onda", color = TextBlack, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Tem certeza que deseja excluir a onda \"${onda.nome}\"?\n\nEsta ação não pode ser desfeita.",
                    color = TextGrayLightMode
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
                    Text("CANCELAR", color = TextGrayLightMode)
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
    // Slot da importação (rota sem motorista): visual próprio, toque escolhe o motorista
    if (motorista.isSlot) {
        Surface(
            color = LightSurfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCardClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rota livre: ${motorista.rota}",
                        color = TextBlack,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp
                    )
                    if (motorista.vaga.isNotBlank()) {
                        Text(
                            text = "Vaga ${motorista.vaga}",
                            color = MeliBlue,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Toque para escolher o motorista",
                        color = TextGrayLightMode,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    color = NeonOrange.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, NeonOrange)
                ) {
                    Text(
                        text = "LIVRE",
                        color = NeonOrange,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
        return
    }

    val estadoAtual = status?.estado?.takeIf { it.isNotBlank() } ?: "A_CAMINHO"
    val isConcluido = estadoAtual == "CONCLUIDO"
    
    val corStatus = statusColor(estadoAtual)
    
    val corModalidade = modalidadeColor(motorista.modalidade)
    
    Surface(
        color = LightSurfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isConcluido) 0.5f else 1f) // Opacidade reduzida quando concluido
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
                    color = TextBlack,
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
                        color = corStatus.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, corStatus)
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
                            color = corStatus,
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
                        color = MeliBlue,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Rota
                    Text(
                        text = if (motorista.rota.isBlank()) "--" else motorista.rota,
                        color = if (motorista.rota.isBlank()) TextGrayLightMode else MeliBlue,
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
                    color = corModalidade.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = motorista.modalidade,
                        color = corModalidade,
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
 * Escolher motorista para preencher um slot de rota vindo da importação.
 * Disponíveis primeiro; indisponíveis aparecem com aviso.
 */
@Composable
fun EscolherMotoristaSlotDialog(
    slot: OndaItem,
    motoristas: List<AdminMotoristaCardData>,
    disponibilidadeMap: Map<String, Boolean?>,
    ondas: List<Onda> = emptyList(),
    ondaAtualIndex: Int = -1,
    onMoverSlot: (destinoOndaIndex: Int) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (AdminMotoristaCardData) -> Unit
) {
    var busca by remember { mutableStateOf("") }

    val lista = motoristas
        .filter { it.papel != "admin" }
        .sortedWith(
            compareBy<AdminMotoristaCardData> {
                when (disponibilidadeMap[it.id]) {
                    true -> 0
                    null -> 1
                    false -> 2
                }
            }.thenBy { it.nome }
        )
        .filter {
            busca.isBlank() || it.nome.contains(busca, ignoreCase = true) ||
                it.telefone.contains(busca, ignoreCase = true)
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Quem pega a rota ${slot.rota}?", color = TextBlack, fontWeight = FontWeight.Bold)
                    if (slot.vaga.isNotBlank()) {
                        Text(
                            "Vaga ${slot.vaga}",
                            color = MeliBlue,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextGrayLightMode)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    label = { Text("Buscar motorista...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MeliBlue,
                        unfocusedBorderColor = CardBorderLight,
                        cursorColor = MeliBlue
                    )
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(lista.size) { index ->
                        val m = lista[index]
                        val disponibilidade = disponibilidadeMap[m.id]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(m) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    m.nome,
                                    color = TextBlack,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    when (disponibilidade) {
                                        true -> "✓ Disponível neste turno"
                                        false -> "✕ Indisponível neste turno"
                                        else -> "Sem resposta"
                                    },
                                    color = when (disponibilidade) {
                                        true -> NeonGreen
                                        false -> StatusError
                                        else -> TextGrayLightMode
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Icon(
                                Icons.Default.Done,
                                contentDescription = "Escalar ${m.nome}",
                                tint = MeliBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Mover esta rota livre para outra onda
                var mostrarOndas by remember { mutableStateOf(false) }
                if (!mostrarOndas) {
                    TextButton(onClick = { mostrarOndas = true }) {
                        Text(
                            "📌 Mover rota para outra onda",
                            color = MeliBlue,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    // Ondas reais (sem a PRÉ-ESCALA). O destino é convertido para o
                    // índice posicional, que é o que o ViewModel usa
                    val ondasReais = ondas.filter { !it.nome.equals("PRÉ-ESCALA", true) }
                    ondasReais.forEachIndexed { destinoIdx, ondaDestino ->
                        val destinoPosicional = ondas.indicePosicional(destinoIdx)
                        if (destinoPosicional != ondaAtualIndex) {
                            TextButton(
                                onClick = {
                                    onMoverSlot(destinoPosicional)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "→ ${ondaDestino.nome}",
                                    color = TextBlack,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            onMoverSlot(ondas.indicePosicional(ondasReais.size))
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "→ Nova onda",
                            color = MeliBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("CANCELAR", color = TextGrayLightMode)
                }
            }
        }
    )
}

/**
 * Badge de status consolidado
 */
@Composable
fun StatusBadge(count: Int, label: String, color: Color) {    Surface(
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

    // Código da planilha (I13_AM1): já é o código completo da rota — não mexe
    // (a formatação abaixo o transformaria em "I-131")
    if (trimmed.contains('_')) return trimmed
    
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
@OptIn(ExperimentalMaterial3Api::class)
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
    onRemove: () -> Unit,
    onMoverParaOnda: (destinoOndaIndex: Int) -> Unit
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

    // Valida vaga/rota; retorna o par formatado (vazio = inválido)
    fun validarCampos(): Pair<String, String> {
        val vagaFormatada = formatarVaga(vagaEditavel)
        val rotaFormatada = formatarRota(rotaEditavel)
        val (vagaValida, mensagemVaga) = validarVagaNaOnda(ondas, ondaIndex, vagaFormatada, motorista.motoristaId)
        val (rotaValida, mensagemRota) = validarRotaNoTurno(ondas, rotaFormatada, motorista.motoristaId)
        erroVaga = mensagemVaga
        erroRota = mensagemRota
        return if (vagaValida && rotaValida) vagaFormatada to rotaFormatada else "" to ""
    }

    // Dialog de confirmação de exclusão
    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            containerColor = Color.White,
            title = {
                Text("Confirmar Exclusão", color = TextBlack, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Deseja realmente excluir ${motorista.nome} desta onda?",
                    color = TextGrayLightMode
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
                    Text("CANCELAR", color = TextGrayLightMode)
                }
            }
        )
    }

    // Dialog de ajuda
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            containerColor = Color.White,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MeliBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Ações do Motorista", color = TextBlack, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Como funciona este painel:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextBlack,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "📝 Edição de Campos:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextBlack,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("• Vaga: Edite a vaga de carregamento do motorista", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                        Text("• Rota: Edite a rota do motorista", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                        Text("• Sacas: Informe a quantidade de sacas (opcional)", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                        Text("• Clique em SALVAR para confirmar as alterações", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "🎯 Ações do Motorista:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextBlack,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("• Vaga: Chama o motorista para a vaga de carregamento", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                        Text("• Estacionamento: Chama o motorista para aguardar no estacionamento", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                        Text("• WhatsApp: Abre conversa no WhatsApp do motorista (se disponível)", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                        Text("• CONCLUIR: Marca o carregamento como concluído", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                        Text("• RESETAR STATUS: Volta o status do motorista para 'A CAMINHO'", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                        Text("• Excluir (🗑️): Remove o motorista da onda", style = MaterialTheme.typography.bodySmall, color = TextGrayLightMode)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Entendi", color = MeliBlue)
                }
            },
            titleContentColor = TextBlack,
            textContentColor = TextGrayLightMode
        )
    }

    // ===== Painel principal (bottom sheet rolável — nada é cortado) =====
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Cabeçalho: nome + situação atual + ajuda + fechar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        motorista.nome,
                        color = TextBlack,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Vaga ${motorista.vaga.ifBlank { "-" }} • Rota ${formatarRota(motorista.rota).ifBlank { "-" }} • ${motorista.sacas ?: "-"} sacas",
                        color = TextGrayLightMode,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { showHelpDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Help, contentDescription = "Ajuda", tint = MeliBlue, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextGrayLightMode, modifier = Modifier.size(20.dp))
                }
            }

            // CARD 1: campos editáveis em linha + SALVAR
            AppCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Campo Vaga
                        val vagaInteractionSource = remember { MutableInteractionSource() }
                        val vagaFocused by vagaInteractionSource.collectIsFocusedAsState()
                        LaunchedEffect(vagaFocused) {
                            if (!vagaFocused && vagaEditavel.isNotBlank()) {
                                val vagaFormatada = formatarVaga(vagaEditavel)
                                if (vagaFormatada != vagaEditavel) {
                                    vagaEditavel = vagaFormatada
                                }
                            }
                        }
                        OutlinedTextField(
                            value = if (vagaFocused) vagaEditavel else formatarVaga(vagaEditavel),
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    vagaEditavel = newValue
                                    val (_, mensagem) = validarVagaNaOnda(ondas, ondaIndex, newValue, motorista.motoristaId)
                                    erroVaga = mensagem
                                }
                            },
                            interactionSource = vagaInteractionSource,
                            label = { Text("Vaga", color = TextGrayLightMode, style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            isError = erroVaga != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextBlack,
                                unfocusedTextColor = TextBlack,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = if (erroVaga != null) Color(0xFFEF4444) else MeliBlue,
                                unfocusedBorderColor = if (erroVaga != null) Color(0xFFEF4444) else CardBorderLight
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Campo Rota
                        val rotaInteractionSource = remember { MutableInteractionSource() }
                        val rotaFocused by rotaInteractionSource.collectIsFocusedAsState()
                        LaunchedEffect(rotaFocused) {
                            if (!rotaFocused && rotaEditavel.isNotBlank()) {
                                rotaEditavel = formatarRota(rotaEditavel)
                            }
                        }
                        OutlinedTextField(
                            value = if (rotaFocused) rotaEditavel else formatarRota(rotaEditavel),
                            onValueChange = { newValue ->
                                rotaEditavel = newValue
                                if (!rotaFocused && newValue.isNotBlank()) {
                                    val rotaFormatada = formatarRota(newValue)
                                    val (_, mensagem) = validarRotaNoTurno(ondas, rotaFormatada, motorista.motoristaId)
                                    erroRota = mensagem
                                }
                            },
                            interactionSource = rotaInteractionSource,
                            label = { Text("Rota", color = TextGrayLightMode, style = MaterialTheme.typography.labelSmall) },
                            placeholder = { Text("F-9", color = TextGrayLightMode.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            isError = erroRota != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextBlack,
                                unfocusedTextColor = TextBlack,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = if (erroRota != null) Color(0xFFEF4444) else MeliBlue,
                                unfocusedBorderColor = if (erroRota != null) Color(0xFFEF4444) else CardBorderLight
                            ),
                            modifier = Modifier.weight(1.4f)
                        )

                        // Campo Sacas
                        OutlinedTextField(
                            value = sacasEditavel,
                            onValueChange = {
                                if (it.isEmpty() || it.all { char -> char.isDigit() })
                                    sacasEditavel = it
                            },
                            label = { Text("Sacas", color = TextGrayLightMode, style = MaterialTheme.typography.labelSmall) },
                            placeholder = { Text("12", color = TextGrayLightMode.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextBlack,
                                unfocusedTextColor = TextBlack,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = NeonOrange,
                                unfocusedBorderColor = CardBorderLight
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Mensagens de erro abaixo dos campos (compactas)
                    if (erroVaga != null || erroRota != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            erroVaga?.let {
                                Text(it, color = Color(0xFFEF4444), style = MaterialTheme.typography.labelSmall)
                            }
                            erroRota?.let {
                                Text(it, color = Color(0xFFEF4444), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Botão Salvar
                    Button(
                        onClick = {
                            val (vagaFormatada, rotaFormatada) = validarCampos()
                            if (vagaFormatada.isNotEmpty() && rotaFormatada.isNotEmpty()) {
                                onSaveChanges(vagaFormatada, rotaFormatada, sacasEditavel.toIntOrNull())
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeliBlue,
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

            // CARD 2: ações — grade de 2 colunas (compacta, nada corta)
            AppCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Botão: Chamar para Vaga
                        Button(
                            onClick = {
                                val (vagaFormatada, rotaFormatada) = validarCampos()
                                if (vagaFormatada.isNotEmpty() && rotaFormatada.isNotEmpty()) {
                                    onChamarParaVaga(vagaFormatada, rotaFormatada)
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MeliBlue,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Directions, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vaga", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Place, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Estacionar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    // Botão: WhatsApp (se telefone disponível)
                    telefone?.let { tel ->
                        if (tel.isNotBlank()) {
                            Button(
                                onClick = { abrirWhatsApp(tel) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF25D366), // Cor verde do WhatsApp
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Message, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("WhatsApp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Botão: Marcar como Concluído
                        TextButton(
                            onClick = {
                                onMarcarComoConcluido()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "CONCLUIR",
                                style = MaterialTheme.typography.labelLarge,
                                color = NeonOrange,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Botão: Resetar Status
                        TextButton(
                            onClick = {
                                onResetarStatus()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "RESETAR STATUS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MeliBlue,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Mover para outra onda (rolagem de ondas abaixo da atual, + nova onda)
            var mostrarOndasDestino by remember { mutableStateOf(false) }
            if (!mostrarOndasDestino) {
                TextButton(
                    onClick = { mostrarOndasDestino = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = null,
                        tint = MeliBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Mover para outra onda",
                        style = MaterialTheme.typography.labelLarge,
                        color = MeliBlue
                    )
                }
            } else {
                // Lista de ondas destino (reais + nova onda no fim). As reais são
                // indexadas sem a PRÉ-ESCALA; o destino é convertido para o
                // índice posicional, que é o que o ViewModel usa
                val ondasReaisDestino = ondas.filter { !it.nome.equals("PRÉ-ESCALA", true) }
                ondasReaisDestino.forEachIndexed { destinoIdx, ondaDestino ->
                    val destinoPosicional = ondas.indicePosicional(destinoIdx)
                    if (destinoPosicional != ondaIndex) {
                        TextButton(
                            onClick = {
                                onMoverParaOnda(destinoPosicional)
                                onDismiss()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                "→ ${ondaDestino.nome}",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextBlack,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                // Nova onda no fim
                TextButton(
                    onClick = {
                        onMoverParaOnda(ondas.indicePosicional(ondasReaisDestino.size))
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "→ Nova onda",
                        style = MaterialTheme.typography.labelLarge,
                        color = MeliBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Ação destrutiva discreta no rodapé
            TextButton(
                onClick = { showConfirmDelete = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Excluir da onda",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFEF4444)
                )
            }
        }
    }
}
