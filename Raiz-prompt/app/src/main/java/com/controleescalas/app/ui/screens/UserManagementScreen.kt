package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.controleescalas.app.ui.components.CustomTextField
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.NeonButton
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.data.models.AdminMotoristaCardData
import com.controleescalas.app.ui.viewmodels.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    navController: NavController,
    viewModel: AdminViewModel,
    baseId: String,
    currentUserId: String
) {
    val motoristas by viewModel.motoristas.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()

    var showAddUserDialog by remember { mutableStateOf(false) }
    var showEditUserDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedMotorista by remember { mutableStateOf<AdminMotoristaCardData?>(null) }
    var searchQuery by remember { mutableStateOf("") } // ✅ NOVO: Campo de pesquisa
    
    LaunchedEffect(baseId) {
        viewModel.loadMotoristas(baseId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Gestão de Equipe", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddUserDialog = true },
                containerColor = NeonGreen,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Usuário")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // ✅ NOVO: Campo de pesquisa logo abaixo do TopAppBar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    placeholder = { Text("Pesquisar...", color = TextGray) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Pesquisar",
                            tint = NeonGreen
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = TextGray,
                        focusedLabelColor = NeonGreen,
                        unfocusedLabelColor = TextGray,
                        focusedPlaceholderColor = TextGray,
                        unfocusedPlaceholderColor = TextGray
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                SectionHeader(title = "Membros da Equipe")
                
                // ✅ Filtrar motoristas por nome ou telefone
                val filteredMotoristas = remember(motoristas, searchQuery) {
                    if (searchQuery.isBlank()) {
                        motoristas
                    } else {
                        val query = searchQuery.lowercase().trim()
                        motoristas.filter {
                            it.nome.lowercase().contains(query) ||
                            it.telefone.contains(query)
                        }
                    }
                }
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonGreen)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredMotoristas) { motorista ->
                            UserCard(
                                motorista = motorista,
                                onEdit = {
                                    selectedMotorista = motorista
                                    showEditUserDialog = true
                                },
                                onDelete = {
                                    selectedMotorista = motorista
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }
            
            if (showAddUserDialog) {
                AddUserDialog(
                    onDismiss = { showAddUserDialog = false },
                    onConfirm = { nome, telefone, pin, papel, modalidade ->
                        viewModel.createMotorista(
                            baseId = baseId,
                            nome = nome,
                            telefone = telefone,
                            pin = pin,
                            papel = papel,
                            modalidade = if (papel == "admin") "" else modalidade, // Admin não tem modalidade
                            criadoPor = currentUserId
                        )
                        showAddUserDialog = false
                    }
                )
            }
            
            if (showEditUserDialog && selectedMotorista != null) {
                EditUserDialog(
                    motorista = selectedMotorista!!,
                    onDismiss = {
                        showEditUserDialog = false
                        selectedMotorista = null
                    },
                    onConfirm = { nome, telefone, modalidade, pin, funcao ->
                        viewModel.updateMotorista(
                            motoristaId = selectedMotorista!!.id,
                            baseId = baseId,
                            nome = nome,
                            telefone = telefone,
                            modalidade = modalidade,
                            pin = pin, // PIN opcional
                            funcao = funcao // ✅ NOVO: Função (papel)
                        )
                        showEditUserDialog = false
                        selectedMotorista = null
                    }
                )
            }
            
            if (showDeleteConfirmDialog && selectedMotorista != null) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteConfirmDialog = false
                        selectedMotorista = null
                    },
                    containerColor = DarkSurface,
                    title = { Text("Confirmar Exclusão", color = TextWhite) },
                    text = {
                        Text(
                            "Tem certeza que deseja remover ${selectedMotorista!!.nome}?",
                            color = TextGray
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.removeUser(selectedMotorista!!.id, baseId)
                                showDeleteConfirmDialog = false
                                selectedMotorista = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444)
                            )
                        ) {
                            Text("Excluir")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = {
                                showDeleteConfirmDialog = false
                                selectedMotorista = null
                            }
                        ) {
                            Text("Cancelar", color = TextGray)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UserCard(
    motorista: AdminMotoristaCardData,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        when(motorista.papel) {
                            "admin" -> NeonPurple
                            "auxiliar" -> NeonBlue
                            else -> NeonGreen // Motorista
                        }, 
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = motorista.nome.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (motorista.papel == "motorista") Color.Black else TextWhite
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = motorista.nome,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )
                Text(
                    text = motorista.telefone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                // Mostrar Modalidade se for motorista
                if (motorista.papel == "motorista") {
                    Surface(
                        color = when(motorista.modalidade) {
                            "DEDICADO" -> Color(0xFFE65100) // Laranja Escuro
                            "UTILITARIO" -> Color(0xFF00ACC1) // Ciano
                            "PASSEIO" -> Color(0xFF7CB342) // Verde Claro
                            else -> Color(0xFF3949AB) // Azul Indigo (FROTA)
                        },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = motorista.modalidade,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            // Botões de Editar e Excluir
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = NeonBlue
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = Color(0xFFEF4444)
                    )
                }
            }
            
            if (motorista.papel != "motorista") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = motorista.papel.replaceFirstChar { it.uppercaseChar() },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var telefone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var papel by remember { mutableStateOf("motorista") } // motorista, auxiliar
    var modalidade by remember { mutableStateOf("FROTA") } // FROTA, PASSEIO, DEDICADO, UTILITARIO
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Novo Usuário", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CustomTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = "Nome",
                    leadingIcon = Icons.Default.Person
                )
                
                CustomTextField(
                    value = telefone,
                    onValueChange = { telefone = it },
                    label = "Telefone",
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )
                
                CustomTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = "PIN (Senha)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                )
                
                // Seletor de Papel
                Text("Função:", color = TextGray, style = MaterialTheme.typography.bodySmall)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = papel == "motorista",
                            onClick = { papel = "motorista" },
                            label = { Text("Motorista") },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonGreen,
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = papel == "auxiliar",
                            onClick = { papel = "auxiliar" },
                            label = { Text("Auxiliar") },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonBlue,
                                selectedLabelColor = TextWhite
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = papel == "admin",
                            onClick = { papel = "admin" },
                            label = { Text("Admin") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple,
                                selectedLabelColor = TextWhite
                            )
                        )
                    }
                }
                
                // Seletor de Modalidade (apenas se for motorista ou auxiliar)
                if (papel == "motorista" || papel == "auxiliar") {
                    Text("Modalidade:", color = TextGray, style = MaterialTheme.typography.bodySmall)
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = modalidade == "FROTA",
                                onClick = { modalidade = "FROTA" },
                                label = { Text("Frota") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = modalidade == "PASSEIO",
                                onClick = { modalidade = "PASSEIO" },
                                label = { Text("Passeio") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = modalidade == "DEDICADO",
                                onClick = { modalidade = "DEDICADO" },
                                label = { Text("Dedicado") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = modalidade == "UTILITARIO",
                                onClick = { modalidade = "UTILITARIO" },
                                label = { Text("Utilitário") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            NeonButton(
                text = "Criar",
                onClick = { onConfirm(nome, telefone, pin, papel, modalidade) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextGray)
            }
        }
    )
}

@Composable
fun EditUserDialog(
    motorista: AdminMotoristaCardData,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String?, String) -> Unit // nome, telefone, modalidade, pin (opcional), funcao
) {
    var nome by remember { mutableStateOf(motorista.nome) }
    var telefone by remember { mutableStateOf(motorista.telefone) }
    var modalidade by remember { mutableStateOf(motorista.modalidade) }
    var pin by remember { mutableStateOf("") } // ✅ Campo PIN (vazio - admin pode alterar se quiser)
    var funcao by remember { mutableStateOf(motorista.papel) } // ✅ NOVO: Campo Função (papel atual)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Editar Motorista", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CustomTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = "Nome",
                    leadingIcon = Icons.Default.Person
                )
                
                CustomTextField(
                    value = telefone,
                    onValueChange = { telefone = it },
                    label = "Telefone",
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )
                
                // ✅ NOVO: Campo PIN (opcional - só atualiza se preenchido)
                CustomTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = "PIN (Senha) - Deixe vazio para manter atual",
                    leadingIcon = Icons.Default.Lock,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                
                // Seletor de Modalidade (somente para motoristas)
                if (motorista.papel == "motorista") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Modalidade:", 
                            color = TextWhite, 
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // ✅ Organização em 2 linhas (2x2) - mais organizado
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = modalidade == "FROTA",
                                onClick = { modalidade = "FROTA" },
                                label = { Text("Frota", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfaceVariant,
                                    selectedContainerColor = NeonBlue,
                                    labelColor = TextGray,
                                    selectedLabelColor = Color.Black
                                )
                            )
                            FilterChip(
                                selected = modalidade == "PASSEIO",
                                onClick = { modalidade = "PASSEIO" },
                                label = { Text("Passeio", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfaceVariant,
                                    selectedContainerColor = NeonBlue,
                                    labelColor = TextGray,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = modalidade == "DEDICADO",
                                onClick = { modalidade = "DEDICADO" },
                                label = { Text("Dedicado", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfaceVariant,
                                    selectedContainerColor = NeonBlue,
                                    labelColor = TextGray,
                                    selectedLabelColor = Color.Black
                                )
                            )
                            FilterChip(
                                selected = modalidade == "UTILITARIO",
                                onClick = { modalidade = "UTILITARIO" },
                                label = { Text("Utilitário", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfaceVariant,
                                    selectedContainerColor = NeonBlue,
                                    labelColor = TextGray,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
                
                // ✅ NOVO: Seletor de Função (sempre visível, exceto para superadmin)
                if (motorista.papel != "superadmin") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Função:",
                            color = TextWhite,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // Layout em 2 linhas: Motorista e Auxiliar na primeira, Admin na segunda
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = funcao == "motorista",
                                onClick = { funcao = "motorista" },
                                label = { Text("Motorista", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfaceVariant,
                                    selectedContainerColor = NeonBlue,
                                    labelColor = TextGray,
                                    selectedLabelColor = Color.Black
                                )
                            )
                            FilterChip(
                                selected = funcao == "auxiliar",
                                onClick = { funcao = "auxiliar" },
                                label = { Text("Auxiliar", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfaceVariant,
                                    selectedContainerColor = NeonBlue,
                                    labelColor = TextGray,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = funcao == "admin",
                                onClick = { funcao = "admin" },
                                label = { Text("Admin", style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = DarkSurfaceVariant,
                                    selectedContainerColor = NeonBlue,
                                    labelColor = TextGray,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            NeonButton(
                text = "Salvar",
                onClick = { 
                    // ✅ Passar PIN apenas se foi preenchido (não vazio)
                    val pinToUpdate = if (pin.isNotBlank()) pin else null
                    onConfirm(nome, telefone, modalidade, pinToUpdate, funcao) 
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextGray)
            }
        }
    )
}
