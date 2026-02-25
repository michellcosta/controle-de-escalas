package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.luminance
import com.controleescalas.app.ui.components.PremiumBackground
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import java.util.Calendar
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.controleescalas.app.ui.components.DisponibilidadeCard
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DisponibilidadeViewModel
import com.controleescalas.app.ui.viewmodels.QuinzenaViewModel
import com.controleescalas.app.ui.viewmodels.DriverViewModel
import com.controleescalas.app.data.repositories.EscalaRepository
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Scaffold principal para o app dos motoristas com navegação inferior
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverAppScaffold(
    motoristaId: String,
    baseId: String,
    onLogout: () -> Unit
) {
    // VALIDAÇÃO: Se os valores estiverem vazios, mostrar erro e redirecionar
    if (motoristaId.isBlank() || baseId.isBlank()) {
        LaunchedEffect(Unit) {
            println("❌ DriverAppScaffold: motoristaId ou baseId está vazio! motoristaId='$motoristaId', baseId='$baseId'")
            android.util.Log.e("DriverAppScaffold", "❌ motoristaId ou baseId está vazio! motoristaId='$motoristaId', baseId='$baseId'")
            // Redirecionar para login
            onLogout()
        }
        // Mostrar mensagem de erro temporariamente
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Erro: Dados de login inválidos. Redirecionando...",
                color = Color.Red
            )
        }
        return
    }
    
    val navController = rememberNavController()
    val viewModel: DriverViewModel = viewModel()
    val motoristaNome by viewModel.motoristaNome.collectAsState()
    
    // Permissões centralizadas na MainActivity - carregar dados diretamente
    LaunchedEffect(motoristaId, baseId) {
        android.util.Log.i("DriverAppScaffold", "🚀 [MOTORISTA] Iniciando - motoristaId=$motoristaId, baseId=$baseId")
        viewModel.observeMotoristaNome(motoristaId, baseId)
        viewModel.observeEscalaMotorista(motoristaId, baseId)
        viewModel.observeStatusMotorista(motoristaId, baseId)
        viewModel.loadDriverData(motoristaId, baseId)
    }
    
    DisposableEffect(motoristaNome) {
        android.util.Log.d("DriverAppScaffold", "📝 motoristaNome mudou para: $motoristaNome")
        onDispose { }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        val displayText = motoristaNome ?: "Minha Jornada"
                        android.util.Log.d("DriverAppScaffold", "🎨 Renderizando TopAppBar com texto: $displayText")
                        Text(
                            text = displayText,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (motoristaNome != null) {
                            Text(
                                text = "Minha Jornada",
                                color = TextGray,
                                style = MaterialTheme.typography.bodySmall
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
        bottomBar = {
            DriverBottomNavigationBar(navController = navController)
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = DriverNavItem.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(DriverNavItem.Home.route) {
                DriverHomeContent(
                    motoristaId = motoristaId,
                    baseId = baseId,
                    viewModel = viewModel,
                    onConfirmarChamada = {
                        viewModel.confirmarChamada(motoristaId, baseId)
                    }
                )
            }
            
            composable(DriverNavItem.Availability.route) {
                val disponibilidadeViewModel: DisponibilidadeViewModel = viewModel()
                DriverAvailabilityScreen(
                    baseId = baseId,
                    motoristaId = motoristaId,
                    viewModel = disponibilidadeViewModel
                )
            }
            
            composable(DriverNavItem.Config.route) {
                var showSobreApp by remember { mutableStateOf(false) }
                var showAjuda by remember { mutableStateOf(false) }
                var showTermos by remember { mutableStateOf(false) }
                var showNotificacoes by remember { mutableStateOf(false) }
                
                DriverConfigScreen(
                    baseId = baseId,
                    motoristaId = motoristaId,
                    onIdentificacaoClick = {
                        navController.navigate("driver_identificacao")
                    },
                    onQuinzenaClick = {
                        navController.navigate("driver_quinzena")
                    },
                    onDevolucaoClick = {
                        navController.navigate("driver_devolucao")
                    },
                    onNotificacoesClick = { showNotificacoes = true },
                    onSobreAppClick = { showSobreApp = true },
                    onAjudaClick = { showAjuda = true },
                    onTermosClick = { showTermos = true },
                    onLogout = onLogout
                )
                
                // Diálogos
                if (showSobreApp) {
                    SobreAppDialog(onDismiss = { showSobreApp = false })
                }
                if (showAjuda) {
                    AjudaDialog(onDismiss = { showAjuda = false })
                }
                if (showTermos) {
                    TermosDialog(onDismiss = { showTermos = false })
                }
                if (showNotificacoes) {
                    NotificacoesDialog(onDismiss = { showNotificacoes = false })
                }
            }
            
            // Rota para Identificação (acessada via Config)
            composable("driver_identificacao") {
                DriverIdentificacaoScreen(
                    motoristaId = motoristaId,
                    onDismiss = { navController.popBackStack() }
                )
            }
            
            // Rota separada para Quinzena (acessada via Config)
            composable("driver_quinzena") {
                val quinzenaViewModel: QuinzenaViewModel = viewModel()
                DriverQuinzenaScreen(
                    baseId = baseId,
                    motoristaId = motoristaId,
                    viewModel = quinzenaViewModel
                )
            }
            
            // Rota para Devolução (acessada via Config)
            composable("driver_devolucao") {
                val devolucaoViewModel: com.controleescalas.app.ui.viewmodels.DevolucaoViewModel = viewModel()
                DriverDevolucaoScreen(
                    baseId = baseId,
                    motoristaId = motoristaId,
                    motoristaNome = motoristaNome ?: "Motorista",
                    onNavigateToHistorico = {
                        navController.navigate("driver_devolucao_historico")
                    },
                    onDismiss = {
                        navController.popBackStack()
                    },
                    viewModel = devolucaoViewModel
                )
            }
            
            // Rota para Histórico de Devoluções
            composable("driver_devolucao_historico") {
                val devolucaoViewModel: com.controleescalas.app.ui.viewmodels.DevolucaoViewModel = viewModel()
                DriverDevolucaoHistoricoScreen(
                    baseId = baseId,
                    motoristaId = motoristaId,
                    onDismiss = {
                        navController.popBackStack()
                    },
                    onNavigateToDetalhes = { devolucaoId ->
                        // Codificar o ID apenas se tiver caracteres especiais que precisam ser codificados
                        // Usar URL encoding para garantir que funciona com qualquer caractere
                        val encodedId = java.net.URLEncoder.encode(devolucaoId, "UTF-8")
                        println("🔍 DriverAppScaffold: Navegando para detalhes - ID original: $devolucaoId, ID codificado: $encodedId")
                        navController.navigate("driver_devolucao_detalhes/$encodedId")
                    },
                    viewModel = devolucaoViewModel
                )
            }
            
            // Rota para Detalhes da Devolução
            composable("driver_devolucao_detalhes/{devolucaoId}") { backStackEntry ->
                val devolucaoViewModel: com.controleescalas.app.ui.viewmodels.DevolucaoViewModel = viewModel()
                val devolucaoIdEncoded = backStackEntry.arguments?.getString("devolucaoId") ?: ""
                // Decodificar usando URL decoding
                val devolucaoId = try {
                    java.net.URLDecoder.decode(devolucaoIdEncoded, "UTF-8")
                } catch (e: Exception) {
                    // Se falhar, usar o valor original
                    devolucaoIdEncoded
                }
                
                println("🔍 DriverAppScaffold: DevolucaoId decodificado: $devolucaoId (original: $devolucaoIdEncoded)")
                
                DriverDevolucaoDetalhesScreen(
                    devolucaoId = devolucaoId,
                    baseId = baseId,
                    motoristaId = motoristaId,
                    onDismiss = {
                        navController.popBackStack()
                    },
                    viewModel = devolucaoViewModel
                )
            }
        }
    }
}


/**
 * Itens de navegação para motoristas
 */
sealed class DriverNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object Home : DriverNavItem(
        route = "driver_home",
        icon = Icons.Default.Home,
        label = "Operação"
    )
    
    object Availability : DriverNavItem(
        route = "driver_availability",
        icon = Icons.Default.DateRange,
        label = "Disponibilidade"
    )
    
    object Config : DriverNavItem(
        route = "driver_config",
        icon = Icons.Default.Settings,
        label = "Configuração"
    )
}

/**
 * Barra de navegação inferior para motoristas
 */
@Composable
fun DriverBottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        DriverNavItem.Home,
        DriverNavItem.Availability,
        DriverNavItem.Config
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDarkMode = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            border = BorderStroke(
                width = 0.5.dp,
                color = (if (isDarkMode) Color.White else Color.Black).copy(alpha = 0.15f)
            ),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    
                    val animatedWeight by animateFloatAsState(
                        targetValue = if (isSelected) 2.2f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                        label = "weight"
                    )
                    
                    val animatedColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else TextGray,
                        label = "color"
                    )

                    val animatedScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.1f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(animatedWeight)
                            .height(48.dp)
                            .padding(horizontal = 2.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) 
                                        else Color.Transparent
                            )
                            .clickable {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = animatedColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(animatedScale)
                            )
                            
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.label,
                                    color = animatedColor,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Conteúdo da tela Home do motorista (sem Scaffold próprio)
 */
