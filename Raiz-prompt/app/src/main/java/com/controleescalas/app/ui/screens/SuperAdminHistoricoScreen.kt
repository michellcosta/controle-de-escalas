package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.controleescalas.app.data.models.AcaoHistorico
import com.controleescalas.app.data.repositories.HistoricoRepository
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminHistoricoScreen(
    superAdminId: String,
    onBack: () -> Unit
) {
    val historicoRepository = HistoricoRepository()
    val scope = rememberCoroutineScope()
    
    var acoes by remember { mutableStateOf<List<AcaoHistorico>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var filtroTipo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // ✅ OTIMIZADO: Carregar histórico uma vez ao invés de listener em tempo real
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isLoading = true
                acoes = historicoRepository.getAllAcoes()
            } catch (e: Exception) {
                error = "Erro ao carregar histórico: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    // ✅ Adicionar botão de refresh manual
    var isRefreshing by remember { mutableStateOf(false) }
    
    fun refreshHistorico() {
        scope.launch {
            try {
                isRefreshing = true
                acoes = historicoRepository.getAllAcoes(forceRefresh = true)
            } catch (e: Exception) {
                error = "Erro ao atualizar histórico: ${e.message}"
            } finally {
                isRefreshing = false
            }
        }
    }
    
    // Filtrar ações
    val acoesFiltradas = remember(acoes, filtroTipo) {
        if (filtroTipo == null) {
            acoes
        } else {
            acoes.filter { it.tipo == filtroTipo }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Ações", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshHistorico() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = NeonGreen,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Atualizar",
                                tint = TextWhite
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filtros
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filtroTipo == null,
                    onClick = { filtroTipo = null },
                    label = { 
                        Text(
                            "Todos",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = filtroTipo == "aprovacao",
                    onClick = { filtroTipo = if (filtroTipo == "aprovacao") null else "aprovacao" },
                    label = { 
                        Text(
                            "Aprovações",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonGreen,
                        selectedLabelColor = Color.Black
                    )
                )
                FilterChip(
                    selected = filtroTipo == "rejeicao",
                    onClick = { filtroTipo = if (filtroTipo == "rejeicao") null else "rejeicao" },
                    label = { 
                        Text(
                            "Rejeições",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEF4444),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = filtroTipo == "edicao",
                    onClick = { filtroTipo = if (filtroTipo == "edicao") null else "edicao" },
                    label = { 
                        Text(
                            "Edições",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonBlue,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = filtroTipo == "configuracao",
                    onClick = { filtroTipo = if (filtroTipo == "configuracao") null else "configuracao" },
                    label = { 
                        Text(
                            "Configurações",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonPurple,
                        selectedLabelColor = Color.White
                    )
                )
            }
            
            SectionHeader(title = "Ações (${acoesFiltradas.size})")
            
            if (isLoading && acoesFiltradas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonGreen)
                }
            } else if (acoesFiltradas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Nenhuma ação registrada",
                            color = TextGray,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(acoesFiltradas) { acao ->
                        AcaoHistoricoCard(acao = acao)
                    }
                }
            }
        }
    }
}

@Composable
fun AcaoHistoricoCard(acao: AcaoHistorico) {
    val tipoIcon = when (acao.tipo) {
        "aprovacao" -> Icons.Default.CheckCircle
        "rejeicao" -> Icons.Default.Close
        "edicao" -> Icons.Default.Edit
        "configuracao" -> Icons.Default.Settings
        else -> Icons.Default.Info
    }
    
    val tipoColor = when (acao.tipo) {
        "aprovacao" -> NeonGreen
        "rejeicao" -> Color(0xFFEF4444)
        "edicao" -> NeonBlue
        "configuracao" -> NeonPurple
        else -> TextGray
    }
    
    val tipoText = when (acao.tipo) {
        "aprovacao" -> "Aprovação"
        "rejeicao" -> "Rejeição"
        "edicao" -> "Edição"
        "configuracao" -> "Configuração"
        else -> acao.tipo
    }
    
    val dataFormatada = remember(acao.data) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(acao.data))
    }
    
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                tipoIcon,
                contentDescription = null,
                tint = tipoColor,
                modifier = Modifier.size(32.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tipoText,
                        style = MaterialTheme.typography.titleMedium,
                        color = tipoColor,
                        fontWeight = FontWeight.Bold
                    )
                    if (acao.baseNome != null) {
                        Text(
                            text = "• ${acao.baseNome}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Text(
                    text = acao.descricao,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextWhite,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Text(
                    text = dataFormatada,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        }
    }
}

