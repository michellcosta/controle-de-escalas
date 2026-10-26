package com.controleescalas.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.controleescalas.app.ui.theme.NeonGreen
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Tela de scanner de código de barras/QR Code usando ML Kit e CameraX
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onError: (String, String) -> Unit, // Agora recebe (mensagem, código)
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modoMultiplosScans: Boolean = false, // NOVO: modo de múltiplos scans
    pacotesEscaneados: List<String> = emptyList(), // NOVO: lista de pacotes já escaneados
    onRegistrarTodos: (() -> Unit)? = null, // NOVO: callback para registrar todos
    onRemoverPacote: ((String) -> Unit)? = null, // NOVO: callback para remover pacote
    aceitarJSONCompleto: Boolean = false // NOVO: se true, passa JSON completo sem processar
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    
    // Variável para evitar processar o mesmo código válido múltiplas vezes
    var lastProcessedCode by remember { mutableStateOf<String?>(null) }
    
    // Variável para evitar mostrar o mesmo erro múltiplas vezes
    var lastErrorCode by remember { mutableStateOf<String?>(null) }
    
    // NOVO: Estados para controlar visibilidade da mensagem e expansão da lista
    var mostrarMensagemInstrucao by remember { mutableStateOf(false) }
    var listaExpandida by remember { mutableStateOf(false) }
    
    // NOVO: LaunchedEffect para mostrar mensagem ao abrir a câmera (modo múltiplos)
    LaunchedEffect(modoMultiplosScans) {
        if (modoMultiplosScans) {
            mostrarMensagemInstrucao = true
            delay(1000) // 1 segundo
            mostrarMensagemInstrucao = false
        } else {
            mostrarMensagemInstrucao = false
        }
    }
    
    // Scanner ML Kit - Configurado para detectar QR codes e códigos de barras
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_AZTEC
            )
            .build()
        BarcodeScanning.getClient(options)
    }
    
    // Gerador de som para feedback quando código for detectado
    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) // Volume 80%
    }
    
    // Inicializar câmera
    LaunchedEffect(hasPermission) {
        if (hasPermission && previewView != null) {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            val executor = ContextCompat.getMainExecutor(context)
            
            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView!!.surfaceProvider)
            }
            
            // Image Analysis para detecção de código
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        Executors.newSingleThreadExecutor()
                    ) { imageProxy ->
                        processImageProxy(
                            imageProxy = imageProxy,
                            scanner = scanner,
                            lastProcessedCodeRef = { lastProcessedCode },
                            lastErrorCodeRef = { lastErrorCode },
                            aceitarJSONCompleto = aceitarJSONCompleto,
                            onBarcodeDetected = { barcode ->
                                println("🎯 BarcodeScanner: onBarcodeDetected chamado com: $barcode")
                                // Processar no thread principal
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    println("📱 BarcodeScanner: Processando no thread principal: $barcode")
                                    // Tocar som de "bip" quando código válido for detectado
                                    try {
                                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150) // Bip de 150ms
                                        println("🔊 BarcodeScanner: Som de sucesso tocado")
                                    } catch (e: Exception) {
                                        println("⚠️ BarcodeScanner: Erro ao tocar som: ${e.message}")
                                    }
                                    
                                    lastProcessedCode = barcode
                                    lastErrorCode = null // Limpar erro quando código válido for detectado
                                    println("✅ BarcodeScanner: Chamando onBarcodeScanned com: $barcode")
                                    onBarcodeScanned(barcode)
                                    
                                    // NOVO: Só fecha se não estiver em modo múltiplos scans
                                    if (!modoMultiplosScans) {
                                        println("🚪 BarcodeScanner: Chamando onDismiss (modo único)")
                                        onDismiss()
                                    } else {
                                        println("📦 BarcodeScanner: Modo múltiplos scans - mantendo câmera aberta")
                                    }
                                }
                            },
                            onError = { errorMessage, errorCode ->
                                // Não exibir mensagem de erro - apenas logar para debug
                                println("❌ BarcodeScanner: Código inválido detectado (não exibindo mensagem): $errorCode")
                            }
                        )
                    }
                }
            
            // Selecionar câmera traseira
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                // Desvincular todos os casos de uso antes de vincular
                cameraProvider.unbindAll()
                
                // Vincular casos de uso à câmera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Limpar recursos quando sair
    DisposableEffect(Unit) {
        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            scanner.close()
            toneGenerator.release()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (modoMultiplosScans) "Escanear Múltiplos Pacotes" else "Escanear Código", 
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Fechar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (hasPermission) {
                // Preview da câmera
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            previewView = pv
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // NOVO: Lista de pacotes escaneados (modo múltiplos scans)
                if (modoMultiplosScans && pacotesEscaneados.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Card de contagem (clicável para expandir/recolher)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { listaExpandida = !listaExpandida },
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.9f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${String.format("%02d", pacotesEscaneados.size)} ${if (pacotesEscaneados.size == 1) "pacote escaneado" else "pacotes escaneados"}",
                                    color = NeonGreen,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    if (listaExpandida) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (listaExpandida) "Recolher" else "Expandir",
                                    tint = NeonGreen
                                )
                            }
                        }
                        
                        // Lista de IDs (expansível com scroll)
                        if (listaExpandida) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp), // Altura máxima com scroll
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Black.copy(alpha = 0.9f)
                                )
                            ) {
                                LazyColumn(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    itemsIndexed(pacotesEscaneados) { index, idPacote ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}. $idPacote",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            // Botão de delete
                                            IconButton(
                                                onClick = {
                                                    onRemoverPacote?.invoke(idPacote)
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    "Remover",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Overlay com instruções e botão (parte inferior fixa)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mensagem temporária (1 segundo) - acima do botão
                    if (mostrarMensagemInstrucao && modoMultiplosScans) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.7f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Aponte a câmera para escanear mais pacotes",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Botão Registrar (sempre fixo abaixo da mensagem, na parte inferior)
                    if (modoMultiplosScans && pacotesEscaneados.isNotEmpty() && onRegistrarTodos != null) {
                        Button(
                            onClick = {
                                onRegistrarTodos()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Registrar ${String.format("%02d", pacotesEscaneados.size)} ${if (pacotesEscaneados.size == 1) "pacote" else "pacotes"}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Mensagem para modo único (não múltiplos)
                    if (!modoMultiplosScans) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Black.copy(alpha = 0.7f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Aponte a câmera para o código",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "O código será detectado automaticamente",
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // SnackbarHost para exibir mensagens de erro/sucesso acima do scanner
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (modoMultiplosScans && pacotesEscaneados.isNotEmpty()) 180.dp else 100.dp)
                ) { snackbarData ->
                    val messageText = snackbarData.visuals.message
                    val isError = messageText.contains("inválido", ignoreCase = true)
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor = if (isError) Color(0xFFEF4444) else Color(0xFF10B981),
                        contentColor = if (isError) Color.White else Color.Black
                    )
                }
            } else {
                // Mensagem de permissão negada
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Permissão de câmera necessária",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Por favor, conceda permissão de câmera nas configurações do app",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Processa a imagem da câmera para detectar códigos de barras e QR codes
 */
private fun processImageProxy(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    lastProcessedCodeRef: () -> String?,
    lastErrorCodeRef: () -> String?,
    aceitarJSONCompleto: Boolean,
    onBarcodeDetected: (String) -> Unit,
    onError: (String, String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                println("🔍 BarcodeScanner: Códigos detectados: ${barcodes.size}")
                
                if (barcodes.isEmpty()) {
                    println("⚠️ BarcodeScanner: Nenhum código detectado na imagem")
                    return@addOnSuccessListener
                }
                
                // Processar todos os códigos detectados
                for (barcode in barcodes) {
                    val value = barcode.rawValue
                    val rawBytes = barcode.rawBytes
                    val displayValue = barcode.displayValue
                    println("📦 BarcodeScanner: Código detectado - Tipo: ${barcode.valueType}, Valor: $value")
                    println("📦 BarcodeScanner: rawBytes: ${rawBytes?.size ?: 0} bytes")
                    println("📦 BarcodeScanner: displayValue: $displayValue")
                    
                    // Se aceitarJSONCompleto, tentar processar mesmo se value for null
                    if (aceitarJSONCompleto) {
                        var textToProcess: String? = null
                        
                        // Tentar 1: usar rawValue se disponível
                        if (value != null && value.isNotBlank()) {
                            textToProcess = value.trim()
                            println("📋 BarcodeScanner: Usando rawValue: ${textToProcess.length} caracteres")
                        } 
                        // Tentar 2: usar rawBytes e converter para Base64
                        else if (rawBytes != null && rawBytes.isNotEmpty()) {
                            val base64 = android.util.Base64.encodeToString(rawBytes, android.util.Base64.NO_WRAP)
                            textToProcess = base64
                            println("📋 BarcodeScanner: Usando rawBytes convertido para Base64: ${base64.length} caracteres")
                        }
                        // Tentar 3: usar displayValue se disponível
                        else if (displayValue != null && displayValue.isNotBlank() && displayValue != "Unknown encoding") {
                            textToProcess = displayValue.trim()
                            println("📋 BarcodeScanner: Usando displayValue: ${textToProcess.length} caracteres")
                        }
                        
                        if (textToProcess != null && textToProcess.isNotBlank()) {
                            // Ignorar se for o mesmo código já processado
                            if (textToProcess == lastProcessedCodeRef()) {
                                println("⏭️ BarcodeScanner: Código já processado, ignorando")
                                continue
                            }
                            println("✅ BarcodeScanner: Chamando onBarcodeDetected com: ${textToProcess.take(50)}...")
                            onBarcodeDetected(textToProcess)
                            return@addOnSuccessListener
                        } else {
                            println("⚠️ BarcodeScanner: Não foi possível extrair valor do QR Code (rawValue, rawBytes e displayValue são null/vazios)")
                            // Continuar para o próximo barcode
                            continue
                        }
                    }
                    
                    // Processamento normal (quando aceitarJSONCompleto = false)
                    if (value != null && value.isNotBlank()) {
                        var cleanValue = value.trim()
                        println("🔍 BarcodeScanner: Valor original: $cleanValue")
                        
                        // Verificar se é JSON (QR code com estrutura JSON) - para devoluções
                        if (cleanValue.startsWith("{") && cleanValue.contains("\"id\"")) {
                            println("📋 BarcodeScanner: JSON detectado, tentando extrair campo 'id'")
                            try {
                                val jsonObject = JSONObject(cleanValue)
                                val idValue = jsonObject.optString("id", "")
                                if (idValue.isNotBlank()) {
                                    cleanValue = idValue
                                    println("✅ BarcodeScanner: ID extraído do JSON: $cleanValue")
                                } else {
                                    println("⚠️ BarcodeScanner: Campo 'id' não encontrado ou vazio no JSON")
                                }
                            } catch (e: Exception) {
                                println("❌ BarcodeScanner: Erro ao parsear JSON: ${e.message}")
                                e.printStackTrace()
                            }
                        } else {
                            println("📊 BarcodeScanner: Não é JSON, tratando como código de barras direto")
                        }
                        
                        // Remover espaços e caracteres especiais
                        cleanValue = cleanValue.replace(" ", "").replace("-", "").replace(".", "")
                        println("🧹 BarcodeScanner: Valor limpo: $cleanValue (tamanho: ${cleanValue.length})")
                        
                        // Verificar se o valor contém apenas números após limpeza
                        if (cleanValue.all { it.isDigit() }) {
                            println("✅ BarcodeScanner: Código contém apenas números")
                            
                            // Ignorar se for o mesmo código válido já processado
                            if (cleanValue == lastProcessedCodeRef()) {
                                println("⏭️ BarcodeScanner: Código válido já processado, ignorando: $cleanValue")
                                continue
                            }
                            
                            // Validar se tem exatamente 11 dígitos
                            if (cleanValue.length == 11) {
                                println("✅ BarcodeScanner: Código válido! Chamando onBarcodeDetected: $cleanValue")
                                onBarcodeDetected(cleanValue)
                                return@addOnSuccessListener
                            } else if (cleanValue.length > 0) {
                                // Código inválido - não tem 11 dígitos
                                // Verificar se este código já foi processado como erro
                                if (cleanValue == lastErrorCodeRef()) {
                                    println("⏭️ BarcodeScanner: Código inválido já processado, ignorando: $cleanValue")
                                    return@addOnSuccessListener
                                }
                                println("❌ BarcodeScanner: Código inválido - tamanho incorreto. Esperado: 11, Encontrado: ${cleanValue.length}")
                                // Não exibir mensagem de erro - apenas logar
                                return@addOnSuccessListener
                            }
                        } else if (cleanValue.isNotBlank()) {
                            // Código contém caracteres não numéricos
                            // Verificar se este código já foi processado como erro
                            if (cleanValue == lastErrorCodeRef()) {
                                println("⏭️ BarcodeScanner: Código inválido já processado, ignorando: $cleanValue")
                                return@addOnSuccessListener
                            }
                            println("❌ BarcodeScanner: Código inválido - contém caracteres não numéricos: $cleanValue")
                            // Não exibir mensagem de erro - apenas logar
                            return@addOnSuccessListener
                        } else {
                            println("⚠️ BarcodeScanner: Valor limpo está vazio")
                        }
                    } else {
                        println("⚠️ BarcodeScanner: Valor do código é null ou vazio")
                    }
                }
                
                println("⚠️ BarcodeScanner: Nenhum código válido encontrado após processamento")
            }
            .addOnFailureListener { exception ->
                println("❌ BarcodeScanner: Erro ao processar imagem: ${exception.message}")
                exception.printStackTrace()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

