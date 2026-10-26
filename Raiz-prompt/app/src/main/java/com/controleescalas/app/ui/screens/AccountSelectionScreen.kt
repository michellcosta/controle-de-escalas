package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.data.SessionManager
import com.controleescalas.app.data.models.SavedSession
import com.controleescalas.app.ui.components.CustomTextField
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.NeonButton
import com.controleescalas.app.ui.components.PremiumBackground
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.LoginViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tela de seleção de conta/transportadora
 * Mostra todas as transportadoras e contas do usuário
 */
@Composable
fun AccountSelectionScreen(
    onAccountSelected: (SavedSession) -> Unit,
    onAddNewAccount: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    val loginViewModel: LoginViewModel = viewModel()
    
    val userSessions by sessionManager.userSessionsFlow.collectAsState(initial = com.controleescalas.app.data.models.UserSessions())
    
    // Estado para sessões válidas (bases que ainda existem)
    // IMPORTANTE: Inicializar com todas as sessões para mostrar imediatamente
    var validSessions by remember { mutableStateOf<List<com.controleescalas.app.data.models.SavedSession>>(emptyList()) }
    var isValidating by remember { mutableStateOf(false) }
    var lastValidationTime by remember { mutableStateOf(0L) }
    
    // Atualizar validSessions imediatamente quando userSessions mudar
    LaunchedEffect(userSessions.sessions.size) {
        if (userSessions.sessions.isNotEmpty() && validSessions.isEmpty()) {
            // Primeira vez ou após limpar: mostrar todas as sessões imediatamente
            validSessions = userSessions.sessions
            println("📋 AccountSelectionScreen: Mostrando ${userSessions.sessions.size} sessões imediatamente")
        }
    }
    
    // Validar sessões quando a lista mudar (com debounce para evitar loops)
    LaunchedEffect(userSessions.sessions) {
        if (userSessions.sessions.isEmpty()) {
            println("ℹ️ AccountSelectionScreen: Nenhuma sessão salva")
            validSessions = emptyList()
            isValidating = false
            return@LaunchedEffect
        }
        
        // Debounce: não validar se já validou há menos de 2 segundos
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastValidationTime < 2000) {
            println("⏭️ AccountSelectionScreen: Validação ignorada (muito recente)")
            return@LaunchedEffect
        }
        
        // Evitar validação simultânea
        if (isValidating) {
            println("⏭️ AccountSelectionScreen: Validação já em andamento, ignorando")
            return@LaunchedEffect
        }
        
    println("🔍 AccountSelectionScreen: Validando ${userSessions.sessions.size} sessões...")
    isValidating = true
    lastValidationTime = currentTime
    
    // Remover duplicatas antes de validar
    sessionManager.removeDuplicateSessions()
    
    // Adicionar delay para garantir que Firebase está pronto
    kotlinx.coroutines.delay(500L)
    
    val valid = mutableListOf<com.controleescalas.app.data.models.SavedSession>()
    val invalid = mutableListOf<com.controleescalas.app.data.models.SavedSession>()
    
    // ✅ VALIDAR TODAS as sessões, independente de quando foram salvas
    for (session in userSessions.sessions) {
        try {
            val isValid = sessionManager.validateSession(session)
            if (isValid) {
                valid.add(session)
                println("✅ AccountSelectionScreen: Sessão válida - ${session.baseName} (${session.userName})")
            } else {
                invalid.add(session)
                println("⚠️ AccountSelectionScreen: Sessão inválida (base não aprovada) - ${session.baseName} (${session.userName})")
            }
        } catch (e: Exception) {
            println("❌ AccountSelectionScreen: Erro ao validar sessão ${session.baseName}: ${e.message}")
            e.printStackTrace()
            // Em caso de erro, considerar inválida para forçar revalidação
            invalid.add(session)
            println("⚠️ AccountSelectionScreen: Sessão marcada como inválida devido a erro de validação")
        }
    }
        
        validSessions = valid
        isValidating = false
        
        println("✅ AccountSelectionScreen: Validação concluída - ${valid.size} válidas, ${invalid.size} inválidas")
        
        // Remover sessões inválidas APENAS se realmente inválidas (não por erro)
        if (invalid.isNotEmpty()) {
            println("⚠️ AccountSelectionScreen: Removendo ${invalid.size} sessões inválidas...")
            // Remover em batch para evitar múltiplos disparos do LaunchedEffect
            scope.launch {
                invalid.forEach { session ->
                    sessionManager.removeSession(session.userId, session.baseId)
                }
                // Pequeno delay após remoção para evitar loop
                kotlinx.coroutines.delay(1000L)
            }
        }
    }
    
    // Usar sessões válidas ao invés de todas as sessões
    val groupedSessions = remember(validSessions) {
        validSessions.groupBy { it.baseId }
    }
    
    // Estado para controlar o diálogo de login
    var showLoginDialog by remember { mutableStateOf(false) }
    var telefone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    
    val isLoading by loginViewModel.isLoading.collectAsState()
    val error by loginViewModel.error.collectAsState()
    val loginResult by loginViewModel.loginResult.collectAsState()
    
    // Processar resultado do login
    LaunchedEffect(loginResult) {
        loginResult?.let { result ->
            when (result) {
                is com.controleescalas.app.data.LoginResult.Success -> {
                    // Salvar sessão
                    // IMPORTANTE: Usar motoristaId (ID do documento), não telefone
                    val session = SavedSession(
                        userId = result.motoristaId, // ID do documento do motorista/admin
                        baseId = result.baseId,
                        baseName = result.baseName.ifBlank { "Transportadora" },
                        userName = result.nome,
                        userRole = result.papel
                    )
                    
                    // Aguardar salvamento antes de navegar
                    scope.launch {
                        sessionManager.saveSession(session)
                        println("✅ AccountSelectionScreen: Sessão salva - ${session.baseName} (${session.userName})")
                        
                        // Fechar diálogo e limpar campos
                        showLoginDialog = false
                        val telefoneSalvo = telefone
                        telefone = ""
                        pin = ""
                        loginViewModel.clearMessages()
                        
                        // Navegar para a tela apropriada
                        onAccountSelected(session)
                    }
                }
                is com.controleescalas.app.data.LoginResult.Error -> {
                    // Erro será mostrado no diálogo
                }
            }
        }
    }
    
    PremiumBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Voltar", tint = MaterialTheme.colorScheme.onBackground)
                }
                
                Text(
                    text = "Selecione uma Conta",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(48.dp)) // Balance
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (groupedSessions.isEmpty()) {
                // Nenhuma conta salva
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "Nenhuma conta salva",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Faça login em uma transportadora para salvar seu acesso",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Lista de transportadoras com contas
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    groupedSessions.forEach { (baseId, sessions) ->
                        item {
                            TransportadoraCard(
                                baseName = sessions.first().baseName,
                                accounts = sessions,
                                onAccountClick = { session ->
                                    scope.launch {
                                        sessionManager.saveSession(session)
                                        onAccountSelected(session)
                                    }
                                },
                                onRemoveAccount = { session ->
                                    scope.launch {
                                        sessionManager.removeSession(session.userId, session.baseId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Botão adicionar nova conta - agora abre o diálogo
            NeonButton(
                text = "ADICIONAR NOVA CONTA",
                onClick = { showLoginDialog = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Add
            )
        }
    }
    
    // Diálogo de login
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { 
                showLoginDialog = false
                telefone = ""
                pin = ""
                loginViewModel.clearMessages()
            },
            containerColor = DarkSurface,
            title = {
                Text(
                    "Adicionar Nova Conta",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CustomTextField(
                        value = telefone,
                        onValueChange = { if (!isLoading) telefone = it },
                        label = "Telefone",
                        leadingIcon = Icons.Default.Phone,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    
                    CustomTextField(
                        value = pin,
                        onValueChange = { if (!isLoading) pin = it },
                        label = "PIN (Senha)",
                        leadingIcon = Icons.Default.Lock,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    
                    if (error != null) {
                        Text(
                            text = error!!,
                            color = Color(0xFFEF4444),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    if (isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = NeonGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (telefone.isNotBlank() && pin.isNotBlank()) {
                            loginViewModel.login(telefone, pin)
                        }
                    },
                    enabled = !isLoading && telefone.isNotBlank() && pin.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text("ENTRAR", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showLoginDialog = false
                        telefone = ""
                        pin = ""
                        loginViewModel.clearMessages()
                    }
                ) {
                    Text("Cancelar", color = TextGray)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportadoraCard(
    baseName: String,
    accounts: List<SavedSession>,
    onAccountClick: (SavedSession) -> Unit,
    onRemoveAccount: (SavedSession) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Header da transportadora
            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                baseName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${accounts.size} conta(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Recolher" else "Expandir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Lista de contas (expansível)
            if (expanded) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    accounts.forEach { account ->
                        AccountRowCard(
                            account = account,
                            onClick = { onAccountClick(account) },
                            onRemove = { onRemoveAccount(account) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountRowCard(
    account: SavedSession,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val lastAccess = remember(account.lastAccessTimestamp) {
        dateFormat.format(Date(account.lastAccessTimestamp))
    }
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = when (account.userRole) {
                        "admin" -> NeonPurple
                        "auxiliar" -> NeonBlue
                        else -> NeonGreen
                    },
                    modifier = Modifier.size(28.dp)
                )
                
                Column {
                    Text(
                        account.userName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = when (account.userRole) {
                                "admin" -> NeonPurple.copy(alpha = 0.2f)
                                "auxiliar" -> NeonBlue.copy(alpha = 0.2f)
                                else -> NeonGreen.copy(alpha = 0.2f)
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = account.userRole.uppercase(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = when (account.userRole) {
                                    "admin" -> NeonPurple
                                    "auxiliar" -> NeonBlue
                                    else -> NeonGreen
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "• $lastAccess",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remover",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    
    // Dialog de confirmação de remoção
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Remover Conta", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Deseja remover esta conta salva? Você precisará fazer login novamente para acessá-la.",
                    color = TextGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Text("Remover", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar", color = TextGray)
                }
            }
        )
    }
}