@Composable
fun DriverHomeContent(
    motoristaId: String,
    baseId: String,
    viewModel: com.controleescalas.app.ui.viewmodels.DriverViewModel,
    onConfirmarChamada: (() -> Unit)? = null
) {
    val escalaInfo by viewModel.escalaInfo.collectAsState()
    val statusInfo by viewModel.statusInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    // Garantir que o nome seja observado também aqui
    val motoristaNome by viewModel.motoristaNome.collectAsState()
    
    // Os listeners já são iniciados no DriverAppScaffold, não precisamos iniciar novamente aqui
    // Apenas observamos os StateFlows que já estão sendo atualizados pelos listeners
    
    // Debug: Log quando statusInfo mudar
    LaunchedEffect(statusInfo) {
        println("🎨 DriverHomeContent: statusInfo atualizado - estado=${statusInfo?.estado}, mensagem=${statusInfo?.mensagem}")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = NeonGreen
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // STATUS PRINCIPAL - Destaque
                    // Só mostrar StatusCard se houver escala (motorista escalado)
                    // Isso garante que quando o motorista for removido da escala, o status não será exibido
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
                    
                    // ESCALA DO DIA - Compacta
                    EscalaCompactCard(escalaInfo = escalaInfo)
                }
            }
        }
        
        error?.let { errorMessage ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp, start = 16.dp, end = 16.dp),
                containerColor = Color.Red,
                contentColor = TextWhite
            ) {
                Text(errorMessage)
            }
        }
    }
}

