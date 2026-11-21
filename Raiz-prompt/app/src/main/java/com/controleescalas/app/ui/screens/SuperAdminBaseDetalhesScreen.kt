package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.controleescalas.app.data.FirebaseManager
import com.controleescalas.app.data.models.Base
import com.controleescalas.app.data.models.AcaoHistorico
import com.controleescalas.app.data.repositories.BaseRepository
import com.controleescalas.app.data.repositories.HistoricoRepository
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.components.AppCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminBaseDetalhesScreen(
    baseId: String,
    superAdminId: String,
    onBack: () -> Unit,
    onNavigateToBase: (String) -> Unit
) {
    val baseRepository = BaseRepository()
    val historicoRepository = HistoricoRepository()
    val scope = rememberCoroutineScope()
    val firestore = FirebaseManager.firestore
    
    var base by remember { mutableStateOf<Base?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPlanoDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Estatísticas
    var totalMotoristas by remember { mutableStateOf(0) }
    var totalAdmins by remember { mutableStateOf(0) }
    var escalasHoje by remember { mutableStateOf(0) }
    var escalasEsteMes by remember { mutableStateOf(0) }
    
    // Carregar dados da base
    LaunchedEffect(baseId) {
        scope.launch {
            try {
                isLoading = true
                base = baseRepository.getBase(baseId)
                
                if (base != null) {
                    // Contar motoristas
                    val motoristasSnapshot = firestore
                        .collection("bases")
                        .document(baseId)
                        .collection("motoristas")
                        .whereEqualTo("ativo", true)
                        .get()
                        .await()
                    totalMotoristas = motoristasSnapshot.size()
                    
                    // Contar admins
                    val adminsSnapshot = firestore
                        .collection("bases")
                        .document(baseId)
                        .collection("motoristas")
                        .whereEqualTo("papel", "admin")
                        .whereEqualTo("ativo", true)
                        .get()
                        .await()
                    totalAdmins = adminsSnapshot.size()
                    
                    // Contar escalas de hoje
                    val hoje = Calendar.getInstance()
                    val dataHoje = String.format("%04d-%02d-%02d", hoje.get(Calendar.YEAR), hoje.get(Calendar.MONTH) + 1, hoje.get(Calendar.DAY_OF_MONTH))
                    val escalasHojeSnapshot = firestore
                        .collection("bases")
                        .document(baseId)
                        .collection("escalas")
                        .whereEqualTo("data", dataHoje)
                        .get()
                        .await()
                    escalasHoje = escalasHojeSnapshot.size()
                    
                    // Contar escalas deste mês
                    val inicioMes = String.format("%04d-%02d-01", hoje.get(Calendar.YEAR), hoje.get(Calendar.MONTH) + 1)
                    val escalasMesSnapshot = firestore
                        .collection("bases")
                        .document(baseId)
                        .collection("escalas")
                        .whereGreaterThanOrEqualTo("data", inicioMes)
                        .get()
                        .await()
                    escalasEsteMes = escalasMesSnapshot.size()
                }
            } catch (e: Exception) {
                error = "Erro ao carregar dados: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    // Limpar mensagens
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000)
            message = null
        }
    }
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(5000)
            error = null
        }
    }
    
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Detalhes da Transportadora",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MeliBlue)
            }
        } else if (base == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Transportadora não encontrada", color = TextGrayLightMode)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Informações básicas
                SectionHeader(title = "Informações Básicas")
                AppCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoRow("Nome", base!!.nome)
                        InfoRow("Transportadora", base!!.transportadora)
                        InfoRow("Status", base!!.statusAprovacao.replaceFirstChar { it.uppercaseChar() })
                        base!!.plano?.let { 
                            InfoRow("Plano", it.replaceFirstChar { it.uppercaseChar() }) 
                        }
                        InfoRow(
                            "Criada em",
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                .format(Date(base!!.criadoEm))
                        )
                    }
                }
                
                // Estatísticas
                SectionHeader(title = "Estatísticas")
                AppCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem("Motoristas", totalMotoristas.toString(), MeliBlue)
                            StatItem("Admins", totalAdmins.toString(), MeliBlue)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatItem("Escalas Hoje", escalasHoje.toString(), NeonOrange)
                            StatItem("Escalas Este Mês", escalasEsteMes.toString(), NeonPurple)
                        }
                    }
                }
                
                // Aprovar/Rejeitar (apenas para bases pendentes)
                if (base!!.statusAprovacao == "pendente") {
                    SectionHeader(title = "Aprovação")
                    AppCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val ok = baseRepository.aprovarBase(baseId, superAdminId)
                                            if (ok) {
                                                base = base!!.copy(statusAprovacao = "ativa")
                                                historicoRepository.registrarAcao(
                                                    AcaoHistorico(
                                                        tipo = "aprovacao",
                                                        baseId = baseId,
                                                        baseNome = base!!.nome,
                                                        superAdminId = superAdminId,
                                                        descricao = "Transportadora '${base!!.nome}' aprovada",
                                                        data = System.currentTimeMillis()
                                                    )
                                                )
                                                message = "Transportadora aprovada!"
                                            } else {
                                                error = "Erro ao aprovar"
                                            }
                                        } catch (e: Exception) {
                                            error = "Erro: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MeliBlue)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aprovar")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val ok = baseRepository.rejeitarBase(baseId, superAdminId)
                                            if (ok) {
                                                message = "Transportadora rejeitada"
                                                onBack()
                                            } else {
                                                error = "Erro ao rejeitar"
                                            }
                                        } catch (e: Exception) {
                                            error = "Erro: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                            ) {
                                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rejeitar")
                            }
                        }
                    }
                }
                
                // Ações
                SectionHeader(title = "Ações")
                AppCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MeliBlue)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar Informações")
                        }
                        
                        Button(
                            onClick = { showPlanoDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                        ) {
                            Icon(Icons.Default.CreditCard, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gerenciar Plano")
                        }
                        
                        if (base!!.statusAprovacao == "ativa") {
                            OutlinedButton(
                                onClick = { onNavigateToBase(baseId) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MeliBlue)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Entrar na Transportadora")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Diálogo de edição
    if (showEditDialog && base != null) {
        EditBaseDialog(
            base = base!!,
            onDismiss = { showEditDialog = false },
            onSave = { nome, transportadora, corTema ->
                scope.launch {
                    try {
                        val firestore = FirebaseManager.firestore
                        firestore.collection("bases").document(baseId).update(
                            mapOf(
                                "nome" to nome,
                                "transportadora" to transportadora,
                                "corTema" to corTema
                            )
                        ).await()
                        
                        base = base!!.copy(
                            nome = nome,
                            transportadora = transportadora,
                            corTema = corTema
                        )
                        
                        // ✅ Registrar ação no histórico
                        historicoRepository.registrarAcao(
                            AcaoHistorico(
                                tipo = "edicao",
                                baseId = baseId,
                                baseNome = nome,
                                superAdminId = superAdminId,
                                descricao = "Informações da transportadora '${nome}' editadas",
                                data = System.currentTimeMillis()
                            )
                        )
                        
                        message = "Informações atualizadas com sucesso!"
                        showEditDialog = false
                    } catch (e: Exception) {
                        error = "Erro ao atualizar: ${e.message}"
                    }
                }
            }
        )
    }
    
    // Diálogo de plano
    if (showPlanoDialog && base != null) {
        GerenciarPlanoDialog(
            base = base!!,
            onDismiss = { showPlanoDialog = false },
            onSave = { plano ->
                scope.launch {
                    try {
                        val firestore = FirebaseManager.firestore
                        val updates = mutableMapOf<String, Any>("plano" to plano)
                        
                        if (plano == "trial") {
                            val agora = System.currentTimeMillis()
                            val fimTrial = agora + (30L * 24 * 60 * 60 * 1000) // 30 dias
                            updates["dataInicioTrial"] = agora
                            updates["dataFimTrial"] = fimTrial
                        }
                        
                        firestore.collection("bases").document(baseId).update(updates).await()
                        base = base!!.copy(plano = plano)
                        
                        // ✅ Registrar ação no histórico
                        historicoRepository.registrarAcao(
                            AcaoHistorico(
                                tipo = "edicao",
                                baseId = baseId,
                                baseNome = base!!.nome,
                                superAdminId = superAdminId,
                                descricao = "Plano da transportadora '${base!!.nome}' alterado para $plano",
                                data = System.currentTimeMillis()
                            )
                        )
                        
                        message = "Plano atualizado com sucesso!"
                        showPlanoDialog = false
                    } catch (e: Exception) {
                        error = "Erro ao atualizar plano: ${e.message}"
                    }
                }
            }
        )
    }
    
    // Mensagens
    message?.let { msg ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = MeliBlue,
            contentColor = Color.Black
        ) {
            Text(msg)
        }
    }
    
    error?.let { err ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = Color(0xFFEF4444),
            contentColor = Color.White
        ) {
            Text(err)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label, 
            color = TextGrayLightMode, 
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
        )
        Text(
            value, 
            color = TextBlack, 
            style = MaterialTheme.typography.bodyMedium, 
            fontWeight = FontWeight.Bold,
            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value, 
            style = MaterialTheme.typography.headlineMedium, 
            color = color, 
            fontWeight = FontWeight.Bold
        )
        Text(
            label, 
            style = MaterialTheme.typography.bodySmall, 
            color = TextGrayLightMode,
            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EditBaseDialog(
    base: Base,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var nome by remember { mutableStateOf(base.nome) }
    var transportadora by remember { mutableStateOf(base.transportadora) }
    var corTema by remember { mutableStateOf(base.corTema) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Transportadora", color = TextBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome", color = TextGrayLightMode) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextBlack,
                        unfocusedTextColor = TextBlack,
                        focusedBorderColor = MeliBlue
                    )
                )
                OutlinedTextField(
                    value = transportadora,
                    onValueChange = { transportadora = it },
                    label = { Text("Transportadora", color = TextGrayLightMode) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextBlack,
                        unfocusedTextColor = TextBlack,
                        focusedBorderColor = MeliBlue
                    )
                )
                OutlinedTextField(
                    value = corTema,
                    onValueChange = { corTema = it },
                    label = { Text("Cor do Tema (hex)", color = TextGrayLightMode) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextBlack,
                        unfocusedTextColor = TextBlack,
                        focusedBorderColor = MeliBlue
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(nome, transportadora, corTema) },
                colors = ButtonDefaults.buttonColors(containerColor = MeliBlue)
            ) {
                Text("Salvar", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextGrayLightMode)
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun GerenciarPlanoDialog(
    base: Base,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var planoSelecionado by remember { mutableStateOf(base.plano ?: "gratuito") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gerenciar Plano", color = TextBlack) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("gratuito", "premium", "trial").forEach { plano ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = planoSelecionado == plano,
                            onClick = { planoSelecionado = plano }
                        )
                        Text(
                            plano.uppercase(),
                            color = TextBlack,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(planoSelecionado) },
                colors = ButtonDefaults.buttonColors(containerColor = MeliBlue)
            ) {
                Text("Salvar", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextGrayLightMode)
            }
        },
        containerColor = Color.White
    )
}

