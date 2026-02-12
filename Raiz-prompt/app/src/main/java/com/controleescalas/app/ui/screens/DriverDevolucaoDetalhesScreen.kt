package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.controleescalas.app.data.models.Devolucao
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DevolucaoViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDevolucaoDetalhesScreen(
    devolucaoId: String,
    baseId: String,
    motoristaId: String,
    onDismiss: () -> Unit,
    viewModel: DevolucaoViewModel = viewModel()
) {
    val devolucoes by viewModel.minhasDevolucoes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    
    var showConfirmDelete by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Carregar devoluções sempre que a tela abrir
    LaunchedEffect(devolucaoId) {
        println("🔍 DriverDevolucaoDetalhesScreen: Carregando devoluções para ID: $devolucaoId")
        viewModel.carregarMinhasDevolucoesComFiltros(baseId, motoristaId, null, null)
    }
    
    // Buscar a devolução específica
    val devolucao = remember(devolucaoId, devolucoes) {
        val encontrada = devolucoes.find { it.id == devolucaoId }
        println("🔍 DriverDevolucaoDetalhesScreen: Devolução encontrada: ${encontrada != null}")
        if (encontrada != null) {
            println("🔍 DriverDevolucaoDetalhesScreen: IDs encontrados: ${encontrada.idsPacotes.size} - ${encontrada.idsPacotes.joinToString(", ")}")
        } else {
            println("🔍 DriverDevolucaoDetalhesScreen: IDs disponíveis: ${devolucoes.map { it.id }.joinToString(", ")}")
        }
        encontrada
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
            TopAppBar(
                title = { Text("Detalhes", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextWhite
                        )
                    }
                },
                // Removido botão excluir do TopAppBar - agora cada ID tem seu próprio botão
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && devolucao == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonGreen)
                    }
                }
                devolucao == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = "Erro",
                                modifier = Modifier.size(64.dp),
                                tint = TextGray
                            )
                            Text(
                                "Devolução não encontrada",
                                color = TextGray,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                else -> {
                    if (devolucao.idsPacotes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Aviso",
                                    modifier = Modifier.size(64.dp),
                                    tint = TextGray
                                )
                                Text(
                                    "Nenhum ID encontrado nesta devolução",
                                    color = TextGray,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Lista de IDs
                            items(devolucao.idsPacotes) { idPacote ->
                                GlassCard {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                "ID: $idPacote",
                                                color = TextWhite,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "${devolucao.data} - ${devolucao.hora}",
                                                color = TextGray,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = { showConfirmDelete = true }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Excluir devolução",
                                                tint = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // SnackbarHost
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) { snackbarData ->
                val messageText = snackbarData.visuals.message
                val isError = error != null || messageText.contains("erro", ignoreCase = true)
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = if (isError) Color(0xFFEF4444) else NeonGreen,
                    contentColor = if (isError) TextWhite else Color.Black
                )
            }
        }
        
        // Dialog de confirmação de exclusão
        if (showConfirmDelete && devolucao != null) {
            AlertDialog(
                onDismissRequest = { showConfirmDelete = false },
                containerColor = DarkSurface,
                title = {
                    Text("Excluir Devolução", color = TextWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            "Deseja realmente excluir esta devolução?",
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "${devolucao.quantidade} ${if (devolucao.quantidade == 1) "pacote" else "pacotes"}",
                            color = TextGray
                        )
                        Text(
                            "IDs: ${devolucao.idsPacotes.joinToString(", ")}",
                            color = TextGray
                        )
                        Text(
                            "Data/Hora: ${devolucao.data} - ${devolucao.hora}",
                            color = TextGray
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.excluirDevolucao(baseId, devolucao.id, motoristaId)
                            showConfirmDelete = false
                            // Voltar após excluir
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Excluir", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDelete = false }) {
                        Text("Cancelar", color = TextGray)
                    }
                }
            )
        }
    }
}

