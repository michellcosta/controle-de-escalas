package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.models.Onda
import com.controleescalas.app.data.models.OndaItem
import com.controleescalas.app.ui.components.CustomTextField
import com.controleescalas.app.ui.components.AppCard
import com.controleescalas.app.ui.components.AppButton
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.components.AddDriversMultiSelectDialog
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.ui.viewmodels.ScaleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleScreen(
    turnoSelecionado: String = "AM",
    baseId: String,
    onVoltar: () -> Unit,
    onOpenAssistente: (() -> Unit)? = null,
    viewModel: ScaleViewModel = viewModel(),
    quinzenaViewModel: com.controleescalas.app.ui.viewmodels.QuinzenaViewModel = viewModel()
) {
    val escala by viewModel.escala.collectAsState()
    val motoristasDisponiveis by viewModel.motoristasDisponiveis.collectAsState()
    val disponibilidadeMap by viewModel.disponibilidadeMap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    
    // Estado para o Dialog de adicionar motorista
    var showAddDriverDialog by remember { mutableStateOf(false) }
    var selectedOndaIndex by remember { mutableStateOf(-1) }
    var selectedTurno by remember { mutableStateOf(turnoSelecionado) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadData(baseId, selectedTurno)
    }
    
    // Observar mensagens
    LaunchedEffect(message) {
        message?.let { msg ->
            // Nota: O incremento de dia trabalhado agora é feito quando o motorista marca "CONCLUIDO"
            // Não incrementar mais ao salvar a escala
            // TODO: Mostrar Snackbar ou Toast (já implementado abaixo)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Escala do Dia", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextWhite)
                    }
                },
                actions = {
                    onOpenAssistente?.let { onAssistente ->
                        IconButton(onClick = onAssistente) {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = "Assistente",
                                tint = TextWhite
                            )
                        }
                    }
                    TextButton(onClick = { showSaveConfirmDialog = true }) {
                        Text("SALVAR", color = MeliBlue, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.addOnda(selectedTurno) },
                containerColor = MeliBlue,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Onda")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White, Color.White)
                    )
                )
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Seletor de Turno
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            selectedTurno = "AM"
                            viewModel.loadData(baseId, "AM")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTurno == "AM") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedTurno == "AM") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TURNO AM", fontWeight = FontWeight.Bold)
                            Text("05:50 - 10:59", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    
                    Button(
                        onClick = {
                            selectedTurno = "PM"
                            viewModel.loadData(baseId, "PM")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTurno == "PM") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedTurno == "PM") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TURNO PM", fontWeight = FontWeight.Bold)
                            Text("11:00 - 23:59", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                
                // Lista de Ondas
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MeliBlue)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 80.dp)
                    ) {
                        escala?.ondas?.let { ondas ->
                            itemsIndexed(ondas) { index, onda ->
                                OndaCard(
                                    onda = onda,
                                    ondaIndex = index,
                                    onAddDriver = {
                                        selectedOndaIndex = index
                                        showAddDriverDialog = true
                                    },
                                    onUpdateItem = { itemIndex, horario, rota, vaga ->
                                        viewModel.updateOndaItem(index, itemIndex, horario, rota, vaga)
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Dialog de Seleção Múltipla de Motoristas (montagem rápida)
            if (showAddDriverDialog) {
                val jaEscalados = escala?.ondas
                    ?.flatMap { it.itens }
                    ?.map { it.motoristaId }
                    ?.toSet() ?: emptySet()
                val ondaAtualNome = escala?.ondas?.getOrNull(selectedOndaIndex)?.nome ?: ""
                AddDriversMultiSelectDialog(
                    motoristas = motoristasDisponiveis,
                    disponibilidadeMap = disponibilidadeMap,
                    ondaAtualIndex = selectedOndaIndex,
                    ondaAtualNome = ondaAtualNome,
                    motoristasJaEscalados = jaEscalados,
                    ondasExistentes = escala?.ondas?.map { it.nome } ?: emptyList(),
                    rotasAtuais = escala?.ondas?.flatMap { it.itens }
                        ?.filter { it.rota.isNotBlank() }
                        ?.associate { it.motoristaId to it.rota } ?: emptyMap(),
                    onDismiss = { showAddDriverDialog = false },
                    onConfirm = { selecionados, destinoOndaIndex, distribuirEm, rotas, destinosPorMotorista ->
                        // destinosPorMotorista usa índice no espaço de ondas REAIS
                        // (sem PRÉ-ESCALA) — converter para índice posicional:
                        val ondasAtuais = escala?.ondas ?: emptyList()
                        val ondasReais = ondasAtuais.filter { !it.nome.equals("PRÉ-ESCALA", true) }
                        fun posicional(realIdx: Int): Int =
                            if (realIdx < ondasReais.size) {
                                ondasAtuais.indexOfFirst { it.nome == ondasReais[realIdx].nome }
                            } else {
                                ondasAtuais.size + (realIdx - ondasReais.size)
                            }
                        val destinosPosicionais = destinosPorMotorista.mapValues { (_, realIdx) ->
                            posicional(realIdx)
                        }
                        viewModel.addMotoristasBatch(selecionados, destinoOndaIndex, distribuirEm, rotas, destinosPosicionais)
                        showAddDriverDialog = false
                    }
                )
            }
            
            // Feedback Message
            message?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    containerColor = MeliBlue,
                    contentColor = Color.Black
                ) {
                    Text(msg)
                }
            }
        }
    }
    
    // Diálogo de Confirmação de Salvamento
    if (showSaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            containerColor = Color.White,
            title = { 
                Text("Salvar Escala", color = TextWhite, fontWeight = FontWeight.Bold) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Deseja salvar a escala do dia?",
                        color = TextWhite,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    escala?.let { esc ->
                        Surface(
                            color = Color(0xFFE5E5EA),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Resumo:", color = MeliBlue, fontWeight = FontWeight.Bold)
                                Text("• Turno: ${esc.turno}", color = TextGrayLightMode, style = MaterialTheme.typography.bodySmall)
                                Text("• Ondas: ${esc.ondas.size}", color = TextGrayLightMode, style = MaterialTheme.typography.bodySmall)
                                Text("• Motoristas: ${esc.ondas.sumOf { it.itens.size }}", color = TextGrayLightMode, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = "SALVAR",
                    onClick = {
                        viewModel.saveEscala(baseId)
                        showSaveConfirmDialog = false
                    },
                    icon = Icons.Default.Check
                )
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmDialog = false }) {
                    Text("CANCELAR", color = TextGrayLightMode)
                }
            }
        )
    }
}