/**
 * Versão da tela de disponibilidade adaptada para motoristas
 */
@Composable
fun DriverAvailabilityScreen(
    baseId: String,
    motoristaId: String,
    viewModel: DisponibilidadeViewModel
) {
    val disponibilidade by viewModel.disponibilidade.collectAsState()
    val message by viewModel.message.collectAsState()
    val minhaDisponibilidade by viewModel.minhaDisponibilidade.collectAsState()
    
    LaunchedEffect(baseId, motoristaId) {
        viewModel.carregarDisponibilidade(baseId)
        viewModel.carregarMinhaDisponibilidade(baseId, motoristaId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Card de disponibilidade atual
                minhaDisponibilidade?.let { disp ->
                    DisponibilidadeCard(
                        data = disponibilidade?.data ?: "",
                        jaRespondeu = disp.disponivel != null,
                        disponivel = disp.disponivel,
                        onMarcarDisponivel = {
                            viewModel.marcarDisponibilidade(
                                baseId, motoristaId, true
                            )
                        },
                        onMarcarIndisponivel = {
                            viewModel.marcarDisponibilidade(
                                baseId, motoristaId, false
                            )
                        }
                    )
                } ?: run {
                    // Não há solicitação pendente
                    GlassCard {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TextGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Nenhuma solicitação de disponibilidade",
                                color = TextGray,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Quando o administrador solicitar sua disponibilidade, ela aparecerá aqui.",
                                color = TextGray.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        
        // Snackbar para mensagens
        message?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp, start = 16.dp, end = 16.dp),
                containerColor = NeonGreen,
                contentColor = Color.Black
            ) {
                Text(msg)
            }
        }
    }
}

