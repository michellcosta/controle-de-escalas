package com.controleescalas.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controleescalas.app.ui.components.BarcodeScannerScreen
import com.controleescalas.app.ui.components.GlassCard
import com.controleescalas.app.ui.components.NeonButton
import com.controleescalas.app.ui.theme.*
import com.controleescalas.app.ui.viewmodels.DevolucaoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDevolucaoScreen(
    baseId: String,
    motoristaId: String,
    motoristaNome: String,
    onNavigateToHistorico: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: DevolucaoViewModel = viewModel()
) {
    val context = LocalContext.current
    var idPacote by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var modoMultiplosScans by remember { mutableStateOf(false) } // NOVO: modo de múltiplos scans
    var pacotesEscaneados by remember { mutableStateOf<List<String>>(emptyList()) } // NOVO: lista de pacotes escaneados
    val coroutineScope = rememberCoroutineScope()
    
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    
    // Obter data/hora atual
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val agora = Date()
    val dataAtual = dateFormat.format(agora)
    val horaAtual = timeFormat.format(agora)
    
    // Launcher para permissão de câmera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showScanner = true
        }
    }
    
    // Mensagens de erro/sucesso usando SnackbarHost (movido para fora do Box para ser acessível nos callbacks)
    val snackbarHostState = remember { SnackbarHostState() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devolução", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, "Voltar", tint = TextWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, "Informações", tint = NeonBlue)
                    }
                }
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card de formulário
                GlassCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Campo ID do Pacote
                        OutlinedTextField(
                            value = idPacote,
                            onValueChange = { 
                                // Permitir apenas números
                                if (it.all { char -> char.isDigit() }) {
                                    idPacote = it
                                }
                            },
                            label = { Text("ID do Pacote *", color = TextGray) },
                            placeholder = { Text("12345678912", color = TextGray.copy(alpha = 0.5f)) },
                            leadingIcon = {
                                Icon(Icons.Default.QrCode, null, tint = NeonGreen)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        // Verificar permissão de câmera
                                        when {
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED -> {
                                                showScanner = true
                                            }
                                            else -> {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.CameraAlt, "Escanear", tint = NeonBlue)
                                }
                            },
                            isError = error != null && idPacote.isNotBlank(),
                            supportingText = if (error != null && idPacote.isNotBlank()) {
                                { Text(error ?: "", color = Color(0xFFEF4444)) }
                            } else {
                                { Text("Digite 11 dígitos ou escaneie o código", color = TextGray) }
                            },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = NeonGreen,
                                unfocusedBorderColor = TextGray,
                                focusedLabelColor = NeonGreen,
                                unfocusedLabelColor = TextGray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Data/Hora atual (não editável)
                        OutlinedTextField(
                            value = "$dataAtual - $horaAtual",
                            onValueChange = {},
                            label = { Text("Data/Hora Atual", color = TextGray) },
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, null, tint = NeonOrange)
                            },
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = TextGray,
                                disabledBorderColor = TextGray.copy(alpha = 0.5f),
                                disabledLabelColor = TextGray.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // NOVO: Botão para modo múltiplos scans
                        OutlinedButton(
                            onClick = {
                                modoMultiplosScans = true
                                pacotesEscaneados = emptyList()
                                when {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED -> {
                                        showScanner = true
                                    }
                                    else -> {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Escanear Pacotes", style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        // Botões
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showConfirmDialog = true },
                                enabled = !isLoading && idPacote.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minWidth = 0.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    BoxWithConstraints {
                                        val hasSpace = maxWidth > 120.dp
                                        if (hasSpace) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Check, 
                                                    null, 
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    "Registrar", 
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            if (maxWidth > 60.dp) {
                                                Text(
                                                    "Registrar", 
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Check, 
                                                    null, 
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isLoading,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minWidth = 0.dp)
                            ) {
                                Text(
                                    "Cancelar", 
                                    color = TextGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                // Botão Ver Histórico
                NeonButton(
                    text = "Ver Histórico",
                    onClick = onNavigateToHistorico,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            LaunchedEffect(message) {
                if (message != null) {
                    // Limpar campo após sucesso
                    idPacote = ""
                    // Mostrar mensagem de sucesso
                    snackbarHostState.showSnackbar(
                        message = message!!,
                        duration = SnackbarDuration.Short
                    )
                    // Limpar mensagem após exibir
                    delay(3500)
                    viewModel.limparMensagem()
                }
            }
            
            LaunchedEffect(error) {
                if (error != null) {
                    // Mostrar mensagem de erro
                    snackbarHostState.showSnackbar(
                        message = error!!,
                        duration = SnackbarDuration.Short
                    )
                    // Limpar erro após exibir
                    delay(3500)
                    viewModel.limparMensagem()
                }
            }
            
            // Removido LaunchedEffect(scannerError) - agora usamos showSnackbar diretamente no callback
            
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) { snackbarData ->
                // Determinar cor baseado no tipo de mensagem
                val messageText = snackbarData.visuals.message
                val isError = error != null || messageText.contains("inválido", ignoreCase = true)
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = if (isError) Color(0xFFEF4444) else NeonGreen,
                    contentColor = if (isError) TextWhite else Color.Black
                )
            }
        }
        
        // Dialog de confirmação
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                containerColor = DarkSurface,
                title = {
                    Text("Confirmar Devolução", color = TextWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text("ID do Pacote: $idPacote", color = TextWhite)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Data/Hora: $dataAtual - $horaAtual", color = TextWhite)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Deseja registrar esta devolução?", color = TextGray)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmDialog = false
                            viewModel.registrarDevolucao(
                                baseId = baseId,
                                idPacote = idPacote,
                                motoristaId = motoristaId,
                                motoristaNome = motoristaNome
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Confirmar", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancelar", color = TextGray)
                    }
                }
            )
        }
        
        // Dialog de informações
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                containerColor = DarkSurface,
                title = {
                    Text("Sobre Devoluções", color = TextWhite, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "O que é uma devolução?",
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "A devolução é o registro de pacotes que você devolve ao galpão. " +
                                    "Este registro serve como segurança para você.",
                            color = TextWhite
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Como usar o scanner?",
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "1. Clique no ícone de câmera ao lado do campo ID\n" +
                                    "2. Aponte a câmera para o QR Code ou código de barras\n" +
                                    "3. O ID será preenchido automaticamente",
                            color = TextWhite
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "Como funciona o registro?",
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "• O ID do pacote deve ter exatamente 11 dígitos numéricos\n" +
                                    "• Cada ID só pode ser registrado uma vez (globalmente)\n" +
                                    "• A data/hora é registrada automaticamente\n" +
                                    "• Você pode ver seu histórico de devoluções",
                            color = TextWhite
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showInfoDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Entendi", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        
        // Scanner de QR Code/Código de Barras
        if (showScanner) {
            BarcodeScannerScreen(
                onBarcodeScanned = { barcodeValue ->
                    println("✅ DriverDevolucaoScreen: Código escaneado recebido: $barcodeValue")
                    
                    if (modoMultiplosScans) {
                        // Modo múltiplos: adicionar à lista se não existir
                        if (!pacotesEscaneados.contains(barcodeValue)) {
                            pacotesEscaneados = pacotesEscaneados + barcodeValue
                            // Mensagem removida conforme solicitado
                        }
                        // Se já existe, simplesmente ignora (sem mensagem)
                    } else {
                        // Modo único: preencher campo e fechar
                        idPacote = barcodeValue
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Código escaneado com sucesso!",
                                duration = SnackbarDuration.Short
                            )
                            delay(500) // Pequeno delay para mostrar a mensagem
                            showScanner = false
                            println("🚪 DriverDevolucaoScreen: Scanner fechado")
                        }
                    }
                },
                onError = { errorMessage, errorCode ->
                    // Mostrar erro diretamente no SnackbarHost
                    println("📱 DriverDevolucaoScreen: Exibindo erro do scanner: $errorMessage (Código: $errorCode)")
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = errorMessage,
                            duration = SnackbarDuration.Short
                        )
                    }
                    // Não fechar o scanner, permitir tentar novamente
                },
                onDismiss = { 
                    showScanner = false
                    modoMultiplosScans = false
                    pacotesEscaneados = emptyList()
                },
                snackbarHostState = snackbarHostState,
                modoMultiplosScans = modoMultiplosScans, // NOVO
                pacotesEscaneados = pacotesEscaneados, // NOVO
                onRegistrarTodos = { // NOVO
                    if (pacotesEscaneados.isNotEmpty()) {
                        viewModel.registrarDevolucoesEmLote(
                            baseId = baseId,
                            idPacotes = pacotesEscaneados,
                            motoristaId = motoristaId,
                            motoristaNome = motoristaNome
                        )
                    }
                },
                onRemoverPacote = { idPacote -> // NOVO: callback para remover pacote
                    pacotesEscaneados = pacotesEscaneados.filter { it != idPacote }
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Pacote removido",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        }
    }
}


