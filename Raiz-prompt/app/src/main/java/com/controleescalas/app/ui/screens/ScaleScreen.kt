package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.NeonButton
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.ui.viewmodels.ScaleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleScreen(
    turnoSelecionado: String = "AM",
    baseId: String,
    onVoltar: () -> Unit,
    viewModel: ScaleViewModel = viewModel(),
    quinzenaViewModel: com.controleescalas.app.ui.viewmodels.QuinzenaViewModel = viewModel()
) {
    val escala by viewModel.escala.collectAsState()
    val motoristasDisponiveis by viewModel.motoristasDisponiveis.collectAsState()
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
                    TextButton(onClick = { showSaveConfirmDialog = true }) {
                        Text("SALVAR", color = NeonGreen, fontWeight = FontWeight.Bold)
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
                containerColor = NeonGreen,
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
                        colors = listOf(DarkBackground, DeepBlue)
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
                            containerColor = if (selectedTurno == "AM") NeonGreen else DarkSurface,
                            contentColor = if (selectedTurno == "AM") Color.Black else TextGray
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
                            containerColor = if (selectedTurno == "PM") NeonBlue else DarkSurface,
                            contentColor = if (selectedTurno == "PM") Color.Black else TextGray
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
                        CircularProgressIndicator(color = NeonGreen)
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
            
            // Dialog de Seleção de Motorista
            if (showAddDriverDialog) {
                AlertDialog(
                    onDismissRequest = { showAddDriverDialog = false },
                    containerColor = DarkSurface,
                    title = { 
                        Text(
                            "Adicionar Motorista à Onda ${selectedOndaIndex + 1}", 
                            color = TextWhite,
                            style = MaterialTheme.typography.titleLarge
                        ) 
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (motoristasDisponiveis.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = TextGray,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            "Nenhum motorista cadastrado",
                                            color = TextGray,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "Cadastre motoristas primeiro",
                                            color = TextGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    "Selecione um motorista:",
                                    color = TextGray,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyColumn(
                                    modifier = Modifier.height(300.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(motoristasDisponiveis) { motorista ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                            onClick = {
                                                viewModel.addMotoristaToOnda(selectedOndaIndex, motorista)
                                                showAddDriverDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Person, 
                                                    contentDescription = null, 
                                                    tint = NeonGreen,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Column {
                                                    Text(
                                                        motorista.nome,
                                                        color = TextWhite,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        motorista.telefone,
                                                        color = TextGray,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAddDriverDialog = false }) {
                            Text("FECHAR", color = NeonGreen)
                        }
                    }
                )
            }
            
            // Feedback Message
            message?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    containerColor = NeonGreen,
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
            containerColor = DarkSurface,
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
                            color = DarkSurfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Resumo:", color = NeonGreen, fontWeight = FontWeight.Bold)
                                Text("• Turno: ${esc.turno}", color = TextGray, style = MaterialTheme.typography.bodySmall)
                                Text("• Ondas: ${esc.ondas.size}", color = TextGray, style = MaterialTheme.typography.bodySmall)
                                Text("• Motoristas: ${esc.ondas.sumOf { it.itens.size }}", color = TextGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                NeonButton(
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
                    Text("CANCELAR", color = TextGray)
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
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = onda.nome,
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonGreen
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
                    color = TextGray,
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
                        Divider(color = TextGray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))
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
