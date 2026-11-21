package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.components.*
import com.controleescalas.app.ui.components.AppTopBar
import com.controleescalas.app.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.controleescalas.app.ui.viewmodels.AdminViewModel
import com.controleescalas.app.data.models.AdminMotoristaCardData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    baseId: String,
    isSuperAdmin: Boolean = false,
    superAdminId: String = "",
    onEscalaClick: () -> Unit,
    onLocationConfigClick: () -> Unit,
    onUserManagementClick: () -> Unit,
    onQuinzenaClick: () -> Unit,
    onDevolucaoClick: () -> Unit,
    onPlanosClick: () -> Unit = {},
    showPlanosButton: Boolean = false,
    onBaseApprovalClick: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: AdminViewModel = viewModel()
) {
    // Log para verificar se o callback foi passado
    LaunchedEffect(Unit) {
        println("🔴 AdminPanelScreen: Componente renderizado")
        println("🔴 AdminPanelScreen: onDevolucaoClick foi passado? ${onDevolucaoClick != {}}")
    }
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()

    val context = LocalContext.current

    // Estados para controlar os diálogos
    var showSobreApp by remember { mutableStateOf(false) }
    var showAjuda by remember { mutableStateOf(false) }
    var showTermos by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    var showTurnosDialog by remember { mutableStateOf(false) }

    // Turnos cadastrados da base (dirigem Operação e Disponibilidade)
    val escopo = rememberCoroutineScope()
    val configRepository = remember { com.controleescalas.app.data.repositories.ConfigRepository() }
    var turnosDaBase by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(baseId) {
        turnosDaBase = configRepository.getTurnosBase(baseId)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = "Configurações"
            )
        }
    ) { paddingValues ->
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionHeader(title = "Geral")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppMenuItem(
                        text = "Local",
                        icon = Icons.Default.LocationOn,
                        onClick = onLocationConfigClick,
                        modifier = Modifier.weight(1f)
                    )
                    AppMenuItem(
                        text = "Quinzena",
                        icon = Icons.Default.DateRange,
                        onClick = onQuinzenaClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                AppMenuItem(
                    text = "Turnos da Base",
                    icon = Icons.Default.Schedule,
                    onClick = { showTurnosDialog = true }
                )
                AppMenuItem(
                    text = "Devolução",
                    icon = Icons.Default.Inventory2,
                    onClick = onDevolucaoClick
                )
                if (showPlanosButton) {
                    AppMenuItem(
                        text = "Planos e Assinatura",
                        icon = Icons.Default.Star,
                        onClick = onPlanosClick
                    )
                }
                AppMenuItem(
                    text = "Notificações",
                    icon = Icons.Default.Notifications,
                    onClick = { showNotificationSettings = true }
                )

                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "Gestão")
                AppMenuItem(
                    text = "Gerenciar Equipe e Motoristas",
                    icon = Icons.Default.Person,
                    onClick = onUserManagementClick
                )
                if (isSuperAdmin) {
                    AppMenuItem(
                        text = "Aprovar Transportadoras",
                        icon = Icons.Default.CheckCircle,
                        onClick = onBaseApprovalClick
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "Ajuda e Suporte")
                AppMenuItem("Sobre o App", Icons.Default.Info, { showSobreApp = true })
                AppMenuItem("Ajuda", Icons.Default.Help, { showAjuda = true })
                AppMenuItem("Termos de Uso", Icons.Default.Description, { showTermos = true })
                AppMenuItem("Enviar Feedback", Icons.Default.Feedback, onFeedbackClick)

                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "Conta")
                AppMenuItem(
                    text = "Sair da Conta",
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    onClick = onLogout,
                    iconTint = Color(0xFFEF4444),
                    textColor = Color(0xFFEF4444)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MeliBlue)
                    }
                }
                
                // Feedback Message
                message?.let { msg ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Snackbar(
                            containerColor = MeliBlue,
                            contentColor = Color.Black
                        ) {
                            Text(msg)
                        }
                    }
                    LaunchedEffect(msg) {
                        viewModel.clearMessages()
                    }
                }
                
                error?.let { msg ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Snackbar(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ) {
                            Text(msg)
                        }
                    }
                    LaunchedEffect(msg) {
                        viewModel.clearMessages()
                    }
                }
            }
        }
    }

    // Diálogos
    if (showSobreApp) {
        AdminSobreAppDialog(onDismiss = { showSobreApp = false })
    }
    if (showAjuda) {
        AdminAjudaDialog(onDismiss = { showAjuda = false })
    }
    if (showTermos) {
        AdminTermosDialog(onDismiss = { showTermos = false })
    }

    // Editor de Turnos da Base
    if (showTurnosDialog) {
        TurnosDaBaseDialog(
            baseId = baseId,
            turnosAtuais = turnosDaBase,
            configRepository = configRepository,
            escopo = escopo,
            onDismiss = { showTurnosDialog = false },
            onSalvo = { novos ->
                turnosDaBase = novos
                showTurnosDialog = false
            }
        )
    }

    // Tela de configurações de notificações
    if (showNotificationSettings) {
        AdminNotificationSettingsScreen(
            onBack = { showNotificationSettings = false }
        )
    }
}

