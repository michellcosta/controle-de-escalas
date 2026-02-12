package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.models.Base
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.components.TransportadoraCardSkeleton
import androidx.compose.foundation.background
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.BaseApprovalViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminTransportadorasScreen(
    superAdminId: String,
    onNavigateToBase: (String) -> Unit,
    onVerDetalhes: (String) -> Unit = onNavigateToBase,
    initialStatusFilter: String? = null,
    viewModel: BaseApprovalViewModel = viewModel()
) {
    val bases by viewModel.bases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var filtroStatus by remember { mutableStateOf<String?>(initialStatusFilter) }
    var filtroPlano by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.loadBases()
    }
    
    // Filtrar bases
    val basesFiltradas = remember(bases, searchQuery, filtroStatus, filtroPlano) {
        bases.filter { base ->
            if (base.id == "super_admin_base") return@filter false
            
            val matchesSearch = searchQuery.isBlank() ||
                base.nome.lowercase().contains(searchQuery.lowercase()) ||
                base.transportadora.lowercase().contains(searchQuery.lowercase())
            
            val matchesStatus = filtroStatus == null || base.statusAprovacao == filtroStatus
            
            val matchesPlano = filtroPlano == null || (base.plano ?: "gratuito") == filtroPlano
            
            matchesSearch && matchesStatus && matchesPlano
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Campo de pesquisa
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { 
                Text(
                    "Pesquisar...",
                    color = TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                ) 
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Pesquisar", tint = NeonGreen)
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedBorderColor = NeonGreen,
                unfocusedBorderColor = TextGray
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        // Filtros
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filtroStatus == null,
                onClick = { filtroStatus = null },
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
                selected = filtroStatus == "ativa",
                onClick = { filtroStatus = if (filtroStatus == "ativa") null else "ativa" },
                label = { 
                    Text(
                        "Ativas",
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
                selected = filtroStatus == "pendente",
                onClick = { filtroStatus = if (filtroStatus == "pendente") null else "pendente" },
                label = { 
                    Text(
                        "Pendentes",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonOrange,
                    selectedLabelColor = Color.White
                )
            )
            FilterChip(
                selected = filtroStatus == "rejeitada",
                onClick = { filtroStatus = if (filtroStatus == "rejeitada") null else "rejeitada" },
                label = { 
                    Text(
                        "Rejeitadas",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFEF4444),
                    selectedLabelColor = Color.White
                )
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filtroPlano == null,
                onClick = { filtroPlano = null },
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
                selected = filtroPlano == "premium",
                onClick = { filtroPlano = if (filtroPlano == "premium") null else "premium" },
                label = { 
                    Text(
                        "Premium",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonPurple,
                    selectedLabelColor = Color.White
                )
            )
            FilterChip(
                selected = filtroPlano == "gratuito",
                onClick = { filtroPlano = if (filtroPlano == "gratuito") null else "gratuito" },
                label = { 
                    Text(
                        "Gratuito",
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
                selected = filtroPlano == "trial",
                onClick = { filtroPlano = if (filtroPlano == "trial") null else "trial" },
                label = { 
                    Text(
                        "Trial",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonOrange,
                    selectedLabelColor = Color.White
                )
            )
        }
        
        // Lista de bases
        if (isLoading && basesFiltradas.isEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(5) {
                    TransportadoraCardSkeleton()
                }
            }
        } else if (basesFiltradas.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Nenhuma transportadora encontrada",
                        color = TextGray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(basesFiltradas) { base ->
                    TransportadoraCard(
                        base = base,
                        onNavigateToBase = { onNavigateToBase(base.id) },
                        onVerDetalhes = { onVerDetalhes(base.id) },
                        onAprovar = {
                            if (base.statusAprovacao == "pendente") {
                                viewModel.aprovarBase(base.id, superAdminId)
                            }
                        },
                        onRejeitar = {
                            if (base.statusAprovacao == "pendente") {
                                viewModel.rejeitarBase(base.id, superAdminId)
                            }
                        },
                        onDeletar = {
                            viewModel.deletarBase(base.id, superAdminId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TransportadoraCard(
    base: Base,
    onNavigateToBase: () -> Unit,
    onVerDetalhes: () -> Unit = onNavigateToBase, // ✅ NOVO: Callback para ver detalhes
    onAprovar: () -> Unit,
    onRejeitar: () -> Unit,
    onDeletar: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val statusColor = when (base.statusAprovacao) {
        "ativa" -> NeonGreen
        "pendente" -> NeonOrange
        "rejeitada" -> Color(0xFFEF4444)
        else -> TextGray
    }
    
    val statusText = when (base.statusAprovacao) {
        "ativa" -> "Ativa"
        "pendente" -> "Pendente"
        "rejeitada" -> "Rejeitada"
        else -> "Desconhecido"
    }
    
    val dataFormatada = remember(base.criadoEm) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.format(Date(base.criadoEm))
    }
    
    GlassCard(
        onClick = onNavigateToBase
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = base.nome,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = base.transportadora,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Badge de status
                    Surface(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Menu de ações
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = TextWhite
                        )
                    }
                }
            }
            
            // Informações adicionais
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Criada em: $dataFormatada",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                base.plano?.let { plano ->
                    Text(
                        text = "Plano: ${plano.replaceFirstChar { it.uppercaseChar() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (plano == "premium") NeonPurple else TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Botões de ação
            if (base.id != "super_admin_base") {
                HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
                
                // Botão Entrar (sempre disponível para bases ativas)
                if (base.statusAprovacao == "ativa") {
                    OutlinedButton(
                        onClick = onNavigateToBase,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ENTRAR")
                    }
                }
                
                // Botões de aprovação/rejeição (apenas se pendente)
                if (base.statusAprovacao == "pendente") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAprovar,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Aprovar")
                        }
                        Button(
                            onClick = onRejeitar,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rejeitar")
                        }
                    }
                }
            }
        }
    }
    
    // Menu dropdown
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        modifier = Modifier.background(DarkSurface)
    ) {
        DropdownMenuItem(
            text = { Text("Ver Detalhes", color = TextWhite) },
            onClick = {
                showMenu = false
                onVerDetalhes()
            }
        )
        if (base.statusAprovacao == "pendente") {
            DropdownMenuItem(
                text = { Text("Aprovar", color = NeonGreen) },
                onClick = {
                    showMenu = false
                    onAprovar()
                }
            )
            DropdownMenuItem(
                text = { Text("Rejeitar", color = Color(0xFFEF4444)) },
                onClick = {
                    showMenu = false
                    onRejeitar()
                }
            )
        }
        if (base.statusAprovacao == "ativa") {
            DropdownMenuItem(
                text = { Text("Deletar", color = Color(0xFFEF4444)) },
                onClick = {
                    showMenu = false
                    showDeleteDialog = true
                }
            )
        }
    }
    
    // Dialog de confirmação para deletar
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    "Confirmar Exclusão",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Tem certeza que deseja excluir a transportadora '${base.nome}'?\n\n" +
                    "⚠️ Esta ação não pode ser desfeita e excluirá todos os dados relacionados.",
                    color = TextGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeletar()
                    }
                ) {
                    Text("Deletar", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancelar", color = TextWhite)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextWhite,
            textContentColor = TextGray
        )
    }
}

