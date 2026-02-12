package com.controleescalas.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import com.controleescalas.app.data.models.Motorista
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.SectionHeader
import com.controleescalas.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminContaScreen(
    superAdminId: String,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val firestore = FirebaseManager.firestore
    val scope = rememberCoroutineScope()
    
    var superAdminInfo by remember { mutableStateOf<Motorista?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAlterarPinDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Carregar informações do superadmin
    LaunchedEffect(superAdminId) {
        scope.launch {
            try {
                isLoading = true
                // Buscar em todas as bases
                val basesSnapshot = firestore.collection("bases").get().await()
                
                for (baseDoc in basesSnapshot.documents) {
                    val baseId = baseDoc.id
                    val motoristaDoc = firestore
                        .collection("bases")
                        .document(baseId)
                        .collection("motoristas")
                        .document(superAdminId)
                        .get()
                        .await()
                    
                    if (motoristaDoc.exists()) {
                        val motorista = motoristaDoc.toObject(Motorista::class.java)
                        if (motorista?.papel == "superadmin") {
                            superAdminInfo = motorista
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                error = "Erro ao carregar informações: ${e.message}"
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
            TopAppBar(
                title = { Text("Configurações de Conta", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonGreen)
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
                // Informações do Perfil
                SectionHeader(title = "Perfil")
                GlassCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoRowConta("Nome", superAdminInfo?.nome ?: "Super Admin")
                        InfoRowConta("Telefone", superAdminInfo?.telefone ?: "")
                        InfoRowConta("Papel", "Super Administrador")
                        InfoRowConta(
                            "Status",
                            if (superAdminInfo?.ativo == true) "Ativo" else "Inativo"
                        )
                    }
                }
                
                // Ações
                SectionHeader(title = "Ações")
                GlassCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showAlterarPinDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                        ) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Alterar PIN")
                        }
                        
                        OutlinedButton(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sair da Conta")
                        }
                    }
                }
            }
        }
    }
    
    // Diálogo de alterar PIN
    if (showAlterarPinDialog) {
        AlterarPinDialog(
            superAdminId = superAdminId,
            onDismiss = { showAlterarPinDialog = false },
            onSuccess = {
                message = "PIN alterado com sucesso!"
                showAlterarPinDialog = false
            },
            onError = { errorMsg ->
                error = errorMsg
            }
        )
    }
    
    // Mensagens
    message?.let { msg ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = NeonGreen,
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
private fun InfoRowConta(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label, 
            color = TextGray, 
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            color = TextWhite,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlterarPinDialog(
    superAdminId: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    var pinAtual by remember { mutableStateOf("") }
    var novoPin by remember { mutableStateOf("") }
    var confirmarPin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firestore = FirebaseManager.firestore
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alterar PIN", color = TextWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pinAtual,
                    onValueChange = { pinAtual = it },
                    label = { Text("PIN Atual", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NeonGreen
                    )
                )
                OutlinedTextField(
                    value = novoPin,
                    onValueChange = { novoPin = it },
                    label = { Text("Novo PIN", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NeonGreen
                    )
                )
                OutlinedTextField(
                    value = confirmarPin,
                    onValueChange = { confirmarPin = it },
                    label = { Text("Confirmar Novo PIN", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NeonGreen
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            isLoading = true
                            
                            // Validações
                            if (pinAtual.isBlank() || novoPin.isBlank() || confirmarPin.isBlank()) {
                                onError("Preencha todos os campos")
                                return@launch
                            }
                            
                            if (novoPin.length != 4) {
                                onError("O PIN deve ter 4 dígitos")
                                return@launch
                            }
                            
                            if (novoPin != confirmarPin) {
                                onError("Os PINs não coincidem")
                                return@launch
                            }
                            
                            // Buscar superadmin e verificar PIN atual
                            val basesSnapshot = firestore.collection("bases").get().await()
                            var superAdminDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                            var baseId: String? = null
                            
                            for (baseDoc in basesSnapshot.documents) {
                                val bid = baseDoc.id
                                val doc = firestore
                                    .collection("bases")
                                    .document(bid)
                                    .collection("motoristas")
                                    .document(superAdminId)
                                    .get()
                                    .await()
                                
                                if (doc.exists()) {
                                    val motorista = doc.toObject(Motorista::class.java)
                                    if (motorista?.papel == "superadmin") {
                                        superAdminDoc = doc
                                        baseId = bid
                                        break
                                    }
                                }
                            }
                            
                            if (superAdminDoc == null || baseId == null) {
                                onError("Superadmin não encontrado")
                                return@launch
                            }
                            
                            // Verificar PIN atual (hash simples)
                            val pinAtualHash = hashPin(pinAtual)
                            val pinArmazenado = superAdminDoc.getString("pinHash") ?: ""
                            
                            if (pinAtualHash != pinArmazenado) {
                                onError("PIN atual incorreto")
                                return@launch
                            }
                            
                            // Atualizar PIN
                            val novoPinHash = hashPin(novoPin)
                            firestore
                                .collection("bases")
                                .document(baseId)
                                .collection("motoristas")
                                .document(superAdminId)
                                .update("pinHash", novoPinHash)
                                .await()
                            
                            onSuccess()
                        } catch (e: Exception) {
                            onError("Erro ao alterar PIN: ${e.message}")
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Alterar", color = Color.Black)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar", color = TextGray)
            }
        },
        containerColor = DarkSurface
    )
}

/**
 * Hash simples do PIN (SHA-256)
 * Em produção, usar bcrypt via Cloud Function
 */
private fun hashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(pin.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}