/**
 * Versão da tela de quinzena adaptada para motoristas
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DriverQuinzenaScreen(
    baseId: String,
    motoristaId: String,
    viewModel: QuinzenaViewModel,
    onBack: (() -> Unit)? = null // Opcional: para quando usado no contexto do admin
) {
    val minhaQuinzena by viewModel.minhaQuinzena.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Estado para controlar mês/ano selecionado
    val calendarAtual = remember { Calendar.getInstance() }
    var mesSelecionado by remember { mutableIntStateOf(calendarAtual.get(Calendar.MONTH)) } // 0-11
    var anoSelecionado by remember { mutableIntStateOf(calendarAtual.get(Calendar.YEAR)) }
    
    // Estado para controlar diálogo de informação
    var showInfoDialog by remember { mutableStateOf(false) }
    
    // Carregar quinzena quando mudar mês/ano
    LaunchedEffect(baseId, motoristaId, mesSelecionado, anoSelecionado) {
        viewModel.carregarMinhaQuinzenaPorMes(
            baseId, 
            motoristaId, 
            mesSelecionado + 1, // Converter para 1-12
            anoSelecionado
        )
    }
    
    // Nome do mês (compacto)
    val nomeMes = remember(mesSelecionado) {
        val months = arrayOf(
            "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
            "Jul", "Ago", "Set", "Out", "Nov", "Dez"
        )
        months[mesSelecionado]
    }
    
    // Último dia do mês
    val ultimoDiaMes = remember(mesSelecionado, anoSelecionado) {
        val cal = Calendar.getInstance()
        cal.set(anoSelecionado, mesSelecionado, 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    
    // Lista de datas trabalhadas (concluídas) - permite duplicação para detectar múltiplas marcações
    val datasTrabalhadas = remember(minhaQuinzena) {
        val primeira = minhaQuinzena?.primeiraQuinzena?.datas ?: emptyList()
        val segunda = minhaQuinzena?.segundaQuinzena?.datas ?: emptyList()
        primeira + segunda // Mantém como List para permitir duplicação
    }
    
    // Lista de datas escaladas (mas não necessariamente concluídas)
    val escalaRepository = remember { EscalaRepository() }
    var datasEscaladas by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Buscar escalas do mês quando mudar mês/ano
    LaunchedEffect(baseId, motoristaId, mesSelecionado, anoSelecionado) {
        val escalas = escalaRepository.getEscalasDoMes(
            baseId = baseId,
            motoristaId = motoristaId,
            mes = mesSelecionado + 1, // Converter para 1-12
            ano = anoSelecionado
        )
        datasEscaladas = escalas
    }
    
    // Detectar se é modo admin (editável)
    val isEditable = onBack != null
    
    // Coroutine scope para chamadas suspensas
    val coroutineScope = rememberCoroutineScope()
    
    // Função para incrementar/remover dia trabalhado (apenas no modo admin)
    val onDayClick: (String) -> Unit = { data ->
        if (isEditable && minhaQuinzena != null) {
            coroutineScope.launch {
                try {
                    // Verificar quantas vezes o dia já está marcado
                    val vezesMarcado = datasTrabalhadas.count { it == data }
                    
                    if (vezesMarcado >= 2) {
                        // 3º clique (ou mais): remover todos os dias trabalhados daquele dia
                        println("🗑️ Removendo todos os dias trabalhados do dia $data (já estava marcado $vezesMarcado vezes)")
                        viewModel.removerDiaTrabalhado(
                            baseId = baseId,
                            motoristaId = motoristaId,
                            motoristaNome = minhaQuinzena!!.motoristaNome,
                            data = data
                        )
                    } else {
                        // 1º ou 2º clique: adicionar mais um dia trabalhado
                        println("➕ Adicionando dia trabalhado no dia $data (já estava marcado $vezesMarcado vezes)")
                        viewModel.incrementarDiaTrabalhado(
                            baseId = baseId,
                            motoristaId = motoristaId,
                            motoristaNome = minhaQuinzena!!.motoristaNome,
                            data = data
                        )
                    }
                    
                    // Recarregar a quinzena após a edição
                    viewModel.carregarMinhaQuinzenaPorMes(
                        baseId,
                        motoristaId,
                        mesSelecionado + 1,
                        anoSelecionado
                    )
                } catch (e: Exception) {
                    // Log do erro (pode adicionar um snackbar se necessário)
                    println("Erro ao editar dia trabalhado: ${e.message}")
                }
            }
        }
    }
    
    // Funções de navegação
    fun irParaMesAnterior() {
        if (mesSelecionado == 0) {
            mesSelecionado = 11
            anoSelecionado--
        } else {
            mesSelecionado--
        }
    }
    
    fun irParaProximoMes() {
        if (mesSelecionado == 11) {
            mesSelecionado = 0
            anoSelecionado++
        } else {
            mesSelecionado++
        }
    }
    
    // Conteúdo principal da tela
    val screenContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            PremiumBackground(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = NeonGreen
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Título do Mês com navegação
                        item {
                            GlassCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Botão anterior
                                    IconButton(
                                        onClick = { irParaMesAnterior() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Mês anterior",
                                            tint = NeonBlue
                                        )
                                    }
                                    
                                    // Nome do mês e ano (compacto)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = nomeMes,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = anoSelecionado.toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextGray
                                        )
                                    }
                                    
                                    // Botão de informação e navegação
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Ícone de informação
                                        IconButton(
                                            onClick = { showInfoDialog = true }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "Informações sobre a quinzena",
                                                tint = NeonGreen
                                            )
                                        }
                                        
                                        // Botão próximo
                                        IconButton(
                                            onClick = { irParaProximoMes() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = "Próximo mês",
                                                tint = NeonBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Primeiro Calendário: Primeira Quinzena (01 a 15)
                        item {
                            QuinzenaCalendarSection(
                                titulo = "Primeira Quinzena",
                                diasInicio = 1,
                                diasFim = 15,
                                mes = mesSelecionado + 1, // Calendar usa 0-11, precisamos 1-12
                                ano = anoSelecionado,
                                datasTrabalhadas = datasTrabalhadas,
                                datasEscaladas = datasEscaladas,
                                cor = NeonBlue,
                                isEditable = isEditable,
                                onDayClick = if (isEditable) onDayClick else null
                            )
                        }
                        
                        // Segundo Calendário: Segunda Quinzena (16 até o último dia)
                        item {
                            QuinzenaCalendarSection(
                                titulo = "Segunda Quinzena",
                                diasInicio = 16,
                                diasFim = ultimoDiaMes,
                                mes = mesSelecionado + 1,
                                ano = anoSelecionado,
                                datasTrabalhadas = datasTrabalhadas,
                                datasEscaladas = datasEscaladas,
                                cor = NeonBlue,
                                isEditable = isEditable,
                                onDayClick = if (isEditable) onDayClick else null
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Se onBack for fornecido, usar Scaffold com TopAppBar (contexto do admin)
    if (onBack != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = minhaQuinzena?.motoristaNome ?: "Quinzena",
                            color = TextWhite
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = TextWhite
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBackground
                    )
                )
            },
            containerColor = DarkBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                screenContent()
            }
        }
    } else {
        // Caso contrário, usar apenas o conteúdo (contexto do motorista)
        screenContent()
    }
    
    // Diálogo de informação sobre a quinzena
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    text = "Como funciona a Quinzena",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Esta página mostra os dias trabalhados durante o mês, divididos em duas quinzenas (1ª quinzena: dias 1-15, 2ª quinzena: dias 16 até o fim do mês).",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        text = "Legenda das cores:",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    // Legenda Laranja
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = NeonOrange.copy(alpha = 0.3f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .border(
                                    width = 2.dp,
                                    color = NeonOrange.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        Text(
                            text = "Laranja: Dia concluído 1 vez",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // Legenda Vermelho
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = StatusError.copy(alpha = 0.3f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .border(
                                    width = 2.dp,
                                    color = StatusError.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        Text(
                            text = "Vermelho: Dia marcado 2 vezes",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // Legenda Azul
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = NeonBlue.copy(alpha = 0.1f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .border(
                                    width = 1.dp,
                                    color = NeonBlue.copy(alpha = 0.3f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                        Text(
                            text = "Azul: Dia não concluído",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfoDialog = false }
                ) {
                    Text("Fechar", color = NeonGreen)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextWhite,
            textContentColor = TextWhite,
            shape = MaterialTheme.shapes.large
        )
    }
}

/**
 * Componente de calendário para uma seção de quinzena
 */