/**
 * Diálogo para o admin montar a lista de turnos da base.
 * Esses turnos dirigem os botões da Operação e as opções que os
 * motoristas marcam na Disponibilidade.
 */
@Composable
fun TurnosDaBaseDialog(
    baseId: String,
    turnosAtuais: List<String>,
    configRepository: com.controleescalas.app.data.repositories.ConfigRepository,
    escopo: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
    onSalvo: (List<String>) -> Unit
) {
    var turnosEditaveis by remember { mutableStateOf(turnosAtuais) }
    var novoTurno by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Turnos da Base",
                color = TextBlack,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Esses turnos aparecem como botões na Operação e como opções que os motoristas marcam na Disponibilidade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGrayLightMode
                )

                if (turnosEditaveis.isEmpty()) {
                    Text(
                        "Nenhum turno. Adicione pelo menos um.",
                        color = StatusError,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Renomear: índice do turno em edição + valor corrente do campo
                var editandoIndex by remember { mutableStateOf(-1) }
                var valorEdicao by remember { mutableStateOf("") }

                turnosEditaveis.forEachIndexed { index, turno ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (editandoIndex == index) {
                            OutlinedTextField(
                                value = valorEdicao,
                                onValueChange = { valorEdicao = it.uppercase() },
                                label = { Text("Renomear turno") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val limpo = valorEdicao.trim().uppercase()
                                    val duplicado = limpo != turno && limpo in turnosEditaveis
                                    if (limpo.isNotBlank() && !duplicado) {
                                        turnosEditaveis = turnosEditaveis.mapIndexed { j, t ->
                                            if (j == index) limpo else t
                                        }
                                        editandoIndex = -1
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Confirmar renomear",
                                    tint = NeonGreen
                                )
                            }
                            IconButton(onClick = { editandoIndex = -1 }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancelar renomear",
                                    tint = TextGrayLightMode
                                )
                            }
                        } else {
                            Text(
                                text = turno,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextBlack,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                valorEdicao = turno
                                editandoIndex = index
                            }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Renomear $turno",
                                    tint = MeliBlue
                                )
                            }
                            IconButton(onClick = { turnosEditaveis = turnosEditaveis - turno }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remover $turno",
                                    tint = StatusError
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = novoTurno,
                        onValueChange = { novoTurno = it },
                        label = { Text("Novo turno") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val limpo = novoTurno.trim().uppercase()
                            if (limpo.isNotBlank() && limpo !in turnosEditaveis) {
                                turnosEditaveis = turnosEditaveis + limpo
                                novoTurno = ""
                            }
                        },
                        enabled = novoTurno.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    salvando = true
                    escopo.launch {
                        val ok = configRepository.salvarTurnosBase(baseId, turnosEditaveis)
                        salvando = false
                        if (ok) onSalvo(turnosEditaveis)
                    }
                },
                enabled = turnosEditaveis.isNotEmpty() && !salvando
            ) {
                if (salvando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextGrayLightMode)
            }
        },
        containerColor = Color.White,
        titleContentColor = TextBlack,
        textContentColor = TextBlack
    )
}

/**
 * Diálogo Sobre o App (Admin)
 */
@Composable
fun AdminSobreAppDialog(onDismiss: () -> Unit) {
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
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Controle de Escalas",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Versão ${com.controleescalas.app.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGrayLightMode
                )
                Text(
                    "Desenvolvido por: Michell Oliveira",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGrayLightMode
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Aplicativo para gestão de escalas de motoristas e controle de disponibilidade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGrayLightMode
                )
                Spacer(modifier = Modifier.height(8.dp))
                com.controleescalas.app.ui.components.BotaoBuscarAtualizacao()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = MeliBlue)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = TextGrayLightMode
    )
}

/**
 * Diálogo de Ajuda (Admin)
 */
@Composable
fun AdminAjudaDialog(onDismiss: () -> Unit) {
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
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Como usar o app:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "• Operação: Gerencie ondas, escalas e chamadas de motoristas\n" +
                    "• Disponibilidade: Solicite e gerencie disponibilidade da equipe\n" +
                    "• Configurações: Acesse configurações de local, quinzena e gestão de equipe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGrayLightMode
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Para suporte técnico, entre em contato com o desenvolvedor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGrayLightMode.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = MeliBlue)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = TextGrayLightMode
    )
}

/**
 * Diálogo de Termos de Uso (Admin)
 */
@Composable
fun AdminTermosDialog(onDismiss: () -> Unit) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Ao usar este aplicativo, você concorda com os seguintes termos:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGrayLightMode
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
                    color = TextGrayLightMode.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Para mais informações, entre em contato com o desenvolvedor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGrayLightMode.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = MeliBlue)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = TextGrayLightMode
    )
}
