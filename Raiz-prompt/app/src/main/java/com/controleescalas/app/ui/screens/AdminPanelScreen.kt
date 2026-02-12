package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.controleescalas.app.ui.components.CustomTextField
import com.controleescalas.app.ui.components.DisponibilidadeListDialog
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.NeonButton
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
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

    // Estados para controlar os diálogos
    var showSobreApp by remember { mutableStateOf(false) }
    var showAjuda by remember { mutableStateOf(false) }
    var showTermos by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Configurações", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Seção de Configurações Gerais
            SectionHeader(title = "Geral")
            
            // Botões de Configuração
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onLocationConfigClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Local", style = MaterialTheme.typography.bodyMedium)
                }
                
                OutlinedButton(
                    onClick = onQuinzenaClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Quinzena", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            // Botão Devolução
            OutlinedButton(
                onClick = {
                    println("🔴 AdminPanelScreen: BOTÃO DEVOLUÇÃO CLICADO!")
                    println("🔴 AdminPanelScreen: Chamando onDevolucaoClick...")
                    println("🔴 AdminPanelScreen: onDevolucaoClick.toString() = ${onDevolucaoClick.toString()}")
                    try {
                        onDevolucaoClick.invoke()
                        println("🔴 AdminPanelScreen: onDevolucaoClick.invoke() executado")
                    } catch (e: Exception) {
                        println("❌ AdminPanelScreen: Erro ao executar onDevolucaoClick: ${e.message}")
                        e.printStackTrace()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange)
            ) {
                Icon(Icons.Default.Inventory2, contentDescription = "Devolução", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Devolução", style = MaterialTheme.typography.bodyMedium)
            }
            
            // Botão Notificações
            OutlinedButton(
                onClick = { showNotificationSettings = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = "Notificações", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Notificações", style = MaterialTheme.typography.bodyMedium)
            }
            
            HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
            
            // Seção de Gestão de Equipe (Acesso rápido)
            SectionHeader(title = "Gestão")
            
            OutlinedButton(
                onClick = onUserManagementClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gerenciar Equipe e Motoristas", style = MaterialTheme.typography.bodyMedium)
            }
            
            // Botão de Aprovação de Transportadoras (apenas para Super Admin)
            if (isSuperAdmin) {
                OutlinedButton(
                    onClick = onBaseApprovalClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Aprovar Transportadoras", style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(color = TextGray.copy(alpha = 0.2f))

            // Seção Ajuda e Suporte
            SectionHeader(title = "Ajuda e Suporte")

            OutlinedButton(
                onClick = { showSobreApp = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sobre o App", style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedButton(
                onClick = { showAjuda = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
            ) {
                Icon(Icons.Default.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ajuda", style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedButton(
                onClick = { showTermos = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Termos de Uso", style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedButton(
                onClick = onFeedbackClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
            ) {
                Icon(Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Enviar Feedback", style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
            
            // Seção de Conta
            SectionHeader(title = "Conta")
            
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sair da Conta", style = MaterialTheme.typography.bodyMedium)
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonGreen)
                }
            }
            
            // Feedback Message
            message?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                ) {
                    Text(msg)
                }
                LaunchedEffect(msg) {
                    viewModel.clearMessages()
                }
            }
            
            error?.let { msg ->
                Snackbar(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ) {
                    Text(msg)
                }
                LaunchedEffect(msg) {
                    viewModel.clearMessages()
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
    
    // Tela de configurações de notificações
    if (showNotificationSettings) {
        AdminNotificationSettingsScreen(
            onBack = { showNotificationSettings = false }
        )
    }
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
                color = TextWhite,
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
                    color = TextWhite,
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
 * Diálogo de Ajuda (Admin)
 */
@Composable
fun AdminAjudaDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Ajuda",
                color = TextWhite,
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
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "• Operação: Gerencie ondas, escalas e chamadas de motoristas\n" +
                    "• Disponibilidade: Solicite e gerencie disponibilidade da equipe\n" +
                    "• Configurações: Acesse configurações de local, quinzena e gestão de equipe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Para suporte técnico, entre em contato com o desenvolvedor.",
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
 * Diálogo de Termos de Uso (Admin)
 */
@Composable
fun AdminTermosDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Termos de Uso",
                color = TextWhite,
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
                    "Para mais informações, entre em contato com o desenvolvedor.",
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


