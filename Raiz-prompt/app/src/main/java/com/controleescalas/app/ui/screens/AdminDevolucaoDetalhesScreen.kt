package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.components.AppCard
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DevolucaoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDevolucaoDetalhesScreen(
    baseId: String,
    motoristaId: String,
    motoristaNome: String,
    onDismiss: () -> Unit,
    viewModel: DevolucaoViewModel = viewModel()
) {
    var filtroData by remember { mutableStateOf<String?>(null) } // "hoje", "semana", "mes"
    var idPacoteFiltro by remember { mutableStateOf("") }
    var showFilterMenu by remember { mutableStateOf(false) }
    var devolucaoSelecionada by remember { mutableStateOf<com.controleescalas.app.data.models.Devolucao?>(null) }
    
    val devolucoes by viewModel.devolucoesMotorista.collectAsState()
    val contagemPorDia by viewModel.contagemPorDia.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Verificar se há filtros ativos
    val temFiltrosAtivos = filtroData != null || idPacoteFiltro.isNotBlank()
    
    // Debounce para busca de ID
    var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Função para recarregar dados
    fun recarregarDados() {
        viewModel.carregarDevolucoesMotorista(
            baseId = baseId,
            motoristaId = motoristaId,
            filtroData = filtroData,
            idPacoteFiltro = idPacoteFiltro.takeIf { it.isNotBlank() }
        )
    }
    
    LaunchedEffect(filtroData, idPacoteFiltro) {
        debounceJob?.cancel()
        debounceJob = launch {
            delay(300)
            recarregarDados()
        }
    }
    
    LaunchedEffect(Unit) {
        recarregarDados()
    }
    
    // Mensagens de sucesso/erro
    LaunchedEffect(message) {
        message?.let { msg ->
            if (msg.isNotBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = msg,
                        duration = SnackbarDuration.Short
                    )
                    delay(3500)
                    viewModel.limparMensagem()
                }
            }
        }
    }
    
    LaunchedEffect(error) {
        error?.let { errorMsg ->
            if (errorMsg.isNotBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = errorMsg,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Devoluções",
                subtitle = motoristaNome,
                navigationIcon = Icons.Default.ArrowBack,
                onNavigationClick = onDismiss,
                actions = {
                    // Menu de 3 pontos com filtros
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Filtros"
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Hoje", color = if (filtroData == "hoje") MeliBlue else TextBlack)
                                        if (filtroData == "hoje") {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MeliBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    filtroData = if (filtroData == "hoje") null else "hoje"
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Esta Semana", color = if (filtroData == "semana") MeliBlue else TextBlack)
                                        if (filtroData == "semana") {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MeliBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    filtroData = if (filtroData == "semana") null else "semana"
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Este Mês", color = if (filtroData == "mes") MeliBlue else TextBlack)
                                        if (filtroData == "mes") {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MeliBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    filtroData = if (filtroData == "mes") null else "mes"
                                    showFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Todos", color = if (filtroData == null) MeliBlue else TextBlack)
                                        if (filtroData == null) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MeliBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    filtroData = null
                                    showFilterMenu = false
                                }
                            )
                            if (temFiltrosAtivos) {
                                HorizontalDivider(color = TextGrayLightMode.copy(alpha = 0.3f))
                                DropdownMenuItem(
                                    text = {
                                        Text("Limpar Filtros", color = TextGrayLightMode)
                                    },
                                    onClick = {
                                        filtroData = null
                                        idPacoteFiltro = ""
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && devolucoes.isEmpty() -> {
                    // Loading inicial
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MeliBlue)
                    }
                }
                else -> {
                    // Lista com campo de pesquisa no scroll
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total por dia
                        if (contagemPorDia.isNotEmpty()) {
                            item {
                                AppCard {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Total por Dia",
                                            color = MeliBlue,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        contagemPorDia.toList().sortedByDescending { (_, total) -> total }.forEach { (data, total) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(data, color = TextBlack)
                                                val devolucoesText = if (total == 1) {
                                                    "01 devolução"
                                                } else {
                                                    String.format("%02d devoluções", total)
                                                }
                                                Text(devolucoesText, color = TextGrayLightMode)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Campo de pesquisa
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = idPacoteFiltro,
                                    onValueChange = { idPacoteFiltro = it },
                                    label = { Text("Buscar por ID do Pacote", color = TextGrayLightMode) },
                                    placeholder = { Text("Digite o ID...", color = TextGrayLightMode.copy(alpha = 0.5f)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Buscar",
                                            tint = MeliBlue
                                        )
                                    },
                                    trailingIcon = {
                                        if (idPacoteFiltro.isNotBlank()) {
                                            IconButton(onClick = { idPacoteFiltro = "" }) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Limpar busca",
                                                    tint = TextGrayLightMode
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextBlack,
                                        unfocusedTextColor = TextBlack,
                                        focusedBorderColor = MeliBlue,
                                        unfocusedBorderColor = CardBorderLight,
                                        focusedLabelColor = MeliBlue,
                                        unfocusedLabelColor = TextGrayLightMode
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                
                                // Contador de resultados
                                if (devolucoes.isNotEmpty() || temFiltrosAtivos) {
                                    Text(
                                        if (devolucoes.isEmpty() && temFiltrosAtivos) {
                                            "Nenhuma devolução encontrada com os filtros aplicados"
                                        } else {
                                            "${devolucoes.size} devolução(ões) encontrada(s)"
                                        },
                                        color = if (devolucoes.isEmpty() && temFiltrosAtivos) TextGrayLightMode else TextBlack,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        // Estado vazio sem filtros
                        if (devolucoes.isEmpty() && !temFiltrosAtivos) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Inventory2,
                                            contentDescription = "Nenhuma devolução",
                                            modifier = Modifier.size(64.dp),
                                            tint = TextGrayLightMode
                                        )
                                        Text(
                                            "Nenhuma devolução encontrada",
                                            color = TextGrayLightMode,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Estado vazio com filtros ativos
                        if (devolucoes.isEmpty() && temFiltrosAtivos) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.SearchOff,
                                            contentDescription = "Nenhum resultado",
                                            modifier = Modifier.size(64.dp),
                                            tint = TextGrayLightMode
                                        )
                                        Text(
                                            "Nenhuma devolução encontrada",
                                            color = TextGrayLightMode,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Tente ajustar os filtros",
                                            color = TextGrayLightMode,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Lista de devoluções - Card simplificado (apenas quantidade, data e hora)
                        items(devolucoes) { devolucao ->
                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    // Abrir diálogo com os IDs dos pacotes
                                    devolucaoSelecionada = devolucao
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Ícone indicativo
                                        Icon(
                                            Icons.Default.Inventory2,
                                            contentDescription = null,
                                            tint = MeliBlue,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Column {
                                            // Quantidade de pacotes
                                            Text(
                                                "${devolucao.quantidade} ${if (devolucao.quantidade == 1) "pacote" else "pacotes"}",
                                                color = TextBlack,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            // Data e hora
                                            Text(
                                                "${devolucao.data} - ${devolucao.hora}",
                                                color = TextGrayLightMode,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    
                                    // Ícone de seta para indicar que é clicável
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Ver IDs dos pacotes",
                                        tint = TextGrayLightMode
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Dialog para mostrar IDs dos pacotes
            devolucaoSelecionada?.let { devolucao ->
                AlertDialog(
                    onDismissRequest = { devolucaoSelecionada = null },
                    title = {
                        Column {
                            Text(
                                "IDs dos Pacotes",
                                color = TextBlack,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${devolucao.data} - ${devolucao.hora}",
                                color = TextGrayLightMode,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    text = {
                        if (devolucao.idsPacotes.isEmpty()) {
                            Text(
                                "Nenhum ID de pacote encontrado",
                                color = TextGrayLightMode
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Total: ${devolucao.idsPacotes.size} ${if (devolucao.idsPacotes.size == 1) "pacote" else "pacotes"}",
                                    color = TextBlack,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                HorizontalDivider(color = TextGrayLightMode.copy(alpha = 0.3f))
                                devolucao.idsPacotes.forEachIndexed { index, idPacote ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${index + 1}.",
                                            color = MeliBlue,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Text(
                                            idPacote,
                                            color = TextBlack,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { devolucaoSelecionada = null }
                        ) {
                            Text("Fechar", color = MeliBlue)
                        }
                    },
                    containerColor = Color.White,
                    titleContentColor = TextBlack,
                    textContentColor = TextBlack
                )
            }
            
            // SnackbarHost para mensagens
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) { snackbarData ->
                val messageText = snackbarData.visuals.message
                val isError = error != null || messageText.contains("erro", ignoreCase = true)
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = if (isError) Color(0xFFEF4444) else MeliBlue,
                    contentColor = TextWhite
                )
            }
        }
    }
}
