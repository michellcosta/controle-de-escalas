package com.controleescalas.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun LoginExistingScreen(
    onLoginAsMotorista: (String, String) -> Unit,
    onLoginAsAdmin: (String, String) -> Unit,
    onLoginAsSuperAdmin: (String) -> Unit = { _ -> },
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )
    
    var telefone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showAccountsList by remember { mutableStateOf(true) }

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val loginResult by viewModel.loginResult.collectAsState()
    val availableAccounts by viewModel.availableAccounts.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadAvailableAccounts()
    }

    LaunchedEffect(loginResult) {
        loginResult?.let { result ->
            if (result is com.controleescalas.app.data.LoginResult.Success) {
                val session = SavedSession(
                    userId = result.motoristaId,
                    baseId = result.baseId,
                    baseName = result.baseName.ifBlank { "Transportadora" },
                    userName = result.nome,
                    userRole = result.papel
                )
                
                scope.launch {
                    sessionManager.saveSession(session)
                    when (result.papel) {
                        "motorista" -> onLoginAsMotorista(result.motoristaId, result.baseId)
                        "superadmin" -> onLoginAsSuperAdmin(result.motoristaId)
                        "admin", "auxiliar" -> onLoginAsAdmin(result.motoristaId, result.baseId)
                        else -> {}
                    }
                }
            }
        }
    }

    PremiumBackground(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CONTROLE DE\nESCALAS",
                    style = MaterialTheme.typography.displayLarge,
                    color = NeonGreen,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.scale(logoScale)
                )
                
                if (showAccountsList) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Contas Disponíveis", style = MaterialTheme.typography.titleLarge, color = TextWhite, fontWeight = FontWeight.Bold)
                        
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = NeonGreen)
                            } else if (availableAccounts.isEmpty()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = TextGray, modifier = Modifier.size(48.dp))
                                    Text("Nenhuma conta encontrada", color = TextGray)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(availableAccounts) { account ->
                                        AccountCard(
                                            nome = account.nome,
                                            telefone = account.telefone,
                                            papel = account.papel,
                                            onClick = {
                                                telefone = account.telefone
                                                showAccountsList = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            Divider(color = TextGray.copy(alpha = 0.2f))
                            TextButton(onClick = { showAccountsList = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("Fazer login manual", color = NeonBlue)
                            }
                        }
                    }
                } else {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            CustomTextField(
                                value = telefone,
                                onValueChange = { telefone = it },
                                label = "Telefone",
                                leadingIcon = Icons.Default.Phone,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )

                            CustomTextField(
                                value = pin,
                                onValueChange = { newValue ->
                                    val numericValue = newValue.filter { it.isDigit() }
                                    if (numericValue.length <= 6) pin = numericValue
                                },
                                label = "PIN (6 dígitos)",
                                leadingIcon = Icons.Default.Lock,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                            )

                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = NeonGreen)
                            } else {
                                NeonButton(
                                    text = "ENTRAR",
                                    onClick = { if (telefone.isNotBlank() && pin.length == 6) viewModel.login(telefone, pin) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = telefone.isNotBlank() && pin.length == 6
                                )
                            }
                            
                            TextButton(onClick = { showAccountsList = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Ver contas disponíveis", color = TextGray)
                            }
                        }
                    }
                    
                    error?.let { msg ->
                        Snackbar(
                            modifier = Modifier.padding(16.dp),
                            containerColor = StatusError,
                            contentColor = TextWhite
                        ) {
                            Text(text = msg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountCard(
    nome: String,
    telefone: String,
    papel: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = when (papel) {
                    "admin" -> NeonPurple
                    "auxiliar" -> NeonBlue
                    else -> NeonGreen
                },
                modifier = Modifier.size(32.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(nome, color = TextWhite, fontWeight = FontWeight.Bold)
                Text(telefone, color = TextGray, style = MaterialTheme.typography.bodySmall)
            }
            
            Surface(
                color = when (papel) {
                    "admin" -> NeonPurple.copy(alpha = 0.2f)
                    "auxiliar" -> NeonBlue.copy(alpha = 0.2f)
                    else -> NeonGreen.copy(alpha = 0.2f)
                },
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = papel.uppercase(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = when (papel) {
                        "admin" -> NeonPurple
                        "auxiliar" -> NeonBlue
                        else -> NeonGreen
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