@Composable
fun QuinzenaCalendarSection(
    titulo: String,
    diasInicio: Int,
    diasFim: Int,
    mes: Int,
    ano: Int,
    datasTrabalhadas: List<String>, // Datas concluídas (permite duplicação - vermelho se 2+ vezes)
    datasEscaladas: Set<String>, // Datas escaladas mas não concluídas (azul)
    cor: androidx.compose.ui.graphics.Color,
    isEditable: Boolean = false, // Se true, permite clicar nos dias para editar
    onDayClick: ((String) -> Unit)? = null // Callback quando clicar em um dia (apenas se isEditable == true)
) {
    GlassCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título da seção
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleLarge,
                color = cor,
                fontWeight = FontWeight.Bold
            )
            
            // Grid de dias
            val dias = (diasInicio..diasFim).toList()
            val diasPorLinha = 7
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dias.chunked(diasPorLinha).forEach { linha ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        linha.forEach { dia ->
                            val dataFormatada = formatarDataParaComparacao(dia, mes, ano)
                            // ✅ NOVO: Contar quantas vezes a data foi marcada (permite duplicação)
                            val vezesMarcado = datasTrabalhadas.count { it == dataFormatada }
                            val concluido = vezesMarcado > 0
                            val escalado = datasEscaladas.contains(dataFormatada)
                            
                            // Determinar cor baseado no estado
                            val corDia = when {
                                vezesMarcado >= 2 -> StatusError // Vermelho para duplicado (2+ vezes)
                                concluido -> NeonOrange // Laranja para concluído (1 vez)
                                escalado -> NeonBlue // Azul para escalado (mas não concluído)
                                else -> cor // Cor padrão (cinza/transparente)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                // Caixa principal com cor baseada no estado
                                // Apenas concluído tem cor mais forte, escalado fica fraco (igual ao padrão)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (isEditable && onDayClick != null) {
                                                Modifier.clickable {
                                                    onDayClick(dataFormatada)
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .background(
                                            // Apenas concluído tem cor mais forte (0.3f), escalado fica fraco (0.1f)
                                            color = corDia.copy(alpha = if (concluido) 0.3f else 0.1f),
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .border(
                                            // Apenas concluído tem borda mais grossa (2.dp), escalado fica fino (1.dp)
                                            width = if (concluido) 2.dp else 1.dp,
                                            // Apenas concluído tem borda mais forte (0.6f), escalado fica fraco (0.3f)
                                            color = corDia.copy(alpha = if (concluido) 0.6f else 0.3f),
                                            shape = MaterialTheme.shapes.small
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Número do dia
                                    Text(
                                        text = dia.toString().padStart(2, '0'),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        // Apenas concluído tem texto em negrito
                                        fontWeight = if (concluido) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        
                        // Preencher espaços vazios se necessário
                        repeat(diasPorLinha - linha.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            
            // Contador de dias trabalhados nesta seção (total de vezes marcado nos dias desta quinzena)
            val diasTrabalhadosNestaSecao = dias.sumOf { dia ->
                val dataFormatada = formatarDataParaComparacao(dia, mes, ano)
                datasTrabalhadas.count { it == dataFormatada }
            }
            
            HorizontalDivider(color = TextGray.copy(alpha = 0.3f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dias trabalhados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                Text(
                    text = "$diasTrabalhadosNestaSecao / ${diasFim - diasInicio + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = cor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Diálogo Sobre o App
 */
@Composable
fun SobreAppDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Sobre o App",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Controle de Escalas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Versão 1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                Text(
                    "Desenvolvido por: Michell Oliveira",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Aplicativo para gestão de escalas de motoristas e controle de disponibilidade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = NeonGreen)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = TextWhite,
        textContentColor = TextGray
    )
}

/**
 * Diálogo com instruções para notificações em Xiaomi/Huawei
 */
@Composable
fun NotificacoesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Notificações não aparecem?",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Em Xiaomi, Redmi, Huawei e similares, o celular pode bloquear notificações quando o app está fechado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                Text(
                    "Para receber chamadas com o app fechado:\n\n" +
                    "1. Configurações → Apps → Controle de Escalas\n" +
                    "2. Economia de bateria → \"Sem restrições\"\n" +
                    "3. Permissões → Inicialização automática → Ative\n" +
                    "4. Notificações → Libere todas",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) {
                Text("Abrir configurações do app")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = TextGray)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = TextWhite,
        textContentColor = TextGray
    )
}

/**
 * Diálogo de Ajuda
 */
@Composable
fun AjudaDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Ajuda",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Como usar o app:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "• Início: Visualize sua escala do dia e status atual\n" +
                    "• Disponibilidade: Responda se está disponível para trabalhar amanhã\n" +
                    "• Configuração: Acesse sua quinzena e outras opções",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Para suporte, entre em contato com o administrador da sua transportadora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = NeonGreen)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = TextWhite,
        textContentColor = TextGray
    )
}

/**
 * Diálogo de Termos de Uso
 */
@Composable
fun TermosDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Termos de Uso",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Ao usar este aplicativo, você concorda com os seguintes termos:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                Text(
                    "• O aplicativo é destinado exclusivamente para uso profissional\n" +
                    "• Os dados fornecidos são de responsabilidade do usuário\n" +
                    "• O uso indevido pode resultar em bloqueio da conta\n" +
                    "• Os dados são confidenciais e não devem ser compartilhados com terceiros\n" +
                    "• Os dados coletados são propriedade da transportadora\n" +
                    "• O aplicativo utiliza GPS para localização. Ao usar o app, você autoriza o uso da sua localização\n" +
                    "• O aplicativo pode apresentar interrupções temporárias para manutenção ou melhorias\n" +
                    "• O desenvolvedor se reserva o direito de modificar os termos a qualquer momento",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Para mais informações, entre em contato com o administrador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = NeonGreen)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = TextWhite,
        textContentColor = TextGray
    )
}

/**
 * Formata data no formato "dd/MM/yyyy" para comparação
 */
fun formatarDataParaComparacao(dia: Int, mes: Int, ano: Int): String {
    return String.format("%02d/%02d/%04d", dia, mes, ano)
}