@Composable
fun OndaCard(
    onda: Onda,
    ondaIndex: Int,
    onAddDriver: () -> Unit,
    onUpdateItem: (Int, String, String, String) -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = onda.nome,
                    style = MaterialTheme.typography.titleLarge,
                    color = MeliBlue
                )
                IconButton(onClick = onAddDriver) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar Motorista", tint = TextWhite)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (onda.itens.isEmpty()) {
                Text(
                    text = "Nenhum motorista nesta onda",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGrayLightMode,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                onda.itens.forEachIndexed { index, item ->
                    OndaItemRow(
                        item = item,
                        onUpdate = { horario, rota, vaga ->
                            onUpdateItem(index, horario, rota, vaga)
                        }
                    )
                    if (index < onda.itens.size - 1) {
                        Divider(color = TextGrayLightMode.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun OndaItemRow(
    item: OndaItem,
    onUpdate: (String, String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.nome,
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomTextField(
                value = item.horario,
                onValueChange = { onUpdate(it, item.rota, item.vaga) },
                label = "Horário",
                modifier = Modifier.weight(1f)
            )
            
            CustomTextField(
                value = item.rota,
                onValueChange = { onUpdate(item.horario, it, item.vaga) },
                label = "Rota",
                modifier = Modifier.weight(1f)
            )
            
            CustomTextField(
                value = item.vaga,
                onValueChange = { onUpdate(item.horario, item.rota, it) },
                label = "Vaga",
                modifier = Modifier.weight(0.8f)
            )
        }
    }
}
