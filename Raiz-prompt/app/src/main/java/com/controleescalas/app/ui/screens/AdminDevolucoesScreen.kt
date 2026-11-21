package com.controleescalas.app.ui.screens

import androidx.compose.foundation.clickable
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
import com.controleescalas.app.ui.components.AppCard
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DevolucaoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDevolucoesScreen(
    baseId: String,
    onDismiss: () -> Unit,
    onMotoristaClick: (String, String) -> Unit, // motoristaId, motoristaNome
    viewModel: DevolucaoViewModel = viewModel()
) {
    // Log quando a tela é renderizada
    android.util.Log.e("DEBUG", "🔵 AdminDevolucoesScreen: Tela renderizada, baseId: $baseId, callback null: ${onMotoristaClick == null}")
    println("🔵 AdminDevolucoesScreen: Tela renderizada, baseId: $baseId")
    android.util.Log.e("DEBUG", "🔵 AdminDevolucoesScreen: ViewModel is null: ${viewModel == null}")
    println("🔵 AdminDevolucoesScreen: ViewModel is null: ${viewModel == null}")
    var nomeFiltro by remember { mutableStateOf("") }
    val motoristas by viewModel.motoristasComDevolucoes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    
    // Log quando motoristas mudam
    LaunchedEffect(motoristas) {
        android.util.Log.e("DEBUG", "🔵 AdminDevolucoesScreen: motoristas atualizados - count: ${motoristas.size}, isLoading: $isLoading")
        println("🔵 AdminDevolucoesScreen: motoristas atualizados - count: ${motoristas.size}, isLoading: $isLoading")
        motoristas.forEachIndexed { index, motorista ->
            android.util.Log.e("DEBUG", "🔵 AdminDevolucoesScreen: motorista[$index]: ${motorista.motoristaNome} (${motorista.totalDevolucoes} devoluções)")
            println("🔵 AdminDevolucoesScreen: motorista[$index]: ${motorista.motoristaNome} (${motorista.totalDevolucoes} devoluções)")
        }
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Verificar se há filtros ativos
    val temFiltrosAtivos = nomeFiltro.isNotBlank()
    
    // Debounce para busca em tempo real
    var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // Função para recarregar dados
    fun recarregarDados() {
        android.util.Log.e("DEBUG", "🔵 AdminDevolucoesScreen: recarregarDados() chamado - baseId: $baseId, filtro: '$nomeFiltro'")
        println("🔵 AdminDevolucoesScreen: recarregarDados() chamado - baseId: $baseId, filtro: '$nomeFiltro'")
        viewModel.carregarMotoristasComDevolucoes(baseId, nomeFiltro.takeIf { it.isNotBlank() })
    }
    
    LaunchedEffect(nomeFiltro) {
        android.util.Log.e("DEBUG", "🔵 AdminDevolucoesScreen: LaunchedEffect(nomeFiltro) executado - filtro: '$nomeFiltro'")
        println("🔵 AdminDevolucoesScreen: LaunchedEffect(nomeFiltro) executado - filtro: '$nomeFiltro'")
        debounceJob?.cancel()
        debounceJob = launch {
            delay(300)
            recarregarDados()
        }
    }
    
    LaunchedEffect(Unit) {
        android.util.Log.e("DEBUG", "🔵 AdminDevolucoesScreen: LaunchedEffect(Unit) executado - carregando dados iniciais")
        println("🔵 AdminDevolucoesScreen: LaunchedEffect(Unit) executado - carregando dados iniciais")
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
                navigationIcon = Icons.Default.ArrowBack,
                onNavigationClick = onDismiss
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
                isLoading && motoristas.isEmpty() -> {
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
                        // Campo de pesquisa
                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = nomeFiltro,
                                    onValueChange = { nomeFiltro = it },
                                    label = { Text("Pesquisar Motorista", color = TextGrayLightMode) },
                                    placeholder = { Text("Digite o nome do motorista ou ID do pacote...", color = TextGrayLightMode.copy(alpha = 0.5f)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = "Buscar",
                                            tint = MeliBlue
                                        )
                                    },
                                    trailingIcon = {
                                        if (nomeFiltro.isNotBlank()) {
                                            IconButton(onClick = { nomeFiltro = "" }) {
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
                                if (motoristas.isNotEmpty() || temFiltrosAtivos) {
                                    Text(
                                        if (motoristas.isEmpty() && temFiltrosAtivos) {
                                            "Nenhum motorista encontrado com os filtros aplicados"
                                        } else {
                                            "${motoristas.size} motorista(s) encontrado(s)"
                                        },
                                        color = if (motoristas.isEmpty() && temFiltrosAtivos) TextGrayLightMode else TextBlack,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                        
                        // Estado vazio sem filtros
                        if (motoristas.isEmpty() && !temFiltrosAtivos) {
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
                                            Icons.Default.Person,
                                            contentDescription = "Nenhum motorista",
                                            modifier = Modifier.size(64.dp),
                                            tint = TextGrayLightMode
                                        )
                                        Text(
                                            "Nenhum motorista com devoluções",
                                            color = TextGrayLightMode,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Estado vazio com filtros ativos
                        if (motoristas.isEmpty() && temFiltrosAtivos) {
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
                                            "Nenhum motorista encontrado",
                                            color = TextGrayLightMode,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "Tente ajustar a busca",
                                            color = TextGrayLightMode,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Lista de motoristas
                        item {
                            android.util.Log.e("DEBUG", "🔵 AdminDevolucoesScreen: Renderizando lista - ${motoristas.size} motoristas")
                            println("🔵 AdminDevolucoesScreen: Renderizando lista - ${motoristas.size} motoristas")
                            Spacer(modifier = Modifier.height(0.dp)) // Item invisível para log
                        }
                        items(motoristas) { motorista ->
                            android.util.Log.e("DEBUG", "🔵 AdminDevolucoesScreen: Renderizando item - ${motorista.motoristaNome}")
                            println("🔵 AdminDevolucoesScreen: Renderizando item - ${motorista.motoristaNome}")
                            AppCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    android.util.Log.e("DEBUG", "🔵 [HYP-A] AdminDevolucoesScreen: CLICK DETECTED - motorista: ${motorista.motoristaNome}, ID: ${motorista.motoristaId}")
                                    println("🔵 [HYP-A] AdminDevolucoesScreen: Click detected - motorista: ${motorista.motoristaNome}, ID: ${motorista.motoristaId}")
                                    android.util.Log.e("DEBUG", "🔵 [HYP-B] AdminDevolucoesScreen: About to call onMotoristaClick - callback is null: ${onMotoristaClick == null}")
                                    println("🔵 [HYP-B] AdminDevolucoesScreen: About to call onMotoristaClick - callback is null: ${onMotoristaClick == null}")
                                    try {
                                        onMotoristaClick(motorista.motoristaId, motorista.motoristaNome)
                                        android.util.Log.e("DEBUG", "🔵 [HYP-B] AdminDevolucoesScreen: onMotoristaClick callback executed SUCCESSFULLY")
                                        println("🔵 [HYP-B] AdminDevolucoesScreen: onMotoristaClick callback executed")
                                    } catch (e: Exception) {
                                        android.util.Log.e("DEBUG", "❌ [HYP-B] AdminDevolucoesScreen: ERROR calling onMotoristaClick - ${e.message}")
                                        println("❌ [HYP-B] AdminDevolucoesScreen: ERROR calling onMotoristaClick - ${e.message}")
                                        e.printStackTrace()
                                    }
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
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MeliBlue,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Column {
                                            Text(
                                                motorista.motoristaNome,
                                                color = TextBlack,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                if (motorista.totalDevolucoes == 1) {
                                                    "01 devolução"
                                                } else {
                                                    String.format("%02d devoluções", motorista.totalDevolucoes)
                                                },
                                                color = TextGrayLightMode,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Ver detalhes",
                                        tint = TextGrayLightMode
                                    )
                                }
                            }
                        }
                    }
                }
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
