package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.controleescalas.app.data.SessionManager
import com.controleescalas.app.ui.components.*
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.data.repositories.SistemaRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverConfigScreen(
    baseId: String,
    motoristaId: String,
    onIdentificacaoClick: () -> Unit,
    onQuinzenaClick: () -> Unit,
    onDevolucaoClick: () -> Unit,
    onNotificacoesClick: () -> Unit = {},
    onSobreAppClick: () -> Unit,
    onAjudaClick: () -> Unit,
    onTermosClick: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val themeMode by sessionManager.themeModeFlow.collectAsState(initial = "dark")
    val scope = rememberCoroutineScope()
    
    val sistemaRepository = remember { SistemaRepository() }
    var temaHabilitado by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val config = sistemaRepository.getConfiguracao()
        temaHabilitado = config.temaHabilitado
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", color = MaterialTheme.colorScheme.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        PremiumBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Seção Geral
                SectionHeader(title = "Geral")
                
                // Toggle de Tema
                if (temaHabilitado) {
                    GlassCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (themeMode == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = if (themeMode == "dark") NeonPurple else NeonOrange
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (themeMode == "dark") "Modo Escuro" else "Modo Claro",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Switch(
                                checked = themeMode == "dark",
                                onCheckedChange = { isDark ->
                                    scope.launch {
                                        sessionManager.setThemeMode(if (isDark) "dark" else "light")
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = NeonGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = TextGray
                                )
                            )
                        }
                    }
                }
                
                OutlinedButton(
                    onClick = onIdentificacaoClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Identificação", style = MaterialTheme.typography.bodyMedium)
                }
                
                OutlinedButton(
                    onClick = onQuinzenaClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Quinzena", style = MaterialTheme.typography.bodyMedium)
                }
                
                OutlinedButton(
                    onClick = onDevolucaoClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange)
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Devolução", style = MaterialTheme.typography.bodyMedium)
                }
                
                HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
                
                // Notificações (Xiaomi/Huawei)
                SectionHeader(title = "Notificações")
                
                OutlinedButton(
                    onClick = onNotificacoesClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Notificações não aparecem?", style = MaterialTheme.typography.bodyMedium)
                }
                
                HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
                
                // Seção Ajuda e Suporte
                SectionHeader(title = "Ajuda e Suporte")
                
                OutlinedButton(
                    onClick = onSobreAppClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sobre o App", style = MaterialTheme.typography.bodyMedium)
                }
                
                OutlinedButton(
                    onClick = onAjudaClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPurple)
                ) {
                    Icon(Icons.Default.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ajuda", style = MaterialTheme.typography.bodyMedium)
                }
                
                OutlinedButton(
                    onClick = onTermosClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonOrange)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Termos de Uso", style = MaterialTheme.typography.bodyMedium)
                }
                
                HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
                
                // Seção Conta
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
            }
        }
    }
}
