package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.models.Base
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.BaseApprovalViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseApprovalScreen(
    superAdminId: String,
    onNavigateToBase: (String) -> Unit,
    onNavigateToSistemaConfig: () -> Unit = {},
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: BaseApprovalViewModel = viewModel()
) {
    val bases by viewModel.bases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    
    // Estado para controlar qual aba está selecionada
    var selectedTab by remember { mutableStateOf(0) }
    
    // Estado para controlar menu dropdown
    var showMenu by remember { mutableStateOf(false) }
    
    // Separar bases pendentes e todas as bases
    val basesPendentes = remember(bases) {
        bases.filter { it.statusAprovacao == "pendente" }
    }
    val todasBases = remember(bases) {
        bases.sortedByDescending { it.criadoEm }
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadBases()
    }
    
    // Limpar mensagem de sucesso automaticamente após 3 segundos
    LaunchedEffect(message) {
        if (message != null) {
            delay(3000L) // 3 segundos
            viewModel.clearMessages()
        }
    }
    
    // Limpar mensagem de erro automaticamente após 5 segundos
    LaunchedEffect(error) {
        if (error != null) {
            delay(5000L) // 5 segundos
            viewModel.clearMessages()
        }
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Gestão de Transportadoras", color = MaterialTheme.colorScheme.onBackground) },
                actions = {
                    // Menu de 3 pontos
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    // DropdownMenu
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NeonBlue else NeonBlueContrast,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "Configurações do Sistema",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                showMenu = false
                                onNavigateToSistemaConfig()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "Sair da Conta",
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            },
                            onClick = {
                                showMenu = false
                                onLogout()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mensagens de erro/sucesso
                    error?.let {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = it,
                                color = Color.White,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    
                    message?.let {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NeonGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = it,
                                color = Color.Black,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    
                    // Tabs para alternar entre seções
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Todas as Transportadoras", color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { 
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Aprovações", color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (basesPendentes.isNotEmpty()) {
                                        Surface(
                                            color = NeonOrange,
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "${basesPendentes.size}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = Color.Black,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                    
                    // Conteúdo baseado na aba selecionada
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeonGreen)
                        }
                    } else {
                        when (selectedTab) {
                            0 -> {
                                // Seção 1: Todas as Transportadoras
                                if (todasBases.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Nenhuma transportadora encontrada",
                                            color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(todasBases) { base ->
                                            BaseListCard(
                                                base = base,
                                                onClick = {
                                                    onNavigateToBase(base.id)
                                                },
                                                onExcluir = {
                                                    viewModel.deletarBase(base.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // Seção 2: Aprovações Pendentes
                                if (basesPendentes.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = NeonGreen,
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Text(
                                                text = "Nenhuma aprovação pendente",
                                                color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) TextGray else TextGrayLightMode,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(basesPendentes) { base ->
                                            BaseApprovalCard(
                                                base = base,
                                                onAprovar = {
                                                    viewModel.aprovarBase(base.id, superAdminId)
                                                },
                                                onRejeitar = {
                                                    viewModel.rejeitarBase(base.id, superAdminId)
                                                },
                                                onEntrar = {
                                                    onNavigateToBase(base.id)
                                                },
                                                showAprovarRejeitar = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Novo componente para listar todas as transportadoras (clicável)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseListCard(
    base: Base,
    onClick: () -> Unit,
    onExcluir: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = base.transportadora,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = base.nome,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                if (base.criadoEm > 0) {
                    val dataFormatada = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        .format(Date(base.criadoEm))
                    Text(
                        text = "Criada em: $dataFormatada",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Badge de status
            Surface(
                color = when (base.statusAprovacao) {
                    "ativa" -> NeonGreen.copy(alpha = 0.3f)
                    "rejeitada" -> Color(0xFFEF4444).copy(alpha = 0.3f)
                    else -> NeonOrange.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = base.statusAprovacao.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (base.statusAprovacao) {
                        "ativa" -> NeonGreen
                        "rejeitada" -> Color(0xFFEF4444)
                        else -> NeonOrange
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // ✅ PROTEÇÃO: Não mostrar botão de excluir para a base do superadmin
            if (base.id != "super_admin_base") {
                // Botão de excluir
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Entrar",
                tint = NeonBlue,
                modifier = Modifier.size(24.dp)
            )
        }
    }
    
    // Diálogo de confirmação de exclusão
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    "Excluir Transportadora",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Tem certeza que deseja excluir a transportadora:",
                        color = TextGray
                    )
                    Text(
                        text = base.transportadora,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Esta ação não pode ser desfeita!",
                        color = Color(0xFFEF4444),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onExcluir()
                        showDeleteDialog = false
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
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancelar", color = TextGray)
                }
            }
        )
    }
}

@Composable
fun BaseApprovalCard(
    base: Base,
    onAprovar: () -> Unit,
    onRejeitar: () -> Unit,
    onEntrar: () -> Unit,
    showAprovarRejeitar: Boolean = true
) {
    GlassCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header com nome e status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = base.transportadora,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = base.nome,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
                    )
                }
                
                // Badge de status
                Surface(
                    color = when (base.statusAprovacao) {
                        "ativa" -> NeonGreen.copy(alpha = 0.3f)
                        "rejeitada" -> Color(0xFFEF4444).copy(alpha = 0.3f)
                        else -> NeonOrange.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = base.statusAprovacao.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (base.statusAprovacao) {
                            "ativa" -> NeonGreen
                            "rejeitada" -> Color(0xFFEF4444)
                            else -> NeonOrange
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            // Data de criação
            if (base.criadoEm > 0) {
                val dataFormatada = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .format(Date(base.criadoEm))
                Text(
                    text = "Criada em: $dataFormatada",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray.copy(alpha = 0.7f)
                )
            }
            
            // Botões de ação
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botão Entrar (sempre disponível) - linha completa
                OutlinedButton(
                    onClick = onEntrar,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                ) {
                    Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ENTRAR")
                }
                
                // ✅ PROTEÇÃO: Não mostrar botões de aprovação/rejeição para a base do superadmin
                // Botões de aprovação/rejeição (apenas se showAprovarRejeitar for true e for pendente)
                if (showAprovarRejeitar && base.statusAprovacao == "pendente" && base.id != "super_admin_base") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAprovar,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("APROVAR")
                        }
                        
                        Button(
                            onClick = onRejeitar,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("REJEITAR")
                        }
                    }
                }
            }
        }
    }
}

